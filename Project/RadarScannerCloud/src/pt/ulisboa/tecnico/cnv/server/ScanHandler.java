package pt.ulisboa.tecnico.cnv.server;

import BIT.SolverInstrumentation;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

/** Handles the Scan Requests that LoadBalancer forwards to the WebServer. */
public class ScanHandler implements HttpHandler {

  private final MSS mss;

  public ScanHandler() {
    this.mss = new MSS();
  }

  /**
   * Extracts the Request Query. Runs Solver with the arguments contained in the Query. Responds to
   * the Load Balancer with the Image resulting from the execution of the Solver. Gets the Metrics
   * associated with the Request. Calculates the Cost resulting from the Solver Metrics and stores
   * it in the MSS, which will later be fetched by the LoadBalancer.
   *
   * @param t Encapsulates an HTTP Request.
   */
  @Override
  public void handle(final HttpExchange t) throws IOException {

    // Get the query.
    final String query = t.getRequestURI().getQuery();
    System.out.println("> Query:\t" + query);

    // Store as if it was a direct call to SolverMain.
    final ArrayList<String> solverArgs = new ArrayList<>();

    for (final String p : query.split("&")) {
      final String[] pSplit = p.split("=");

      if (pSplit[0].equals("i")) {
        pSplit[1] = WebServer.sap.getMapsDirectory() + "/" + pSplit[1];
      }

      solverArgs.add("-" + pSplit[0]);
      solverArgs.add(pSplit[1]);
    }

    if (WebServer.sap.isDebugging()) {
      solverArgs.add("-d");
    }

    // Create solver instance from factory.
    final Solver s = SolverFactory.getInstance().makeSolver(solverArgs.toArray(new String[0]));

    if (s == null) {
      System.out.println("> Problem creating Solver. Exiting.");
      System.exit(1);
    }

    // Write figure file to disk.
    File responseFile;
    try {

      final BufferedImage outputImg = s.solveImage();

      final String outPath = WebServer.sap.getOutputDirectory();

      final String imageName = String.format("%s-%s", UUID.randomUUID(), s);

      final Path imagePathPNG = Paths.get(outPath, imageName);
      ImageIO.write(outputImg, "png", imagePathPNG.toFile());

      responseFile = imagePathPNG.toFile();

    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    // Send response to browser.
    final Headers hdrs = t.getResponseHeaders();

    hdrs.add("Content-Type", "image/png");
    hdrs.add("Access-Control-Allow-Origin", "*");
    hdrs.add("Access-Control-Allow-Credentials", "true");
    hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
    hdrs.add(
        "Access-Control-Allow-Headers",
        "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

    t.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseFile.length());

    final OutputStream os = t.getResponseBody();
    Files.copy(responseFile.toPath(), os);

    os.close();

    System.out.println("> Sent response to " + t.getRemoteAddress().toString());

    SolverInstrumentation.SolverMetrics solverMetrics = SolverInstrumentation.popSolverMetrics();
    if (solverMetrics == null) {
      System.out.println("No SolverMetrics Found!");
      return;
    }

    double cost = solverMetrics.getRoutineCallCounter();
    this.mss.addRequestCost(query, cost);
  }
}
