package org.opentripplanner.updater.trip;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.siri.EntityResolver;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriFuzzyTripMatcher;
import org.opentripplanner.updater.trip.siri.SiriRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.updater.EstimatedTimetableHandler;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

public class SiriTestHelper {

  private final TransitTestEnvironment transitTestEnvironment;
  private final SiriRealTimeTripUpdateAdapter siriAdapter;
  private final TransitService realTimeTransitService;

  SiriTestHelper(TransitTestEnvironment transitTestEnvironment) {
    this.transitTestEnvironment = transitTestEnvironment;
    this.siriAdapter = new SiriRealTimeTripUpdateAdapter(
      transitTestEnvironment.timetableRepository(),
      DeduplicatorService.NOOP,
      transitTestEnvironment.timetableSnapshotManager()
    );
    this.realTimeTransitService = new DefaultTransitService(
      transitTestEnvironment.timetableRepository(),
      transitTestEnvironment.timetableSnapshotManager().getTimetableSnapshotBuffer()
    );
  }

  public static SiriTestHelper of(TransitTestEnvironment transitTestEnvironment) {
    return new SiriTestHelper(transitTestEnvironment);
  }

  public SiriEtBuilder etBuilder() {
    return new SiriEtBuilder(transitTestEnvironment.localTimeParser());
  }

  public UpdateResult applyEstimatedTimetableWithFuzzyMatcher(
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    return applyEstimatedTimetable(updates, true);
  }

  public UpdateResult applyEstimatedTimetable(List<EstimatedTimetableDeliveryStructure> updates) {
    return applyEstimatedTimetable(updates, false);
  }

  public TransitTestEnvironment realtimeTestEnvironment() {
    return transitTestEnvironment;
  }

  private UpdateResult applyEstimatedTimetable(
    List<EstimatedTimetableDeliveryStructure> updates,
    boolean fuzzyMatching
  ) {
    UpdateResult updateResult = getEstimatedTimetableHandler(fuzzyMatching).applyUpdate(
      updates,
      DIFFERENTIAL
    );
    commitTimetableSnapshot();
    return updateResult;
  }

  private EstimatedTimetableHandler getEstimatedTimetableHandler(boolean fuzzyMatching) {
    SiriFuzzyTripMatcher matcher = fuzzyMatching
      ? new SiriFuzzyTripMatcher(realTimeTransitService)
      : null;
    return new EstimatedTimetableHandler(
      siriAdapter,
      matcher,
      new EntityResolver(realTimeTransitService, transitTestEnvironment.feedId()),
      transitTestEnvironment.feedId()
    );
  }

  private void commitTimetableSnapshot() {
    transitTestEnvironment.timetableSnapshotManager().purgeAndCommit();
  }
}
