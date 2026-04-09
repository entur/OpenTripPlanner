package org.opentripplanner.ext.carpooling.constraints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.util.GraphPathUtils.calculateCumulativeDurations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.CarpoolGraphPathBuilder;
import org.opentripplanner.ext.carpooling.model.CarpoolStop;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

class PassengerDelayConstraintsTest {

  private static final AtomicInteger STOP_COUNTER = new AtomicInteger(0);
  private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

  private PassengerDelayConstraints constraints;

  @BeforeEach
  void setup() {
    constraints = new PassengerDelayConstraints();
  }

  private static CarpoolStop stopWithBudget(Duration budget) {
    return CarpoolStop.of(
      FeedScopedId.ofNullable("TEST", "stop-" + STOP_COUNTER.incrementAndGet()),
      STOP_COUNTER::getAndIncrement
    )
      .withCoordinate(
        new org.opentripplanner.street.geometry.WgsCoordinate(59.9, 10.7)
      )
      .withDeviationBudget(budget)
      .build();
  }

  @Test
  void satisfiesConstraints_delayWellUnderBudget_accepts() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(5), Duration.ofMinutes(15) };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    // Stop1 delay: 7min - 5min = 2min (within 5min budget)
    // Destination delay: 17min - 15min = 2min (within 5min budget)
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(3)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(4)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertTrue(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_delayExactlyAtBudget_accepts() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(10), Duration.ofMinutes(20) };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    // Stop1 delay: 15min - 10min = 5min (exactly at 5min budget)
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(10)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertTrue(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_delayOverBudget_rejects() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(10), Duration.ofMinutes(20) };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    // Stop1 delay: 16min - 10min = 6min (exceeds 5min budget)
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(11)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertFalse(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_destinationOverBudget_rejects() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(10), Duration.ofMinutes(20) };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(Duration.ofMinutes(20)),
      stopWithBudget(FIVE_MINUTES)
    );

    // Stop1 delay: 12min - 10min = 2min (within 20min budget)
    // Destination delay: 27min - 20min = 7min (exceeds 5min budget)
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(7)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(10)),
    };

    assertFalse(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_multipleStops_oneOverBudget_rejects() {
    Duration[] originalTimes = {
      Duration.ZERO,
      Duration.ofMinutes(10),
      Duration.ofMinutes(20),
      Duration.ofMinutes(30),
    };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    // Stop1 delay: 13min - 10min = 3min ok
    // Stop2 delay: 27min - 20min = 7min exceeds
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(8)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(9)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertFalse(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_multipleStops_allUnderBudget_accepts() {
    Duration[] originalTimes = {
      Duration.ZERO,
      Duration.ofMinutes(10),
      Duration.ofMinutes(20),
      Duration.ofMinutes(30),
    };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(7)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(7)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(10)),
    };

    assertTrue(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_differentBudgetsPerStop() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(10), Duration.ofMinutes(20) };
    // Stop 1 has 2min budget, destination has 10min budget
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(Duration.ofMinutes(2)),
      stopWithBudget(Duration.ofMinutes(10))
    );

    // Stop1 delay: 13min - 10min = 3min (exceeds 2min budget)
    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(8)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertFalse(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }

  @Test
  void satisfiesConstraints_noDelay_accepts() {
    Duration[] originalTimes = { Duration.ZERO, Duration.ofMinutes(10), Duration.ofMinutes(20) };
    var stops = List.of(
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES),
      stopWithBudget(FIVE_MINUTES)
    );

    GraphPath<State, Edge, Vertex>[] modifiedSegments = new GraphPath[] {
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(4)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(6)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
      CarpoolGraphPathBuilder.createGraphPath(Duration.ofMinutes(5)),
    };

    assertTrue(
      constraints.satisfiesConstraints(
        originalTimes,
        calculateCumulativeDurations(modifiedSegments),
        1,
        3,
        stops
      )
    );
  }
}
