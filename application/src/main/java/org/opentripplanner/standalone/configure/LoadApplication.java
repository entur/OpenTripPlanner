package org.opentripplanner.standalone.configure;

import javax.annotation.Nullable;
import org.opentripplanner.datastore.OtpDataStore;
import org.opentripplanner.datastore.api.DataSource;
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
import org.opentripplanner.graph_builder.issue.api.DataImportIssueSummary;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.routing.graph.SerializedGraphObject;
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
import org.opentripplanner.standalone.configure.spring.PhaseContext;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.configure.StreetRepositoryModule;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.configure.TransferRepositoryModule;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * This class is responsible for loading configuration and setting up the OTP data store.
 * This is used to load the graph, and finally this class can create the
 * {@link ConstructApplication} for the next phase.
 * <p>
 * By splitting these two responsibilities into two separate phases we are sure all
 * components (graph and transit model) created in the load phase will be available for
 * creating the application.
 * <p>
 * The load context is a lazy-init Spring {@link PhaseContext}: beans materialize on first access,
 * not at refresh. This preserves the deferred IO of the former Dagger component &mdash; the data
 * store opens and config is read on the first explicit access (via
 * {@link #validateConfigAndDataSources()} / {@link #config()}), not in this constructor.
 */
public class LoadApplication {

  private final CommandLineParameters cli;
  private final PhaseContext context;

  private boolean dataStoreLoaded = false;

  /**
   * Create a new OTP configuration instance for a given directory.
   */
  public LoadApplication(CommandLineParameters commandLineParameters) {
    this.cli = commandLineParameters;
    this.context = new PhaseContext(true)
      .registerInstance(CommandLineParameters.class, commandLineParameters)
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
      )
      .refresh();
  }

  public void validateConfigAndDataSources() {
    // Load Graph Builder Data Sources to validate it.
    context.get(GraphBuilderDataSources.class);
    this.dataStoreLoaded = true;
  }

  public DataSource getInputGraphDataStore() {
    return cli.doLoadGraph() ? datastore().getGraph() : datastore().getStreetGraph();
  }

  /** Construct application from serialized graph */
  public ConstructApplication appConstruction(SerializedGraphObject obj) {
    return createAppConstruction(
      obj.graph,
      obj.osmInfoGraphBuildRepository,
      obj.streetDetailsRepository,
      obj.timetableRepository,
      obj.transferRepository,
      obj.worldEnvelopeRepository,
      obj.parkingRepository,
      obj.issueSummary,
      obj.emissionRepository,
      obj.empiricalDelayRepository,
      obj.stopConsolidationRepository,
      obj.streetRepository,
      obj.fareServiceFactory
    );
  }

  /** Construct application with an empty model. */
  public ConstructApplication appConstruction() {
    return createAppConstruction(
      context.get(Graph.class),
      context.get(OsmInfoGraphBuildRepository.class),
      context.get(StreetDetailsRepository.class),
      context.get(TimetableRepository.class),
      context.get(TransferRepository.class),
      context.get(WorldEnvelopeRepository.class),
      context.get(VehicleParkingRepository.class),
      DataImportIssueSummary.empty(),
      context.get(EmissionRepository.class),
      context.getNullable(EmpiricalDelayRepository.class),
      context.get(StopConsolidationRepository.class),
      context.get(StreetRepository.class),
      context.get(FareServiceFactory.class)
    );
  }

  public GraphBuilderDataSources graphBuilderDataSources() {
    if (!dataStoreLoaded) {
      throw new IllegalStateException("Validate graphBuilderDataSources before using it");
    }
    return context.get(GraphBuilderDataSources.class);
  }

  public ConfigModel config() {
    return context.get(ConfigModel.class);
  }

  private OtpDataStore datastore() {
    return context.get(OtpDataStore.class);
  }

  private ConstructApplication createAppConstruction(
    Graph graph,
    OsmInfoGraphBuildRepository osmInfoGraphBuildRepository,
    StreetDetailsRepository streetDetailsRepository,
    TimetableRepository timetableRepository,
    TransferRepository transferRepository,
    WorldEnvelopeRepository worldEnvelopeRepository,
    VehicleParkingRepository parkingRepository,
    DataImportIssueSummary issueSummary,
    @Nullable EmissionRepository emissionRepository,
    @Nullable EmpiricalDelayRepository empiricalDelayRepository,
    @Nullable StopConsolidationRepository stopConsolidationRepository,
    StreetRepository streetRepository,
    FareServiceFactory fareServiceFactory
  ) {
    return new ConstructApplication(
      cli,
      graph,
      osmInfoGraphBuildRepository,
      streetDetailsRepository,
      timetableRepository,
      transferRepository,
      worldEnvelopeRepository,
      config(),
      graphBuilderDataSources(),
      issueSummary,
      emissionRepository,
      empiricalDelayRepository,
      parkingRepository,
      stopConsolidationRepository,
      streetRepository,
      fareServiceFactory
    );
  }
}
