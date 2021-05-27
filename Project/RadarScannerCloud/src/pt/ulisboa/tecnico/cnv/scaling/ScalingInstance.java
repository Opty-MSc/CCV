package pt.ulisboa.tecnico.cnv.scaling;

import com.amazonaws.services.ec2.model.Instance;

/** Keeps track of an AWS Instance, its State and current estimated Cost. */
public class ScalingInstance {

  private static final int UNHEALTHY_THRESHOLD = 2;
  private static final int HEALTHY_THRESHOLD = 4;
  private final Instance instance;
  private double currentCost;
  private int nHealthy;
  private int nUnhealthy;
  private boolean isUp;

  public ScalingInstance(Instance instance) {
    this.instance = instance;
    this.currentCost = 0.0;
    this.nHealthy = 0;
    this.nUnhealthy = 0;
    this.isUp = false;
  }

  /**
   * Adds the estimated Cost of the Request that will be forwarded to this WebServer Instance to its
   * current associated Cost.
   *
   * @param cost Incoming Request estimated Cost.
   */
  public synchronized void addCost(double cost) {
    this.currentCost += cost;
  }

  /**
   * Removes the estimated Cost of the Request that this WebServer Instance has finalized from its
   * associated current Cost.
   *
   * @param cost Incoming Request estimated Cost.
   */
  public synchronized void removeCost(double cost) {
    this.currentCost -= cost;
  }

  /**
   * Records a successful Health Check. If the Instance is already Healthy, it returns. Otherwise,
   * it will set the Instance to be initialized and increase the number of successful Health Checks,
   * that when equal to or greater than the HealthyThreshold, the Instance is defined as Healthy.
   */
  public synchronized void registerHealthyCheck() {
    if (this.isHealthy()) return;
    this.isUp = true;
    if (++this.nHealthy >= HEALTHY_THRESHOLD) this.nUnhealthy = 0;
  }

  /**
   * Records an unsuccessful Health Check. If the Instance has not yet been initialized, it returns.
   * Otherwise, it will increase the number of unsuccessful Health Checks and redefine the number of
   * successful ones.
   */
  public synchronized void registerUnhealthyCheck() {
    if (!this.isUp) return;
    this.nUnhealthy++;
    this.nHealthy = 0;
  }

  /**
   * Checks whether the Instance is Healthy. The Instance is Healthy when it has already been
   * initialized and the number of unsuccessful Health Checks is 0.
   *
   * @return True if the Instance is Healthy, otherwise, False.
   */
  public synchronized boolean isHealthy() {
    return this.isUp && this.nUnhealthy == 0;
  }

  /**
   * Checks whether the Instance is Unhealthy. The Instance is Unhealthy when the number of
   * unsuccessful Health Checks is greater than or equal to the UnhealthyThreshold.
   *
   * @return True if the Instance is Unhealthy, otherwise, False.
   */
  public synchronized boolean isUnhealthy() {
    return this.nUnhealthy >= UNHEALTHY_THRESHOLD;
  }

  /**
   * Gets the Public DNS Name for this Instance.
   *
   * @return Instance Public DNS Name.
   */
  public String getPublicDnsName() {
    return this.instance.getPublicDnsName();
  }

  /**
   * Gets the Id of this Instance.
   *
   * @return Instance Id.
   */
  public String getInstanceId() {
    return this.instance.getInstanceId();
  }

  /**
   * Gets the current estimated Cost associated with this Instance.
   *
   * @return Instance current estimated Cost.
   */
  public double getCurrentCost() {
    return this.currentCost;
  }

  /**
   * Gets the State of this Instance at the current time.
   *
   * @return Instance State.
   */
  @Override
  public String toString() {
    return String.format(
        "InstanceState{ instance=%s, currentCost=%.1f, nUnhealthy=%d, nHealthy=%d }",
        instance.getInstanceId(), currentCost, nUnhealthy, nHealthy);
  }
}
