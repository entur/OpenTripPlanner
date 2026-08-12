package org.opentripplanner.ext.updater.trip.unified.siri;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.JourneyEndpoints;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.VehicleDescription;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.updater.spi.UpdateErrorType;

/**
 * A SIRI-ET journey is identified by where it starts and ends, so the matcher can only work on a
 * command that describes those two calls. This class pins the decline for the command shapes that
 * describe neither: it is what keeps removals resolving exactly as they did before the removal
 * factory learned to consult the matcher, instead of failing on a matcher that has nothing to
 * match on.
 */
class SiriTripMatcherTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2025, 8, 5);

  private final TransitTestEnvironment env = TransitTestEnvironment.of(SERVICE_DATE).build();

  private final SiriTripMatcher matcher = new SiriTripMatcher(
    new SiriTripMatcherCache(env.transitRepository()),
    env.transitService(),
    new StopResolver(env.transitService()),
    env.timeZone()
  );
  private final TripReference reference = TripReference.builder()
    .withStartDate(SERVICE_DATE)
    .build();

  @Test
  void declinesACommandThatDescribesNoJourney() {
    var cancel = new CancelTrip(reference, SERVICE_DATE, null, null);
    assertThat(matcher.match(reference, cancel, SERVICE_DATE)).isEmpty();

    var delete = new DeleteTrip(reference, SERVICE_DATE, null, null);
    assertThat(matcher.match(reference, delete, SERVICE_DATE)).isEmpty();
  }

  /**
   * A cancellation that describes its journey gets a verdict, even when nothing matches: the
   * endpoints it states are the matcher's own to answer for.
   */
  @Test
  void givesAVerdictOnACancellationThatDescribesItsJourney() {
    var unknownStop = StopReference.ofStopId(new FeedScopedId(env.feedId(), "no-such-stop"));
    var endpoints = new JourneyEndpoints(
      unknownStop,
      SERVICE_DATE.atTime(8, 0).atZone(env.timeZone()),
      unknownStop,
      SERVICE_DATE.atTime(9, 0).atZone(env.timeZone())
    );
    var cancel = new CancelTrip(
      reference,
      SERVICE_DATE,
      null,
      null,
      VehicleDescription.unknown(),
      endpoints
    );

    assertFailure(UpdateErrorType.NO_VALID_STOPS, () ->
      matcher.match(reference, cancel, SERVICE_DATE)
    );
  }
}
