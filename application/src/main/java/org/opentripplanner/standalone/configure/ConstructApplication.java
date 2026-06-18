package org.opentripplanner.standalone.configure;

import jakarta.ws.rs.core.Application;
import javax.annotation.Nullable;
import org.opentripplanner.apis.gtfs.configure.SchemaModule;
import org.opentripplanner.apis.transmodel.configure.TransmodelSchemaModule;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.datastore.api.DataSource;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.configure.CarpoolingModule;
import org.opentripplanner.ext.dataoverlay.configure.DataOverlayParameterBindingsModule;
import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.configure.EmissionServiceModule;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.configure.EmpiricalDelayServiceModule;
import org.opentripplanner.ext.geocoder.LuceneIndex;
import org.opentripplanner.ext.geocoder.configure.GeocoderModule;
import org.opentripplanner.ext.interactivelauncher.configuration.InteractiveLauncherModule;
import org.opentripplanner.ext.ridehailing.configure.RideHailingServicesModule;
import org.opentripplanner.ext.sorlandsbanen.configure.SorlandsbanenNorwayModule;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.configure.StopConsolidationServiceModule;
import org.opentripplanner.framework.application.LogMDCSupport;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueSummary;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParameters;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorTransitDataMapper;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.routing.linking.configure.LinkingServiceModule;
import org.opentripplanner.routing.util.EllipsoidUtils;
import org.opentripplanner.routing.via.configure.ViaModule;
import org.opentripplanner.service.osminfo.OsmInfoGraphBuildRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.configure.RealtimeVehicleRepositoryModule;
import org.opentripplanner.service.realtimevehicles.configure.RealtimeVehicleServiceModule;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.configure.StreetDetailsServiceModule;
import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehicleparking.configure.VehicleParkingServiceModule;
import org.opentripplanner.service.vehiclerental.VehicleRentalRepository;
import org.opentripplanner.service.vehiclerental.configure.VehicleRentalRepositoryModule;
import org.opentripplanner.service.vehiclerental.configure.VehicleRentalServiceModule;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeRepository;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeService;
import org.opentripplanner.service.worldenvelope.configure.WorldEnvelopeServiceModule;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.config.CommandLineParameters;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.DebugUiConfig;
import org.opentripplanner.standalone.config.OtpConfig;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.standalone.config.configure.ConfigModule;
import org.opentripplanner.standalone.config.configure.DeduplicatorServiceModule;
import org.opentripplanner.standalone.configure.spring.PhaseContext;
import org.opentripplanner.standalone.server.GrizzlyServer;
import org.opentripplanner.standalone.server.MetricsLogging;
import org.opentripplanner.standalone.server.OTPWebApplication;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.service.StreetLimitationParametersServiceModule;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.configure.TransferServiceModule;
import org.opentripplanner.transit.configure.TransitModule;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.updater.configure.UpdaterConfigurator;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.utils.logging.ProgressTracker;
import org.opentripplanner.warmup.WarmupLauncher;
import org.opentripplanner.warmup.configure.WarmupModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for creating the top level services like the {@link OTPWebApplication}
 * and {@link GraphBuilder}. The purpose of this class is to wire the application, creating the
 * necessary Services and modules and putting them together. It is NOT responsible for starting or
 * running the application. The whole idea of this class is to separate application construction
 * from running it.
 * <p>
 * The top level construction class(this class) may delegate to other construction classes
 * to inject configuration and services into submodules. An instance of this class is created
 * using the {@link LoadApplication} - An application is constructed AFTER config and input files
 * are loaded.
 * <p>
 * THIS CLASS IS NOT THREAD SAFE - THE APPLICATION SHOULD BE CREATED IN ONE THREAD. This
 * should be really fast, since the only IO operations are reading config files and logging. Loading
 * transit or map data should NOT happen during this phase.
 */
public class ConstructApplication {

  private static final Logger LOG = LoggerFactory.getLogger(ConstructApplication.class);

