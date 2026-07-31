package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.gtfs.GtfsNewTripUpdateAdapter;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.TripUpdateAdapterUnderTest;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Test helper for applying GTFS-RT trip updates. Which implementation the update goes through is
 * decided by {@link TripUpdateAdapterUnderTest}, so the same tests cover both. Fuzzy trip matching
 * is selected when the helper is created, with {@link #ofFuzzyMatching(TransitTestEnvironment)}.
 */
public class GtfsRtTestHelper {

  /** The delay propagation a GTFS-RT updater runs with unless the router is configured otherwise. */
  private static final ForwardsDelayPropagationType DEFAULT_FORWARDS =
    ForwardsDelayPropagationType.DEFAULT;
  private static final BackwardsDelayPropagationType DEFAULT_BACKWARDS =
    BackwardsDelayPropagationType.REQUIRED_NO_DATA;

  private final TransitTestEnvironment transitTestEnvironment;
  private final GtfsTripUpdateAdapter gtfsAdapter;
  private final boolean fuzzyTripMatching;
  private final ForwardsDelayPropagationType forwardsPropagation;
  private final BackwardsDelayPropagationType backwardsPropagation;

  GtfsRtTestHelper(
    TransitTestEnvironment transitTestEnvironment,
    boolean fuzzyTripMatching,
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    this.transitTestEnvironment = transitTestEnvironment;
    this.fuzzyTripMatching = fuzzyTripMatching;
    this.forwardsPropagation = forwardsPropagation;
    this.backwardsPropagation = backwardsPropagation;
    this.gtfsAdapter = createAdapter(
      transitTestEnvironment,
      fuzzyTripMatching,
      forwardsPropagation,
      backwardsPropagation
    );
  }

  private static GtfsTripUpdateAdapter createAdapter(
    TransitTestEnvironment env,
    boolean fuzzyTripMatching,
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return switch (TripUpdateAdapterUnderTest.current()) {
      case LEGACY -> new GtfsRealTimeTripUpdateAdapter(
        env.transitRepository(),
        DeduplicatorService.NOOP,
        env::defaultServiceDate
      );
      case UNIFIED -> new GtfsNewTripUpdateAdapter(
        env.transitRepository(),
        DeduplicatorService.NOOP,
        forwardsPropagation,
        backwardsPropagation,
        fuzzyTripMatching,
        env.feedId(),
        env::defaultServiceDate
      );
    };
  }

  public static GtfsRtTestHelper of(TransitTestEnvironment transitTestEnvironment) {
    return new GtfsRtTestHelper(transitTestEnvironment, false, DEFAULT_FORWARDS, DEFAULT_BACKWARDS);
  }

  /**
   * A helper for a router configured with delay propagation other than the default. Switching a
   * direction off makes the updater reject an update that leaves times unfilled in that direction,
   * so tests of that contract need to say which pair they run with.
   */
  public static GtfsRtTestHelper of(
    TransitTestEnvironment transitTestEnvironment,
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return new GtfsRtTestHelper(
      transitTestEnvironment,
      false,
      forwardsPropagation,
      backwardsPropagation
    );
  }

  public static GtfsRtTestHelper ofFuzzyMatching(TransitTestEnvironment transitTestEnvironment) {
    return new GtfsRtTestHelper(transitTestEnvironment, true, DEFAULT_FORWARDS, DEFAULT_BACKWARDS);
  }

  public TripUpdateBuilder tripUpdateScheduled(String tripId) {
    return tripUpdate(tripId, GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED);
  }

  public TripUpdateBuilder tripUpdateScheduled(String tripId, LocalDate serviceDate) {
    return tripUpdate(
      tripId,
      serviceDate,
      GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED
    );
  }

  public TripUpdateBuilder tripUpdate(
    String tripId,
    GtfsRealtime.TripDescriptor.ScheduleRelationship scheduleRelationship
  ) {
    return tripUpdate(tripId, transitTestEnvironment.defaultServiceDate(), scheduleRelationship);
  }

  public TripUpdateBuilder tripUpdate(
    String tripId,
    LocalDate serviceDate,
    GtfsRealtime.TripDescriptor.ScheduleRelationship scheduleRelationship
  ) {
    return new TripUpdateBuilder(
      tripId,
      serviceDate,
      scheduleRelationship,
      transitTestEnvironment.timeZone()
    );
  }

  public UpdateResult applyTripUpdate(GtfsRealtime.TripUpdate update) {
    return applyTripUpdates(List.of(update), FULL_DATASET);
  }

  public UpdateResult applyTripUpdate(
    GtfsRealtime.TripUpdate update,
    UpdateIncrementality incrementality
  ) {
    return applyTripUpdates(List.of(update), incrementality);
  }

  public UpdateResult applyTripUpdates(List<GtfsRealtime.TripUpdate> updates) {
    return applyTripUpdates(updates, FULL_DATASET);
  }

  public UpdateResult applyTripUpdates(
    List<GtfsRealtime.TripUpdate> updates,
    UpdateIncrementality incrementality
  ) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      transitTestEnvironment
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(transitTestEnvironment.timetableHandle());
          resultRef.set(
            gtfsAdapter
              .forUpdate(buffer)
              .applyTripUpdates(
                fuzzyMatcher(buffer),
                forwardsPropagation,
                backwardsPropagation,
                incrementality,
                updates,
                transitTestEnvironment.feedId()
              )
          );
        })
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resultRef.get();
  }

  /**
   * The matcher the legacy implementation takes per update, over an update-scoped transit service so
   * that it sees the real-time additions already in the buffer, as production does. The unified
   * implementation ignores this argument and builds its own matcher from the flag it was
   * constructed with.
   */
  @Nullable
  private GtfsRealtimeFuzzyTripMatcher fuzzyMatcher(TimetableRepository buffer) {
    if (!fuzzyTripMatching) {
      return null;
    }
    return new GtfsRealtimeFuzzyTripMatcher(
      new DefaultTransitService(transitTestEnvironment.transitRepository(), buffer)
    );
  }
}
