import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;


/**
 * Provides a way to measure AWS EC2 Instance stats.
 * Dont forget to setup the file ~/.aws/credentials with correct format and AWS credentials.
 */
public class Interface_AmazonEC2 {
	AmazonEC2 ec2;
	AmazonCloudWatch cloudWatch;
	AmazonDynamoDBClient dynamoDB;

	public Interface_AmazonEC2() {
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		this.ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-west-2")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		this.cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-west-2")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		this.dynamoDB = new AmazonDynamoDBClient(credentials);
		// NOTE I suspect this will cause the LoadBalancer to fail on other regions
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		dynamoDB.setRegion(usWest2);
	}

	public Set<Instance> getInstances() {
		DescribeInstancesResult describeInstancesResult = this.ec2.describeInstances();
		List<Reservation> reservations = describeInstancesResult.getReservations();
		Set<Instance> instances = new HashSet<Instance>();

		for (Reservation reservation : reservations) {
			instances.addAll(reservation.getInstances());
		}
		return instances;
	}

	/**
	 * Get CPULoad of all running Instances
	 * @return Map with CPULoad for each Instance
	 */
	public Map<Instance, Double> getCPULoad() {
		Map<Instance, Double> measures = new HashMap<Instance, Double>();

		try {
			Set<Instance> instances = this.getInstances();

			/* total observation time in milliseconds */
			long offsetInMilliseconds = 1000 * 60 * 10;
			Dimension instanceDimension = new Dimension();
			instanceDimension.setName("InstanceId");
			List<Dimension> dims = new ArrayList<Dimension>();
			dims.add(instanceDimension);
			for (Instance instance : instances) {
				if (instance.getState().getName().equals("running")) {
					instanceDimension.setValue(instance.getInstanceId());
					GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
							.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
							.withNamespace("AWS/EC2")
							.withPeriod(60)
							.withMetricName("CPUUtilization")
							.withStatistics("Average")
							.withDimensions(instanceDimension)
							.withEndTime(new Date());
					GetMetricStatisticsResult getMetricStatisticsResult = this.cloudWatch.getMetricStatistics(request);
					List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
					for (Datapoint dp : datapoints) {
						measures.put(instance, dp.getAverage());
					}
				}
			}
		} catch (AmazonServiceException ase) {
			System.err.println("Caught AmazonServiceException: " + ase.getMessage());
		}
		return measures;
	}

	/**
	 * Creates a DynamoDB Table with given name
	 * @param String table name
	 */
	public void createTable(String tableName) {
		try {
			CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
					.withKeySchema(new KeySchemaElement().withAttributeName("query").withKeyType(KeyType.HASH))
					.withAttributeDefinitions(new AttributeDefinition().withAttributeName("query")
							.withAttributeType(ScalarAttributeType.S))
					.withProvisionedThroughput(
							new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);
		} catch (AmazonServiceException ase) {
			System.err.println(
					"Caugerran AmazonServiceException, which means your request made it to AWS, but was rejected with an error response for some reason.");
			System.err.println("Error Message:    " + ase.getMessage());
		} catch (AmazonClientException ace) {
			System.err.println(
					"Caught an AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with AWS, such as not being able to access the network.");
			System.err.println("Error Message: " + ace.getMessage());
		} catch (InterruptedException ie) {
			System.err.println("Caught Interrupted Exception.");
			System.err.println("Error Message: " + ie.getMessage());
		}
	}

	/**
	 * This function creates a TableEntry Item with Table Entries given
	 * @param String Key item used, XXX in this case it must be the query
	 * @param TableEntry... Arbitrary number of Table Entry, the number of these should match table format...
	 */
	public Map<String, AttributeValue> makeItem(String keyItem, TableEntry... args) {
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("query", new AttributeValue(keyItem));
		for (TableEntry entry : args) {
			item.put(entry.key, new AttributeValue(entry.value));
		}
		return item;
	}

	/**
	 * Add entry to table
	 * @param String name of table where to insert the data
	 * @param Map<String, AttributeValue> item to be inserted
	 */
	public void addTableEntry(String tableName, Map<String, AttributeValue> item) {
		try {
			PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
			PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
		} catch (AmazonClientException e) {
			System.out.println("Failed to insert into table " + tableName + ": " + e.getMessage());
		}
	}

	/**
	 * Scan a table by query, this query is the main key used to index the table
	 *  so only one result is returned
	 * @param String table to acess
	 * @param String query
	 * @return Map representing table entry or null if query doesn't exist
	 */
	public Map<String, AttributeValue> scanTableByQuery(String tableName, String query) {
		try {
			HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			Condition condition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
					.withAttributeValueList(new AttributeValue(query));
			scanFilter.put("query", condition);
			ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			return scanResult.getCount() > 0 ? scanResult.getItems().get(0) : null;
		} catch (AmazonClientException e) {
			System.out.println("Failed to scan table " + tableName + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Scan table by equal values in same column
	 * @param String table to acess
	 * @param String column to search for
	 * @param String value to compare with in the column
	 * @return List of Maps representing entries in the table
	 */
	public List<Map<String, AttributeValue>> scanTableEqualValues(String tableName, String column, String value) {
		try {
			HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			Condition condition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString()).withAttributeValueList(new AttributeValue(value));
			scanFilter.put(column, condition);
			ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			return scanResult.getCount() > 0 ? scanResult.getItems() : null;
		} catch (AmazonClientException e) {
			System.out.println("Failed to scan table " + tableName + ": " + e.getMessage());
			return null;
		}
	}
	/**
	 * Scan table for lower and higher values
	 * Usefull to make interpolation
	 * @param String table to acess
	 * @param String column to search for
	 * @param String value to compare with in the column
	 * @return Return list with entries, the first element is the upper bound and the second is the lower bound
	 */
	public List<Map<String, AttributeValue>> scanTableBoundValues(String tableName, String column, String value) {
		try {
			// output list
			List<Map<String, AttributeValue>> finalResult = new ArrayList<Map<String, AttributeValue>>();
			// scan higher values
			HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			Condition condition = new Condition().withComparisonOperator(ComparisonOperator.GT.toString()).withAttributeValueList(new AttributeValue(value));
			scanFilter.put(column, condition);
			ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			// select lowest from the higher values
			Double lowerColumn = new Double(0);
			Map<String,AttributeValue> lowerEntry = null;
			if (scanResult.getCount() > 0) {
				for (Map<String,AttributeValue> item : scanResult.getItems()) {
					Double val = Double.valueOf(item.get(column).getS());
					if (val > lowerColumn) {
						lowerColumn = val;
						lowerEntry = item;
					}
				}
			}
			if (lowerEntry == null) return null; // upper bound doesnt exist so return null
			else finalResult.add(lowerEntry); // insert upper bound on index 0

			// scan lower values
			scanFilter = new HashMap<String, Condition>();
			condition = new Condition().withComparisonOperator(ComparisonOperator.LT.toString()).withAttributeValueList(new AttributeValue(value));
			scanFilter.put(column, condition);
			scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
			scanResult = dynamoDB.scan(scanRequest);
			// select higher from the lower values
			Double higherColumn = null;
			Map<String,AttributeValue> higherEntry = null;
			if (scanResult.getCount() > 0) {
				for (Map<String,AttributeValue> item : scanResult.getItems()) {
					Double val = Double.valueOf(item.get(column).getS());
					if (higherColumn == null || val < higherColumn) {
						higherColumn = val;
						higherEntry = item;
					}
				}
			}
			if (higherEntry == null) return null; // lower bound not found return null
			else finalResult.add(higherEntry); // add lower bound to index 1

			return finalResult;

		} catch (AmazonClientException e) {
			System.out.println("Failed to scan table " + tableName + ": " + e.getMessage());
			return null;
		}
	}
}
