package org.opentripplanner.updater.trip.gtfs;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.regression.RecordingTimetableSnapshot;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) GTFS-RT adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * The primary handler writes through a {@link RecordingTimetableSnapshot}, which captures the
 * record it produces for each trip so it can be compared with the record produced by the unified
 * path.
 */
public class ShadowGtfsTripUpdateAdapter implements GtfsTripUpdateAdapter {

  private final GtfsTripUpdateAdapter primaryAdapter;
  private final GtfsNewTripUpdateAdapter shadowAdapter;

  @Nullable
  private final Path outputDirectory;

  public ShadowGtfsTripUpdateAdapter(
    GtfsTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId
  ) {
    this(
      primaryAdapter,
      timetableRepository,
      deduplicator,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      fuzzyMatchingEnabled,
      feedId,
      null
    );
  }

  public ShadowGtfsTripUpdateAdapter(
    GtfsTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyMatchingEnabled,
    String feedId,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.shadowAdapter = new GtfsNewTripUpdateAdapter(
      timetableRepository,
      deduplicator,
      forwardsDelayPropagationType,
      backwardsDelayPropagationType,
      fuzzyMatchingEnabled,
      feedId
    );
    this.outputDirectory = outputDirectory;
  }

  @Override
  public GtfsTripUpdateHandler forUpdate(MutableTimetableSnapshot buffer) {
    var recordingBuffer = new RecordingTimetableSnapshot(buffer);
    return new ShadowGtfsTripUpdateHandler(
      primaryAdapter.forUpdate(recordingBuffer),
      shadowAdapter.forUpdate(buffer),
      recordingBuffer,
      outputDirectory
    );
  }
}
