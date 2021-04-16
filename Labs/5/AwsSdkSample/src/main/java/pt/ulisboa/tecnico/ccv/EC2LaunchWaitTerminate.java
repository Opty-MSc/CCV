package pt.ulisboa.tecnico.ccv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EC2LaunchWaitTerminate {

  // TODO: Fill Constants
  private static final String INSTANCE_ID = null;
  private static final String INSTANCE_TYPE = null;
  private static final String KEY_NAME = null;
  private static final String SECURITY_GROUP = null;
  private static final Integer N_INSTANCES = null;
  private static AmazonEC2 ec2;

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
  }

  public static void main(String[] args) throws Exception {

    System.out.println("================================");
    System.out.println("| Welcome to the AWS Java SDK! |");
    System.out.println("================================");
    init();
    try {
      DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
      System.out.println(
          "You have access to "
              + availabilityZonesResult.getAvailabilityZones().size()
              + " Availability Zones.");
      printNrInstances();
      System.out.println("Starting a new instance.");
      RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
      runInstancesRequest
          .withImageId(INSTANCE_ID)
          .withInstanceType(INSTANCE_TYPE)
          .withMinCount(N_INSTANCES)
          .withMaxCount(N_INSTANCES)
          .withKeyName(KEY_NAME)
          .withSecurityGroups(SECURITY_GROUP);
      RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
      String newInstanceId =
          runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
      printNrInstances();
      System.out.println("Waiting 1 minute. See your instance in the AWS console...");
      Thread.sleep(60000);
      System.out.println("Terminating the instance.");
      TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
      termInstanceReq.withInstanceIds(newInstanceId);
      ec2.terminateInstances(termInstanceReq);

    } catch (AmazonServiceException ase) {
      System.out.println("Caught Exception: " + ase.getMessage());
      System.out.println("Response Status Code: " + ase.getStatusCode());
      System.out.println("Error Code: " + ase.getErrorCode());
      System.out.println("Request ID: " + ase.getRequestId());
    }
  }

  private static void printNrInstances() {
    Set<Instance> instances;
    DescribeInstancesResult describeInstancesRequest;
    List<Reservation> reservations;
    describeInstancesRequest = ec2.describeInstances();
    reservations = describeInstancesRequest.getReservations();
    instances = new HashSet<>();

    for (Reservation reservation : reservations) {
      instances.addAll(reservation.getInstances());
    }

    System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");
  }
}
