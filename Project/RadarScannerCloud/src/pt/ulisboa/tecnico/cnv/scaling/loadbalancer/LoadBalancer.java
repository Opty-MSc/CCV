package pt.ulisboa.tecnico.cnv.scaling.loadbalancer;

import pt.ulisboa.tecnico.cnv.scaling.ScalingInstance;
import pt.ulisboa.tecnico.cnv.util.LoggerFormatter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Contains the LoadBalancer Logic. */
public class LoadBalancer {

  public static final Integer CAPACITY = 1000;
  private static final Logger logger = Logger.getLogger(LoadBalancer.class.getName());
  private final Set<String> queries;
  private final UserRequestsCosts uRequestsCosts;
  private final MSS mss;
  private final Map<String, ScalingInstance> instances;

  public LoadBalancer(Map<String, ScalingInstance> instances, Level level) {
    logger.setLevel(level);
    logger.setUseParentHandlers(false);
    ConsoleHandler loggerHandler = new ConsoleHandler();
    loggerHandler.setFormatter(new LoggerFormatter());
    logger.addHandler(loggerHandler);
    this.queries = new HashSet<>();
    this.uRequestsCosts = new UserRequestsCosts();
    this.mss = new MSS();
    this.instances = instances;
    this.fetchWithoutFilter();
    this.newTimer();
  }

  /** Obtains a predefined capacity of Costs associated with its Requests. */
  private void fetchWithoutFilter() {
    Map<UserRequest, Double> uRequestsCostsMap = this.mss.fetchWithoutFilter();
    logger.info(String.format("User Requests Fetched: %d", uRequestsCostsMap.size()));
    this.uRequestsCosts.putAll(uRequestsCostsMap);
  }

  /**
   * Gets the Costs associated with the most recently received Requests. Place them in the Cache.
   */
  private void fetchWithFilter() {
    Map<UserRequest, Double> uRequestsCostsMap = this.mss.fetchWithFilter(this.queries);
    this.queries.clear();
    logger.info(String.format("User Requests Fetched: %d", uRequestsCostsMap.size()));
    this.uRequestsCosts.putAll(uRequestsCostsMap);
  }

  /**
   * Gets the estimated Cost of the Incoming Request and the WebServer Instance that has the lowest
   * associated Cost, and adds the estimated Cost to it.
   *
   * @param uRequest Incoming Request.
   * @return Instance of the WebServer that has the lowest associated Cost and the estimated Cost of
   *     the Incoming Request.
   */
  public synchronized Map.Entry<ScalingInstance, Double> onReceiveRequest(UserRequest uRequest) {
    double estimatedCost = this.uRequestsCosts.getEstimatedCost(uRequest);
    logger.info(String.format("Estimated Cost of %s for the Request: %s", estimatedCost, uRequest));

    ScalingInstance minInstance = null;
    for (ScalingInstance instance : this.instances.values()) {
      if ((minInstance == null || instance.getCurrentCost() < minInstance.getCurrentCost())
          && instance.isHealthy()) {
        minInstance = instance;
      }
    }
    if (minInstance == null) {
      logger.warning("No Instances Available!");
      return null;
    }
    logger.info(String.format("Redirecting Request to Instance %s", minInstance.getInstanceId()));
    minInstance.addCost(estimatedCost);
    return new AbstractMap.SimpleEntry<>(minInstance, estimatedCost);
  }

  /**
   * Creates a Timer responsible for carrying out Health Checks and fetching the Costs associated
   * with the most recent Incoming Requests, from 30 to 30 seconds.
   */
  private void newTimer() {
    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                healthChecks();
                fetchWithFilter();
              }
            },
            0,
            30000);
  }

  /**
   * Performs a Health Check for each running WebServer Instance and records whether it successfully
   * responds to the Health Check or not. Detects an unhealthy Instance, removes it from the
   * available WebServer instances to respond to Scan Requests.
   */
  private synchronized void healthChecks() {
    for (ScalingInstance instance : this.instances.values()) {
      HttpURLConnection con = null;
      Integer responseCode = null;
      try {
        con =
            (HttpURLConnection)
                new URL(String.format("http://%s:%d/health", instance.getPublicDnsName(), 8000))
                    .openConnection();
        con.setRequestMethod("GET");
        responseCode = con.getResponseCode();
      } catch (IOException ignored) {
      }
      if (con != null && responseCode != null && responseCode.equals(HttpURLConnection.HTTP_OK)) {
        logger.info(String.format("Instance: %s - HealthCheck Succeed!", instance.getInstanceId()));
        instance.registerHealthyCheck();
      } else {
        logger.warning(
            String.format("Instance: %s - HealthCheck Failed!", instance.getInstanceId()));
        instance.registerUnhealthyCheck();
      }
    }
  }

  /**
   * Removes the estimated Cost of the Incoming Request from the Costs associated with the WebServer
   * Instance from which it was forwarded and records that its forwarding was unsuccessful,
   * registering it as a failed Health Check.
   *
   * @param instanceRequestCost WebServer Instance that responded to the Incoming Request and its
   *     estimated Cost.
   */
  public void onInstanceFailure(Map.Entry<ScalingInstance, Double> instanceRequestCost) {
    logger.warning(
        String.format("Instance %s Bad Reply!", instanceRequestCost.getKey().getInstanceId()));
    ScalingInstance instance = instanceRequestCost.getKey();
    instance.removeCost(instanceRequestCost.getValue());
    instance.registerUnhealthyCheck();
  }

  /**
   * Removes the estimated Cost of the Incoming Request from the Costs associated with the WebServer
   * Instance from which it was forwarded, adds the Request Query to the set of Queries made
   * recently, which will later be used to fetch the actual Cost that the request imposed on the
   * Instance and records that its forwarding was successful, registering it as a successful Health
   * Check.
   *
   * @param instanceRequestCost WebServer Instance that responded to the Incoming Request and its
   *     estimated Cost.
   */
  public void onInstanceSuccess(
      String query, Map.Entry<ScalingInstance, Double> instanceRequestCost) {
    logger.info(
        String.format(
            "Instance %s Successfully Replied!", instanceRequestCost.getKey().getInstanceId()));
    this.queries.add(query);
    ScalingInstance instance = instanceRequestCost.getKey();
    instance.removeCost(instanceRequestCost.getValue());
    instance.registerHealthyCheck();
  }
}
