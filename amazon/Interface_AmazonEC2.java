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

	public Map<Instance, Double> getCPULoad() {
		Map<Instance, Double> measures = new HashMap<Instance, Double>();

		try {
			DescribeInstancesResult describeInstancesResult = this.ec2.describeInstances();
			List<Reservation> reservations = describeInstancesResult.getReservations();
			Set<Instance> instances = new HashSet<Instance>();

			for (Reservation reservation : reservations) {
				instances.addAll(reservation.getInstances());
			}

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
						System.out.println("Reading CPU Utilization from " + instance.getInstanceId() + ": " + dp.getAverage());
						measures.put(instance, dp.getAverage());
					}
				}
			}
		} catch (AmazonServiceException ase) {
			System.err.println("Caught Exception: " + ase.getMessage());
			System.err.println("Reponse Status Code: " + ase.getStatusCode());
			System.err.println("Error Code: " + ase.getErrorCode());
			System.err.println("Request ID: " + ase.getRequestId());
		}
		return measures;
	}

	public void createTable(String tableName) {
		try {
			CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
					.withKeySchema(new KeySchemaElement().withAttributeName("name").withKeyType(KeyType.HASH))
					.withAttributeDefinitions(new AttributeDefinition().withAttributeName("name")
							.withAttributeType(ScalarAttributeType.S))
					.withProvisionedThroughput(
							new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);
			System.out.println("Table " + tableName + " created sucessfully");
            // Describe our new table
            DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
            TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
            System.out.println("Table Description: " + tableDescription);
		} catch (AmazonServiceException ase) {
			System.err.println(
					"Caugerran AmazonServiceException, which means your request made it to AWS, but was rejected with an error response for some reason.");
			System.err.println("Error Message:    " + ase.getMessage());
			System.err.println("HTTP Status Code: " + ase.getStatusCode());
			System.err.println("AWS Error Code:   " + ase.getErrorCode());
			System.err.println("Error Type:       " + ase.getErrorType());
			System.err.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.err.println(
					"Caugerran AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with AWS, such as not being able to access the network.");
			System.err.println("Error Message: " + ace.getMessage());
		} catch (InterruptedException ie) {
			System.err.println("Caught Interrupted Exception.");
			System.err.println("Error Message: " + ie.getMessage());

		}

	}

}
