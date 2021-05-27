package pt.ulisboa.tecnico.cnv.scaling.loadbalancer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the most recently received Requests and Costs obtained from the MSS. */
public class UserRequestsCosts {

  private static final int DELTA = 20;
  private final Map<UserRequest, Double> cache;

  public UserRequestsCosts() {
    this.cache = new LinkedHashMap<>(LoadBalancer.CAPACITY);
  }

  /**
   * Gets the Cost associated with a UserRequest, if it exists, and places the Request at the
   * beginning of the cache.
   *
   * @return The Cost associated with a UserRequest, or null if it does not exist.
   */
  public synchronized Double get(UserRequest uRequest) {
    Double cost = this.cache.get(uRequest);

    if (cost != null) {
      this.cache.remove(uRequest);
      this.cache.put(uRequest, cost);
    }
    return cost;
  }

  /**
   * Puts a UserRequest and its Cost in the cache, if it is not present. If the cache has reached
   * its limit, it removes the oldest member (LRU Policy).
   */
  public synchronized void put(UserRequest uRequest, double cost) {
    if (this.get(uRequest) != null) return;

    if (this.cache.size() == LoadBalancer.CAPACITY) {
      this.cache.remove(this.cache.keySet().iterator().next());
    }
    this.cache.put(uRequest, cost);
  }

  /** Puts all UserRequests and their Costs at the end of the cache. */
  public void putAll(Map<UserRequest, Double> uRequestsCosts) {
    for (Map.Entry<UserRequest, Double> uRequestsCost : uRequestsCosts.entrySet()) {
      this.cache.remove(uRequestsCost.getKey());
      this.put(uRequestsCost.getKey(), uRequestsCost.getValue());
    }
  }

  /**
   * Gets the estimated Cost of a UserRequest. If the Request exists in the cache, it will return
   * its Cost. Otherwise, it will find the most similar UserRequest in the cache.
   *
   * @return The estimated Cost of the Incoming Request.
   */
  public synchronized double getEstimatedCost(UserRequest uRequest) {
    Double cost = this.get(uRequest);
    if (cost != null) return cost;

    UserRequest closestUserRequest = null;
    double closestCost = 0;
    double proximityCost;

    for (UserRequest uR : this.cache.keySet()) {
      proximityCost = uRequest.getStrategy().equals(uR.getStrategy()) ? 0.4 : 0;
      proximityCost += uRequest.getImageName().equals(uR.getImageName()) ? 0.2 : 0;
      proximityCost += uRequest.getViewPort().equals(uR.getViewPort()) ? 0.1 : 0;
      double absArea = Math.abs(uRequest.getArea() - uR.getArea());
      proximityCost += absArea < DELTA ? (DELTA - absArea) / DELTA * 0.2 : 0;
      double absStartingPoint = uRequest.getStartingPoint().distance(uR.getStartingPoint());
      proximityCost += absStartingPoint < DELTA ? (DELTA - absStartingPoint) / DELTA * 0.1 : 0;
      if (closestCost < proximityCost) {
        closestCost = proximityCost;
        closestUserRequest = uR;
      }
    }
    cost = this.cache.get(closestUserRequest);
    return cost != null ? cost : 0;
  }
}
