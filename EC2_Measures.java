import java.util.HashMap;
import java.util.Map;

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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;


public class EC2_Measures {
	AmazonEC2 ec2;
	AmazonCloudWatch cloudWatch;

	public EC2_Measures() throws Exception {
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-west-2")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-west-2")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	}


	public Map<Instance, Datapoint> getMeasures() {
		Map<Instance, Datapoint> measures = new HashMap<Instance, Datapoint>();

		try {
			DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
			List<Reservation> reservations = describeInstancesResult.getReservations();
			Set<Instance> instances = new HashSet<Instance>();

			System.out.println("total reservations = " + reservations.size());
			for (Reservation reservation : reservations) {
				instances.addAll(reservation.getInstances());
			}
			System.out.println("total instances = " + instances.size());

			/* NOTE total observation time in milliseconds */
			long offsetInMilliseconds = 1000 * 60 * 10;
			Dimension instanceDimension = new Dimension();
			instanceDimension.setName("InstanceId");
			List<Dimension> dims = new ArrayList<Dimension>();
			dims.add(instanceDimension);
			for (Instance instance : instances) {
				String name = instance.getInstanceId();
				String state = instance.getState().getName();
				if (state.equals("running")) {
					instanceDimension.setValue(name);
					GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
							.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
							.withNamespace("AWS/EC2")
							.withPeriod(60).withMetricName("CPUUtilization")
							.withStatistics("Average")
							.withDimensions(instanceDimension)
							.withEndTime(new Date());
					GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
					List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
					for (Datapoint dp : datapoints) { /* TODO why is this in a loop? */
						System.out.println(" CPU utilization for instance " + name + " = " + dp.getAverage());
						measures.put(instance, dp);
					}
				}
			}
			return measures;
		} catch (AmazonServiceException ase) {
			System.out.println("Caught Exception: " + ase.getMessage());
			System.out.println("Reponse Status Code: " + ase.getStatusCode());
			System.out.println("Error Code: " + ase.getErrorCode());
			System.out.println("Request ID: " + ase.getRequestId());
		}
	}
}