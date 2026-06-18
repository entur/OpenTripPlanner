package org.opentripplanner.standalone.configure.spring;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.gtfs.configure.SchemaModule;
import org.opentripplanner.apis.transmodel.configure.TransmodelSchemaModule;
import org.opentripplanner.ext.carpooling.configure.CarpoolingModule;
import org.opentripplanner.ext.dataoverlay.configure.DataOverlayParameterBindingsModule;
import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.configure.EmissionServiceModule;
import org.opentripplanner.ext.emission.internal.DefaultEmissionRepository;
import org.opentripplanner.ext.empiricaldelay.configure.EmpiricalDelayServiceModule;
import org.opentripplanner.ext.fares.service.NoopFareServiceFactory;
import org.opentripplanner.ext.geocoder.configure.GeocoderModule;
import org.opentripplanner.ext.interactivelauncher.configuration.InteractiveLauncherModule;
import org.opentripplanner.ext.ridehailing.configure.RideHailingServicesModule;
import org.opentripplanner.ext.sorlandsbanen.configure.SorlandsbanenNorwayModule;
import org.opentripplanner.ext.stopconsolidation.configure.StopConsolidationServiceModule;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueSummary;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.routing.linking.configure.LinkingServiceModule;
import org.opentripplanner.routing.via.configure.ViaModule;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.service.realtimevehicles.configure.RealtimeVehicleRepositoryModule;
import org.opentripplanner.service.realtimevehicles.configure.RealtimeVehicleServiceModule;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.configure.StreetDetailsServiceModule;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository;
import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehicleparking.configure.VehicleParkingServiceModule;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.opentripplanner.service.vehiclerental.VehicleRentalRepository;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.service.vehiclerental.configure.VehicleRentalRepositoryModule;
import org.opentripplanner.service.vehiclerental.configure.VehicleRentalServiceModule;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeRepository;
import org.opentripplanner.service.worldenvelope.configure.WorldEnvelopeServiceModule;
import org.opentripplanner.service.worldenvelope.internal.DefaultWorldEnvelopeRepository;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.opentripplanner.standalone.config.configure.ConfigModule;
import org.opentripplanner.standalone.config.configure.DeduplicatorServiceModule;
import org.opentripplanner.standalone.configure.ConstructApplicationModule;
import org.opentripplanner.standalone.server.MetricsLogging;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.internal.DefaultStreetRepository;
import org.opentripplanner.street.service.StreetLimitationParametersServiceModule;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.configure.TransferServiceModule;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.opentripplanner.transit.configure.TransitModule;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.warmup.WarmupLauncher;
import org.opentripplanner.warmup.configure.WarmupModule;

/**
 * Verifies that the Spring {@link PhaseContext} replacing the former Dagger {@code
 * ConstructApplicationFactory} wires up like the Dagger graph: every bean resolves, the unscoped
 * {@code @Provides} bindings are translated to prototype scope, the dual-interface singletons share
 * a single instance, and the {@code javax.inject} qualifiers on the GraphQL schemas still resolve
 * (otherwise {@link OtpServerRequestContext}, which injects them, would fail to build).
 */
class ConstructApplicationContextTest {

  private PhaseContext context;

  private PhaseContext buildContext() {
    var config = new ConfigModel(OtpConfigLoader.fromString("{}"));
    var routerConfig = config.routerConfig();

    var timetableRepository = new TimetableRepository();
    timetableRepository.index();

    var transferRepository = new DefaultTransferRepository(new TransferIndex());
    // Mirror ConstructApplication.setupTransitRoutingServer(): the Raptor transit data must exist
    // before the eagerly-instantiated TimetableSnapshotManager commits its first snapshot.
    org.opentripplanner.standalone.configure.ConstructApplication.createRaptorTransitData(
      timetableRepository,
      transferRepository,
      routerConfig.transitTuningConfig()
    );

    var ctx = new PhaseContext()
      .registerInstance(ConfigModel.class, config)
      .registerInstance(Graph.class, new Graph())
      .registerInstance(TimetableRepository.class, timetableRepository)
      .registerInstance(TransferRepository.class, transferRepository)
      .registerInstance(WorldEnvelopeRepository.class, new DefaultWorldEnvelopeRepository())
      .registerInstance(VehicleParkingRepository.class, new DefaultVehicleParkingRepository())
      .registerInstance(DataImportIssueSummary.class, DataImportIssueSummary.empty())
      .registerInstance(StreetDetailsRepository.class, new DefaultStreetDetailsRepository())
      .registerInstance(EmissionRepository.class, new DefaultEmissionRepository())
      .registerInstance(StreetRepository.class, new DefaultStreetRepository())
      .registerInstance(FareServiceFactory.class, new NoopFareServiceFactory())
      .registerInstance(RouteRequest.class, routerConfig.routingRequestDefaults())
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
      );
    ctx.refresh();
    return ctx;
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
      context = null;
    }
  }

  @Test
  void contextRefreshesAndResolvesCoreBeans() {
    context = buildContext();

    assertThat(context.get(TransitService.class)).isNotNull();
    assertThat(context.get(TimetableSnapshotManager.class)).isNotNull();
    assertThat(context.get(MetricsLogging.class)).isNotNull();
    assertThat(context.get(WarmupLauncher.class)).isNotNull();
    // Building the server context exercises the @GtfsSchema / @TransmodelSchema qualified
    // injection points; if the javax.inject qualifiers did not resolve, this would throw.
    assertThat(context.get(OtpServerRequestContext.class)).isNotNull();
  }

  @Test
  void serverContextIsPrototype() {
    context = buildContext();
    var a = context.get(OtpServerRequestContext.class);
    var b = context.get(OtpServerRequestContext.class);
    assertThat(a).isNotSameInstanceAs(b);
  }

  @Test
  void timetableSnapshotBeanIsReResolvedEachCall() {
    context = buildContext();
    var manager = context.get(TimetableSnapshotManager.class);
    // The prototype @Bean must be re-evaluated against the live manager on every lookup.
    var snapshot1 = context.getNullable(TimetableSnapshot.class);
    var snapshot2 = context.getNullable(TimetableSnapshot.class);
    assertThat(snapshot1).isEqualTo(manager.getTimetableSnapshot());
    assertThat(snapshot1).isEqualTo(snapshot2);
  }

  @Test
  void vehicleRentalServiceAndRepositoryShareOneSingleton() {
    context = buildContext();
    assertThat(context.get(VehicleRentalService.class)).isSameInstanceAs(
      context.get(VehicleRentalRepository.class)
    );
  }

  @Test
  void realtimeVehicleServiceAndRepositoryShareOneSingleton() {
    context = buildContext();
    assertThat(context.get(RealtimeVehicleService.class)).isSameInstanceAs(
      context.get(RealtimeVehicleRepository.class)
    );
  }
}
