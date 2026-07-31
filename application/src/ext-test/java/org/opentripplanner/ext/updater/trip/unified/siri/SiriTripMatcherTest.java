package org.opentripplanner.ext.updater.trip.unified.siri;

import static com.google.common.truth.Truth.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.updater.trip.unified.model.command.CancelTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DeleteTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.transit.model.TransitTestEnvironment;

/**
 * A SIRI-ET journey is identified by the stops and times of its calls, so the matcher can only
 * work on a command that carries calls. This class pins the decline for the command shapes that
 * carry none: it is what keeps removals resolving exactly as they did before the removal factory
 * learned to consult the matcher, instead of failing on a matcher that has nothing to match on.
 */
class SiriTripMatcherTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2025, 8, 5);

  private final TransitTestEnvironment env = TransitTestEnvironment.of(SERVICE_DATE).build();

  @Test
  void declinesACommandThatCarriesNoCalls() {
    var matcher = new SiriTripMatcher(
      new SiriTripMatcherCache(env.transitRepository()),
      env.transitService(),
      new StopResolver(env.transitService()),
      env.timeZone()
    );
    var reference = TripReference.builder().withStartDate(SERVICE_DATE).build();

    var cancel = new CancelTrip(reference, SERVICE_DATE, null, null);
    assertThat(matcher.match(reference, cancel, SERVICE_DATE)).isEmpty();

    var delete = new DeleteTrip(reference, SERVICE_DATE, null, null);
    assertThat(matcher.match(reference, delete, SERVICE_DATE)).isEmpty();
  }
}
