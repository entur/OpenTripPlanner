package org.opentripplanner.perfrig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.raptor.RigRaptorServiceFactory;
import org.opentripplanner.routing.api.request.RouteRequest;

/**
 * Runs a case through the Transmodel API and records every layer: the API response, the routing
 * responses and the Raptor calls.
 */
final class CaseRunner {

  /**
   * The result of one trip query; a case with pages has several. The Raptor calls are only kept if
   * asked for, they are big.
   */
  record Result(
    String id,
    Map<String, Object> variables,
    String api,
    String routing,
    String raptor,
    List<Routing> routings,
    List<RaptorCapture.RouteCall> raptorCalls,
    long nanos,
    @Nullable String nextPageCursor,
    @Nullable String previousPageCursor
  ) {
    String layer(String name) {
      return switch (name) {
        case "api" -> api;
        case "routing" -> routing;
        case "raptor" -> raptor;
        default -> throw new IllegalArgumentException(name);
      };
    }
  }

  /** A request to the routing service, and the fingerprint of its response. */
  record Routing(RouteRequest request, String fingerprint) {}

  static final List<String> LAYERS = List.of("api", "routing", "raptor");

  private final RigServer server;
  private final TripQuery query;
  private final IntFunction<String> stopId;

  CaseRunner(RigServer server) {
    this.server = server;
    this.query = new TripQuery(server.transmodelSchema());
    var raptorData = server.factory().transitService().getRaptorTransitData();
    this.stopId = i -> {
      var stop = raptorData.getStopByIndex(i);
      return stop == null ? "#" + i : stop.getId().toString();
    };
  }

  /** Run the case, and follow the page cursors of the first response. */
  List<Result> run(RigCase c, boolean keepRaptorData) {
    var results = new ArrayList<Result>();
    var first = runOne(c.id(), c.variables(), keepRaptorData);
    results.add(first);

    var cursor = first.nextPageCursor();
    for (int i = 1; i <= c.next() && cursor != null; i++) {
      var page = runOne(c.id() + "/next" + i, withCursor(c.variables(), cursor), keepRaptorData);
      results.add(page);
      cursor = page.nextPageCursor();
    }
    cursor = first.previousPageCursor();
    for (int i = 1; i <= c.prev() && cursor != null; i++) {
      var page = runOne(c.id() + "/prev" + i, withCursor(c.variables(), cursor), keepRaptorData);
      results.add(page);
      cursor = page.previousPageCursor();
    }
    return results;
  }

  Result runOne(String id, Map<String, Object> variables, boolean keepRaptorData) {
    var scope = server.newRequestScope();
    var capture = new RaptorCapture();
    Map<String, Object> json;
    long start = System.nanoTime();
    RigRaptorServiceFactory.setListener(capture);
    try {
      json = query.execute(variables, scope.transmodelContext());
    } finally {
      RigRaptorServiceFactory.setListener(null);
    }
    long nanos = System.nanoTime() - start;

    var routing = new StringBuilder();
    var routings = new ArrayList<Routing>();
    for (var call : scope.recorder().calls()) {
      var dump = ResponseDump.dump(call.request(), call.response());
      routing.append(dump);
      routings.add(new Routing(call.request(), Dump.hash(dump)));
    }
    var raptorCalls = keepRaptorData
      ? capture.routeCalls().stream().map(call -> call.withoutResponse(stopId)).toList()
      : List.<RaptorCapture.RouteCall>of();
    return new Result(
      id,
      variables,
      Json.pretty(json),
      routing.toString(),
      capture.dump(stopId),
      routings,
      raptorCalls,
      nanos,
      cursor(json, "nextPageCursor"),
      cursor(json, "previousPageCursor")
    );
  }

  private static Map<String, Object> withCursor(Map<String, Object> variables, String cursor) {
    var copy = new LinkedHashMap<>(variables);
    copy.put("pageCursor", cursor);
    return copy;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static String cursor(Map<String, Object> json, String name) {
    var data = (Map<String, Object>) json.get("data");
    if (data == null || data.get("trip") == null) {
      return null;
    }
    return (String) ((Map<String, Object>) data.get("trip")).get(name);
  }
}
