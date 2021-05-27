package pt.ulisboa.tecnico.cnv.server;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** WebServer to respond to LoadBalancer Requests. */
public class WebServer {

  protected static ServerArgumentParser sap = null;

  /**
   * Creates the WebServer and its handlers: ScanHandler and HealthHandler.
   *
   * @param args CommandLine Arguments optionally containing: WebServer Address, WebServer Port,
   *     etc.
   */
  public static void main(final String[] args) {

    try {
      // Get user-provided flags.
      WebServer.sap = new ServerArgumentParser(args);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    System.out.println("> Finished parsing Server args.");

    final HttpServer server;
    try {
      server =
          HttpServer.create(
              new InetSocketAddress(
                  WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()),
              0);
    } catch (IOException e) {
      System.err.println("HttpServer Creation Failed!");
      return;
    }

    server.createContext("/scan", new ScanHandler());
    server.createContext("/health", new HealthHandler());

    server.setExecutor(Executors.newCachedThreadPool());
    server.start();

    System.out.println(server.getAddress().toString());
  }
}
