package org.opentripplanner.ext.flexbooking.internal;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.basic.Cost;
import org.opentripplanner.ext.carpooling.routing.InsertionCandidate;
import org.opentripplanner.ext.carpooling.util.GraphPathUtils;
import org.opentripplanner.ext.carpooling.util.LegMappingUtils;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.ext.flex.FlexibleTransitLeg;
import org.opentripplanner.ext.flex.edgetype.FlexTripEdge;
import org.opentripplanner.ext.flex.flexpathcalculator.FlexPath;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;

/**
 * Maps a feasible tour insertion to an OTP itinerary with a {@link FlexibleTransitLeg}.
 * <p>
 * The flex ride is represented by a fabricated, graph-detached {@link FlexTripEdge} whose
 * {@link FlexPath} carries the insertion-derived ride duration and the geometry of the actually
 * driven route — including any intermediate booked stops the passenger rides through (other
 * passengers' stops are not exposed as intermediate stop arrivals). Snap walks become WALK legs
 * anchored to the absolute pickup/dropoff times; the itinerary is never time-shifted.
 */
public class FlexBookingItineraryMapper {

  /**
   * Builds the itinerary for the passenger's journey: an optional WALK leg to the pickup, the
   * flex leg between pickup and dropoff, and an optional WALK leg from the dropoff.
   *
   * @return the itinerary, or {@code null} when the candidate has no shared segments (a safety
   *         check that should not trigger for valid candidates)
   */
  @Nullable
  public Itinerary toItinerary(
    InsertionCandidate candidate,
    UnscheduledTrip trip,
    int boardStopPosition,
    int alightStopPosition,
    LocalDate serviceDate,
    FlexInsertionFeasibility.TimedInsertion times,
    FlexParameters flexParameters
  ) {
    var sharedSegments = candidate.getSharedSegments();
    if (sharedSegments.isEmpty()) {
      return null;
    }

    Vertex pickupVertex = sharedSegments.getFirst().states.getFirst().getVertex();
    Vertex dropoffVertex = sharedSegments.getLast().states.getLast().getVertex();
    var boardStop = trip.getStop(boardStopPosition);
    var alightStop = trip.getStop(alightStopPosition);

    var rideEdges = sharedSegments
      .stream()
      .flatMap(segment -> segment.edges.stream())
      .toList();
    int distanceMeters = (int) rideEdges.stream().mapToDouble(Edge::getDistanceMeters).sum();
    int rideSeconds = (int) Duration.between(times.pickupTime(), times.dropoffTime()).toSeconds();

    var flexPath = new FlexPath(distanceMeters, rideSeconds, () ->
      GeometryUtils.concatenateLineStrings(rideEdges, Edge::getGeometry)
    );
    var flexTripEdge = new FlexTripEdge(
      pickupVertex,
      dropoffVertex,
      boardStop.getId(),
      alightStop.getId(),
      trip,
      boardStopPosition,
      alightStopPosition,
      serviceDate,
      flexPath,
      flexParameters
    );

    int rideCost = (int) (flexParameters.reluctance() * rideSeconds + flexParameters.boardCost());
    var flexLeg = FlexibleTransitLeg.of()
      .withFlexTripEdge(flexTripEdge)
      .withFromStop(boardStop)
      .withToStop(alightStop)
      .withStartTime(times.pickupTime())
      .withEndTime(times.dropoffTime())
      .withGeneralizedCost(rideCost)
      .build();

    List<Leg> legs = new ArrayList<>(3);
    var walkToPickup = candidate.walkToPickup();
    if (walkToPickup != null) {
      var walkStart = times.pickupTime().minus(GraphPathUtils.durationOrZero(walkToPickup));
      legs.add(
        LegMappingUtils.buildWalkLeg(
          walkToPickup,
          walkStart,
          times.pickupTime(),
          LegMappingUtils.makePlace(walkToPickup.states.getFirst().getVertex()),
          flexLeg.from()
        )
      );
    }
    legs.add(flexLeg);
    var walkFromDropoff = candidate.walkFromDropoff();
    if (walkFromDropoff != null) {
      var walkEnd = times.dropoffTime().plus(GraphPathUtils.durationOrZero(walkFromDropoff));
      legs.add(
        LegMappingUtils.buildWalkLeg(
          walkFromDropoff,
          times.dropoffTime(),
          walkEnd,
          flexLeg.to(),
          LegMappingUtils.makePlace(walkFromDropoff.states.getLast().getVertex())
        )
      );
    }

    int totalCost = legs.stream().mapToInt(Leg::generalizedCost).sum();
    return Itinerary.ofDirect(legs).withGeneralizedCost(Cost.costOfSeconds(totalCost)).build();
  }
}
