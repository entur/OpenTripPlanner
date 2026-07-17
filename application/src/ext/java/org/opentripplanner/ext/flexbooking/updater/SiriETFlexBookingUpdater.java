package org.opentripplanner.ext.flexbooking.updater;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.opentripplanner.ext.flexbooking.FlexBookingRepository;
import org.opentripplanner.transit.service.TimetableRepository;
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
 * Polls a SIRI-ET source for the booked tours of flex vehicles and mirrors them into the
 * {@link FlexBookingRepository}. Writes go directly to the concurrent repository — no graph
 * writer thread is involved, following the carpooling updater pattern.
 */
public class SiriETFlexBookingUpdater extends PollingGraphUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(SiriETFlexBookingUpdater.class);

  /**
   * How long a tour is kept after its latest end time before it is purged. The SIRI-ET source is
   * not guaranteed to send an explicit cancellation once a journey has completed, so completed
   * tours are removed once their latest end time is further in the past than this duration.
   */
  private static final Duration TOUR_EXPIRY = Duration.ofDays(2);

  private final EstimatedTimetableSource updateSource;

  private final FlexBookingRepository repository;
  private final FlexBookingSiriMapper mapper;

  public SiriETFlexBookingUpdater(
    DefaultSiriETUpdaterParameters config,
    FlexBookingRepository repository,
    TimetableRepository timetableRepository
  ) {
    super(config);
    this.updateSource = new SiriETHttpTripUpdateSource(config, siriLoader(config));
    this.repository = repository;
    this.blockReadinessUntilInitialized = config.blockReadinessUntilInitialized();

    LOG.info(
      "Creating SIRI-ET flex booking updater running every {}: {}",
      pollingPeriod(),
      updateSource
    );

    this.mapper = new FlexBookingSiriMapper(config.feedId(), timetableRepository);
  }

  /**
   * Repeatedly makes blocking calls to an UpdateStreamer to retrieve flex booking updates.
   */
  @Override
  public void runPolling() {
    boolean moreData;
    do {
      moreData = fetchAndProcessUpdates();
    } while (moreData);
    repository.removeExpiredTours(Instant.now(), TOUR_EXPIRY);
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
   * Maps a single estimated vehicle journey to a booked tour and upserts it, or removes the tour
   * when the journey is cancelled or has fewer than 2 calls. Journeys that cannot be matched to
   * an active unscheduled flex trip, or that violate the feed contract, are skipped and any
   * previously stored tour is kept.
   */
  void processEstimatedVehicleJourney(EstimatedVehicleJourney estimatedVehicleJourney) {
    try {
      var key = mapper.resolveTourKey(estimatedVehicleJourney);
      if (key.isEmpty() || !mapper.isActiveUnscheduledTrip(key.get())) {
        return;
      }
      if (Boolean.TRUE.equals(estimatedVehicleJourney.isCancellation())) {
        repository.removeTour(key.get());
        return;
      }
      var tour = mapper.mapToTour(estimatedVehicleJourney);
      if (tour == null) {
        repository.removeTour(key.get());
        return;
      }
      repository.upsertTour(key.get(), tour);
    } catch (Exception e) {
      LOG.warn("Failed to process EstimatedVehicleJourney: {}", e.getMessage());
    }
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(SiriETFlexBookingUpdater.class)
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
