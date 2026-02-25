package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner._support.time.ZoneIds;

public class SiriTimeResolverTest {

  private static final ZonedDateTime T1 = ZonedDateTime.of(
    LocalDateTime.of(2024, 1, 15, 10, 0),
    ZoneIds.CET
  );
  private static final ZonedDateTime T2 = ZonedDateTime.of(
    LocalDateTime.of(2024, 1, 15, 10, 5),
    ZoneIds.CET
  );
  private static final ZonedDateTime T3 = ZonedDateTime.of(
    LocalDateTime.of(2024, 1, 15, 10, 10),
    ZoneIds.CET
  );
  private static final ZonedDateTime T4 = ZonedDateTime.of(
    LocalDateTime.of(2024, 1, 15, 10, 15),
    ZoneIds.CET
  );

  @Test
  public void testFirstStop_MissingArrival_UsesDeparture() {
    // First stop with missing arrival should fallback to departure
    var call = TestCall.of().withExpectedDepartureTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 3);

    assertEquals(T1, result.arrivalTime(), "First stop arrival should fallback to departure");
    assertEquals(T1, result.departureTime());
  }

  @Test
  public void testFirstStop_MissingArrival_PrefersActualDeparture() {
    // First stop should prefer actual over expected when falling back
    var call = TestCall.of().withActualDepartureTime(T1).withExpectedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 3);

    assertEquals(
      T1,
      result.arrivalTime(),
      "First stop should use actual departure for arrival fallback"
    );
    assertEquals(T1, result.departureTime());
  }

  @Test
  public void testFirstStop_WithArrival_NoFallback() {
    // First stop with arrival present should not fallback
    var call = TestCall.of().withExpectedArrivalTime(T1).withExpectedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime());
  }

  @Test
  public void testLastStop_MissingDeparture_UsesArrival() {
    // Last stop with missing departure should fallback to arrival
    var call = TestCall.of().withExpectedArrivalTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 2, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T1, result.departureTime(), "Last stop departure should fallback to arrival");
  }

  @Test
  public void testLastStop_MissingDeparture_PrefersActualArrival() {
    // Last stop should prefer actual over expected when falling back
    var call = TestCall.of().withActualArrivalTime(T1).withExpectedArrivalTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 2, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(
      T1,
      result.departureTime(),
      "Last stop should use actual arrival for departure fallback"
    );
  }

  @Test
  public void testLastStop_WithDeparture_NoFallback() {
    // Last stop with departure present should not fallback
    var call = TestCall.of().withExpectedArrivalTime(T1).withExpectedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 2, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime());
  }

  @Test
  public void testMiddleStop_MissingArrival_NoCrossfieldFallback() {
    // Middle stop with missing arrival should NOT fallback to departure
    var call = TestCall.of().withExpectedDepartureTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertNull(result.arrivalTime(), "Middle stop should not fallback arrival to departure");
    assertEquals(T1, result.departureTime());
  }

  @Test
  public void testMiddleStop_MissingDeparture_NoCrossfieldFallback() {
    // Middle stop with missing departure should NOT fallback to arrival
    var call = TestCall.of().withExpectedArrivalTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime());
    assertNull(result.departureTime(), "Middle stop should not fallback departure to arrival");
  }

  @Test
  public void testMiddleStop_WithBothTimes() {
    // Middle stop with both times should use them as-is
    var call = TestCall.of().withExpectedArrivalTime(T1).withExpectedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime());
  }

  @Test
  public void testSingleStopTrip_MissingArrival_UsesDeparture() {
    // Single stop trip is both first and last, both fallbacks should apply
    var call = TestCall.of().withExpectedDepartureTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 1);

    assertEquals(T1, result.arrivalTime(), "Single stop should fallback arrival to departure");
    assertEquals(T1, result.departureTime());
  }

  @Test
  public void testSingleStopTrip_MissingDeparture_UsesArrival() {
    // Single stop trip is both first and last, both fallbacks should apply
    var call = TestCall.of().withExpectedArrivalTime(T1).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 1);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T1, result.departureTime(), "Single stop should fallback departure to arrival");
  }

  @Test
  public void testSingleStopTrip_WithBothTimes() {
    // Single stop trip with both times should use them
    var call = TestCall.of().withExpectedArrivalTime(T1).withExpectedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveTimes(call, 0, 1);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime());
  }

  @Test
  public void testActualTimePrecedence_Arrival() {
    // Actual arrival should be preferred over expected arrival
    var call = TestCall.of()
      .withActualArrivalTime(T1)
      .withExpectedArrivalTime(T2)
      .withExpectedDepartureTime(T3)
      .build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime(), "Actual arrival should be preferred");
    assertEquals(T3, result.departureTime());
  }

  @Test
  public void testActualTimePrecedence_Departure() {
    // Actual departure should be preferred over expected departure
    var call = TestCall.of()
      .withExpectedArrivalTime(T1)
      .withActualDepartureTime(T2)
      .withExpectedDepartureTime(T3)
      .build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime(), "Actual departure should be preferred");
  }

  @Test
  public void testActualTimePrecedence_BothActual() {
    // Both actual times should be preferred
    var call = TestCall.of()
      .withActualArrivalTime(T1)
      .withExpectedArrivalTime(T2)
      .withActualDepartureTime(T3)
      .withExpectedDepartureTime(T4)
      .build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime(), "Actual arrival should be preferred");
    assertEquals(T3, result.departureTime(), "Actual departure should be preferred");
  }

  @Test
  public void testAllNullTimes_ReturnsNull() {
    // Call with no times should return null times
    var call = TestCall.of().build();

    var result = SiriTimeResolver.resolveTimes(call, 1, 3);

    assertNull(result.arrivalTime());
    assertNull(result.departureTime());
  }

  @Test
  public void testAimedTimes_FirstStop_MissingArrival() {
    // Aimed times should also apply fallback for first stop
    var call = TestCall.of().withAimedDepartureTime(T1).build();

    var result = SiriTimeResolver.resolveAimedTimes(call, 0, 3);

    assertEquals(
      T1,
      result.arrivalTime(),
      "First stop aimed arrival should fallback to aimed departure"
    );
    assertEquals(T1, result.departureTime());
  }

  @Test
  public void testAimedTimes_LastStop_MissingDeparture() {
    // Aimed times should also apply fallback for last stop
    var call = TestCall.of().withAimedArrivalTime(T1).build();

    var result = SiriTimeResolver.resolveAimedTimes(call, 2, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(
      T1,
      result.departureTime(),
      "Last stop aimed departure should fallback to aimed arrival"
    );
  }

  @Test
  public void testAimedTimes_MiddleStop_NoFallback() {
    // Aimed times should NOT apply fallback for middle stops
    var call = TestCall.of().withAimedArrivalTime(T1).build();

    var result = SiriTimeResolver.resolveAimedTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime());
    assertNull(result.departureTime(), "Middle stop aimed times should not cross-fallback");
  }

  @Test
  public void testAimedTimes_WithBothTimes() {
    // Aimed times with both present should use them as-is
    var call = TestCall.of().withAimedArrivalTime(T1).withAimedDepartureTime(T2).build();

    var result = SiriTimeResolver.resolveAimedTimes(call, 1, 3);

    assertEquals(T1, result.arrivalTime());
    assertEquals(T2, result.departureTime());
  }

  @Test
  public void testAimedTimes_AllNull() {
    // Aimed times all null should return null
    var call = TestCall.of().build();

    var result = SiriTimeResolver.resolveAimedTimes(call, 1, 3);

    assertNull(result.arrivalTime());
    assertNull(result.departureTime());
  }
}
