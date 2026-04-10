package org.opentripplanner.ext.carpooling;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.model.CarpoolStop;
import org.opentripplanner.ext.carpooling.model.CarpoolStopType;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * Builder utility for creating test CarpoolTrip instances without requiring full Graph infrastructure.
 */
public class CarpoolTripTestData {

  private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
  private static final AtomicInteger AREA_STOP_COUNTER = new AtomicInteger(0);
  public static final Duration DEFAULT_TEST_DEVIATION_BUDGET = Duration.ofMinutes(10);

  /**
   * Creates a simple trip with origin and destination stops, default capacity of 4.
   */
  public static CarpoolTrip createSimpleTrip(WgsCoordinate boarding, WgsCoordinate alighting) {
    var origin = createOriginStop(boarding);
    var destination = createDestinationStop(alighting, 1);
    return createTripWithCapacity(4, List.of(origin, destination));
  }

  /**
   * Creates a simple trip with specific departure time.
   */
  public static CarpoolTrip createSimpleTripWithTime(
    WgsCoordinate boarding,
    WgsCoordinate alighting,
    ZonedDateTime startTime
  ) {
    var origin = createOriginStopWithTime(boarding, startTime, startTime);
    var destination = createDestinationStopWithTime(
      alighting,
      1,
      startTime.plusHours(1),
      startTime.plusHours(1)
    );
    return createTripWithTime(startTime, 4, List.of(origin, destination));
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
    renumberIntermediateStops(intermediateStops, allStops);
    allStops.add(createDestinationStop(alighting, allStops.size()));
    return createTripWithCapacity(4, allStops);
  }

  /**
   * Creates a trip with origin, intermediate stops, and destination. The deviation budget is applied
   * to the origin and destination stops, while intermediate stops retain their own deviation budget.
   */
  public static CarpoolTrip createTripWithStops(
    WgsCoordinate boarding,
    List<CarpoolStop> intermediateStops,
    WgsCoordinate alighting,
    Duration deviationBudget
  ) {
    List<CarpoolStop> allStops = new ArrayList<>();
    allStops.add(createOriginStopWithDeviationBudget(boarding, deviationBudget));
    renumberIntermediateStops(intermediateStops, allStops);
    allStops.add(createDestinationStopWithDeviationBudget(alighting, allStops.size(), deviationBudget));
    return createTripWithCapacity(4, allStops);
  }

  private static void renumberIntermediateStops(
    List<CarpoolStop> intermediateStops,
    List<CarpoolStop> allStops
  ) {
    for (int i = 0; i < intermediateStops.size(); i++) {
      CarpoolStop intermediate = intermediateStops.get(i);
      allStops.add(
        CarpoolStop.of(intermediate.getId(), () -> intermediate.getIndex() + 1)
          .withCoordinate(intermediate.getCoordinate())
          .withCarpoolStopType(intermediate.getCarpoolStopType())
          .withExpectedDepartureTime(intermediate.getExpectedDepartureTime())
          .withAimedArrivalTime(intermediate.getAimedArrivalTime())
          .withExpectedArrivalTime(intermediate.getExpectedArrivalTime())
          .withAimedDepartureTime(intermediate.getAimedDepartureTime())
          .withSequenceNumber(intermediate.getSequenceNumber() + 1)
          .withPassengerDelta(intermediate.getPassengerDelta())
          .withDeviationBudget(intermediate.getDeviationBudget())
          .build()
      );
    }
  }

  /**
   * Creates a trip with specified capacity and all stops (including origin/destination).
   */
  public static CarpoolTrip createTripWithCapacity(int seats, List<CarpoolStop> stops) {
    return new org.opentripplanner.ext.carpooling.model.CarpoolTripBuilder(
      FeedScopedId.ofNullable("TEST", "trip-" + ID_COUNTER.incrementAndGet())
    )
      .withStops(stops)
      .withAvailableSeats(seats)
      .withStartTime(ZonedDateTime.now())
      .build();
  }

  /**
   * Creates a trip with specified deviation budget on all stops.
   */
  public static CarpoolTrip createTripWithDeviationBudget(
    Duration deviationBudget,
    WgsCoordinate boarding,
    WgsCoordinate alighting
  ) {
    var origin = createOriginStopWithDeviationBudget(boarding, deviationBudget);
    var destination = createDestinationStopWithDeviationBudget(alighting, 1, deviationBudget);
    return createTripWithCapacity(4, List.of(origin, destination));
  }

  /**
   * Creates a trip with specific start time and all other parameters.
   * End time is calculated as startTime + 1 hour.
   */
  public static CarpoolTrip createTripWithTime(
    ZonedDateTime startTime,
    int seats,
    List<CarpoolStop> stops
  ) {
    return new org.opentripplanner.ext.carpooling.model.CarpoolTripBuilder(
      FeedScopedId.ofNullable("TEST", "trip-" + ID_COUNTER.incrementAndGet())
    )
      .withStops(stops)
      .withAvailableSeats(seats)
      .withStartTime(startTime)
      .withEndTime(startTime.plusHours(1))
      .build();
  }

