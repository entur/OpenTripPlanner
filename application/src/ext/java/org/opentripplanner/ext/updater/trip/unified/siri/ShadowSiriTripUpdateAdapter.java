package org.opentripplanner.ext.updater.trip.unified.siri;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.regression.RecordingTimetableRepository;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.repository.TimetableRepository;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateHandler;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) SIRI-ET adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * The primary handler writes through a {@link RecordingTimetableRepository}, which captures the
 * record it produces for each trip so it can be compared with the record produced by the unified
 * path.
 * <p>
 * Turning shadow comparison on must not change what ends up in the timetable. The primary handler is
 * driven one journey at a time, but it is given the caller's own incrementality rather than a
 * substitute, and the recording buffer collapses the resulting repeated requests to clear the buffer
 * down to one per batch.
 */
public class ShadowSiriTripUpdateAdapter implements SiriTripUpdateAdapter {

  private final SiriTripUpdateAdapter primaryAdapter;
  private final SiriNewTripUpdateAdapter shadowAdapter;

  @Nullable
  private final Path outputDirectory;

  public ShadowSiriTripUpdateAdapter(
    SiriTripUpdateAdapter primaryAdapter,
    TransitRepository transitRepository,
    DeduplicatorService deduplicator,
    boolean fuzzyTripMatching,
    String feedId,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.shadowAdapter = new SiriNewTripUpdateAdapter(
      transitRepository,
      deduplicator,
      fuzzyTripMatching,
      feedId
    );
    this.outputDirectory = outputDirectory;
  }

  @Override
  public SiriTripUpdateHandler forUpdate(TimetableRepository buffer) {
    var recordingBuffer = new RecordingTimetableRepository(buffer);
    return new ShadowSiriTripUpdateHandler(
      primaryAdapter.forUpdate(recordingBuffer),
      shadowAdapter.forUpdate(buffer),
      recordingBuffer,
      outputDirectory
    );
  }
}
