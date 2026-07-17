package org.opentripplanner.updater.configure;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.updater.SiriETCarpoolingUpdater;
import org.opentripplanner.ext.siri.updater.azure.SiriAzureETUpdaterParameters;
import org.opentripplanner.ext.siri.updater.azure.SiriAzureUpdater;
import org.opentripplanner.ext.siri.updater.mqtt.MqttSiriETUpdaterParameters;
import org.opentripplanner.ext.siri.updater.mqtt.SiriETMqttUpdater;
import org.opentripplanner.ext.vehiclerentalservicedirectory.VehicleRentalServiceDirectoryFetcher;
import org.opentripplanner.ext.vehiclerentalservicedirectory.api.VehicleRentalServiceDirectoryFetcherParameters;
import org.opentripplanner.framework.io.OtpHttpClientFactory;
import org.opentripplanner.framework.transaction.UpdateManager;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehiclerental.VehicleRentalRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.model.openinghours.OpeningHoursCalendarService;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.GraphWriterService;
import org.opentripplanner.updater.UpdatersParameters;
import org.opentripplanner.updater.alert.gtfs.GtfsRealtimeAlertsUpdater;
import org.opentripplanner.updater.spi.GraphUpdater;
import org.opentripplanner.updater.trip.gtfs.GtfsNewTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.GtfsRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.GtfsTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.ShadowGtfsTripUpdateAdapter;
import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.updater.http.PollingTripUpdater;
import org.opentripplanner.updater.trip.gtfs.updater.http.PollingTripUpdaterParameters;
import org.opentripplanner.updater.trip.gtfs.updater.mqtt.MqttGtfsRealtimeUpdater;
import org.opentripplanner.updater.trip.gtfs.updater.mqtt.MqttGtfsRealtimeUpdaterParameters;
import org.opentripplanner.updater.trip.siri.ShadowSiriTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.SiriFuzzyTripMatcherCache;
import org.opentripplanner.updater.trip.siri.SiriNewTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.SiriRealTimeTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateAdapter;
import org.opentripplanner.updater.trip.siri.updater.SiriETUpdaterParameters;
import org.opentripplanner.updater.trip.siri.updater.google.SiriETGooglePubsubUpdater;
import org.opentripplanner.updater.trip.siri.updater.google.SiriETGooglePubsubUpdaterParameters;
import org.opentripplanner.updater.vehicle_parking.AvailabilityDataSourceFactory;
import org.opentripplanner.updater.vehicle_parking.VehicleParkingAvailabilityUpdater;
import org.opentripplanner.updater.vehicle_parking.VehicleParkingDataSourceFactory;
import org.opentripplanner.updater.vehicle_parking.VehicleParkingUpdater;
import org.opentripplanner.updater.vehicle_position.PollingVehiclePositionUpdater;
import org.opentripplanner.updater.vehicle_rental.VehicleRentalUpdater;
import org.opentripplanner.updater.vehicle_rental.datasources.VehicleRentalDataSourceFactory;

/**
 * Sets up and starts all the graph updaters.
 * <p>
 * Updaters are instantiated based on the updater parameters contained in UpdaterConfig. Updaters
 * are then setup by providing the graph as a parameter. Finally, the updaters are added to the
 * GraphUpdaterManager.
 */
public class UpdaterConfigurator {

  private final Graph graph;
  private final DeduplicatorService deduplicator;
  private final VertexLinker linker;
  private final TimetableRepository timetableRepository;
  private final UpdatersParameters updatersParameters;
  private final RealtimeVehicleRepository realtimeVehicleRepository;
  private final VehicleRentalRepository vehicleRentalRepository;
  private final CarpoolingRepository carpoolingRepository;
  private final VehicleParkingRepository parkingRepository;
  private final UpdateManager updateManager;
  private final RepositoryHandle<
    ReadOnlyTimetableSnapshot,
    MutableTimetableSnapshot
  > timetableRepositoryHandle;

  @Nullable
  private SiriFuzzyTripMatcherCache siriFuzzyTripMatcherCache;

