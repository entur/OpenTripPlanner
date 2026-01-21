package org.opentripplanner.updater.trip;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.DefaultRealTimeUpdateContext;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.siri.SiriEtBuilder;
import org.opentripplanner.updater.trip.siri.SiriRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.updater.EstimatedTimetableHandler;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

public class SiriTestHelper {

  private final TransitTestEnvironment transitTestEnvironment;

  SiriTestHelper(TransitTestEnvironment transitTestEnvironment) {
    this.transitTestEnvironment = transitTestEnvironment;
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
    var adapter = createAdapter(fuzzyMatching);
    UpdateResult updateResult = getEstimatedTimetableHandler(adapter).applyUpdate(
      updates,
      DIFFERENTIAL,
      new DefaultRealTimeUpdateContext(
        new Graph(),
        transitTestEnvironment.timetableRepository(),
        transitTestEnvironment.timetableSnapshotManager().getTimetableSnapshotBuffer()
      )
    );
    commitTimetableSnapshot();
    return updateResult;
  }

  private SiriRealTimeTripUpdateAdapter createAdapter(boolean fuzzyMatching) {
    SiriTripMatcher tripMatcher = fuzzyMatching
      ? new SiriTripMatcher(
        new DefaultTransitService(
          transitTestEnvironment.timetableRepository(),
          transitTestEnvironment.timetableSnapshotManager().getTimetableSnapshotBuffer()
        ),
        transitTestEnvironment.feedId()
      )
      : null;
    return new SiriRealTimeTripUpdateAdapter(
      transitTestEnvironment.feedId(),
      transitTestEnvironment.timetableRepository(),
      transitTestEnvironment.timetableSnapshotManager(),
      tripMatcher
    );
  }

  private EstimatedTimetableHandler getEstimatedTimetableHandler(
    SiriRealTimeTripUpdateAdapter adapter
  ) {
    return new EstimatedTimetableHandler(adapter, transitTestEnvironment.feedId());
  }

  private void commitTimetableSnapshot() {
    transitTestEnvironment.timetableSnapshotManager().purgeAndCommit();
  }
}
