package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.framework.EntityNotFoundException;

class StopIndicesResolverTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final ZoneId TIME_ZONE = ZoneId.of("GMT");

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of(
    SERVICE_DATE,
    TIME_ZONE
  );

  @Test
  void resolvesSingleStopIndex() {
    var stopA = ENV_BUILDER.stop("A");
    var stopB = ENV_BUILDER.stop("B");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05")
    ).build();

    var resolver = new TransitServiceStopIndexResolver(env.transitService());
    var indices = resolver.lookupStopLocationIndexes(stopA.getId()).boxed().toList();

    assertEquals(1, indices.size());
    assertEquals(stopA.getIndex(), indices.getFirst());
  }

  @Test
  void resolvesStationToChildStopIndices() {
    var stopA = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopB = ENV_BUILDER.stop("B");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA, "10:00").addStop(stopB, "10:05")
    ).build();

    var resolver = new TransitServiceStopIndexResolver(env.transitService());
    var indices = resolver.lookupStopLocationIndexes(id("StationA")).boxed().toList();

    assertEquals(1, indices.size());
    assertEquals(stopA.getIndex(), indices.getFirst());
  }

  @Test
  void resolvesStationWithMultipleChildStops() {
    var stopA1 = ENV_BUILDER.stopAtStation("SA1", "StationA");
    var stopA2 = ENV_BUILDER.stopAtStation("SA2", "StationA");
    var stopB = ENV_BUILDER.stop("B");
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1").addStop(stopA1, "10:00").addStop(stopB, "10:05")
    ).build();

    var resolver = new TransitServiceStopIndexResolver(env.transitService());
    var indices = resolver
      .lookupStopLocationIndexes(id("StationA"))
      .boxed()
      .collect(Collectors.toSet());

    assertEquals(2, indices.size());
    assertEquals(true, indices.contains(stopA1.getIndex()));
    assertEquals(true, indices.contains(stopA2.getIndex()));
  }

  @Test
  void throwsOnUnknownStopOrStationId() {
    var env = ENV_BUILDER.addTrip(
      TripInput.of("T1")
        .addStop(ENV_BUILDER.stop("A"), "10:00")
        .addStop(ENV_BUILDER.stop("B"), "10:05")
    ).build();

    var resolver = new TransitServiceStopIndexResolver(env.transitService());
    assertThrows(EntityNotFoundException.class, () ->
      resolver.lookupStopLocationIndexes(id("unknown")).boxed().toList()
    );
  }
}
