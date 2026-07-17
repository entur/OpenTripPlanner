package org.opentripplanner.ext.flexbooking.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_EAST;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.CarpoolTripTestData;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;

class DefaultFlexBookingRepositoryTest {

  private static final ZonedDateTime NOON = ZonedDateTime.of(
    2026,
    6,
    5,
    12,
    0,
    0,
    0,
    ZoneId.of("Europe/Oslo")
  );
  private static final LocalDate SERVICE_DATE = NOON.toLocalDate();
  private static final TripIdAndServiceDate KEY = new TripIdAndServiceDate(
    new FeedScopedId("F", "flex-trip-1"),
    SERVICE_DATE
  );

  @Test
  void findsStoredTourByKey() {
    var repository = new DefaultFlexBookingRepository();
    var tour = tourEndingAt(NOON);
    repository.upsertTour(KEY, tour);

    assertThat(repository.findTour(KEY)).hasValue(tour);
    assertThat(
      repository.findTour(new TripIdAndServiceDate(KEY.tripId(), SERVICE_DATE.plusDays(1)))
    ).isEmpty();
  }

  @Test
  void upsertReplacesTheTourForTheSameKey() {
    var repository = new DefaultFlexBookingRepository();
    var first = tourEndingAt(NOON);
    var second = tourEndingAt(NOON.plusHours(2));
    repository.upsertTour(KEY, first);
    repository.upsertTour(KEY, second);

    assertThat(repository.findTour(KEY)).hasValue(second);
    assertThat(repository.listTours()).containsExactly(second);
  }

  @Test
  void removeTourIsIdempotent() {
    var repository = new DefaultFlexBookingRepository();
    repository.upsertTour(KEY, tourEndingAt(NOON));

    repository.removeTour(KEY);
    repository.removeTour(KEY);

    assertThat(repository.findTour(KEY)).isEmpty();
    assertThat(repository.listTours()).isEmpty();
  }

  @Test
  void removesToursThatEndedBeforeTheThreshold() {
    var repository = new DefaultFlexBookingRepository();
    var endedKey = new TripIdAndServiceDate(new FeedScopedId("F", "ended"), SERVICE_DATE);
    var ongoingKey = new TripIdAndServiceDate(new FeedScopedId("F", "ongoing"), SERVICE_DATE);
    repository.upsertTour(endedKey, tourEndingAt(NOON));
    var ongoing = tourEndingAt(NOON.plusHours(2));
    repository.upsertTour(ongoingKey, ongoing);

    int removed = repository.removeExpiredTours(NOON.plusHours(1).toInstant(), Duration.ZERO);

    assertThat(removed).isEqualTo(1);
    assertThat(repository.listTours()).containsExactly(ongoing);
  }

  @Test
  void throttlesSweepsToOncePerInterval() {
    var repository = new DefaultFlexBookingRepository();
    repository.upsertTour(KEY, tourEndingAt(NOON));

    // First sweep runs and purges the expired tour.
    assertThat(
      repository.removeExpiredTours(NOON.plusHours(1).toInstant(), Duration.ZERO)
    ).isEqualTo(1);

    // A second, already-expired tour is added shortly after.
    var addedAfterSweep = tourEndingAt(NOON);
    repository.upsertTour(KEY, addedAfterSweep);

    // A call within the sweep interval is throttled: nothing is scanned or removed.
    assertThat(
      repository.removeExpiredTours(NOON.plusHours(1).plusMinutes(30).toInstant(), Duration.ZERO)
    ).isEqualTo(0);
    assertThat(repository.listTours()).containsExactly(addedAfterSweep);

    // Once the interval has elapsed the next call sweeps again.
    assertThat(
      repository.removeExpiredTours(NOON.plusHours(2).toInstant(), Duration.ZERO)
    ).isEqualTo(1);
    assertThat(repository.listTours()).isEmpty();
  }

  private static CarpoolTrip tourEndingAt(ZonedDateTime endTime) {
    return CarpoolTripTestData.createSimpleTripWithTimes(
      OSLO_CENTER,
      OSLO_EAST,
      endTime.minusHours(1),
      endTime
    );
  }
}
