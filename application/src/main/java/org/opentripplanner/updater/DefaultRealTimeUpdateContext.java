package org.opentripplanner.updater;

import java.util.function.Supplier;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;
import org.opentripplanner.updater.trip.siri.EntityResolver;

public class DefaultRealTimeUpdateContext implements RealTimeUpdateContext {

  private final Graph graph;
  private final MutableTimetableSnapshot timetableSnapshotBuffer;
  private final TransitService transitService;

  /**
   * Resolved lazily so that tasks that never touch the realtime vehicles do not mark the vehicle
   * repository as modified in the current transaction.
   */
  private final Supplier<RealtimeVehicleRepository> realtimeVehicleRepository;

  /**
   * The context needs the mutable snapshot so that entity lookups (trips, routes, patterns) see
   * all in-progress real-time additions that have not yet been committed to a published snapshot.
   * <p>
   * A {@link MutableTimetableSnapshot} cannot be used directly for these lookups, because every
   * lookup must also fall back to scheduled data in the {@link TimetableRepository} when an entity
   * is not found in the real-time snapshot. The {@link DefaultTransitService} combines both: it
   * checks the snapshot first, then falls back to the static index.
   * <p>
   * {@link DefaultTransitService} accepts a {@link org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot},
   * because in request scope it must never receive a mutable snapshot. The cast here is safe as
   * long as {@link MutableTimetableSnapshot} and {@link org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot}
   * share a single implementation — which is enforced by {@link MutableTimetableSnapshot} extending
   * {@link org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot}. A cleaner separation
   * would require merging scheduled and real-time data into a single unified store - this is the end goal!
   */
  public DefaultRealTimeUpdateContext(
    Graph graph,
    TimetableRepository timetableRepository,
    MutableTimetableSnapshot timetableSnapshotBuffer,
    Supplier<RealtimeVehicleRepository> realtimeVehicleRepository
  ) {
    this.graph = graph;
    this.timetableSnapshotBuffer = timetableSnapshotBuffer;
    this.transitService = new DefaultTransitService(timetableRepository, timetableSnapshotBuffer);
    this.realtimeVehicleRepository = realtimeVehicleRepository;
  }

  /**
   * Constructor for unit tests only.
   */
  public DefaultRealTimeUpdateContext(Graph graph, TimetableRepository timetableRepository) {
    this(graph, timetableRepository, null, () -> {
      throw new UnsupportedOperationException(
        "The realtime-vehicle repository is not available in this test context"
      );
    });
  }

  @Override
  public MutableTimetableSnapshot mutableSnapshot() {
    return timetableSnapshotBuffer;
  }

  @Override
  public RealtimeVehicleRepository realtimeVehicleRepository() {
    return realtimeVehicleRepository.get();
  }

  @Override
  public Graph graph() {
    return graph;
  }

  @Override
  public TransitService transitService() {
    return transitService;
  }

  @Override
  public GtfsRealtimeFuzzyTripMatcher gtfsRealtimeFuzzyTripMatcher() {
    return new GtfsRealtimeFuzzyTripMatcher(transitService);
  }

  @Override
  public EntityResolver entityResolver(String feedId) {
    return new EntityResolver(transitService, feedId);
  }
}
