package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateResult;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * Test helper for applying SIRI-ET estimated timetables through the unified (new) trip update
 * adapter. Fuzzy trip matching is selected when the helper is created, with
 * {@link #ofFuzzyMatching(TransitTestEnvironment)}.
 */
public class SiriTestHelper {

  private final TransitTestEnvironment transitTestEnvironment;
  private final SiriNewTripUpdateAdapter siriAdapter;

  SiriTestHelper(TransitTestEnvironment transitTestEnvironment, boolean fuzzyTripMatching) {
    this.transitTestEnvironment = transitTestEnvironment;
    this.siriAdapter = new SiriNewTripUpdateAdapter(
      transitTestEnvironment.timetableRepository(),
      DeduplicatorService.NOOP,
      fuzzyTripMatching,
      transitTestEnvironment.feedId()
    );
  }

  public static SiriTestHelper of(TransitTestEnvironment transitTestEnvironment) {
    return new SiriTestHelper(transitTestEnvironment, false);
  }

  public static SiriTestHelper ofFuzzyMatching(TransitTestEnvironment transitTestEnvironment) {
    return new SiriTestHelper(transitTestEnvironment, true);
  }

  public SiriEtBuilder etBuilder() {
    return new SiriEtBuilder(transitTestEnvironment.localTimeParser());
  }

  public UpdateResult applyEstimatedTimetableWithFuzzyMatcher(
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    return applyUpdates(updates);
  }

  public UpdateResult applyEstimatedTimetable(List<EstimatedTimetableDeliveryStructure> updates) {
    return applyUpdates(updates);
  }

  public TransitTestEnvironment realtimeTestEnvironment() {
    return transitTestEnvironment;
  }

  private UpdateResult applyUpdates(List<EstimatedTimetableDeliveryStructure> updates) {
    var resultRef = new AtomicReference<UpdateResult>();
    try {
      transitTestEnvironment
        .updateManager()
        .submit(ctx -> {
          var buffer = ctx.repository(transitTestEnvironment.timetableHandle());
          var feedId = transitTestEnvironment.feedId();
          var transitService = new DefaultTransitService(
            transitTestEnvironment.timetableRepository(),
            buffer
          );
          resultRef.set(
            siriAdapter
              .forUpdate(buffer)
              .applyEstimatedTimetable(
                new EntityResolver(transitService, feedId),
                feedId,
                DIFFERENTIAL,
                updates
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
