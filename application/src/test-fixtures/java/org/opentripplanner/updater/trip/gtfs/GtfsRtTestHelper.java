package org.opentripplanner.updater.trip.gtfs;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.gtfs.GtfsNewTripUpdateAdapter;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.TripUpdateAdapterUnderTest;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Test helper for applying GTFS-RT trip updates. Which implementation the update goes through is
 * decided by {@link TripUpdateAdapterUnderTest}, so the same tests cover both.
 */
public class GtfsRtTestHelper {

  private final TransitTestEnvironment transitTestEnvironment;
  private final GtfsTripUpdateAdapter gtfsAdapter;

  GtfsRtTestHelper(TransitTestEnvironment transitTestEnvironment) {
    this.transitTestEnvironment = transitTestEnvironment;
    this.gtfsAdapter = createAdapter(transitTestEnvironment);
  }

  private static GtfsTripUpdateAdapter createAdapter(TransitTestEnvironment env) {
    return switch (TripUpdateAdapterUnderTest.current()) {
      case LEGACY -> new GtfsRealTimeTripUpdateAdapter(
        env.timetableRepository(),
        DeduplicatorService.NOOP,
        env::defaultServiceDate
      );
      case UNIFIED -> new GtfsNewTripUpdateAdapter(
        env.timetableRepository(),
        DeduplicatorService.NOOP,
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.REQUIRED_NO_DATA,
        false,
        env.feedId()
      );
    };
  }

  public static GtfsRtTestHelper of(TransitTestEnvironment transitTestEnvironment) {
    return new GtfsRtTestHelper(transitTestEnvironment);
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
                null,
                ForwardsDelayPropagationType.DEFAULT,
                BackwardsDelayPropagationType.REQUIRED_NO_DATA,
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
}
