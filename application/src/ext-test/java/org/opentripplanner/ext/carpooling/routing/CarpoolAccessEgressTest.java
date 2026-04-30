package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.ext.carpooling.CarpoolGraphPathBuilder.createGraphPath;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.spi.RaptorCostConverter;

class CarpoolAccessEgressTest {

  private static final int STOP = 0;

  /**
   * Walk time must be billed at the walk path's own A* weight (which already encodes walk
   * reluctance, safety, slope, ...) and the carpool ride at {@code carpoolReluctance}.
   * Multiplying the combined window by a single reluctance silently mis-prices one of them — the
   * buggy variant of this code multiplied the total by {@code carpoolReluctance}, so a passenger
   * with a long walk to an unattractive pickup looked artificially cheap to Raptor.
   */
  @Test
  void c1AddsWalkPathWeightsAndChargesRideAtCarpoolReluctance() {
    var walkToPickup = createGraphPath(Duration.ofSeconds(80));
    var walkFromDropoff = createGraphPath(Duration.ofSeconds(40));
    Duration rideDuration = Duration.ofSeconds(60);
    double carpoolReluctance = 1.0;

    var accessEgress = new CarpoolAccessEgress(
      STOP,
      1_000,
      walkToPickup,
      List.of(),
      rideDuration,
      walkFromDropoff,
      TimeAndCost.ZERO,
      carpoolReluctance
    );

    double expectedWeight =
      walkToPickup.getWeight() + walkFromDropoff.getWeight() + 60 * carpoolReluctance;
    assertEquals(RaptorCostConverter.toRaptorCost(expectedWeight), accessEgress.c1());
    assertEquals(expectedWeight, accessEgress.getTotalWeight());
  }

  /** With no walk, the cost reduces to {@code rideSeconds * carpoolReluctance}. */
  @Test
  void c1WithoutWalkUsesOnlyCarpoolReluctance() {
    var accessEgress = new CarpoolAccessEgress(
      STOP,
      0,
      null,
      List.of(),
      Duration.ofSeconds(300),
      null,
      TimeAndCost.ZERO,
      1.5
    );

    assertEquals(RaptorCostConverter.toRaptorCost(300 * 1.5), accessEgress.c1());
  }

  @Test
  void durationInSecondsCoversWalksAndRide() {
    var accessEgress = new CarpoolAccessEgress(
      STOP,
      1_000,
      createGraphPath(Duration.ofSeconds(80)),
      List.of(),
      Duration.ofSeconds(60),
      createGraphPath(Duration.ofSeconds(40)),
      TimeAndCost.ZERO,
      1.0
    );

    assertEquals(180, accessEgress.durationInSeconds());
    assertEquals(1_000, accessEgress.getPassengerDepartureTime());
    assertEquals(1_180, accessEgress.getPassengerArrivalTime());
  }

  /**
   * {@code withPenalty} must round-trip the cost breakdown. If it dropped the walk/ride split,
   * c1 would shift after a penalty is applied.
   */
  @Test
  void withPenaltyPreservesCostBreakdown() {
    var original = new CarpoolAccessEgress(
      STOP,
      1_000,
      createGraphPath(Duration.ofSeconds(80)),
      List.of(),
      Duration.ofSeconds(60),
      createGraphPath(Duration.ofSeconds(40)),
      TimeAndCost.ZERO,
      1.0
    );

    var withPenalty = original.withPenalty(TimeAndCost.ZERO);

    assertEquals(original.c1(), withPenalty.c1());
    assertEquals(original.durationInSeconds(), withPenalty.durationInSeconds());
  }
}
