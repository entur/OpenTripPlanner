package org.opentripplanner.ext.updater.trip.unified.regression;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;

class RecordingTimetableSnapshotTest {

  private static final String FEED_ID = "F";
  private static final String OTHER_FEED_ID = "F2";
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 30);

  private final MutableTimetableSnapshot delegate = mock(MutableTimetableSnapshot.class);
  private final RecordingTimetableSnapshot subject = new RecordingTimetableSnapshot(delegate);

  @Test
  void updateIsRecordedAndForwarded() {
    var update = realTimeTripUpdate();

    subject.update(update);

    assertThat(subject.lastUpdate()).isSameInstanceAs(update);
    verify(delegate).update(update);
  }

  @Test
  void lastUpdateIsNullUntilSomethingIsWritten() {
    assertThat(subject.lastUpdate()).isNull();
  }

  @Test
  void clearLastUpdateForgetsTheRecordWithoutTouchingTheDelegate() {
    subject.update(realTimeTripUpdate());

    subject.clearLastUpdate();

    assertThat(subject.lastUpdate()).isNull();
    verify(delegate, never()).clear(FEED_ID);
  }

  /**
   * The shadow adapters drive the primary handler once per trip, so it asks to clear the buffer
   * before every trip of a full-dataset batch. Only the first request may reach the real buffer.
   */
  @Test
  void onlyTheFirstClearOfABatchReachesTheDelegate() {
    subject.startBatch();

    subject.clear(FEED_ID);
    subject.clear(FEED_ID);
    subject.clear(FEED_ID);

    verify(delegate, times(1)).clear(FEED_ID);
  }

  @Test
  void startingANewBatchReArmsTheClear() {
    subject.startBatch();
    subject.clear(FEED_ID);
    subject.clear(FEED_ID);

    subject.startBatch();
    subject.clear(FEED_ID);

    verify(delegate, times(2)).clear(FEED_ID);
  }

  @Test
  void eachFeedIsClearedOncePerBatch() {
    subject.startBatch();

    subject.clear(FEED_ID);
    subject.clear(OTHER_FEED_ID);
    subject.clear(FEED_ID);
    subject.clear(OTHER_FEED_ID);

    verify(delegate, times(1)).clear(FEED_ID);
    verify(delegate, times(1)).clear(OTHER_FEED_ID);
  }

  private static RealTimeTripUpdate realTimeTripUpdate() {
    var testModel = TimetableRepositoryForTest.of();
    var route = TimetableRepositoryForTest.route("r1").build();
    var stopPattern = TimetableRepositoryForTest.stopPattern(
      testModel.stop("s1").build(),
      testModel.stop("s2").build()
    );
    var pattern = TimetableRepositoryForTest.tripPattern("pattern1", route)
      .withStopPattern(stopPattern)
      .build();
    var trip = TimetableRepositoryForTest.trip("trip1").build();
    var tripTimes = ScheduledTripTimes.of().withArrivalTimes("00:00 00:01").withTrip(trip).build();
    return RealTimeTripUpdate.of(pattern, tripTimes, SERVICE_DATE).build();
  }
}
