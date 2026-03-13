package org.opentripplanner.ext.carpooling;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.model.CarpoolStop;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.model.CarpoolTripBuilder;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * Builder utility for creating test CarpoolTrip instances without requiring full Graph
 * infrastructure.
 */
public class CarpoolTripTestData {

  private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
  private static final AtomicInteger AREA_STOP_COUNTER = new AtomicInteger(0);

  private static final int DEFAULT_TOTAL_CAPACITY = CarpoolTrip.DEFAULT_TOTAL_CAPACITY;
  private static final Duration DEFAULT_DEVIATION_BUDGET = Duration.ofMinutes(10);

  /**
   * Creates a simple trip with origin and destination stops.
   */
  public static CarpoolTrip createSimpleTrip(WgsCoordinate boarding, WgsCoordinate alighting) {
    var stops = List.of(createOriginStop(boarding), createDestinationStop(alighting, 1));
    return buildTrip(DEFAULT_TOTAL_CAPACITY, DEFAULT_DEVIATION_BUDGET, null, stops);
  }

  /**
   * Creates a simple trip with specific departure time.
   */
  public static CarpoolTrip createSimpleTripWithTime(
    WgsCoordinate boarding,
    WgsCoordinate alighting,
    ZonedDateTime startTime
  ) {
    var stops = List.of(
      createOriginStopWithTime(boarding, startTime, startTime),
      createDestinationStopWithTime(alighting, 1, startTime.plusHours(1), startTime.plusHours(1))
    );
    return buildTrip(DEFAULT_TOTAL_CAPACITY, DEFAULT_DEVIATION_BUDGET, startTime, stops);
  }

  /**
   * Creates a simple trip with specific start and end times.
   */
  public static CarpoolTrip createSimpleTripWithTimes(
    WgsCoordinate boarding,
    WgsCoordinate alighting,
    ZonedDateTime startTime,
    ZonedDateTime endTime
  ) {
    var origin = createOriginStopWithTime(boarding, startTime, startTime);
    var destination = createDestinationStopWithTime(alighting, 1, endTime, endTime);
    return new CarpoolTripBuilder(
      FeedScopedId.ofNullable("TEST", "trip-" + ID_COUNTER.incrementAndGet())
    )
      .withStops(List.of(origin, destination))
      .withTotalCapacity(DEFAULT_TOTAL_CAPACITY)
      .withStartTime(origin.getAimedDepartureTime())
      .withEndTime(destination.getAimedArrivalTime())
      .withDeviationBudget(DEFAULT_DEVIATION_BUDGET)
      .build();
  }

  /**
   * Creates a trip with origin, intermediate stops, and destination.
   */
  public static CarpoolTrip createTripWithStops(
    WgsCoordinate boarding,
    List<CarpoolStop> intermediateStops,
    WgsCoordinate alighting
  ) {
    List<CarpoolStop> allStops = new ArrayList<>();
    allStops.add(createOriginStop(boarding));

    for (int i = 0; i < intermediateStops.size(); i++) {
      CarpoolStop intermediate = intermediateStops.get(i);
      allStops.add(
        CarpoolStop.of(intermediate.getId(), () -> intermediate.getIndex() + 1)
          .withCoordinate(intermediate.getCoordinate())
          .withExpectedDepartureTime(intermediate.getExpectedDepartureTime())
          .withAimedDepartureTime(intermediate.getAimedDepartureTime())
          .withExpectedArrivalTime(intermediate.getExpectedArrivalTime())
          .withAimedArrivalTime(intermediate.getAimedArrivalTime())
          .withSequenceNumber(intermediate.getSequenceNumber() + 1)
          .withOnboardCount(intermediate.getOnboardCount())
          .build()
      );
    }

    allStops.add(createDestinationStop(alighting, allStops.size()));
    return buildTrip(DEFAULT_TOTAL_CAPACITY, DEFAULT_DEVIATION_BUDGET, null, allStops);
  }

  /**
   * Creates a trip with specified capacity and all stops (including origin/destination).
   */
  public static CarpoolTrip createTripWithCapacity(int capacity, List<CarpoolStop> stops) {
    return buildTrip(capacity, DEFAULT_DEVIATION_BUDGET, null, stops);
  }

