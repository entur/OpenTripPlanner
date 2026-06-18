package org.opentripplanner.standalone.configure.spring;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.datastore.OtpDataStore;
import org.opentripplanner.datastore.configure.DataStoreModule;
import org.opentripplanner.ext.datastore.gs.GsDataSourceModule;
import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.configure.EmissionRepositoryModule;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.configure.EmpiricalDelayRepositoryModule;
import org.opentripplanner.ext.fares.configure.FareModule;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.configure.StopConsolidationRepositoryModule;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.service.osminfo.OsmInfoGraphBuildRepository;
import org.opentripplanner.service.osminfo.configure.OsmInfoGraphBuildRepositoryModule;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.configure.StreetDetailsRepositoryModule;
import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehicleparking.configure.VehicleParkingRepositoryModule;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeRepository;
import org.opentripplanner.service.worldenvelope.configure.WorldEnvelopeRepositoryModule;
import org.opentripplanner.standalone.config.CommandLineParameters;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.configure.DeduplicatorServiceModule;
import org.opentripplanner.standalone.config.configure.LoadConfigModule;
import org.opentripplanner.standalone.configure.LoadModelConfig;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.configure.StreetRepositoryModule;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.configure.TransferRepositoryModule;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Verifies that the lazy-init Spring {@link PhaseContext} replacing the former Dagger {@code
 * LoadApplicationFactory} wires the load phase: every load-phase bean resolves, the empty
 * model/repositories and {@link GraphBuilderDataSources} (former Dagger just-in-time {@code @Inject}
 * bindings) build, and the legacy {@code javax.inject}-qualified {@code @OtpBaseDirectory} injection
 * point resolves (otherwise {@link OtpDataStore} / {@link GraphBuilderDataSources} would fail to
 * build).
 */
class LoadApplicationContextTest {

  private File baseDir;
  private PhaseContext context;

  @BeforeEach
  void setUp() throws IOException {
    baseDir = Files.createTempDirectory("LoadApplicationContextTest-").toFile();
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
      context = null;
    }
    baseDir.delete();
  }

  private PhaseContext buildContext() {
    var cli = new CommandLineParameters();
    cli.baseDirectory = List.of(baseDir);

    var ctx = new PhaseContext(true)
      .registerInstance(CommandLineParameters.class, cli)
      .registerConfig(
        LoadConfigModule.class,
        DataStoreModule.class,
        DeduplicatorServiceModule.class,
        GsDataSourceModule.class,
        OsmInfoGraphBuildRepositoryModule.class,
        StreetDetailsRepositoryModule.class,
        WorldEnvelopeRepositoryModule.class,
        EmissionRepositoryModule.class,
        EmpiricalDelayRepositoryModule.class,
        StopConsolidationRepositoryModule.class,
        StreetRepositoryModule.class,
        TransferRepositoryModule.class,
        VehicleParkingRepositoryModule.class,
        FareModule.class,
        LoadModelConfig.class
      );
    ctx.refresh();
    return ctx;
  }

  @Test
  void contextRefreshesAndResolvesLoadPhaseBeans() {
    context = buildContext();

    assertThat(context.get(ConfigModel.class)).isNotNull();
    // Opening the data store exercises the @OtpBaseDirectory / @GoogleStorageDSRepository qualified
    // injection points; if the javax.inject qualifiers did not resolve, this would throw.
    assertThat(context.get(OtpDataStore.class)).isNotNull();
    assertThat(context.get(GraphBuilderDataSources.class)).isNotNull();

    assertThat(context.get(Graph.class)).isNotNull();
    assertThat(context.get(TimetableRepository.class)).isNotNull();
    assertThat(context.get(OsmInfoGraphBuildRepository.class)).isNotNull();
    assertThat(context.get(StreetDetailsRepository.class)).isNotNull();
    assertThat(context.get(WorldEnvelopeRepository.class)).isNotNull();
    assertThat(context.get(TransferRepository.class)).isNotNull();
    assertThat(context.get(VehicleParkingRepository.class)).isNotNull();
    assertThat(context.get(StreetRepository.class)).isNotNull();
    assertThat(context.get(StopConsolidationRepository.class)).isNotNull();
    assertThat(context.get(EmissionRepository.class)).isNotNull();
    assertThat(context.getNullable(EmpiricalDelayRepository.class)).isNotNull();
    assertThat(context.get(FareServiceFactory.class)).isNotNull();
  }

  @Test
  void emptyModelBeansAreSingletons() {
    context = buildContext();
    assertThat(context.get(Graph.class)).isSameInstanceAs(context.get(Graph.class));
    assertThat(context.get(TimetableRepository.class)).isSameInstanceAs(
      context.get(TimetableRepository.class)
    );
  }
}
