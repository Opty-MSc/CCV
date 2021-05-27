package pt.ulisboa.tecnico.cnv.scaling.autoscaler;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import pt.ulisboa.tecnico.cnv.scaling.ScalingInstance;
import pt.ulisboa.tecnico.cnv.util.LoggerFormatter;

import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Manages the creation and destruction of Instances. */
public class AutoScaler implements Runnable {

  private static final Logger logger = Logger.getLogger(AutoScaler.class.getName());
  private static final int COST_THRESHOLD_MIN = 1100000;
  private static final int COST_THRESHOLD_MAX = 3000000;
  private static final int CPU_THRESHOLD_MIN = 30;
  private static final int CPU_THRESHOLD_MAX = 80;
  private static final int MIN_INSTANCES = 2;
  private final String ami;
  private final String keyName;
  private final String securityGroup;
  private final Map<String, ScalingInstance> instances;
  private AmazonEC2 ec2;
  private AmazonCloudWatch cloudWatch;

  public AutoScaler(
      Map<String, ScalingInstance> instances,
      String ami,
      String keyName,
      String securityGroup,
      Level level) {
    logger.setLevel(level);
    logger.setUseParentHandlers(false);
    ConsoleHandler loggerHandler = new ConsoleHandler();
    loggerHandler.setFormatter(new LoggerFormatter());
    logger.addHandler(loggerHandler);
    this.ami = ami;
    this.keyName = keyName;
    this.securityGroup = securityGroup;
    this.instances = instances;
    this.newAWS();
  }

  /**
   * Initializes AWS EC2 and CloudWatch with the credentials of our AWS account, stored in the
   * ~/.aws Directory.
   */
  private void newAWS() {
    try {
      AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
      this.ec2 =
          AmazonEC2ClientBuilder.standard()
              .withRegion(Regions.US_EAST_1)
              .withCredentials(new AWSStaticCredentialsProvider(credentials))
              .build();
      this.cloudWatch =
          AmazonCloudWatchClientBuilder.standard()
              .withRegion(Regions.US_EAST_1)
              .withCredentials(new AWSStaticCredentialsProvider(credentials))
              .build();
    } catch (Exception e) {
      throw new AmazonClientException("Bad Credentials", e);
    }
  }

  /** Initializes the AutoScaler Timer. */
  @Override
  public void run() {
    this.autoScalerBoot();
    this.newTimer();
  }

