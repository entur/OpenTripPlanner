package org.opentripplanner.ext.carpooling.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.model.CarpoolStopType.DROP_OFF_ONLY;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;

public class CarpoolStopBuilderTest {

  public static final ZonedDateTime AIMED_ARRIVAL_TIME = ZonedDateTime.of(
    2026,
    3,
    9,
    10,
    12,
    0,
    0,
    ZoneId.systemDefault()
  );
  public static final ZonedDateTime EXPECTED_ARRIVAL_TIME = ZonedDateTime.of(
    2026,
    3,
    9,
    10,
    17,
    0,
    0,
    ZoneId.systemDefault()
  );
  public static final ZonedDateTime AIMED_DEPARTURE_TIME = ZonedDateTime.of(
    2026,
    3,
    9,
    8,
    17,
    0,
    0,
    ZoneId.systemDefault()
  );
  public static final ZonedDateTime EXPECTED_DEPARTURE_TIME = ZonedDateTime.of(
    2026,
    3,
    9,
    8,
    12,
    0,
    0,
    ZoneId.systemDefault()
  );

  @Test
  void buildFromValues_usingWith_buildToCorrectValues() {
    var builder = new CarpoolStopBuilder(new FeedScopedId("feed", "id"), () -> -1);
    builder
      .withSequenceNumber(1)
      .withPassengerDelta(2)
      .withCoordinate(OSLO_NORTH)
      .withCarpoolStopType(DROP_OFF_ONLY)
      .withAimedArrivalTime(AIMED_ARRIVAL_TIME)
      .withExpectedArrivalTime(EXPECTED_ARRIVAL_TIME)
      .withAimedDepartureTime(AIMED_DEPARTURE_TIME)
      .withExpectedDepartureTime(EXPECTED_DEPARTURE_TIME)
      .withName(new NonLocalizedString("name"))
      .withDescription(new NonLocalizedString("description"))
      .withUrl(new NonLocalizedString("http://url.value"))
      .withDeviationBudget(Duration.ofMinutes(7));
    var stop = builder.buildFromValues();

    assertEquals(-1, stop.getIndex());
    assertEquals(1, stop.getSequenceNumber());
    assertEquals(2, stop.getPassengerDelta());
    assertEquals(OSLO_NORTH, stop.getCoordinate());
    assertEquals(DROP_OFF_ONLY, stop.getCarpoolStopType());
    assertEquals(AIMED_ARRIVAL_TIME, stop.getAimedArrivalTime());
    assertEquals(EXPECTED_ARRIVAL_TIME, stop.getExpectedArrivalTime());
    assertEquals(AIMED_DEPARTURE_TIME, stop.getAimedDepartureTime());
    assertEquals(EXPECTED_DEPARTURE_TIME, stop.getExpectedDepartureTime());
    assertEquals("name", stop.getName().toString());
    assertEquals("description", stop.getDescription().toString());
    assertEquals("http://url.value", stop.getUrl().toString());
    assertEquals(Duration.ofMinutes(7), stop.getDeviationBudget());
  }

  @Test
  void buildFromValues_usingCarPoolStop_buildsCorrectValues() {
    var i = new AtomicInteger(0);
    var originalBuilder = new CarpoolStopBuilder(
      new FeedScopedId("feed", "id"),
      i::incrementAndGet
    );
    originalBuilder
      .withSequenceNumber(2)
      .withPassengerDelta(3)
      .withCoordinate(OSLO_CENTER)
      .withCarpoolStopType(DROP_OFF_ONLY)
      .withAimedArrivalTime(AIMED_ARRIVAL_TIME)
      .withExpectedArrivalTime(EXPECTED_ARRIVAL_TIME)
      .withAimedDepartureTime(AIMED_DEPARTURE_TIME)
      .withExpectedDepartureTime(EXPECTED_DEPARTURE_TIME)
      .withName(new NonLocalizedString("name value"))
      .withDescription(new NonLocalizedString("description value"))
      .withUrl(new NonLocalizedString("http://url.value"))
      .withDeviationBudget(Duration.ofMinutes(7));
    var original = originalBuilder.buildFromValues();

    var copyBuilder = new CarpoolStopBuilder(original);
    var carpoolStop = copyBuilder.buildFromValues();

    assertEquals(1, carpoolStop.getIndex());
    assertEquals(original.getSequenceNumber(), carpoolStop.getSequenceNumber());
    assertEquals(original.getPassengerDelta(), carpoolStop.getPassengerDelta());
    assertEquals(original.getCoordinate(), carpoolStop.getCoordinate());
    assertEquals(original.getCarpoolStopType(), carpoolStop.getCarpoolStopType());
    assertEquals(original.getAimedArrivalTime(), carpoolStop.getAimedArrivalTime());
    assertEquals(original.getExpectedArrivalTime(), carpoolStop.getExpectedArrivalTime());
    assertEquals(original.getAimedDepartureTime(), carpoolStop.getAimedDepartureTime());
    assertEquals(original.getExpectedDepartureTime(), carpoolStop.getExpectedDepartureTime());
    assertEquals(original.getName(), carpoolStop.getName());
    assertEquals(original.getDescription(), carpoolStop.getDescription());
    assertEquals(original.getUrl(), carpoolStop.getUrl());
    assertEquals(original.getDeviationBudget(), carpoolStop.getDeviationBudget());
  }
}
