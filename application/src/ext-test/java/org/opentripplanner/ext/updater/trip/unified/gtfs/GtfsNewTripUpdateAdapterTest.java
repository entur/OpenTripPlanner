package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.transit.realtime.GtfsRealtime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateDispatcher;
import org.opentripplanner.ext.updater.trip.unified.TripUpdateParser;
import org.opentripplanner.ext.updater.trip.unified.resolver.NoOpFuzzyTripMatcher;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;

/**
 * Integration tests for the new GTFS-RT trip update adapter that uses the common trip update
 * infrastructure (GtfsRtTripUpdateParser + TripUpdateDispatcher).
 */
class GtfsNewTripUpdateAdapterTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-id";
  private static final String OPERATOR_ID = "operator-id";

  /** The trip update the stand-in parser of {@link #applyThroughFailingParser} refuses to handle. */
  private static final String POISON_TRIP_ID = "update-the-parser-chokes-on";

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final Operator OPERATOR = ENV_BUILDER.operator(OPERATOR_ID);
  private final Route ROUTE = ENV_BUILDER.route(ROUTE_ID, OPERATOR);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .withWithTripOnServiceDate(TRIP_1_ID)
    .withRoute(ROUTE)
    .addStop(STOP_A, "0:00:10", "0:00:11")
    .addStop(STOP_B, "0:00:20", "0:00:21");

  @Test
  void newAdapterIsInstantiable() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      false,
      env.feedId(),
      env::defaultServiceDate
    );
    assertNotNull(newAdapter);
  }

  @Test
  void emptyUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      false,
      env.feedId(),
      env::defaultServiceDate
    );

    var result = applyTripUpdates(env, newAdapter, List.of());

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }

  @Test
  void nullUpdatesReturnsEmptyResult() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var newAdapter = new GtfsNewTripUpdateAdapter(
      env.transitRepository(),
      new Deduplicator(),
      ForwardsDelayPropagationType.DEFAULT,
      BackwardsDelayPropagationType.REQUIRED_NO_DATA,
      false,
      env.feedId(),
      env::defaultServiceDate
    );

    var result = applyTripUpdates(env, newAdapter, null);

    assertNotNull(result);
    assertEquals(0, result.successful());
    assertEquals(0, result.failed());
  }

  /**
   * No parser can be written that turns every message a real feed can produce into either a command
   * or an {@link UpdateException} - and this adapter is new code besides. What a trip update it
   * cannot handle must never cost is the rest of the poll: the remaining updates, and for a full
   * dataset the whole feed, whose real-time data has already been cleared from the buffer by the
   * time the first update is parsed.
   */
  @Test
  void anUpdateTheAdapterCannotHandleDoesNotDiscardTheRest() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var gtfsRt = GtfsRtTestHelper.of(env);

    var poison = gtfsRt.tripUpdateScheduled(POISON_TRIP_ID).addDelayedStopTime(1, 60).build();
    var delayed = gtfsRt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(1, 60).build();

    var result = applyThroughFailingParser(env, List.of(poison, delayed));

    assertEquals(1, result.successful(), "the update after the failing one should be applied");
    assertEquals(Set.of(UpdateErrorType.UNKNOWN), result.failures().keySet());
    assertEquals(
      "U | A [ND] 0:00:10 0:00:11 | B 0:01:20 0:01:21",
      env.tripData(TRIP_1_ID).showTimetable(),
      "the delay following the failing update should have reached the timetable"
    );
  }

  /**
   * Drive the handler with a parser that throws something it does not model as a rejection for one
   * update, and parses the rest normally - standing in for any defect in the parsing of a single
   * trip update.
   */
  private static UpdateResult applyThroughFailingParser(
    TransitTestEnvironment env,
    List<GtfsRealtime.TripUpdate> updates
  ) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      env
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(env.timetableHandle());
          var feedId = env.feedId();
          var transitService = new DefaultTransitService(env.transitRepository(), buffer);
          var realParser = new GtfsRtTripUpdateParser(
            ForwardsDelayPropagationType.DEFAULT,
            BackwardsDelayPropagationType.REQUIRED_NO_DATA,
            false,
            feedId,
            env.timeZone(),
            env::defaultServiceDate
          );
          TripUpdateParser<GtfsRealtime.TripUpdate> failingParser = update -> {
            if (POISON_TRIP_ID.equals(update.getTrip().getTripId())) {
              throw new IllegalStateException("simulated defect in the GTFS-RT parser");
            }
            return realParser.parse(update);
          };
          var dispatcher = TripUpdateDispatcher.create(
            env.timeZone(),
            transitService,
            DeduplicatorService.NOOP,
            new TripPatternCache(new TripPatternIdGenerator()),
            NoOpFuzzyTripMatcher.INSTANCE,
            new GtfsRtRouteCreationStrategy(feedId)
          );
          resultRef.set(
            new GtfsNewTripUpdateHandler(failingParser, dispatcher, buffer).applyTripUpdates(
              null,
              ForwardsDelayPropagationType.DEFAULT,
              BackwardsDelayPropagationType.REQUIRED_NO_DATA,
              UpdateIncrementality.FULL_DATASET,
              updates,
              feedId
            )
          );
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }

  private static UpdateResult applyTripUpdates(
    TransitTestEnvironment env,
    GtfsNewTripUpdateAdapter adapter,
    List<GtfsRealtime.TripUpdate> updates
  ) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      env
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(env.timetableHandle());
          resultRef.set(
            adapter
              .forUpdate(buffer)
              .applyTripUpdates(
                null,
                ForwardsDelayPropagationType.DEFAULT,
                BackwardsDelayPropagationType.REQUIRED_NO_DATA,
                UpdateIncrementality.DIFFERENTIAL,
                updates,
                env.feedId()
              )
          );
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }
}
