package org.opentripplanner.perfrig;

import static org.opentripplanner.perfrig.Dump.v;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.raptor.RigRaptorServiceFactory;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.request.RaptorRequest;
import org.opentripplanner.raptor.api.response.RaptorResponse;
import org.opentripplanner.raptor.extensions.direct.api.RaptorDirectTransitRequest;
import org.opentripplanner.raptor.extensions.extrasearch.ExtraMcRouterSearch;
import org.opentripplanner.raptor.spi.RaptorDataProvider;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;

/** Collects the Raptor calls of one routing request, and writes them as text. */
final class RaptorCapture implements RigRaptorServiceFactory.Listener<TripSchedule> {

  /**
   * One call to RaptorService#route. The request and data can be replayed. Keep few of them: the
   * data of a request, and the response, hold the transit data of the request (~20 MB).
   */
  record RouteCall(
    RaptorRequest<TripSchedule> request,
    RaptorDataProvider<TripSchedule> data,
    @Nullable ExtraMcRouterSearch<TripSchedule> extraMcSearch,
    @Nullable RaptorResponse<TripSchedule> response,
    @Nullable String fingerprint
  ) {
    /** The call to replay it: with the fingerprint of the paths instead of the response. */
    RouteCall withoutResponse(IntFunction<String> stopId) {
      var paths = RaptorCapture.fingerprint(response.paths(), stopId);
      return new RouteCall(request, data, extraMcSearch, null, paths);
    }
  }

  record DirectTransitCall(RaptorDirectTransitRequest request, Collection<RaptorPath<TripSchedule>> paths) {}

  private final List<RouteCall> routeCalls = new ArrayList<>();
  private final List<DirectTransitCall> directTransitCalls = new ArrayList<>();

  @Override
  public void route(
    RaptorRequest<TripSchedule> request,
    RaptorDataProvider<TripSchedule> data,
    @Nullable ExtraMcRouterSearch<TripSchedule> extraMcSearch,
    RaptorResponse<TripSchedule> response
  ) {
    routeCalls.add(new RouteCall(request, data, extraMcSearch, response, null));
  }

  @Override
  public void findAllDirectTransit(
    RaptorDirectTransitRequest request,
    RaptorTransitDataProvider<TripSchedule> data,
    Collection<RaptorPath<TripSchedule>> paths
  ) {
    directTransitCalls.add(new DirectTransitCall(request, paths));
  }

  List<RouteCall> routeCalls() {
    return routeCalls;
  }

  /** A hash of the paths, in the order Raptor returned them. */
  static String fingerprint(Collection<RaptorPath<TripSchedule>> paths, IntFunction<String> stopId) {
    var text = new StringBuilder();
    for (var p : paths) {
      text.append(p.c1()).append(' ').append(p.c2()).append(' ');
      text.append(p.toStringDetailed(stopId::apply)).append('\n');
    }
    return Dump.hash(text.toString());
  }

  String dump(IntFunction<String> stopId) {
    var out = new Dump();
    for (int i = 0; i < routeCalls.size(); i++) {
      var call = routeCalls.get(i);
      var request = call.request();
      var params = request.searchParams();
      out.line(
        "route " +
          i +
          " " +
          request.alias() +
          " extraMcSearch=" +
          (call.extraMcSearch() == null ? "-" : call.extraMcSearch().getClass().getSimpleName())
      );
      out.indent();
      out.line("request " + v(request::toString));
      out.line(
        "edt=" +
          params.earliestDepartureTime() +
          " lat=" +
          params.latestArrivalTime() +
          " window=" +
          params.searchWindowInSeconds() +
          " oneIteration=" +
          params.searchOneIterationOnly() +
          " preferLateArrival=" +
          params.preferLateArrival() +
          " addTransfers=" +
          params.numberOfAdditionalTransfers() +
          " maxTransfers=" +
          params.maxNumberOfTransfers() +
          " timetable=" +
          params.timetable() +
          " constrainedTransfers=" +
          params.constrainedTransfers() +
          " aeMaxDuration=" +
          params.accessEgressMaxDurationSeconds()
      );
      out.line("multiCriteria " + v(request::multiCriteria));
      out.line("via " + v(params::viaLocations));
      accessEgress(out, "access", params.accessPaths(), stopId);
      accessEgress(out, "egress", params.egressPaths(), stopId);
      var response = call.response();
      out.line(
        "response noConnection=" +
          response.noConnectionFound() +
          " unknownPaths=" +
          response.containsUnknownPaths() +
          " used=" +
          v(() -> response.requestUsed().searchParams())
      );
      paths(out, response.paths(), stopId);
      out.outdent();
    }
    for (int i = 0; i < directTransitCalls.size(); i++) {
      var call = directTransitCalls.get(i);
      out.line("directTransit " + i + " " + v(call.request()::toString));
      out.indent();
      paths(out, call.paths(), stopId);
      out.outdent();
    }
    return out.toString();
  }

  /**
   * The access or egress paths, sorted, with a hash of the order Raptor got them in. The order of
   * paths with the same cost can change when other requests run at the same time (see
   * RecordCommand), so it is compared separately.
   */
  static void accessEgress(
    Dump out,
    String type,
    Collection<RaptorAccessEgress> list,
    IntFunction<String> stopId
  ) {
    var lines = list.stream().map(it -> accessEgress(it, stopId)).toList();
    out.line(type + " " + list.size() + " " + ORDER + Dump.hash(String.join("\n", lines)));
    out.indent();
    lines.stream().sorted().forEach(out::line);
    out.outdent();
  }

  static final String ORDER = "order#";

  private static String accessEgress(RaptorAccessEgress it, IntFunction<String> stopId) {
    return (
        stopId.apply(it.stop()) +
          " duration=" +
          it.durationInSeconds() +
          " c1=" +
          it.c1() +
          " penalty=" +
          it.timePenalty() +
          " rides=" +
          it.numberOfRides() +
          " onBoard=" +
          it.arrivedOnBoard() +
          " via=" +
          it.numberOfViaLocationsVisited() +
          " hours=" +
          (it.hasOpeningHours() ? v(it::openingHoursToString) : "-") +
          " " +
          it.getClass().getSimpleName() +
          " " +
          v(it::defaultToString)
      );
  }

  private static void paths(
    Dump out,
    Collection<RaptorPath<TripSchedule>> paths,
    IntFunction<String> stopId
  ) {
    out.line("paths " + paths.size());
    out.indent();
    for (var p : paths) {
      out.line(
        "iteration=" +
          p.rangeRaptorIterationDepartureTime() +
          " c1=" +
          p.c1() +
          " c2=" +
          p.c2() +
          " transfers=" +
          p.numberOfTransfers() +
          " wait=" +
          p.waitTime() +
          " " +
          v(() -> p.toStringDetailed(stopId::apply))
      );
    }
    out.outdent();
  }
}
