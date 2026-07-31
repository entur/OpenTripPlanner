package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.updater.trip.unified.siri.SiriNewTripUpdateAdapter;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.trip.TripUpdateAdapterUnderTest;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;

/**
 * Test helper for applying SIRI-ET estimated timetables. Which implementation the update goes
 * through is decided by {@link TripUpdateAdapterUnderTest}, so the same tests cover both. Fuzzy
 * trip matching is selected when the helper is created, with
 * {@link #ofFuzzyMatching(TransitTestEnvironment)}.
 */
public class SiriTestHelper {

  private final TransitTestEnvironment transitTestEnvironment;
  private final SiriTripUpdateAdapter siriAdapter;

  SiriTestHelper(TransitTestEnvironment transitTestEnvironment, boolean fuzzyTripMatching) {
    this.transitTestEnvironment = transitTestEnvironment;
    this.siriAdapter = createAdapter(transitTestEnvironment, fuzzyTripMatching);
  }

  private static SiriTripUpdateAdapter createAdapter(
    TransitTestEnvironment env,
    boolean fuzzyTripMatching
  ) {
    var repository = env.transitRepository();
    return switch (TripUpdateAdapterUnderTest.current()) {
      case LEGACY -> new SiriRealTimeTripUpdateAdapter(
        repository,
        DeduplicatorService.NOOP,
        fuzzyTripMatching ? new SiriFuzzyTripMatcherCache(repository) : null
      );
      case UNIFIED -> new SiriNewTripUpdateAdapter(
        repository,
        DeduplicatorService.NOOP,
        fuzzyTripMatching,
        env.feedId()
      );
    };
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
            transitTestEnvironment.transitRepository(),
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
