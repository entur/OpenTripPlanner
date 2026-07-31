package org.opentripplanner.ext.updater.trip.unified.regression;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.TripBuilder;
import org.opentripplanner.updater.spi.UpdateErrorType;

class RealTimeTripUpdateComparatorTest {

  private static final TransitRepositoryForTest TEST_MODEL = TransitRepositoryForTest.of();
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 30);
  private static final String TRIP_REFERENCE = "trip1";

  @Test
  void normalizePatternIdReplacesRtCounter() {
    var id = new FeedScopedId("F", "RUT:Line:1:0:005:RT");
    var normalized = RealTimeTripUpdateComparator.normalizePatternId(id);
    assertEquals("F", normalized.getFeedId());
    assertEquals("RUT:Line:1:0:NNN:RT", normalized.getId());
  }

  @Test
  void normalizePatternIdLeavesNonRtUnchanged() {
    var id = new FeedScopedId("F", "RUT:Line:1:0:Regular");
    var normalized = RealTimeTripUpdateComparator.normalizePatternId(id);
    assertSame(id, normalized);
  }

  @Test
  void normalizePatternIdHandlesMultiDigitCounter() {
    var id = new FeedScopedId("F", "route:0:12345:RT");
    var normalized = RealTimeTripUpdateComparator.normalizePatternId(id);
    assertEquals("route:0:NNN:RT", normalized.getId());
  }

  @Test
  void encodeProducesSameResultForDifferentRtPatternCounters() {
    var route = TransitRepositoryForTest.route("r1").build();
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var stopPattern = TransitRepositoryForTest.stopPattern(stop1, stop2);
    var trip = TransitRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();

    var pattern1 = TransitRepositoryForTest.tripPattern("route:0:005:RT", route)
      .withStopPattern(stopPattern)
      .build();
    var pattern2 = TransitRepositoryForTest.tripPattern("route:0:006:RT", route)
      .withStopPattern(stopPattern)
      .build();

    var update1 = RealTimeTripUpdate.of(pattern1, tripTimes, SERVICE_DATE).build();
    var update2 = RealTimeTripUpdate.of(pattern2, tripTimes, SERVICE_DATE).build();

    assertEquals(
      RealTimeTripUpdateComparator.encode(update1),
      RealTimeTripUpdateComparator.encode(update2)
    );
  }

  @Test
  void encodeDistinguishesDifferentNonRtPatterns() {
    var route = TransitRepositoryForTest.route("r1").build();
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var stopPattern = TransitRepositoryForTest.stopPattern(stop1, stop2);
    var trip = TransitRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();

    var patternA = TransitRepositoryForTest.tripPattern("patternA", route)
      .withStopPattern(stopPattern)
      .build();
    var patternB = TransitRepositoryForTest.tripPattern("patternB", route)
      .withStopPattern(stopPattern)
      .build();

    var updateA = RealTimeTripUpdate.of(patternA, tripTimes, SERVICE_DATE).build();
    var updateB = RealTimeTripUpdate.of(patternB, tripTimes, SERVICE_DATE).build();

    assertNotEquals(
      RealTimeTripUpdateComparator.encode(updateA),
      RealTimeTripUpdateComparator.encode(updateB)
    );
  }

  @Test
  void encodeDistinguishesWheelchairAccessibility() {
    assertNotEquals(
      encodeRealTime(b -> b.withWheelchairAccessibility(Accessibility.POSSIBLE)),
      encodeRealTime(b -> b.withWheelchairAccessibility(Accessibility.NOT_POSSIBLE))
    );
  }

  @Test
  void encodeDistinguishesVehicleId() {
    assertNotEquals(
      encodeRealTime(b -> b.withVehicleId("BUS-1")),
      encodeRealTime(b -> b.withVehicleId("BUS-2"))
    );
  }

  @Test
  void encodeDistinguishesOccupancyStatus() {
    assertNotEquals(
      encodeRealTime(b -> b.withOccupancyStatus(0, OccupancyStatus.MANY_SEATS_AVAILABLE)),
      encodeRealTime(b -> b.withOccupancyStatus(0, OccupancyStatus.FULL))
    );
  }

  /**
   * A created trip is invented by the adapter rather than looked up, so the attributes it is stamped
   * with are an output of their own: the two adapters can agree on the trip id, the pattern and
   * every time while naming or operating the trip differently.
   */
  @Test
  void encodeDistinguishesTheAttributesOfACreatedTrip() {
    assertNotEquals(
      encodeCreation(t -> t.withShortName("T1")),
      encodeCreation(t -> t.withShortName("T2"))
    );
    assertNotEquals(encodeCreation(t -> t.withShortName("T1")), encodeCreation(t -> t));
    assertNotEquals(
      encodeCreation(t -> t.withOperator(operator("op1"))),
      encodeCreation(t -> t.withOperator(operator("op2")))
    );
    assertNotEquals(
      encodeCreation(t -> t.withNetexSubmode("localBus")),
      encodeCreation(t -> t.withNetexSubmode("expressBus"))
    );
  }

  /**
   * An update that is not a trip creation writes times onto a trip that already exists and that
   * neither adapter touches, so its attributes are left out of the encoding - they would add a
   * constant to every comparison.
   */
  @Test
  void encodeIgnoresTripAttributesOfAnUpdateThatCreatesNothing() {
    assertEquals(
      encodeUpdateOfExistingTrip(t -> t.withShortName("T1")),
      encodeUpdateOfExistingTrip(t -> t.withShortName("T2"))
    );
  }

  /**
   * A stop substitution for a stop with the same name is a real divergence between the adapters: it
   * produces a different {@link StopPattern}, hence a second real-time pattern. Comparing names
   * only would report the two as a match.
   */
  @Test
  void encodeDistinguishesStopsWithTheSameName() {
    var sharedName = new NonLocalizedString("Central");
    var stopA = TEST_MODEL.stop("sA").withName(sharedName).build();
    var stopB = TEST_MODEL.stop("sB").withName(sharedName).build();
    var lastStop = TEST_MODEL.stop("s2").build();

    assertNotEquals(
      encodeOverStopPattern(TransitRepositoryForTest.stopPattern(stopA, lastStop)),
      encodeOverStopPattern(TransitRepositoryForTest.stopPattern(stopB, lastStop))
    );
  }

  /**
   * Cancelling a stop that is already non-routable in the scheduled pattern changes the pickup and
   * dropoff without changing the stops or the times, and again yields a second real-time pattern.
   */
  @Test
  void encodeDistinguishesCancelledFromNonRoutablePickDrop() {
    var stops = List.<StopLocation>of(TEST_MODEL.stop("s1").build(), TEST_MODEL.stop("s2").build());

    assertNotEquals(
      encodeOverStopPattern(stopPattern(stops, PickDrop.NONE, PickDrop.NONE)),
      encodeOverStopPattern(stopPattern(stops, PickDrop.CANCELLED, PickDrop.CANCELLED))
    );
  }

  @Test
  void identicalRecordsMatch() {
    var update = realTimeUpdate(UnaryOperator.identity());
    var summary = compareOnce(
      new AdapterOutcome.Published(update),
      new AdapterOutcome.Published(update)
    );

    assertThat(summary.matched()).isEqualTo(1);
    assertThat(summary.mismatched()).isEqualTo(0);
  }

  @Test
  void differingRecordsMismatch() {
    var summary = compareOnce(
      new AdapterOutcome.Published(realTimeUpdate(b -> b.withVehicleId("BUS-1"))),
      new AdapterOutcome.Published(realTimeUpdate(b -> b.withVehicleId("BUS-2")))
    );

    assertThat(summary.mismatched()).isEqualTo(1);
    assertThat(summary.matched()).isEqualTo(0);
  }

  /**
   * Both adapters rejecting the same input is agreement, not a match: nothing was compared. This is
   * the blind spot that let a whole poll report "100 % match".
   */
  @Test
  void bothRejectingForTheSameReasonIsNotAMatch() {
    var summary = compareOnce(
      new AdapterOutcome.Rejected(UpdateErrorType.NEGATIVE_HOP_TIME),
      new AdapterOutcome.Rejected(UpdateErrorType.NEGATIVE_HOP_TIME)
    );

    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.bothRejected()).isEqualTo(1);
    assertThat(summary.rejectedForDifferentReasons()).isEqualTo(0);
  }

  @Test
  void bothRejectingForDifferentReasonsIsReported() {
    var summary = compareOnce(
      new AdapterOutcome.Rejected(UpdateErrorType.NEGATIVE_HOP_TIME),
      new AdapterOutcome.Rejected(UpdateErrorType.TRIP_NOT_FOUND)
    );

    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.bothRejected()).isEqualTo(0);
    assertThat(summary.rejectedForDifferentReasons()).isEqualTo(1);
  }

  /**
   * A shadow adapter that throws is a defect in the shadow adapter, whatever the primary did with
   * the same input, so it is counted even when the primary produced nothing either.
   */
  @Test
  void shadowCrashIsCountedWhenThePrimaryAlsoProducedNothing() {
    var summary = compareOnce(
      new AdapterOutcome.Rejected(UpdateErrorType.TRIP_NOT_FOUND),
      new AdapterOutcome.Crashed("NullPointerException")
    );

    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.bothRejected()).isEqualTo(0);
    assertThat(summary.shadowCrashes()).isEqualTo(1);
  }

  @Test
  void shadowRejectingWhatThePrimaryPublishedIsCounted() {
    var summary = compareOnce(
      new AdapterOutcome.Published(realTimeUpdate(UnaryOperator.identity())),
      new AdapterOutcome.Rejected(UpdateErrorType.TRIP_NOT_FOUND)
    );

    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.onlyPrimaryPublished()).isEqualTo(1);
    assertThat(summary.onlyShadowPublished()).isEqualTo(0);
  }

  @Test
  void primaryRejectingWhatTheShadowPublishedIsCounted() {
    var summary = compareOnce(
      new AdapterOutcome.Rejected(UpdateErrorType.TRIP_NOT_FOUND),
      new AdapterOutcome.Published(realTimeUpdate(UnaryOperator.identity()))
    );

    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.onlyShadowPublished()).isEqualTo(1);
    assertThat(summary.onlyPrimaryPublished()).isEqualTo(0);
  }

  /**
   * Compare a single pair of outcomes and return the resulting tally. No output directory is
   * configured, so nothing is written to disk.
   */
  private static RealTimeTripUpdateComparator.Summary compareOnce(
    AdapterOutcome primary,
    AdapterOutcome shadow
  ) {
    var comparator = new RealTimeTripUpdateComparator(null);
    comparator.compare(primary, shadow, TRIP_REFERENCE, () -> "<input/>");
    var summary = comparator.summary();
    assertThat(summary.total()).isEqualTo(1);
    return summary;
  }

  /**
   * Build a real-time update over a fixed two-stop pattern, apply the given customization to the
   * trip-times builder, and return its comparison encoding.
   */
  private static String encodeRealTime(UnaryOperator<RealTimeTripTimesBuilder> customizer) {
    return RealTimeTripUpdateComparator.encode(realTimeUpdate(customizer));
  }

  /**
   * Build a real-time update over a fixed two-stop pattern, applying the given customization to the
   * trip-times builder.
   */
  private static RealTimeTripUpdate realTimeUpdate(
    UnaryOperator<RealTimeTripTimesBuilder> customizer
  ) {
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var pattern = tripPattern(TransitRepositoryForTest.stopPattern(stop1, stop2));
    var trip = TransitRepositoryForTest.trip("trip1").build();
    var scheduled = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();
    var tripTimes = customizer.apply(scheduled.createRealTimeFromScheduledTimes()).build();
    return RealTimeTripUpdate.of(pattern, tripTimes, SERVICE_DATE).build();
  }

  /**
   * Encode a scheduled update over the given stop pattern. The trip, the pattern id and the times
   * are fixed, so any difference in the encoding comes from the stop pattern.
   */
  private static String encodeOverStopPattern(StopPattern stopPattern) {
    var trip = TransitRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();
    var update = RealTimeTripUpdate.of(tripPattern(stopPattern), tripTimes, SERVICE_DATE).build();
    return RealTimeTripUpdateComparator.encode(update);
  }

  /**
   * Encode an update that creates the trip it describes, applying the given customization to the
   * created trip. The pattern and the times are fixed, so any difference in the encoding comes from
   * the attributes of the trip.
   */
  private static String encodeCreation(UnaryOperator<TripBuilder> customizer) {
    return encodeOverTrip(customizer, true);
  }

  /** Encode an update to a trip that already exists, otherwise as {@link #encodeCreation}. */
  private static String encodeUpdateOfExistingTrip(UnaryOperator<TripBuilder> customizer) {
    return encodeOverTrip(customizer, false);
  }

  private static String encodeOverTrip(
    UnaryOperator<TripBuilder> customizer,
    boolean tripCreation
  ) {
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var pattern = tripPattern(TransitRepositoryForTest.stopPattern(stop1, stop2));
    var trip = customizer.apply(TransitRepositoryForTest.trip("trip1")).build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();
    var update = RealTimeTripUpdate.of(pattern, tripTimes, SERVICE_DATE)
      .withTripCreation(tripCreation)
      .build();
    return RealTimeTripUpdateComparator.encode(update);
  }

  private static Operator operator(String id) {
    return Operator.of(FeedScopedIdForTestFactory.id(id)).withName(id).build();
  }

  private static TripPattern tripPattern(StopPattern stopPattern) {
    var route = TransitRepositoryForTest.route("r1").build();
    return TransitRepositoryForTest.tripPattern("pattern1", route)
      .withStopPattern(stopPattern)
      .build();
  }

  private static StopPattern stopPattern(
    List<StopLocation> stops,
    PickDrop pickup,
    PickDrop dropoff
  ) {
    var builder = StopPattern.create(stops.size());
    for (int i = 0; i < stops.size(); i++) {
      builder.stops.with(i, stops.get(i));
      builder.pickups.with(i, pickup);
      builder.dropoffs.with(i, dropoff);
    }
    return builder.build();
  }
}