  /**
   * Creates a trip with specified deviation budget.
   */
  public static CarpoolTrip createTripWithDeviationBudget(
    Duration deviationBudget,
    WgsCoordinate boarding,
    WgsCoordinate alighting
  ) {
    var stops = List.of(createOriginStop(boarding), createDestinationStop(alighting, 1));
    return buildTrip(DEFAULT_TOTAL_CAPACITY, deviationBudget, null, stops);
  }

  /**
   * Creates a trip with specified deviation budget, capacity, and stops.
   */
  public static CarpoolTrip createTripWithDeviationBudget(
    Duration deviationBudget,
    int capacity,
    List<CarpoolStop> stops
  ) {
    return buildTrip(capacity, deviationBudget, null, stops);
  }

  /**
   * Creates a trip with specific start time.
   */
  public static CarpoolTrip createTripWithTime(
    ZonedDateTime startTime,
    int capacity,
    List<CarpoolStop> stops
  ) {
    return buildTrip(capacity, DEFAULT_DEVIATION_BUDGET, startTime, stops);
  }

  /**
   * Creates a CarpoolStop with specified sequence (0-based) and onboard count.
   */
  public static CarpoolStop createStop(int zeroBasedSequence, int onboardCount) {
    return createStopAt(zeroBasedSequence, onboardCount, CarpoolTestCoordinates.OSLO_CENTER);
  }

  /**
   * Creates a CarpoolStop at a specific location with onboardCount=1 (driver only).
   */
  public static CarpoolStop createStopAt(int sequence, WgsCoordinate location) {
    return createStopAt(sequence, 1, location);
  }

  /**
   * Creates a CarpoolStop with all parameters.
   */
  public static CarpoolStop createStopAt(int sequence, int onboardCount, WgsCoordinate location) {
    return CarpoolStop.of(
      FeedScopedId.ofNullable("TEST", "area-" + AREA_STOP_COUNTER.incrementAndGet()),
      AREA_STOP_COUNTER::getAndIncrement
    )
      .withCoordinate(location)
      .withSequenceNumber(sequence)
      .withOnboardCount(onboardCount)
      .build();
  }

  public static CarpoolStop createOriginStop(WgsCoordinate location) {
    return createOriginStopWithTime(location, null, null);
  }

  public static CarpoolStop createOriginStopWithTime(
    WgsCoordinate location,
    ZonedDateTime expectedDepartureTime,
    ZonedDateTime aimedDepartureTime
  ) {
    return CarpoolStop.of(FeedScopedId.ofNullable("TEST", "area-0"), () -> 0)
      .withCoordinate(location)
      .withOnboardCount(1)
      .withExpectedDepartureTime(expectedDepartureTime)
      .withAimedDepartureTime(aimedDepartureTime)
      .build();
  }

  public static CarpoolStop createDestinationStop(WgsCoordinate location, int sequenceNumber) {
    return createDestinationStopWithTime(location, sequenceNumber, null, null);
  }

  public static CarpoolStop createDestinationStopWithTime(
    WgsCoordinate location,
    int sequenceNumber,
    ZonedDateTime expectedArrivalTime,
    ZonedDateTime aimedArrivalTime
  ) {
    return CarpoolStop.of(
      FeedScopedId.ofNullable("TEST", "area-" + AREA_STOP_COUNTER.incrementAndGet()),
      AREA_STOP_COUNTER::getAndIncrement
    )
      .withCoordinate(location)
      .withSequenceNumber(sequenceNumber)
      .withOnboardCount(1)
      .withExpectedArrivalTime(expectedArrivalTime)
      .withAimedArrivalTime(aimedArrivalTime)
      .build();
  }

  private static CarpoolTrip buildTrip(
    int capacity,
    Duration deviationBudget,
    ZonedDateTime startTime,
    List<CarpoolStop> stops
  ) {
    var actualStartTime = startTime != null ? startTime : ZonedDateTime.now();
    var builder = new CarpoolTripBuilder(
      FeedScopedId.ofNullable("TEST", "trip-" + ID_COUNTER.incrementAndGet())
    )
      .withStops(stops)
      .withTotalCapacity(capacity)
      .withStartTime(actualStartTime)
      .withDeviationBudget(deviationBudget);
    if (startTime != null) {
      builder.withEndTime(startTime.plusHours(1));
    }
    return builder.build();
  }
}
