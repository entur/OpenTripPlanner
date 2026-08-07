package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripAndPattern;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * The matcher identifies a trip by route, direction and start time, all of which are required.
 * <p>
 * A failed match is a non-answer, not a verdict: the matcher declines with an empty Optional and
 * the caller rejects the update for whatever its exact lookup found - the legacy GTFS-RT path
 * never emits a fuzzy error code of its own. The one exception is a reference that names no trip
 * id at all: once the match fails, that message has identified its trip by nothing whatsoever,
 * and only the matcher can see it.
 */
class GtfsTripMatcherTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2025, 8, 5);
  private static final String OUTBOUND_TRIP_ID = "outbound";
  private static final String INBOUND_TRIP_ID = "inbound";
  private static final String OVERNIGHT_TRIP_ID = "overnight";
  private static final String START_TIME = "10:00:00";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of(SERVICE_DATE);
  private final RegularStop stopA = envBuilder.stop("A");
  private final RegularStop stopB = envBuilder.stop("B");
  private final Route route = envBuilder.route("route-1");

  private final TransitTestEnvironment env = envBuilder
    // Outbound and inbound leave at the same time, so the direction is what tells them apart.
    .addTrip(
      TripInput.of(OUTBOUND_TRIP_ID)
        .withRoute(route)
        .addStop(stopA, "10:00")
        .addStop(stopB, "10:10"),
      b -> b.withDirection(Direction.OUTBOUND)
    )
    .addTrip(
      TripInput.of(INBOUND_TRIP_ID)
        .withRoute(route)
        .addStop(stopB, "10:00")
        .addStop(stopA, "10:10"),
      b -> b.withDirection(Direction.INBOUND)
    )
    // Runs the day before and crosses midnight, so its 01:30 departure is 25:30 on that day.
    .addTrip(
      TripInput.of(OVERNIGHT_TRIP_ID)
        .withRoute(route)
        .withServiceDates(SERVICE_DATE.minusDays(1))
        .addStop(stopA, "25:30")
        .addStop(stopB, "25:40"),
      b -> b.withDirection(Direction.OUTBOUND)
    )
    .build();

  @Test
  void matchesTheTripGoingInTheReportedDirection() {
    assertThat(match(reference().withDirection(Direction.OUTBOUND)).trip().getId()).isEqualTo(
      id(OUTBOUND_TRIP_ID)
    );
    assertThat(match(reference().withDirection(Direction.INBOUND)).trip().getId()).isEqualTo(
      id(INBOUND_TRIP_ID)
    );
  }

  /**
   * Route and start time alone leave both directions of the line as candidates, so the update
   * cannot be placed and is rejected instead of being applied to one of them.
   */
  @Test
  void rejectsAReferenceWithoutADirection() {
    assertNoMatch(reference());
  }

  /**
   * A direction the mapping does not recognise is still something the feed said, so it is matched as
   * given rather than ignored: only a pattern with an unknown direction can satisfy it, and this
   * schedule has none.
   */
  @Test
  void doesNotIgnoreAnUnknownDirection() {
    assertNoMatch(reference().withDirection(Direction.UNKNOWN));
  }

  @Test
  void rejectsAReferenceWithoutARoute() {
    assertNoMatch(
      TripReference.builder()
        .withTripId(id("does-not-exist"))
        .withStartTime(START_TIME)
        .withStartDate(SERVICE_DATE)
        .withDirection(Direction.OUTBOUND)
    );
  }

  @Test
  void rejectsAReferenceWithoutAStartTime() {
    assertNoMatch(
      TripReference.builder()
        .withTripId(id("does-not-exist"))
        .withRouteId(route.getId())
        .withStartDate(SERVICE_DATE)
        .withDirection(Direction.OUTBOUND)
    );
  }

  @Test
  void rejectsAnUnknownRoute() {
    assertNoMatch(reference().withRouteId(id("no-such-route")).withDirection(Direction.OUTBOUND));
  }

  @Test
  void rejectsAStartTimeNoTripDepartsAt() {
    assertNoMatch(reference().withStartTime("11:11:11").withDirection(Direction.OUTBOUND));
  }

  /**
   * A date guessed on the feed's behalf would identify whichever trip runs today, so only a reported
   * one is matched against - and the service date the update is applied on is not it.
   */
  @Test
  void rejectsAReferenceWithoutAStartDate() {
    assertNoMatch(
      TripReference.builder()
        .withTripId(id("does-not-exist"))
        .withRouteId(route.getId())
        .withStartTime(START_TIME)
        .withDirection(Direction.OUTBOUND)
    );
  }

  @Test
  void matchesTheReportedDateAndNotTheDateTheUpdateIsAppliedOn() {
    var reference = reference().withDirection(Direction.OUTBOUND).build();
    var matcher = new GtfsTripMatcher(env.transitService());

    var match = matcher.match(
      reference,
      ReviseTrip.builder(reference, SERVICE_DATE).build(),
      // A date nothing runs on: were it the one being matched against, there would be no match.
      SERVICE_DATE.plusYears(1)
    );

    assertThat(match.orElseThrow().trip().getId()).isEqualTo(id(OUTBOUND_TRIP_ID));
  }

  /** A reference without a trip id is matched by the same tuple as one with an unknown id. */
  @Test
  void matchesAReferenceThatNamesNoTripId() {
    var match = match(
      TripReference.builder()
        .withRouteId(route.getId())
        .withStartTime(START_TIME)
        .withStartDate(SERVICE_DATE)
        .withDirection(Direction.OUTBOUND)
    );
    assertThat(match.trip().getId()).isEqualTo(id(OUTBOUND_TRIP_ID));
  }

  /**
   * Once the match fails, a message that named no trip id has identified its trip by nothing at
   * all, and only the matcher can see that - no caller knows whether the tuple named a trip. So
   * this is the one verdict the matcher owns, and it is the same answer legacy reaches through its
   * post-match validation: structurally invalid.
   */
  @Test
  void rejectsAReferenceThatNamesNoTripAtAll() {
    var reference = TripReference.builder()
      .withRouteId(route.getId())
      .withStartTime("11:11:11")
      .withStartDate(SERVICE_DATE)
      .withDirection(Direction.OUTBOUND);

    var exception = assertThrows(UpdateException.class, () -> match(reference));
    assertThat(exception.errorType()).isEqualTo(UpdateErrorType.INVALID_INPUT_STRUCTURE);
  }

  @Test
  void rejectsATripWhoseServiceDoesNotRunOnTheDate() {
    assertNoMatch(
      reference().withDirection(Direction.OUTBOUND).withStartDate(SERVICE_DATE.plusDays(1))
    );
  }

  /**
   * A trip that left the day before and is still running after midnight departs at 25:30 on its own
   * service date, which is 01:30 on the date the update reports.
   */
  @Test
  void matchesATripCarriedOverFromThePreviousDay() {
    var match = match(reference().withStartTime("01:30:00").withDirection(Direction.OUTBOUND));
    assertThat(match.trip().getId()).isEqualTo(id(OVERNIGHT_TRIP_ID));
  }

  private TripReference.Builder reference() {
    return TripReference.builder()
      .withTripId(id("does-not-exist"))
      .withRouteId(route.getId())
      .withStartTime(START_TIME)
      .withStartDate(SERVICE_DATE);
  }

  private TripAndPattern match(TripReference.Builder reference) {
    return tryMatch(reference).orElseThrow();
  }

  private Optional<TripAndPattern> tryMatch(TripReference.Builder reference) {
    var tripReference = reference.build();
    var matcher = new GtfsTripMatcher(env.transitService());
    return matcher.match(
      tripReference,
      ReviseTrip.builder(tripReference, SERVICE_DATE).build(),
      SERVICE_DATE
    );
  }

  /**
   * The references here all carry a trip id, so a failed match is a decline: the caller keeps the
   * error of its own exact lookup and the matcher claims nothing.
   */
  private void assertNoMatch(TripReference.Builder reference) {
    assertThat(tryMatch(reference)).isEmpty();
  }

  private static FeedScopedId id(String id) {
    return new FeedScopedId(FeedScopedIdForTestFactory.FEED_ID, id);
  }
}