  private final CommandLineParameters cli;
  private final GraphBuilderDataSources graphBuilderDataSources;
  /**
   * The OSM Info is injected into the graph-builder, but not the web-server; Hence not part of
   * the application context.
   */
  private final OsmInfoGraphBuildRepository osmInfoGraphBuildRepository;
  private final PhaseContext context;

  /**
   * Create a new OTP configuration instance for a given directory.
   */
  ConstructApplication(
    CommandLineParameters cli,
    Graph graph,
    OsmInfoGraphBuildRepository osmInfoGraphBuildRepository,
    StreetDetailsRepository streetDetailsRepository,
    TimetableRepository timetableRepository,
    TransferRepository transferRepository,
    WorldEnvelopeRepository worldEnvelopeRepository,
    ConfigModel config,
    GraphBuilderDataSources graphBuilderDataSources,
    DataImportIssueSummary issueSummary,
    EmissionRepository emissionRepository,
    @Nullable EmpiricalDelayRepository empiricalDelayRepository,
    VehicleParkingRepository vehicleParkingRepository,
    @Nullable StopConsolidationRepository stopConsolidationRepository,
    StreetRepository streetRepository,
    FareServiceFactory fareServiceFactory
  ) {
    this.cli = cli;
    this.graphBuilderDataSources = graphBuilderDataSources;
    this.osmInfoGraphBuildRepository = osmInfoGraphBuildRepository;

    this.context = new PhaseContext()
      // Pre-built, non-null instances handed off from the prior phases (= @BindsInstance).
      .registerInstance(ConfigModel.class, config)
      .registerInstance(Graph.class, graph)
      .registerInstance(TimetableRepository.class, timetableRepository)
      .registerInstance(TransferRepository.class, transferRepository)
      .registerInstance(WorldEnvelopeRepository.class, worldEnvelopeRepository)
      .registerInstance(VehicleParkingRepository.class, vehicleParkingRepository)
      .registerInstance(DataImportIssueSummary.class, issueSummary)
      .registerInstance(StreetDetailsRepository.class, streetDetailsRepository)
      .registerInstance(EmissionRepository.class, emissionRepository)
      .registerInstance(StreetRepository.class, streetRepository)
      .registerInstance(FareServiceFactory.class, fareServiceFactory)
      .registerInstance(RouteRequest.class, config.routerConfig().routingRequestDefaults())
      // Pre-built, nullable instances (= @Nullable @BindsInstance).
      .registerNullableInstance(StopConsolidationRepository.class, stopConsolidationRepository)
      .registerNullableInstance(EmpiricalDelayRepository.class, empiricalDelayRepository)
      // @Configuration classes replacing the Dagger @Component modules.
      .registerConfig(
        CarpoolingModule.class,
        ConfigModule.class,
        ConstructApplicationModule.class,
        DataOverlayParameterBindingsModule.class,
        EmissionServiceModule.class,
        EmpiricalDelayServiceModule.class,
        DeduplicatorServiceModule.class,
        GeocoderModule.class,
        InteractiveLauncherModule.class,
        StreetDetailsServiceModule.class,
        LinkingServiceModule.class,
        RealtimeVehicleServiceModule.class,
        RealtimeVehicleRepositoryModule.class,
        RideHailingServicesModule.class,
        SchemaModule.class,
        TransmodelSchemaModule.class,
        SorlandsbanenNorwayModule.class,
        StopConsolidationServiceModule.class,
        StreetLimitationParametersServiceModule.class,
        TransitModule.class,
        TransferServiceModule.class,
        VehicleParkingServiceModule.class,
        VehicleRentalRepositoryModule.class,
        VehicleRentalServiceModule.class,
        ViaModule.class,
        WarmupModule.class,
        WorldEnvelopeServiceModule.class
      )
      .refresh();
  }

