package pt.ulisboa.tecnico.cnv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.HttpURLConnection;

/** Handles Health Requests for the LoadBalancer to verify that the WebServer is still active. */
public class HealthHandler implements HttpHandler {

  /**
   * Responds with an HTTP OK.
   *
   * @param t Encapsulates an HTTP Request.
   */
  @Override
  public void handle(final HttpExchange t) throws IOException {
    t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
    t.close();
  }
}
