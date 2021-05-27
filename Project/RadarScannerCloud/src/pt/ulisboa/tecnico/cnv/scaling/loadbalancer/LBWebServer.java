package pt.ulisboa.tecnico.cnv.scaling.loadbalancer;

import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.scaling.ScalingInstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** LoadBalancer WebServer to respond to Clients Requests. */
public class LBWebServer implements Runnable {

  private final Level level;
  private final Map<String, ScalingInstance> instances;

  public LBWebServer(Map<String, ScalingInstance> instances, Level level) {
    this.level = level;
    this.instances = instances;
  }

  /** Creates the LoadBalancer WebServer and its handler: LBScanHandler. */
  @Override
  public void run() {

    final HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(5000), 0);
    } catch (IOException e) {
      System.err.println("HttpServer Creation Failed!");
      return;
    }

    server.createContext("/scan", new LBScanHandler(this.instances, this.level));
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    System.out.println(server.getAddress().toString());
  }
}
