package org.opentripplanner.routing.impl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.opentripplanner.astar.strategy.DurationSkipEdgeStrategy;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.StreetPreferences;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.street.model.StreetConstants;
import org.opentripplanner.street.model.edge.ExtensionRequestContext;
import org.opentripplanner.street.model.path.StreetPath;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.EuclideanRemainingWeightHeuristic;
import org.opentripplanner.street.search.StreetSearchBuilder;
import org.opentripplanner.street.search.strategy.DominanceFunctions;
import org.opentripplanner.streetadapter.StreetSearchRequestMapper;

/**
 * This class contains the logic for repeatedly building shortest path trees and accumulating paths
 * through the graph until the requested number of them have been found. It is used in
 * point-to-point (i.e. not one-to-many / analyst) routing.
 * <p>
 * Its exact behavior will depend on whether the routing request allows transit.
 * <p>
 * When using transit it will incorporate techniques from what we called "long distance" mode, which
 * is designed to provide reasonable response times when routing over large graphs (e.g. the entire
 * Netherlands or New York State). In this case it only uses the street network at the first and
 * last legs of the trip, and all other transfers between transit vehicles will occur via
 * PathTransfer edges which are pre-computed by the graph builder.
 * <p>
 * More information is available on the OTP wiki at: https://github.com/openplans/OpenTripPlanner/wiki/LargeGraphs
 * <p>
 * One instance of this class should be constructed per search (i.e. per RouteRequest: it is
 * request-scoped). Its behavior is undefined if it is reused for more than one search.
 * <p>
 * It is very close to being an abstract library class with only static functions. However it turns
 * out to be convenient and harmless to have the OTPServer object etc. in fields, to avoid passing
 * context around in function parameters.
 */
public class GraphPathFinder {

  private final Collection<ExtensionRequestContext> extensionRequestContexts;

  private final float maxCarSpeed;

  public GraphPathFinder() {
    this(List.of(), StreetConstants.DEFAULT_MAX_CAR_SPEED);
  }

  public GraphPathFinder(
    Collection<ExtensionRequestContext> extensionRequestContexts,
    float maxCarSpeed
  ) {
    this.extensionRequestContexts = Objects.requireNonNull(extensionRequestContexts);
    this.maxCarSpeed = maxCarSpeed;
  }

  /**
   * Try to find N paths through the Graph
   */
  public List<StreetPath> find(
    RouteRequest request,
    LinkingContext linkingContext
  ) {
    Set<Vertex> from = linkingContext.findVertices(request.from());
    Set<Vertex> to = linkingContext.findVertices(request.to());
    OTPRequestTimeoutException.checkForTimeout();

    var paths = getPaths(request, from, to);

    if (paths.isEmpty()) {
      throw new PathNotFoundException();
    }

    return paths;
  }

  private List<StreetPath> getPaths(RouteRequest request, Set<Vertex> from, Set<Vertex> to) {
    StreetPreferences preferences = request.preferences().street();

    StreetSearchBuilder streetSearch = StreetSearchBuilder.of()
      .withPreStartHook(OTPRequestTimeoutException::checkForTimeout)
      .withHeuristic(new EuclideanRemainingWeightHeuristic(maxCarSpeed))
      .withSkipEdgeStrategy(
        new DurationSkipEdgeStrategy(
          preferences.maxDirectDuration().valueOf(request.journey().direct().mode())
        )
      )
      // FORCING the dominance function to weight only
      .withDominanceFunction(new DominanceFunctions.MinimumWeight())
      .withRequest(
        StreetSearchRequestMapper.map(request)
          .withExtensionRequestContexts(extensionRequestContexts)
          .withMode(request.journey().direct().mode())
          .build()
      )
      .withFrom(from)
      .withTo(to);

    return streetSearch.getPathsToTarget();
  }
}