  private UpdaterConfigurator(
    Graph graph,
    DeduplicatorService deduplicator,
    VertexLinker linker,
    RealtimeVehicleRepository realtimeVehicleRepository,
    VehicleRentalRepository vehicleRentalRepository,
    VehicleParkingRepository parkingRepository,
    TimetableRepository timetableRepository,
    CarpoolingRepository carpoolingRepository,
    UpdateManager updateManager,
    RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableRepositoryHandle,
    UpdatersParameters updatersParameters
  ) {
    this.graph = graph;
    this.deduplicator = deduplicator;
    this.linker = linker;
    this.realtimeVehicleRepository = realtimeVehicleRepository;
    this.vehicleRentalRepository = vehicleRentalRepository;
    this.timetableRepository = timetableRepository;
    this.updatersParameters = updatersParameters;
    this.parkingRepository = parkingRepository;
    this.updateManager = updateManager;
    this.timetableRepositoryHandle = timetableRepositoryHandle;
    this.carpoolingRepository = carpoolingRepository;
  }

  public static void configure(
    Graph graph,
    DeduplicatorService deduplicator,
    VertexLinker linker,
    RealtimeVehicleRepository realtimeVehicleRepository,
    VehicleRentalRepository vehicleRentalRepository,
    VehicleParkingRepository parkingRepository,
    TimetableRepository timetableRepository,
    CarpoolingRepository carpoolingRepository,
    UpdateManager updateManager,
    RepositoryHandle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> timetableRepositoryHandle,
    UpdatersParameters updatersParameters
  ) {
    new UpdaterConfigurator(
      graph,
      deduplicator,
      linker,
      realtimeVehicleRepository,
      vehicleRentalRepository,
      parkingRepository,
      timetableRepository,
      carpoolingRepository,
      updateManager,
      timetableRepositoryHandle,
      updatersParameters
    ).configure();
  }

  private void configure() {
    List<GraphUpdater> updaters = new ArrayList<>();

    updaters.addAll(createUpdatersFromConfig());

    updaters.addAll(
      // Setup updaters using the VehicleRentalServiceDirectoryFetcher(Sandbox)
      fetchVehicleRentalServicesFromOnlineDirectory(
        updatersParameters.getVehicleRentalServiceDirectoryFetcherParameters()
      )
    );

    var graphWriterService = new GraphWriterService(
      updateManager,
      timetableRepositoryHandle,
      graph,
      timetableRepository
    );
    var updaterManager = new GraphUpdaterManager(
      graphWriterService,
      graphWriterService::stop,
      updaters
    );

    updaterManager.startUpdaters();

    // Stop the updater manager if it contains nothing
    if (updaterManager.numberOfUpdaters() == 0) {
      updaterManager.stop();
    }
    // Otherwise add it to the graph
    else {
      timetableRepository.initUpdaterManager(updaterManager);
    }
  }

  public static void shutdownGraph(TimetableRepository timetableRepository) {
    GraphUpdaterManager updaterManager = timetableRepository.getUpdaterManager();
    if (updaterManager != null) {
      updaterManager.stop();
    }
  }

  /* private methods */

  /**
   * Use the online UpdaterDirectoryService to fetch VehicleRental updaters.
   */
  private List<GraphUpdater> fetchVehicleRentalServicesFromOnlineDirectory(
    VehicleRentalServiceDirectoryFetcherParameters parameters
  ) {
    if (parameters == null) {
      return List.of();
    }
    return VehicleRentalServiceDirectoryFetcher.createUpdatersFromEndpoint(
      parameters,
      linker,
      vehicleRentalRepository
    );
  }

