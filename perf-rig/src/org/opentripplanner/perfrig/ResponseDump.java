package org.opentripplanner.perfrig;

import static org.opentripplanner.perfrig.Dump.geometry;
import static org.opentripplanner.perfrig.Dump.hash;
import static org.opentripplanner.perfrig.Dump.str;
import static org.opentripplanner.perfrig.Dump.v;

import java.util.List;
import java.util.stream.Collectors;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.leg.LegCallTime;
import org.opentripplanner.model.plan.leg.StopArrival;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.model.plan.walkstep.WalkStep;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.street.model.edge.Edge;

/**
 * Writes a {@link RoutingResponse} with every field of the itineraries and legs. Values are
 * written exactly (doubles with all digits, geometries as hashes of all coordinates), so any
 * change in the routing result changes the text.
 */
final class ResponseDump {

  private ResponseDump() {}

  static String dump(RouteRequest request, RoutingResponse response) {
    var out = new Dump();
    out.line("request " + v(request::toString));
    out.line("metadata " + v(response::getMetadata));
    out.line("next " + cursor(response.getNextPageCursor()));
    out.line("prev " + cursor(response.getPreviousPageCursor()));
    out.line("errors " + v(response::getRoutingErrors));
    var itineraries = response.getTripPlan().itineraries;
    out.line("itineraries " + itineraries.size());
    for (int i = 0; i < itineraries.size(); i++) {
      itinerary(out, i, itineraries.get(i));
    }
    return out.toString();
  }

  private static String cursor(Object cursor) {
    if (cursor == null) {
      return "-";
    }
    var c = (org.opentripplanner.model.plan.paging.cursor.PageCursor) cursor;
    return c + " token=" + v(c::encode);
  }

  private static void itinerary(Dump out, int index, Itinerary it) {
    out.line(
      "itinerary " +
        index +
        " " +
        v(it::startTime) +
        " - " +
        v(it::endTime) +
        " " +
        v(it::totalDuration) +
        " transfers=" +
        v(it::numberOfTransfers) +
        " c1=" +
        v(it::generalizedCost) +
        " c1+penalty=" +
        v(it::generalizedCostIncludingPenalty) +
        " c2=" +
        v(it::generalizedCost2) +
        " waitTimeOptimizedCost=" +
        v(it::waitTimeOptimizedCost) +
        " transferPriorityCost=" +
        v(it::transferPriorityCost)
    );
    out.indent();
    out.line(
      "distance=" +
        v(it::distanceMeters) +
        " street=" +
        v(it::totalStreetDistanceMeters) +
        " walk=" +
        v(it::totalWalkDistanceMeters) +
        " walkDuration=" +
        v(it::totalWalkDuration) +
        " streetDuration=" +
        v(it::totalStreetDuration) +
        " transitDuration=" +
        v(it::totalTransitDuration) +
        " waiting=" +
        v(it::totalWaitingDuration) +
        " effective=" +
        v(it::effectiveDuration)
    );
    out.line(
      "elevationGained=" +
        v(it::totalElevationGained) +
        " elevationLost=" +
        v(it::totalElevationLost) +
        " tooSloped=" +
        v(it::isTooSloped) +
        " maxSlope=" +
        v(it::maxSlope) +
        " accessibilityScore=" +
        v(it::accessibilityScore) +
        " arrivedWithRental=" +
        v(it::isArrivedAtDestinationWithRentedVehicle) +
        " flaggedForDeletion=" +
        v(it::isFlaggedForDeletion) +
        " searchWindowAware=" +
        v(it::isSearchWindowAware)
    );
    out.line(
      "accessPenalty=" +
        v(it::accessPenalty) +
        " egressPenalty=" +
        v(it::egressPenalty) +
        " emission=" +
        v(it::emissionPerPerson) +
        " delays=" +
        v(it::departureDelay) +
        "/" +
        v(it::arrivalDelay)
    );
    out.line("notices " + v(it::systemNotices));
    var legs = it.legs();
    for (int i = 0; i < legs.size(); i++) {
      leg(out, i, legs.get(i));
    }
    out.line("raw " + v(it::toString));
    out.outdent();
  }

  private static void leg(Dump out, int index, Leg leg) {
    String mode = leg instanceof StreetLeg street
      ? v(street::getMode)
      : v(() -> ((org.opentripplanner.model.plan.TransitLeg) leg).mode());
    out.line(
      "leg " +
        index +
        " " +
        leg.getClass().getSimpleName() +
        " " +
        mode +
        " " +
        time(leg.start()) +
        " - " +
        time(leg.end()) +
        " distance=" +
        v(leg::distanceMeters) +
        " c1=" +
        v(leg::generalizedCost)
    );
    out.indent();
    out.line("from " + place(leg.from()));
    out.line("to   " + place(leg.to()));
    out.line("geometry " + v(() -> geometry(leg.legGeometry())));
    out.line("elevation " + v(leg::elevationProfile));
    out.line(
      "rental=" +
        v(leg::rentedVehicle) +
        " network=" +
        v(leg::vehicleRentalNetwork) +
        " accessibilityScore=" +
        v(leg::accessibilityScore) +
        " emission=" +
        v(leg::emissionPerPerson)
    );
    if (leg.isTransitLeg()) {
      transit(out, leg);
    }
    out.line("alerts " + v(() -> sorted(leg.listTransitAlerts().stream().map(a -> a.getId()))));
    out.line("fares " + v(leg::fareOffers));
    out.line("fareZones " + v(() -> sorted(leg.fareZones().stream().map(z -> z.getId()))));
    var steps = leg.listWalkSteps();
    for (int i = 0; i < steps.size(); i++) {
      step(out, i, steps.get(i));
    }
    out.outdent();
  }

