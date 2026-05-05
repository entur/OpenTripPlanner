package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.ext.carpooling.CarpoolGraphPathBuilder.createGraphPath;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTrip;

import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.core.model.basic.Cost;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.spi.RaptorCostConverter;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

class CarpoolAccessEgressTest {

  private static final int STOP = 0;
  private static final Duration STOP_DURATION = Duration.ofMinutes(2);
  private static final int DWELL_SECONDS = (int) STOP_DURATION.getSeconds();
  private static final int PICKUP_POSITION = 1;
  private static final int DROPOFF_POSITION = 2;
  private static final Duration PICKUP_SEGMENT_DURATION = Duration.ofMinutes(1);

  /**
   * Walk time must be billed at the walk path's own A* weight (which already encodes walk
   * reluctance, safety, slope, ...) and the carpool ride at {@code carpoolReluctance}. The ride
   * includes the boarding dwell at the pickup stop.
   */
  @Test
  void c1AddsWalkPathWeightsAndChargesRideAtCarpoolReluctance() {
    var walkToPickup = createGraphPath(Duration.ofSeconds(80));
    var walkFromDropoff = createGraphPath(Duration.ofSeconds(40));
    double carpoolReluctance = 1.0;

    var accessEgress = newAccessEgress(
      1_000,
      walkToPickup,
      Duration.ofSeconds(60),
      walkFromDropoff,
      carpoolReluctance
    );

    int rideSeconds = 60 + DWELL_SECONDS;
    double expectedWeight =
      walkToPickup.getWeight() + walkFromDropoff.getWeight() + rideSeconds * carpoolReluctance;
    assertEquals(RaptorCostConverter.toRaptorCost(expectedWeight), accessEgress.c1());
    assertEquals(expectedWeight, accessEgress.getTotalWeight());
  }

  /** With no walk, the cost reduces to {@code (sharedSegmentSeconds + dwell) * carpoolReluctance}. */
  @Test
  void c1WithoutWalkUsesOnlyCarpoolReluctance() {
    var accessEgress = newAccessEgress(0, null, Duration.ofSeconds(300), null, 1.5);

    int rideSeconds = 300 + DWELL_SECONDS;
    assertEquals(RaptorCostConverter.toRaptorCost(rideSeconds * 1.5), accessEgress.c1());
  }

  @Test
  void durationInSecondsCoversWalksAndRide() {
    var accessEgress = newAccessEgress(
      1_000,
      createGraphPath(Duration.ofSeconds(80)),
      Duration.ofSeconds(60),
      createGraphPath(Duration.ofSeconds(40)),
      1.0
    );

    int expectedDuration = 80 + 60 + DWELL_SECONDS + 40;
    assertEquals(expectedDuration, accessEgress.durationInSeconds());
    assertEquals(1_000, accessEgress.getPassengerDepartureTime());
    assertEquals(1_000 + expectedDuration, accessEgress.getPassengerArrivalTime());
  }

  /**
   * {@code withPenalty} installs the new penalty and preserves everything else. The leg's intrinsic
   * c1 and duration are computed from walks + ride only — Raptor adds the penalty separately — so
   * they must stay unchanged across the copy.
   */
  @Test
  void withPenaltyInstallsPenaltyAndPreservesLeg() {
    var original = newAccessEgress(
      1_000,
      createGraphPath(Duration.ofSeconds(80)),
      Duration.ofSeconds(60),
      createGraphPath(Duration.ofSeconds(40)),
      1.0
    );
    var newPenalty = new TimeAndCost(Duration.ofSeconds(30), Cost.costOfSeconds(45));

    var withPenalty = (CarpoolAccessEgress) original.withPenalty(newPenalty);

    assertEquals(newPenalty, withPenalty.penalty());
    assertEquals(original.stop(), withPenalty.stop());
    assertEquals(original.c1(), withPenalty.c1());
    assertEquals(original.durationInSeconds(), withPenalty.durationInSeconds());
    assertEquals(original.getPassengerDepartureTime(), withPenalty.getPassengerDepartureTime());
    assertEquals(original.getPassengerArrivalTime(), withPenalty.getPassengerArrivalTime());
  }

  /**
   * Builds a CarpoolAccessEgress with the passenger picked up mid-trip (pickupPos = 1, dropoffPos
   * = 2). The route segments list is therefore [pickupSegment, sharedSegment]: the driver runs
   * the first to reach the passenger and the second is the passenger's shared ride. The
   * passenger's ride duration is {@code sharedSegmentDuration + STOP_DURATION} — the boarding
   * dwell at the pickup stop is part of the ride.
   */
  private static CarpoolAccessEgress newAccessEgress(
    int passengerDepartureTime,
    @Nullable GraphPath<State, Edge, Vertex> walkToPickup,
    Duration sharedSegmentDuration,
    @Nullable GraphPath<State, Edge, Vertex> walkFromDropoff,
    double carpoolReluctance
  ) {
    var candidate = new InsertionCandidate(
      createSimpleTrip(OSLO_CENTER, OSLO_NORTH),
      PICKUP_POSITION,
      DROPOFF_POSITION,
      List.of(createGraphPath(PICKUP_SEGMENT_DURATION), createGraphPath(sharedSegmentDuration)),
      STOP_DURATION,
      null,
      walkToPickup,
      walkFromDropoff
    );
    return new CarpoolAccessEgress(
      STOP,
      passengerDepartureTime,
      candidate,
      TimeAndCost.ZERO,
      carpoolReluctance
    );
  }
}
