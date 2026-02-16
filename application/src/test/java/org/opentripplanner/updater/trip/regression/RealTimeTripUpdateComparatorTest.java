package org.opentripplanner.updater.trip.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;

class RealTimeTripUpdateComparatorTest {

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 30);

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
    var route = TimetableRepositoryForTest.route("r1").build();
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var stopPattern = TimetableRepositoryForTest.stopPattern(stop1, stop2);
    var trip = TimetableRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();

    var pattern1 = TimetableRepositoryForTest.tripPattern("route:0:005:RT", route)
      .withStopPattern(stopPattern)
      .build();
    var pattern2 = TimetableRepositoryForTest.tripPattern("route:0:006:RT", route)
      .withStopPattern(stopPattern)
      .build();

    var update1 = new RealTimeTripUpdate(pattern1, tripTimes, SERVICE_DATE);
    var update2 = new RealTimeTripUpdate(pattern2, tripTimes, SERVICE_DATE);

    assertEquals(
      RealTimeTripUpdateComparator.encode(update1),
      RealTimeTripUpdateComparator.encode(update2)
    );
  }

  @Test
  void encodeDistinguishesDifferentNonRtPatterns() {
    var route = TimetableRepositoryForTest.route("r1").build();
    var stop1 = TEST_MODEL.stop("s1").build();
    var stop2 = TEST_MODEL.stop("s2").build();
    var stopPattern = TimetableRepositoryForTest.stopPattern(stop1, stop2);
    var trip = TimetableRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();

    var patternA = TimetableRepositoryForTest.tripPattern("patternA", route)
      .withStopPattern(stopPattern)
      .build();
    var patternB = TimetableRepositoryForTest.tripPattern("patternB", route)
      .withStopPattern(stopPattern)
      .build();

    var updateA = new RealTimeTripUpdate(patternA, tripTimes, SERVICE_DATE);
    var updateB = new RealTimeTripUpdate(patternB, tripTimes, SERVICE_DATE);

    assertNotEquals(
      RealTimeTripUpdateComparator.encode(updateA),
      RealTimeTripUpdateComparator.encode(updateB)
    );
  }
}