  /**
   * Creates a CarpoolStop with specified sequence (0-based) and passenger delta.
   */
  public static CarpoolStop createStop(int zeroBasedSequence, int passengerDelta) {
    return createStopAt(zeroBasedSequence, passengerDelta, CarpoolTestCoordinates.OSLO_CENTER);
  }

  /**
   * Creates a CarpoolStop at a specific location.
   */
  public static CarpoolStop createStopAt(int sequence, WgsCoordinate location) {
    return createStopAt(sequence, 0, location);
  }

  /**
   * Creates a CarpoolStop at a specific location with a specific deviation budget.
   */
  public static CarpoolStop createStopAt(
    int sequence,
    WgsCoordinate location,
    Duration deviationBudget
  ) {
    return createStopAt(sequence, 0, location, deviationBudget);
  }

  /**
   * Creates a CarpoolStop with all parameters.
   */
  public static CarpoolStop createStopAt(int sequence, int passengerDelta, WgsCoordinate location) {
    return createStopAt(sequence, passengerDelta, location, DEFAULT_TEST_DEVIATION_BUDGET);
  }

  /**
   * Creates a CarpoolStop with all parameters and a specific deviation budget.
   */
  public static CarpoolStop createStopAt(
    int sequence,
    int passengerDelta,
    WgsCoordinate location,
    Duration deviationBudget
  ) {
    return CarpoolStop.of(
      FeedScopedId.ofNullable("TEST", "area-" + AREA_STOP_COUNTER.incrementAndGet()),
      AREA_STOP_COUNTER::getAndIncrement
    )
      .withCoordinate(location)
      .withSequenceNumber(sequence)
      .withPassengerDelta(passengerDelta)
      .withDeviationBudget(deviationBudget)
      .build();
  }

  /**
   * Creates an origin stop (first stop, PICKUP_ONLY, passengerDelta=0, departure times only).
   */
  public static CarpoolStop createOriginStop(WgsCoordinate location) {
    return createOriginStopWithTime(location, null, null);
  }

  /**
   * Creates an origin stop with specific departure times.
   */
  public static CarpoolStop createOriginStopWithTime(
    WgsCoordinate location,
    ZonedDateTime expectedDepartureTime,
    ZonedDateTime aimedDepartureTime
  ) {
    return CarpoolStop.of(FeedScopedId.ofNullable("TEST", "area-0"), () -> 0)
      .withCoordinate(location)
      .withExpectedDepartureTime(expectedDepartureTime)
      .withAimedDepartureTime(aimedDepartureTime)
      .withDeviationBudget(DEFAULT_TEST_DEVIATION_BUDGET)
      .build();
  }

  /**
   * Creates an origin stop with specific deviation budget.
   */
  public static CarpoolStop createOriginStopWithDeviationBudget(
    WgsCoordinate location,
    Duration deviationBudget
  ) {
    return CarpoolStop.of(FeedScopedId.ofNullable("TEST", "area-0"), () -> 0)
      .withCoordinate(location)
      .withDeviationBudget(deviationBudget)
      .build();
  }

  /**
   * Creates a destination stop (last stop, DROP_OFF_ONLY, passengerDelta=0, arrival times only).
   */
  public static CarpoolStop createDestinationStop(WgsCoordinate location, int sequenceNumber) {
    return createDestinationStopWithTime(location, sequenceNumber, null, null);
  }

  /**
   * Creates a destination stop with specific arrival times.
   */
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
      .withCarpoolStopType(CarpoolStopType.DROP_OFF_ONLY)
      .withSequenceNumber(sequenceNumber)
      .withExpectedArrivalTime(expectedArrivalTime)
      .withAimedArrivalTime(aimedArrivalTime)
      .withDeviationBudget(DEFAULT_TEST_DEVIATION_BUDGET)
      .build();
  }

  /**
   * Creates a destination stop with specific deviation budget.
   */
  public static CarpoolStop createDestinationStopWithDeviationBudget(
    WgsCoordinate location,
    int sequenceNumber,
    Duration deviationBudget
  ) {
    return CarpoolStop.of(
      FeedScopedId.ofNullable("TEST", "area-" + AREA_STOP_COUNTER.incrementAndGet()),
      AREA_STOP_COUNTER::getAndIncrement
    )
      .withCoordinate(location)
      .withCarpoolStopType(CarpoolStopType.DROP_OFF_ONLY)
      .withSequenceNumber(sequenceNumber)
      .withDeviationBudget(deviationBudget)
      .build();
  }
}