  /**
   * Create a new Grizzly server - call this method once, the new instance is created every time
   * this method is called.
   */
  public GrizzlyServer createGrizzlyServer() {
    return new GrizzlyServer(
      cli,
      createApplication(),
      routerConfig().server().apiProcessingTimeout()
    );
  }

  /**
   * Create the default graph builder.
   */
  public GraphBuilder createGraphBuilder() {
    LOG.info("Wiring up and configuring graph builder task.");
    return GraphBuilder.create(
      buildConfig(),
      graphBuilderDataSources,
      graph(),
      osmInfoGraphBuildRepository,
      streetDetailsRepository(),
      fareServiceFactory(),
      streetRepository(),
      timetableRepository(),
      transferRepository(),
      worldEnvelopeRepository(),
      vehicleParkingRepository(),
      emissionRepository(),
      empiricalDelayRepository(),
      stopConsolidationRepository(),
      cli.doLoadStreetGraph(),
      cli.doSaveStreetGraph()
    );
  }

  /**
   * The output data source to use for saving the serialized graph.
   * <p>
   * This method will return {@code null} if the graph should NOT be saved. The business logic to
   * make that decision is in the {@link GraphBuilderDataSources}.
   */
  @Nullable
  public DataSource graphOutputDataSource() {
    return graphBuilderDataSources.getOutputGraph();
  }

  public GraphBuilderDataSources graphBuilderDataSources() {
    return graphBuilderDataSources;
  }

  private Application createApplication() {
    LOG.info("Wiring up and configuring server.");
    setupTransitRoutingServer();
    return new OTPWebApplication(routerConfig().server(), this::createServerContext);
  }

  private void setupTransitRoutingServer() {
    enableRequestTraceLogging();
    createMetricsLogging();

    createRaptorTransitData(
      timetableRepository(),
      transferRepository(),
      routerConfig().transitTuningConfig()
    );

    /* Create updater modules from JSON config. */
    UpdaterConfigurator.configure(
      graph(),
      deduplicatorService(),
      vertexLinker(),
      realtimeVehicleRepository(),
      vehicleRentalRepository(),
      vehicleParkingRepository(),
      timetableRepository(),
      carpoolingRepository(),
      snapshotManager(),
      routerConfig().updaterConfig()
    );

    // Start application warmup — runs routing queries to warm up the application
    context.get(WarmupLauncher.class).start();

    initEllipsoidToGeoidDifference();

    initializeTransferCache(routerConfig().transitTuningConfig(), timetableRepository());

    if (OTPFeature.SandboxAPIGeocoder.isOn()) {
      LOG.info("Initializing geocoder");
      // eagerly initialize the geocoder
      context.getNullable(LuceneIndex.class);
    }
  }

  private void initEllipsoidToGeoidDifference() {
    try {
      var c = context.get(WorldEnvelopeService.class).envelope().orElseThrow().center();
      double value = EllipsoidUtils.computeEllipsoidToGeoidDifference(c.latitude(), c.longitude());
      graph().initEllipsoidToGeoidDifference(value, c.latitude(), c.longitude());
    } catch (Exception e) {
      LOG.error("Error computing ellipsoid/geoid difference");
    }
  }

  /**
   * Create transit layer for Raptor routing. Here we map the scheduled timetables.
   */
  public static void createRaptorTransitData(
    TimetableRepository timetableRepository,
    TransferRepository transferRepository,
    TransitTuningParameters tuningParameters
  ) {
    if (!timetableRepository.hasTransit() || !timetableRepository.isIndexed()) {
      LOG.warn(
        "Cannot create Raptor data, that requires the graph to have transit data and be indexed."
      );
    }
    LOG.info("Creating transit layer for Raptor routing.");
    timetableRepository.setRaptorTransitData(
      RaptorTransitDataMapper.map(tuningParameters, timetableRepository, transferRepository)
    );
    timetableRepository.setRealtimeRaptorTransitData(
      new RaptorTransitData(timetableRepository.getRaptorTransitData())
    );
  }

