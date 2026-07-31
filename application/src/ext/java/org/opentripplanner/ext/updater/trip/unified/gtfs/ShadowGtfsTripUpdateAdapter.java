package org.opentripplanner.ext.updater.trip.unified.gtfs;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.regression.RecordingTimetableRepository;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateHandler;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) GTFS-RT adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * The primary handler writes through a {@link RecordingTimetableRepository}, which captures the
 * record it produces for each trip so it can be compared with the record produced by the unified
 * path.
 * <p>
 * Turning shadow comparison on must not change what ends up in the timetable. The primary handler is
 * driven one trip at a time, but it is given the caller's own incrementality — which decides how it
 * treats CANCELED and DUPLICATED trips — and the recording buffer collapses the resulting repeated
 * requests to clear the buffer down to one per batch.
 */
public class ShadowGtfsTripUpdateAdapter implements GtfsTripUpdateAdapter {

  private final GtfsTripUpdateAdapter primaryAdapter;
  private final GtfsNewTripUpdateAdapter shadowAdapter;

  @Nullable
  private final Path outputDirectory;

  public ShadowGtfsTripUpdateAdapter(
    GtfsTripUpdateAdapter primaryAdapter,
    TransitRepository transitRepository,
    DeduplicatorService deduplicator,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId,
    Supplier<LocalDate> localDateNow,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.shadowAdapter = new GtfsNewTripUpdateAdapter(
      transitRepository,
      deduplicator,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      fuzzyMatchingEnabled,
      feedId,
      localDateNow
    );
    this.outputDirectory = outputDirectory;
  }

  @Override
  public GtfsTripUpdateHandler forUpdate(TimetableRepository buffer) {
    var recordingBuffer = new RecordingTimetableRepository(buffer);
    return new ShadowGtfsTripUpdateHandler(
      primaryAdapter.forUpdate(recordingBuffer),
      shadowAdapter.forUpdate(buffer),
      recordingBuffer,
      outputDirectory
    );
  }
}
