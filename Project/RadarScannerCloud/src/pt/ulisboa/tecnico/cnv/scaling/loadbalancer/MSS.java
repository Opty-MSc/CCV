package pt.ulisboa.tecnico.cnv.scaling.loadbalancer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.*;

/** Frontend for the LoadBalancer to interact with the MSS. */
public class MSS {

  private static final String REQUESTS_COSTS_TABLE = "RequestsCosts";
  private final DynamoDB ddb;
  private final Table requestsCostsTable;

  public MSS() {
    AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
    this.ddb =
        new DynamoDB(
            AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build());
    this.initDDB();
    this.requestsCostsTable = this.ddb.getTable(REQUESTS_COSTS_TABLE);
  }

  /**
   * Creates the RequestQuery Table in MSS where the WebServer will store the Requests Costs and the
   * LoadBalancer will retrieve them.
   */
  private void initDDB() {
    CreateTableRequest request =
        new CreateTableRequest()
            .withAttributeDefinitions(
                new AttributeDefinition("RequestQuery", ScalarAttributeType.S))
            .withKeySchema(new KeySchemaElement("RequestQuery", KeyType.HASH))
            .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))
            .withTableName(REQUESTS_COSTS_TABLE);

    try {
      this.ddb.createTable(request);
    } catch (AmazonServiceException e) {
      if (!(e instanceof ResourceInUseException)) {
        System.err.println(e.getErrorMessage());
        System.exit(1);
      }
    }
  }

  /**
   * Obtains a predefined capacity of Costs associated with its Requests.
   *
   * @return Costs associated with its Requests.
   */
  public Map<UserRequest, Double> fetchWithoutFilter() {
    ScanSpec scanSpec = new ScanSpec().withMaxResultSize(LoadBalancer.CAPACITY);
    return this.scanUserRequestsCosts(scanSpec);
  }

  /**
   * Gets the Costs associated with Requests containing the Set of Queries provided.
   *
   * @param queries Queries that LoadBalancer has forwarded to the WebServer and has not yet
   *     obtained its Cost.
   * @return Costs associated with its Requests.
   */
  public Map<UserRequest, Double> fetchWithFilter(Set<String> queries) {

    if (queries.isEmpty()) return new HashMap<>();

    ValueMap valueMap = new ValueMap();
    StringBuilder filterExpressionBuilder = new StringBuilder("RequestQuery IN (");
    int i = 0;
    for (String query : queries) {
      String key = String.format(":v_query_%d", i++);
      valueMap.withString(key, query);
      filterExpressionBuilder.append(key).append(",");
    }
    filterExpressionBuilder.deleteCharAt(filterExpressionBuilder.length() - 1).append(")");

    ScanSpec scanSpec =
        new ScanSpec()
            .withFilterExpression(filterExpressionBuilder.toString())
            .withValueMap(valueMap)
            .withMaxResultSize(LoadBalancer.CAPACITY);
    return this.scanUserRequestsCosts(scanSpec);
  }

  /**
   * Gets the Costs associated with the Requests that match the ScanSpec provided.
   *
   * @return Costs associated with its Requests.
   */
  private synchronized Map<UserRequest, Double> scanUserRequestsCosts(ScanSpec scanSpec) {

    ItemCollection<ScanOutcome> queryResult = this.requestsCostsTable.scan(scanSpec);
    Iterator<Item> iterator = queryResult.iterator();

    Map<UserRequest, Double> uRequestsCosts = new LinkedHashMap<>();
    while (iterator.hasNext()) {
      Item item = iterator.next();

      String query = item.getString("RequestQuery");
      double cost = item.getDouble("Cost");

      UserRequest uRequest = UserRequest.parseFromQuery(query);
      if (uRequest != null) uRequestsCosts.put(uRequest, cost);
    }
    return uRequestsCosts;
  }
}
