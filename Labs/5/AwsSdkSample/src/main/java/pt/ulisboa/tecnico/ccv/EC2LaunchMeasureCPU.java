package pt.ulisboa.tecnico.ccv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EC2LaunchMeasureCPU {

  // TODO: Fill Constants
  private static final String INSTANCE_ID = null;
  private static final String INSTANCE_TYPE = null;
  private static final String KEY_NAME = null;
  private static final String SECURITY_GROUP = null;
  private static final Integer N_INSTANCES = null;
  private static AmazonEC2 ec2;
  private static AmazonCloudWatch cloudWatch;

  private static void init() {

    AWSCredentials credentials;
    try {
      credentials = new ProfileCredentialsProvider().getCredentials();
    } catch (Exception e) {
      throw new AmazonClientException(
          "Cannot load the credentials from the credential profiles file. "
              + "Please make sure that your credentials file is at the correct "
              + "location (~/.aws/credentials), and is in valid format.",
          e);
    }
    ec2 =
        AmazonEC2ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();

    cloudWatch =
        AmazonCloudWatchClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
  }

  public static void main(String[] args) {
    System.out.println("================================");
    System.out.println("| Welcome to the AWS Java SDK! |");
    System.out.println("================================");
    if (args.length < 1) {
      System.out.println("Missing argument <startInstance>. Exiting...");
      System.exit(1);
    } else if (!args[0].equals("1") && !args[0].equals("0")) {
      System.out.println("Argument <startInstance> must be 0 or 1. Exiting...");
      System.exit(1);
    }
    boolean startInstance = args[0].equals("1");
    init();
    try {
      if (startInstance) {
        System.out.println("Starting a new instance.");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest
            .withImageId(INSTANCE_ID)
            .withInstanceType(INSTANCE_TYPE)
            .withMinCount(N_INSTANCES)
            .withMaxCount(N_INSTANCES)
            .withKeyName(KEY_NAME)
            .withSecurityGroups(SECURITY_GROUP);
      }
      DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
      List<Reservation> reservations = describeInstancesResult.getReservations();
      Set<Instance> instances = new HashSet<>();

      System.out.println("total reservations = " + reservations.size());
      for (Reservation reservation : reservations) {
        instances.addAll(reservation.getInstances());
      }
      System.out.println("total instances = " + instances.size());
      long offsetInMilliseconds = 1000 * 60 * 10;
      Dimension instanceDimension = new Dimension();
      instanceDimension.setName("InstanceId");
      for (Instance instance : instances) {
        String name = instance.getInstanceId();
        String state = instance.getState().getName();
        if (state.equals("running")) {
          System.out.println("running instance id = " + name);
          instanceDimension.setValue(name);
          GetMetricStatisticsRequest request =
              new GetMetricStatisticsRequest()
                  .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                  .withNamespace("AWS/EC2")
                  .withPeriod(60)
                  .withMetricName("CPUUtilization")
                  .withStatistics("Average")
                  .withDimensions(instanceDimension)
                  .withEndTime(new Date());
          GetMetricStatisticsResult getMetricStatisticsResult =
              cloudWatch.getMetricStatistics(request);
          for (Datapoint dp : getMetricStatisticsResult.getDatapoints()) {
            System.out.println(" CPU utilization for instance " + name + " = " + dp.getAverage());
          }
        } else {
          System.out.println("instance id = " + name);
        }
        System.out.println("Instance State : " + state + ".");
      }
    } catch (AmazonServiceException ase) {
      System.out.println("Caught Exception: " + ase.getMessage());
      System.out.println("Response Status Code: " + ase.getStatusCode());
      System.out.println("Error Code: " + ase.getErrorCode());
      System.out.println("Request ID: " + ase.getRequestId());
    }
  }
}