  public static void initializeTransferCache(
    TransitTuningParameters transitTuningConfig,
    TimetableRepository timetableRepository
  ) {
    var transferCacheRequests = transitTuningConfig.transferCacheRequests();
    if (!transferCacheRequests.isEmpty()) {
      var progress = ProgressTracker.track(
        "Creating initial raptor transfer cache",
        1,
        transferCacheRequests.size()
      );

      LOG.info(progress.startMessage());

      transferCacheRequests.forEach(request -> {
        timetableRepository.getRaptorTransitData().initTransferCacheForRequest(request);

        //noinspection Convert2MethodRef
        progress.step(s -> LOG.info(s));
      });

      LOG.info(progress.completeMessage());
    }
  }

  public TimetableRepository timetableRepository() {
    return context.get(TimetableRepository.class);
  }

  public TransferRepository transferRepository() {
    return context.get(TransferRepository.class);
  }

  @Nullable
  public CarpoolingRepository carpoolingRepository() {
    return context.getNullable(CarpoolingRepository.class);
  }

  public DataImportIssueSummary dataImportIssueSummary() {
    return context.get(DataImportIssueSummary.class);
  }

  public OsmInfoGraphBuildRepository osmInfoGraphBuildRepository() {
    return osmInfoGraphBuildRepository;
  }

  @Nullable
  public StopConsolidationRepository stopConsolidationRepository() {
    return context.getNullable(StopConsolidationRepository.class);
  }

  public StreetRepository streetRepository() {
    return context.get(StreetRepository.class);
  }

  public RealtimeVehicleRepository realtimeVehicleRepository() {
    return context.get(RealtimeVehicleRepository.class);
  }

  public VehicleRentalRepository vehicleRentalRepository() {
    return context.get(VehicleRentalRepository.class);
  }

  private TimetableSnapshotManager snapshotManager() {
    return context.get(TimetableSnapshotManager.class);
  }

  public VehicleParkingService vehicleParkingService() {
    return context.get(VehicleParkingService.class);
  }

  public VehicleParkingRepository vehicleParkingRepository() {
    return context.get(VehicleParkingRepository.class);
  }

  public Graph graph() {
    return context.get(Graph.class);
  }

  public VertexLinker vertexLinker() {
    return context.get(VertexLinker.class);
  }

  public DeduplicatorService deduplicatorService() {
    return context.get(DeduplicatorService.class);
  }

  public WorldEnvelopeRepository worldEnvelopeRepository() {
    return context.get(WorldEnvelopeRepository.class);
  }

  public OtpConfig otpConfig() {
    return context.get(ConfigModel.class).otpConfig();
  }

  public RouterConfig routerConfig() {
    return context.get(ConfigModel.class).routerConfig();
  }

  public BuildConfig buildConfig() {
    return context.get(ConfigModel.class).buildConfig();
  }

  public DebugUiConfig debugUiConfig() {
    return context.get(ConfigModel.class).debugUiConfig();
  }

  public RaptorConfig<TripSchedule> raptorConfig() {
    return context.get(RaptorConfig.class);
  }

  private OtpServerRequestContext createServerContext() {
    return context.get(OtpServerRequestContext.class);
  }

  private void enableRequestTraceLogging() {
    if (routerConfig().server().requestTraceLoggingEnabled()) {
      LogMDCSupport.enable();
    }
  }

  private void createMetricsLogging() {
    context.get(MetricsLogging.class);
  }

  public EmissionRepository emissionRepository() {
    return context.get(EmissionRepository.class);
  }

  public StreetDetailsRepository streetDetailsRepository() {
    return context.get(StreetDetailsRepository.class);
  }

  @Nullable
  public EmpiricalDelayRepository empiricalDelayRepository() {
    return context.getNullable(EmpiricalDelayRepository.class);
  }

  public FareServiceFactory fareServiceFactory() {
    return context.get(FareServiceFactory.class);
  }
}