  private static void transit(Dump out, Leg leg) {
    out.line(
      "trip " +
        v(() -> leg.trip().getId()) +
        " date=" +
        v(leg::serviceDate) +
        " route=" +
        v(() -> leg.route().getId()) +
        " agency=" +
        v(() -> leg.agency().getId()) +
        " operator=" +
        v(() -> leg.operator().getId()) +
        " tripOnDate=" +
        v(() -> leg.tripOnServiceDate().getId()) +
        " headsign=" +
        v(leg::headsign) +
        " routeType=" +
        v(leg::routeType)
    );
    out.line(
      "board pos=" +
        v(leg::boardStopPosInPattern) +
        " seq=" +
        v(leg::boardingGtfsStopSequence) +
        " rule=" +
        v(leg::boardRule) +
        " alight pos=" +
        v(leg::alightStopPosInPattern) +
        " seq=" +
        v(leg::alightGtfsStopSequence) +
        " rule=" +
        v(leg::alightRule)
    );
    out.line(
      "realtime=" +
        v(leg::isRealTimeUpdated) +
        " delays=" +
        v(leg::departureDelay) +
        "/" +
        v(leg::arrivalDelay) +
        " interlined=" +
        v(leg::isInterlinedWithPreviousLeg) +
        " flexible=" +
        v(leg::isFlexibleTrip) +
        " nonExactFrequency=" +
        v(leg::isNonExactFrequency) +
        " headway=" +
        v(leg::headway) +
        " wheelchair=" +
        v(leg::tripWheelchairAccessibility) +
        " tzOffset=" +
        v(leg::agencyTimeZoneOffset)
    );
    out.line("transferFromPrev " + v(leg::transferFromPrevLeg));
    out.line("transferToNext " + v(leg::transferToNextLeg));
    out.line("pickupBooking " + v(leg::pickupBookingInfo));
    out.line("dropOffBooking " + v(leg::dropOffBookingInfo));
    out.line("legReference " + v(leg::legReference));
    for (StopArrival s : leg.listIntermediateStops()) {
      out.line(
        "via " +
          place(s.place) +
          " arr=" +
          time(s.arrival) +
          " dep=" +
          time(s.departure) +
          " pos=" +
          str(s.stopPosInPattern) +
          " seq=" +
          str(s.gtfsStopSequence) +
          " viaType=" +
          str(s.viaLocationType)
      );
    }
  }

  private static void step(Dump out, int index, WalkStep s) {
    out.line(
      "step " +
        index +
        " " +
        v(s::getRelativeDirection) +
        " " +
        v(s::getAbsoluteDirection) +
        " '" +
        v(s::getDirectionText) +
        "' distance=" +
        v(s::getDistance) +
        " start=" +
        v(s::getStartLocation) +
        " angle=" +
        v(s::getAngle) +
        " stayOn=" +
        v(s::isStayOn) +
        " area=" +
        v(s::getArea) +
        " derivedName=" +
        v(s::nameIsDerived) +
        " walkingBike=" +
        v(s::isWalkingBike) +
        " exit=" +
        v(s::highwayExit) +
        " entrance=" +
        v(s::entrance) +
        " vertical=" +
        v(s::verticalTransportationUse) +
        " elevation=" +
        v(s::getElevationProfile) +
        " edges=" +
        v(() -> edges(s.getEdges()))
    );
  }

  /**
   * The vertices the step passes through, as a hash (in full with -Drig.fullEdges=true). Not the
   * edge types: between two vertices there can be parallel edges with the same cost (an area edge
   * and a linked street edge), and which one the search takes depends on the order of the edges
   * of the vertex, which changes when other requests link their temporary edges to it.
   */
  private static String edges(List<Edge> edges) {
    var text = edges
      .stream()
      .map(e -> Dump.vertex(e.getFromVertex()) + ">" + Dump.vertex(e.getToVertex()))
      .collect(Collectors.joining(";"));
    return edges.size() + (Boolean.getBoolean("rig.fullEdges") ? "[" + text + "]" : "#" + hash(text));
  }

  private static String place(Place p) {
    if (p == null) {
      return "-";
    }
    return (
      "'" +
      str(p.name) +
      "' " +
      v(() -> p.coordinate.latitude() + "," + p.coordinate.longitude()) +
      " " +
      str(p.vertexType) +
      " stop=" +
      v(() -> p.stop.getId()) +
      " rental=" +
      v(() -> p.vehicleRentalPlace.id()) +
      " parking=" +
      v(() ->
        p.vehicleParkingWithEntrance.getVehicleParking().getId() +
        "/" +
        p.vehicleParkingWithEntrance.getEntrance().getEntranceId()
      ) +
      " viaType=" +
      str(p.viaLocationType)
    );
  }

  private static String time(LegCallTime t) {
    if (t == null) {
      return "-";
    }
    var estimated = t.estimated();
    return (
      v(t::scheduledTime) +
      (estimated == null ? "" : "/est=" + v(estimated::time) + "(" + v(estimated::delay) + ")")
    );
  }

  private static String sorted(java.util.stream.Stream<?> values) {
    return values.map(Dump::str).sorted().toList().toString();
  }
}