  /**
   * @return a list of GraphUpdaters created from the configuration
   */
  private List<GraphUpdater> createUpdatersFromConfig() {
    OpeningHoursCalendarService openingHoursCalendarService =
      graph.getOpeningHoursCalendarService();

    List<GraphUpdater> updaters = new ArrayList<>();

    if (!updatersParameters.getVehicleRentalParameters().isEmpty()) {
      int maxHttpConnections = updatersParameters.getVehicleRentalParameters().size();
      var otpHttpClientFactory = new OtpHttpClientFactory(maxHttpConnections);
      for (var configItem : updatersParameters.getVehicleRentalParameters()) {
        var source = VehicleRentalDataSourceFactory.create(
          configItem.sourceParameters(),
          otpHttpClientFactory
        );
        updaters.add(new VehicleRentalUpdater(configItem, source, linker, vehicleRentalRepository));
      }
    }
    for (var configItem : updatersParameters.getGtfsRealtimeAlertsUpdaterParameters()) {
      updaters.add(new GtfsRealtimeAlertsUpdater(configItem, timetableRepository));
    }
    for (var configItem : updatersParameters.getPollingStoptimeUpdaterParameters()) {
      updaters.add(new PollingTripUpdater(configItem, createGtfsAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getVehiclePositionsUpdaterParameters()) {
      updaters.add(new PollingVehiclePositionUpdater(configItem, realtimeVehicleRepository));
    }
    for (var configItem : updatersParameters.getSiriETUpdaterParameters()) {
      updaters.add(SiriUpdaterModule.createSiriETUpdater(configItem, createSiriAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getSiriETCarpoolingUpdaterParameters()) {
      updaters.add(new SiriETCarpoolingUpdater(configItem, carpoolingRepository));
    }
    for (var configItem : updatersParameters.getSiriETLiteUpdaterParameters()) {
      updaters.add(SiriUpdaterModule.createSiriETUpdater(configItem, createSiriAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getSiriETGooglePubsubUpdaterParameters()) {
      updaters.add(new SiriETGooglePubsubUpdater(configItem, createSiriAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getSiriSXUpdaterParameters()) {
      updaters.add(
        SiriUpdaterModule.createSiriSXUpdater(
          configItem,
          timetableRepository,
          siriFuzzyTripMatcherCache()
        )
      );
    }
    for (var configItem : updatersParameters.getSiriSXLiteUpdaterParameters()) {
      updaters.add(
        SiriUpdaterModule.createSiriSXUpdater(
          configItem,
          timetableRepository,
          siriFuzzyTripMatcherCache()
        )
      );
    }
    for (var configItem : updatersParameters.getMqttGtfsRealtimeUpdaterParameters()) {
      updaters.add(new MqttGtfsRealtimeUpdater(configItem, createGtfsAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getVehicleParkingUpdaterParameters()) {
      switch (configItem.updateType()) {
        case FULL -> {
          var source = VehicleParkingDataSourceFactory.create(
            configItem,
            openingHoursCalendarService
          );
          updaters.add(new VehicleParkingUpdater(configItem, source, linker, parkingRepository));
        }
        case AVAILABILITY_ONLY -> {
          var source = AvailabilityDataSourceFactory.create(configItem);
          updaters.add(
            new VehicleParkingAvailabilityUpdater(configItem, source, parkingRepository)
          );
        }
      }
    }
    for (var configItem : updatersParameters.getSiriAzureETUpdaterParameters()) {
      updaters.add(SiriAzureUpdater.createETUpdater(configItem, createSiriAdapter(configItem)));
    }
    for (var configItem : updatersParameters.getSiriAzureSXUpdaterParameters()) {
      updaters.add(
        SiriAzureUpdater.createSXUpdater(
          configItem,
          timetableRepository,
          siriFuzzyTripMatcherCache()
        )
      );
    }
    for (var configItem : updatersParameters.getMqttSiriETUpdaterParameters()) {
      updaters.add(new SiriETMqttUpdater(configItem, createSiriAdapter(configItem)));
    }

    return updaters;
  }

  private SiriRealTimeTripUpdateAdapter provideSiriAdapter(boolean fuzzyTripMatching) {
    var cache = fuzzyTripMatching ? siriFuzzyTripMatcherCache() : null;
    return new SiriRealTimeTripUpdateAdapter(timetableRepository, deduplicator, cache);
  }

  private SiriFuzzyTripMatcherCache siriFuzzyTripMatcherCache() {
    if (siriFuzzyTripMatcherCache == null) {
      siriFuzzyTripMatcherCache = new SiriFuzzyTripMatcherCache(timetableRepository);
    }
    return siriFuzzyTripMatcherCache;
  }

  private GtfsRealTimeTripUpdateAdapter provideGtfsAdapter() {
    return new GtfsRealTimeTripUpdateAdapter(timetableRepository, deduplicator, () ->
      LocalDate.now(timetableRepository.getTimeZone())
    );
  }

  private SiriTripUpdateAdapter createSiriAdapter(SiriETUpdaterParameters config) {
    return createSiriAdapter(
      config.shadowComparison(),
      config.useNewUpdaterImplementation(),
      config.fuzzyTripMatching(),
      config.feedId(),
      config.shadowComparisonReportDirectory()
    );
  }

  private SiriTripUpdateAdapter createSiriAdapter(SiriETGooglePubsubUpdaterParameters config) {
    return createSiriAdapter(
      config.shadowComparison(),
      config.useNewUpdaterImplementation(),
      config.fuzzyTripMatching(),
      config.feedId(),
      config.shadowComparisonReportDirectory()
    );
  }

  private SiriTripUpdateAdapter createSiriAdapter(SiriAzureETUpdaterParameters config) {
    return createSiriAdapter(
      config.isShadowComparison(),
      config.isUseNewUpdaterImplementation(),
      config.isFuzzyTripMatching(),
      config.feedId(),
      config.getShadowComparisonReportDirectory()
    );
  }

  private SiriTripUpdateAdapter createSiriAdapter(MqttSiriETUpdaterParameters config) {
    return createSiriAdapter(
      config.shadowComparison(),
      config.useNewUpdaterImplementation(),
      config.fuzzyTripMatching(),
      config.feedId(),
      config.shadowComparisonReportDirectory()
    );
  }

  /**
   * Create the SIRI-ET trip update adapter selected by the configuration: the shadow-comparison
   * adapter (running legacy and unified side by side), the new unified adapter, or the legacy
   * adapter.
   */
  private SiriTripUpdateAdapter createSiriAdapter(
    boolean shadowComparison,
    boolean useNewUpdaterImplementation,
    boolean fuzzyTripMatching,
    String feedId,
    @Nullable Path shadowComparisonReportDirectory
  ) {
    if (shadowComparison) {
      return new ShadowSiriTripUpdateAdapter(
        provideSiriAdapter(fuzzyTripMatching),
        timetableRepository,
        deduplicator,
        fuzzyTripMatching,
        feedId,
        shadowComparisonReportDirectory
      );
    } else if (useNewUpdaterImplementation) {
      return new SiriNewTripUpdateAdapter(
        timetableRepository,
        deduplicator,
        fuzzyTripMatching,
        feedId
      );
    } else {
      return provideSiriAdapter(fuzzyTripMatching);
    }
  }

  private GtfsTripUpdateAdapter createGtfsAdapter(PollingTripUpdaterParameters config) {
    return createGtfsAdapter(
      config.shadowComparison(),
      config.useNewUpdaterImplementation(),
      config.forwardsDelayPropagationType(),
      config.backwardsDelayPropagationType(),
      config.fuzzyTripMatching(),
      config.feedId(),
      config.shadowComparisonReportDirectory()
    );
  }

  private GtfsTripUpdateAdapter createGtfsAdapter(MqttGtfsRealtimeUpdaterParameters config) {
    return createGtfsAdapter(
      config.shadowComparison(),
      config.useNewUpdaterImplementation(),
      config.forwardsDelayPropagationType(),
      config.backwardsDelayPropagationType(),
      config.fuzzyTripMatching(),
      config.feedId(),
      config.shadowComparisonReportDirectory()
    );
  }

  /**
   * Create the GTFS-RT trip update adapter selected by the configuration: the shadow-comparison
   * adapter (running legacy and unified side by side), the new unified adapter, or the legacy
   * adapter.
   */
  private GtfsTripUpdateAdapter createGtfsAdapter(
    boolean shadowComparison,
    boolean useNewUpdaterImplementation,
    ForwardsDelayPropagationType forwardsDelayPropagationType,
    BackwardsDelayPropagationType backwardsDelayPropagationType,
    boolean fuzzyTripMatching,
    String feedId,
    @Nullable Path shadowComparisonReportDirectory
  ) {
    if (shadowComparison) {
      return new ShadowGtfsTripUpdateAdapter(
        provideGtfsAdapter(),
        timetableRepository,
        deduplicator,
        forwardsDelayPropagationType,
        backwardsDelayPropagationType,
        fuzzyTripMatching,
        feedId,
        shadowComparisonReportDirectory
      );
    } else if (useNewUpdaterImplementation) {
      return new GtfsNewTripUpdateAdapter(
        timetableRepository,
        deduplicator,
        forwardsDelayPropagationType,
        backwardsDelayPropagationType,
        fuzzyTripMatching,
        feedId
      );
    } else {
      return provideGtfsAdapter();
    }
  }
}
