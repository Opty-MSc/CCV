package pt.ulisboa.tecnico.cnv.scaling;

import pt.ulisboa.tecnico.cnv.scaling.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.scaling.loadbalancer.LBWebServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ScalingApp {

  /**
   * Initializes the AutoScaler and the LoadBalancer in different threads.
   *
   * @param args CommandLine Arguments containing: Image Id of the WebServer, Key Pair Name,
   *     Security Group.
   */
  public static void main(String[] args) throws InterruptedException {

    if (args.length != 3) {
      System.out.printf(
          "%s Usage: <AMI> <KeyName> <SecurityGroup>%n", ScalingApp.class.getSimpleName());
      return;
    }

    String ami = args[0];
    String keyName = args[1];
    String securityGroup = args[2];
    Map<String, ScalingInstance> instances = new ConcurrentHashMap<>();
    Level level = "1".equals(System.getenv("DEBUG")) ? Level.ALL : Level.OFF;

    Thread autoScalerThread =
        new Thread(new AutoScaler(instances, ami, keyName, securityGroup, level));
    Thread lBWebServerThread = new Thread(new LBWebServer(instances, level));

    autoScalerThread.start();
    lBWebServerThread.start();

    autoScalerThread.join();
    lBWebServerThread.join();
  }
}