  /** Creates the minimum number of Instances for the System to start operating. */
  private void autoScalerBoot() {
    List<Thread> newInstanceThreads = new ArrayList<>();
    for (int i = 0; i < MIN_INSTANCES; i++) {
      Thread newInstanceThread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  newInstance();
                }
              });
      newInstanceThread.start();
      newInstanceThreads.add(newInstanceThread);
    }
    for (Thread newInstanceThread : newInstanceThreads) {
      try {
        newInstanceThread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Creates a Timer that performs automatic Scaling. Every 10 seconds, it checks the current
   * associated Cost of the Instances. Every 30 seconds, it will check the CPU Utilization in AWS.
   * Each time it performs an action, it will have a 30 seconds timeout before another action can be
   * performed.
   */
  private void newTimer() {
    new Timer()
        .schedule(
            new TimerTask() {
              private boolean doScale = true;
              private int i = 0;

              @Override
              public synchronized void run() {
                checkUnhealthy();
                if (!doScale) return;
                if (i >= 3) i = 0;
                boolean toCheckCPU = i++ == 0;
                boolean tookAction = autoScale(toCheckCPU);
                if (tookAction) {
                  doScale = false;
                  new Timer()
                      .schedule(
                          new TimerTask() {
                            @Override
                            public void run() {
                              doScale = true;
                              i = 0;
                            }
                          },
                          30000);
                }
              }
            },
            0,
            10000);
  }

  /** Detects Instances marked as Unhealthy by the LoadBalancer and replaces them. */
  private void checkUnhealthy() {

    for (ScalingInstance instance : this.instances.values()) {
      if (instance.isUnhealthy()) {
        logger.warning(
            String.format("Instance %s Unhealthy: Replacing it!", instance.getInstanceId()));
        this.instances.remove(instance.getInstanceId());
        this.removeInstance(instance.getInstanceId());
        this.newInstance();
      }
    }
  }

  /**
   * Calculates the sum of the Costs of all Instances, if it exceeds a certain Threshold, a new
   * Instance is created, if it does not reach a certain Threshold, the Instance with associated
   * minimum Cost is terminated. If no action has been taken, when it is necessary to check the CPU
   * Utilization, it takes an approach similar to the one it did for the Costs of the Instances.
   *
   * @param toCheckCPU True if it is to verify the CPU Utilization, otherwise, False.
   * @return True if an action was taken, otherwise, False.
   */
  private boolean autoScale(boolean toCheckCPU) {

    double sumCosts = 0.0;
    Double minCost = null;
    String minCostInstanceId = null;

    for (ScalingInstance instance : this.instances.values()) {
      logger.info(
          String.format(
              "Instance %s: Current Cost of %.1f",
              instance.getInstanceId(), instance.getCurrentCost()));
      sumCosts += instance.getCurrentCost();
      if (minCost == null || minCost > instance.getCurrentCost()) {
        minCostInstanceId = instance.getInstanceId();
        minCost = instance.getCurrentCost();
      }
    }

    if (this.takeAction(sumCosts, COST_THRESHOLD_MAX, COST_THRESHOLD_MIN, minCostInstanceId))
      return true;

    if (toCheckCPU) {
      double sumCPUs = 0.0;
      Double minCPU = null;
      String minCPUInstanceId = null;

      for (ScalingInstance instance : this.instances.values()) {
        Dimension dimension = new Dimension();
        dimension.setName("InstanceId");
        dimension.setValue(instance.getInstanceId());
        GetMetricStatisticsRequest request =
            new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - 600000))
                .withEndTime(new Date())
                .withPeriod(60)
                .withNamespace("AWS/EC2")
                .withMetricName("CPUUtilization")
                .withStatistics(Statistic.Average)
                .withDimensions(dimension);
        GetMetricStatisticsResult result;
        try {
          result = this.cloudWatch.getMetricStatistics(request);
        } catch (Exception e) {
          logger.warning(
              String.format(
                  "Unable to GetMetricsStatistics of Instance %s", instance.getInstanceId()));
          continue;
        }

        Datapoint latestDp = null;
        for (Datapoint dp : result.getDatapoints()) {
          if (latestDp == null || latestDp.getTimestamp().before(dp.getTimestamp())) {
            latestDp = dp;
          }
        }
        if (latestDp == null) {
          logger.warning("Insufficient CPU Utilization Metrics to Perform Action!");
          return false;
        }
        logger.info(
            String.format(
                "Instance %s: CPU Utilization of %.1f",
                instance.getInstanceId(), latestDp.getAverage()));

        sumCPUs += latestDp.getAverage();
        if (minCPU == null || minCPU > latestDp.getAverage()) {
          minCPUInstanceId = instance.getInstanceId();
          minCPU = latestDp.getAverage();
        }
      }
      return this.takeAction(sumCPUs, CPU_THRESHOLD_MAX, CPU_THRESHOLD_MIN, minCPUInstanceId);
    }
    return false;
  }

  /**
   * Creates an Instance if valueSum is greater than valueThresholdMax times the number of
   * Instances. Removes the Instance with minValueInstanceId if valueSum is less than
   * valueThresholdMin times the number of Instances and there is more than one Instance.
   *
   * @param valueSum Sum of associated Cost or CPU Utilization of all Instances.
   * @param valueThresholdMax Maximum Threshold of associated Cost or CPU Utilization.
   * @param valueThresholdMin Minimum Threshold of associated Cost or CPU Utilization.
   * @param minValueInstanceId Id of the Instance that has the lowest associated Cost or CPU
   *     Utilization.
   * @return True if an action was taken, otherwise, False.
   */
  private boolean takeAction(
      double valueSum, int valueThresholdMax, int valueThresholdMin, String minValueInstanceId) {

    if (valueSum > valueThresholdMax * this.instances.size()) {
      this.newInstance();
      return true;
    }
    if (valueSum < valueThresholdMin * this.instances.size()
        && this.instances.size() > MIN_INSTANCES) {
      this.instances.remove(minValueInstanceId);
      this.removeInstance(minValueInstanceId);
      return true;
    }
    return false;
  }

  /**
   * Creates a new WebServer Instance using the Image Id, the Key Pair Name and the Security Group
   * provided.
   */
  private void newInstance() {
    RunInstancesRequest runInstancesRequest =
        new RunInstancesRequest()
            .withImageId(this.ami)
            .withInstanceType(InstanceType.T2Micro)
            .withMinCount(1)
            .withMaxCount(1)
            .withKeyName(this.keyName)
            .withSecurityGroups(this.securityGroup)
            .withMonitoring(true);

    String instanceId =
        ec2.runInstances(runInstancesRequest)
            .getReservation()
            .getInstances()
            .get(0)
            .getInstanceId();

    logger.warning(String.format("Creating new Instance %s!", instanceId));
    this.addRunningInstance(new DescribeInstanceStatusRequest().withInstanceIds(instanceId));
  }

  /**
   * It waits until the Instance is running. If the Instance evolves to a state other than running,
   * i.e. its creation failed. It tries to create it again.
   *
   * @param request It is a StatusRequest associated with the newly created Instance to use to
   *     request its status.
   */
  private void addRunningInstance(DescribeInstanceStatusRequest request) {
    while (true) {
      List<InstanceStatus> instanceStatuses =
          ec2.describeInstanceStatus(request).getInstanceStatuses();
      if (!instanceStatuses.isEmpty()) {
        switch (instanceStatuses.get(0).getInstanceState().getName()) {
          case "running":
            String instanceId = request.getInstanceIds().get(0);
            logger.info(String.format("Detected Instance %s is Running!", instanceId));
            this.instances.put(instanceId, new ScalingInstance(this.fetchInstance(instanceId)));
            return;
          case "pending":
            break;
          default:
            this.newInstance();
        }
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignored) {
      }
    }
  }

  /**
   * Gets the the newly created Instance based on its Id.
   *
   * @param instanceId Id of the newly created Instance.
   * @return Fetched Instance.
   */
  private Instance fetchInstance(String instanceId) {
    for (Reservation reservation : ec2.describeInstances().getReservations()) {
      for (Instance instance : reservation.getInstances()) {
        if (instance.getInstanceId().equals(instanceId)) return instance;
      }
    }
    return null;
  }

  /**
   * Terminates an Instance based on its Id.
   *
   * @param instanceId Id of the Instance to be terminated.
   */
  private void removeInstance(String instanceId) {
    logger.warning(String.format("Removing Instance %s!", instanceId));
    this.ec2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(instanceId));
  }
}
