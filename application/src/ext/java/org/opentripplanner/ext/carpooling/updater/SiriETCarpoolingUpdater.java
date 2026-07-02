package org.opentripplanner.ext.carpooling.updater;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.routing.CarpoolTripVertexResolver;
import org.opentripplanner.ext.carpooling.routing.CarpoolTripWithVertices;
import org.opentripplanner.updater.spi.PollingGraphUpdater;
import org.opentripplanner.updater.support.siri.SiriFileLoader;
import org.opentripplanner.updater.support.siri.SiriHttpLoader;
import org.opentripplanner.updater.support.siri.SiriLoader;
import org.opentripplanner.updater.trip.siri.updater.DefaultSiriETUpdaterParameters;
import org.opentripplanner.updater.trip.siri.updater.EstimatedTimetableSource;
import org.opentripplanner.updater.trip.siri.updater.SiriETHttpTripUpdateSource;
import org.opentripplanner.utils.tostring.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.ServiceDelivery;

/**
 * Polls carpool driver trips from a SIRI-ET HTTP source and maintains them in the
 * {@link CarpoolingRepository}. Each trip's route points are resolved to permanent, car-reachable
 * street vertices before insertion, so everything in the repository is routable as stored and no
 * per-request linking or reachability probing is needed for driver trips.
 */
public class SiriETCarpoolingUpdater extends PollingGraphUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(SiriETCarpoolingUpdater.class);

  /**
   * How long a carpool trip is kept after its latest end time before it is purged. The SIRI-ET
   * source is not guaranteed to send an explicit cancellation once a journey has completed, so
   * completed trips are removed once their latest end time is further in the past than this
   * duration. This keeps instances that run for a long time from accumulating trips that can no
   * longer be routed.
   */
  private static final Duration TRIP_EXPIRY = Duration.ofDays(2);

  private final EstimatedTimetableSource updateSource;

  private final CarpoolingRepository repository;
  private final CarpoolTripVertexResolver vertexResolver;
  private final CarpoolSiriMapper mapper;

  public SiriETCarpoolingUpdater(
    DefaultSiriETUpdaterParameters config,
    CarpoolingRepository repository,
    CarpoolTripVertexResolver vertexResolver
  ) {
    super(config);
    this.updateSource = new SiriETHttpTripUpdateSource(config, siriLoader(config));
    this.repository = repository;
    this.vertexResolver = vertexResolver;
    this.blockReadinessUntilInitialized = config.blockReadinessUntilInitialized();

    LOG.info("Creating SIRI-ET updater running every {}: {}", pollingPeriod(), updateSource);

    this.mapper = new CarpoolSiriMapper(config.feedId());
  }

  /**
   * Repeatedly makes blocking calls to an UpdateStreamer to retrieve carpooling trip updates.
   */
  @Override
  public void runPolling() {
    boolean moreData;
    do {
      moreData = fetchAndProcessUpdates();
    } while (moreData);
    removeExpiredTrips();
  }

  /**
   * Asks the repository to purge trips that have ended more than {@link #TRIP_EXPIRY} ago, so
   * completed trips are eventually removed even when the source stops sending updates for them. The
   * repository throttles the actual scan and shares that throttle across every feed, so calling
   * this on every poll is cheap.
   */
  private void removeExpiredTrips() {
    repository.removeExpiredTrips(Instant.now(), TRIP_EXPIRY);
  }

  /**
   * Fetches updates from the source and processes them.
   *
   * @return true if there is more data available to fetch
   */
  private boolean fetchAndProcessUpdates() {
    var updates = updateSource.getUpdates();
    if (updates.isEmpty()) {
      return false;
    }

    ServiceDelivery serviceDelivery = updates.get().getServiceDelivery();
    processEstimatedTimetableDeliveries(serviceDelivery.getEstimatedTimetableDeliveries());
    return Boolean.TRUE.equals(serviceDelivery.isMoreData());
  }

  /**
   * Processes a list of estimated timetable deliveries.
   *
   * @param deliveries the list of estimated timetable deliveries, may be null
   */
  private void processEstimatedTimetableDeliveries(
    List<EstimatedTimetableDeliveryStructure> deliveries
  ) {
    if (deliveries == null || deliveries.isEmpty()) {
      return;
    }

    for (EstimatedTimetableDeliveryStructure delivery : deliveries) {
      var frames = delivery.getEstimatedJourneyVersionFrames();
      for (var frame : frames) {
        var estimatedVehicleJourneys = frame.getEstimatedVehicleJourneies();

        if (estimatedVehicleJourneys == null || estimatedVehicleJourneys.isEmpty()) {
          LOG.warn("Received an empty EstimatedJourneyVersionFrame, skipping");
          continue;
        }

        estimatedVehicleJourneys.forEach(this::processEstimatedVehicleJourney);
      }
    }
  }

  /**
   * Maps a single estimated vehicle journey to a carpool trip, resolves its route points to
   * permanent street vertices, and upserts the result. The trip is removed instead when the
   * journey is cancelled, has fewer than 2 non-cancelled calls, or has a route point no car can
   * reach — an unresolvable trip could never be routed, so rejecting it here surfaces bad feed
   * geometry once, in the updater log, rather than as silently empty routing responses.
   */
  void processEstimatedVehicleJourney(EstimatedVehicleJourney estimatedVehicleJourney) {
    try {
      FeedScopedId tripId = mapper.tripId(estimatedVehicleJourney);
      if (Boolean.TRUE.equals(estimatedVehicleJourney.isCancellation())) {
        repository.removeCarpoolTrip(tripId);
        return;
      }
      var carpoolTrip = mapper.mapSiriToCarpoolTrip(estimatedVehicleJourney);
      if (carpoolTrip == null) {
        repository.removeCarpoolTrip(tripId);
        return;
      }
      var tripWithVertices = resolveVertices(carpoolTrip);
      if (tripWithVertices == null) {
        LOG.warn(
          "Dropping carpool trip {}: a route point has no car-reachable street vertex",
          tripId
        );
        repository.removeCarpoolTrip(tripId);
        return;
      }
      repository.upsertCarpoolTrip(tripWithVertices);
    } catch (Exception e) {
      LOG.warn("Failed to process EstimatedVehicleJourney: {}", e.getMessage());
    }
  }

  /**
   * Resolves the trip's route points to permanent street vertices, or returns {@code null} when a
   * route point cannot be resolved. When an update leaves the route-point geometry unchanged — the
   * common case for booking updates, which only touch budgets and times — the stored trip's
   * vertices are reused instead of re-linking and re-probing every point on every poll.
   */
  @Nullable
  private CarpoolTripWithVertices resolveVertices(CarpoolTrip trip) {
    var existing = repository.getCarpoolTrip(trip.getId());
    if (existing != null && existing.trip().routePoints().equals(trip.routePoints())) {
      return new CarpoolTripWithVertices(trip, existing.vertices());
    }
    return vertexResolver.resolve(trip);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(SiriETCarpoolingUpdater.class)
      .addStr("source", updateSource.toString())
      .addDuration("frequency", pollingPeriod())
      .toString();
  }

  private static SiriLoader siriLoader(DefaultSiriETUpdaterParameters config) {
    // Load real-time updates from a file.
    if (SiriFileLoader.matchesUrl(config.url())) {
      return new SiriFileLoader(config.url());
    }
    return new SiriHttpLoader(
      config.url(),
      config.timeout(),
      config.httpRequestHeaders(),
      config.previewInterval()
    );
  }
}
