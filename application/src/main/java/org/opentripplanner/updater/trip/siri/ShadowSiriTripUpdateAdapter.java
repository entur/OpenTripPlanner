package org.opentripplanner.updater.trip.siri;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.trip.regression.RecordingTimetableSnapshot;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) SIRI-ET adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * The primary handler writes through a {@link RecordingTimetableSnapshot}, which captures the
 * record it produces for each trip so it can be compared with the record produced by the unified
 * path.
 */
public class ShadowSiriTripUpdateAdapter implements SiriTripUpdateAdapter {

  private final SiriTripUpdateAdapter primaryAdapter;
  private final SiriNewTripUpdateAdapter shadowAdapter;

  @Nullable
  private final Path outputDirectory;

  public ShadowSiriTripUpdateAdapter(
    SiriTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    boolean fuzzyTripMatching,
    String feedId
  ) {
    this(primaryAdapter, timetableRepository, deduplicator, fuzzyTripMatching, feedId, null);
  }

  public ShadowSiriTripUpdateAdapter(
    SiriTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    boolean fuzzyTripMatching,
    String feedId,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.shadowAdapter = new SiriNewTripUpdateAdapter(
      timetableRepository,
      deduplicator,
      fuzzyTripMatching,
      feedId
    );
    this.outputDirectory = outputDirectory;
  }

  @Override
  public SiriTripUpdateHandler forUpdate(MutableTimetableSnapshot buffer) {
    var recordingBuffer = new RecordingTimetableSnapshot(buffer);
    return new ShadowSiriTripUpdateHandler(
      primaryAdapter.forUpdate(recordingBuffer),
      shadowAdapter.forUpdate(buffer),
      recordingBuffer,
      outputDirectory
    );
  }
}
