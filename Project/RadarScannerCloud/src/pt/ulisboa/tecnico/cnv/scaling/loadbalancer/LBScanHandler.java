package pt.ulisboa.tecnico.cnv.scaling.loadbalancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;
import pt.ulisboa.tecnico.cnv.scaling.ScalingInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;

/** Handles the Scan Requests that LoadBalancer receives from Clients. */
public class LBScanHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public LBScanHandler(Map<String, ScalingInstance> instances, Level level) {
    this.loadBalancer = new LoadBalancer(instances, level);
  }

  /**
   * Extracts the Request Query, converts it in the LoadBalancer Representation of a Request, and
   * calls the Auxiliary Method: sendRequest.
   *
   * @param t Encapsulates an HTTP Request.
   */
  @Override
  public void handle(final HttpExchange t) throws IOException {

    final String query = t.getRequestURI().getQuery();

    UserRequest uRequest = UserRequest.parseFromQuery(query);
    if (uRequest == null) {
      t.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
      t.close();
      return;
    }
    this.sendRequest(t, query, uRequest);
  }

  /**
   * Forwards the Incoming Request to the WebServer that has the lowest associated Cost and adds the
   * estimated Cost of the Request to it. When the WebServer responds, it responds to the Client
   * with the Image that the WebServer returned and removes the Cost associated with the Request
   * from the WebServer to which it forwarded the Request.
   *
   * @param t Encapsulates an HTTP Request.
   * @param query Incoming Request Query.
   * @param uRequest LoadBalancer Representation of the Incoming Request.
   */
  private void sendRequest(HttpExchange t, String query, UserRequest uRequest) throws IOException {
    Map.Entry<ScalingInstance, Double> instanceRequestCost =
        loadBalancer.onReceiveRequest(uRequest);
    if (instanceRequestCost == null) {
      t.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, 0);
      t.close();
      return;
    }
    String instanceDns = instanceRequestCost.getKey().getPublicDnsName();
    String URL = String.format("http://%s:%d/scan?%s", instanceDns, 8000, query);
    try {
      this.forwardRequest(t, URL);
      loadBalancer.onInstanceSuccess(query, instanceRequestCost);
    } catch (IOException e) {
      loadBalancer.onInstanceFailure(instanceRequestCost);
      this.sendRequest(t, query, uRequest);
    }
  }

  /**
   * Forwards the Incoming Request to the WebServer with the URL provided.
   *
   * @param t Encapsulates an HTTP Request.
   * @param URL WebServer URL that has the lowest associated Cost.
   */
  private void forwardRequest(HttpExchange t, String URL) throws IOException {
    HttpURLConnection con = (HttpURLConnection) new URL(URL).openConnection();
    con.setRequestMethod("GET");

    if (con.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
    final InputStream is = con.getInputStream();
    final OutputStream os = t.getResponseBody();

    final Headers hdrs = t.getResponseHeaders();

    hdrs.add("Content-Type", "image/png");
    hdrs.add("Access-Control-Allow-Origin", "*");
    hdrs.add("Access-Control-Allow-Credentials", "true");
    hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
    hdrs.add(
        "Access-Control-Allow-Headers",
        "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

    t.sendResponseHeaders(HttpURLConnection.HTTP_OK, con.getContentLength());
    IOUtils.copy(is, os);
  }
}
