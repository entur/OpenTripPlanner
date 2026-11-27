package org.opentripplanner.perfrig;

import io.micrometer.core.instrument.Metrics;
import java.io.File;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.apis.transmodel.TransmodelGraphQLRequestContext;
import org.opentripplanner.ext.realtimeresolver.RealtimeResolver;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.raptor.RigRaptorServiceFactory;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.graph.SerializedGraphObject;
import org.opentripplanner.routing.service.DefaultRoutingService;
import org.opentripplanner.routing.util.EllipsoidUtils;
import org.opentripplanner.standalone.config.CommandLineParameters;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.standalone.config.routerconfig.RaptorEnvironmentFactory;
import org.opentripplanner.standalone.configure.ConstructApplication;
import org.opentripplanner.standalone.configure.ConstructApplicationFactory;
import org.opentripplanner.standalone.configure.LoadApplication;
import org.opentripplanner.standalone.configure.RequestScopedFactory;
import graphql.schema.GraphQLSchema;

/**
 * Loads a serialized graph the way {@code OTPMain --load} does, without the web server, the
 * updaters and the warmup, and hands out request scopes wired like {@code RequestScopedModule},
 * except that Raptor goes through a {@link RigRaptorServiceFactory}.
 */
public final class RigServer {

  private final ConstructApplicationFactory factory;
  private final RouterConfig routerConfig;
  private final RigRaptorServiceFactory<TripSchedule> raptorServiceFactory;
  private final GraphQLSchema transmodelSchema;
  private final long loadMillis;

  private RigServer(ConstructApplicationFactory factory, long loadMillis) {
    this.factory = factory;
    this.routerConfig = factory.config().routerConfig();
    var tuning = routerConfig.transitTuningConfig();
    this.raptorServiceFactory = new RigRaptorServiceFactory<>(
      tuning,
      RaptorEnvironmentFactory.create(tuning.searchThreadPoolSize())
    );
    this.transmodelSchema = factory.transmodelSchema();
    if (transmodelSchema == null) {
      throw new IllegalStateException("The Transmodel API is not enabled");
    }
    this.loadMillis = loadMillis;
  }

  /**
   * @param datasetDir the directory with the config files
   * @param graphFile the graph to load, the graph of the dataset directory if {@code null}
   */
  public static RigServer load(File datasetDir, @Nullable File graphFile) {
    long start = System.currentTimeMillis();
    var cli = new CommandLineParameters();
    cli.baseDirectory = List.of(datasetDir);
    // With an explicit graph file, the data sources must not look for a graph in the directory
    cli.load = graphFile == null;

    var loadApp = new LoadApplication(cli);
    var config = loadApp.config();
    if (OTPFeature.ParallelRouting.isOn()) {
      // The Raptor listener is a thread local, and parallel routing runs Raptor in another thread
      throw new IllegalStateException("The rig does not support the ParallelRouting feature");
    }
    loadApp.validateConfigAndDataSources();

    var graph = graphFile == null
      ? SerializedGraphObject.load(loadApp.getInputGraphDataStore())
      : SerializedGraphObject.load(graphFile);
    ConstructApplication app = loadApp.appConstruction(graph);
    config.updateConfigFromSerializedGraph(
      graph.buildConfig,
      graph.routerConfig,
      cli.doUseLoadedGraphAsIs()
    );

    // As OTPMain.startOtpWebServer
    app.transitRepository().freeze();
    app.transferRepository().index();
    app.graph().index();

    // As ConstructApplication.setupTransitRoutingServer, without updaters, warmup and metrics
    var factory = app.getFactory();
    factory.regularTransferRepositoryHandle();
    initEllipsoidToGeoidDifference(factory);

    return new RigServer(factory, System.currentTimeMillis() - start);
  }

  public long loadMillis() {
    return loadMillis;
  }

  public ConstructApplicationFactory factory() {
    return factory;
  }

  public RouterConfig routerConfig() {
    return routerConfig;
  }

  public GraphQLSchema transmodelSchema() {
    return transmodelSchema;
  }

  public RigRaptorServiceFactory<TripSchedule> raptorServiceFactory() {
    return raptorServiceFactory;
  }

  /** A new request scope, as the server creates for each HTTP request. */
  RequestScope newRequestScope() {
    RequestScopedFactory rsf = factory.requestScopedFactoryBuilder().build();
    var transitService = rsf.transitService();
    var transitAlertService = rsf.transitAlertService();
    var transitRoutingConfig = routerConfig.transitTuningConfig();

    RealtimeResolver realtimeResolver = OTPFeature.RealtimeResolver.isOn()
      ? new RealtimeResolver(rsf.refetchItineraryService(), transitService, transitAlertService)
      : null;

    // As RequestScopedModule.routingService
    var routingService = new DefaultRoutingService(
      transitService,
      rsf.graph(),
      raptorServiceFactory,
      Metrics.globalRegistry,
      rsf.streetLimitationParametersService(),
      rsf.vehicleRentalService(),
      rsf.streetDetailsService(),
      rsf.transferService(),
      rsf.regularTransfers(),
      transitAlertService,
      routerConfig.flexParameters(),
      factory.rideHailingServices(),
      factory.dataOverlayParameterBindings(),
      factory.enturSorlandsbanenService(),
      factory.viaTransferResolver(),
      factory.carpoolingService(),
      factory.taxiService(),
      factory.emissionItineraryDecorator(),
      factory.stopConsolidationService(),
      rsf.linkingContextFactory(),
      transitRoutingConfig,
      transitRoutingConfig,
      realtimeResolver
    );
    var recorder = new RecordingRoutingService(routingService);
    return new RequestScope(
      rsf,
      routingService,
      recorder,
      new RigTransmodelContext(rsf.transmodelRequestContext(), recorder)
    );
  }

  /** As ConstructApplication.initEllipsoidToGeoidDifference */
  private static void initEllipsoidToGeoidDifference(ConstructApplicationFactory factory) {
    try {
      var c = factory.worldEnvelopeService().envelope().orElseThrow().center();
      double value = EllipsoidUtils.computeEllipsoidToGeoidDifference(c.latitude(), c.longitude());
      factory.graph().initEllipsoidToGeoidDifference(value, c.latitude(), c.longitude());
    } catch (Exception e) {
      throw new IllegalStateException("Error computing ellipsoid/geoid difference", e);
    }
  }

  record RequestScope(
    RequestScopedFactory factory,
    DefaultRoutingService routingService,
    RecordingRoutingService recorder,
    TransmodelGraphQLRequestContext transmodelContext
  ) {}
}
