package org.opentripplanner.graph_builder;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.dataoverlay.configure.DataOverlayParameterBindingsModule;
import org.opentripplanner.ext.edgenaming.configure.EdgeNamerModule;
import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.configure.EmissionGraphBuilderModule;
import org.opentripplanner.ext.emission.internal.graphbuilder.EmissionGraphBuilder;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.configure.EmpiricalDelayGraphBuilderModule;
import org.opentripplanner.ext.empiricaldelay.internal.graphbuilder.EmpiricalDelayGraphBuilder;
import org.opentripplanner.ext.fares.service.NoopFareServiceFactory;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationRepository;
import org.opentripplanner.graph_builder.module.configure.GraphBuilderModules;
import org.opentripplanner.graph_builder.module.geometry.CalculateWorldEnvelopeModule;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.routing.linking.configure.VertexLinkerGraphBuildingModule;
import org.opentripplanner.service.osminfo.OsmInfoGraphBuildRepository;
import org.opentripplanner.service.osminfo.OsmInfoGraphBuildService;
import org.opentripplanner.service.osminfo.configure.OsmInfoGraphBuildServiceModule;
import org.opentripplanner.service.osminfo.internal.DefaultOsmInfoGraphBuildRepository;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository;
import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.opentripplanner.service.worldenvelope.WorldEnvelopeRepository;
import org.opentripplanner.service.worldenvelope.internal.DefaultWorldEnvelopeRepository;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.configure.spring.PhaseContext;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.internal.DefaultStreetRepository;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Verifies that the lazy-init Spring {@link PhaseContext} replacing the former Dagger {@code
 * GraphBuilderFactory} wires up the graph-build phase: the context refreshes (lazily, so no module
 * is instantiated until accessed) and the key graph-build modules resolve. The optional, {@code
 * @Nullable} providers (emission / empirical-delay graph builders) return {@code null} when their
 * repository hand-off is absent, mirroring Dagger's {@code @Nullable} provider semantics.
 */
class GraphBuildContextTest {

  private PhaseContext context;

  private PhaseContext buildContext() {
    // GraphBuildCacheManager iterates the cached data sources at construction, so the mock must
    // return an empty list rather than the default null.
    var dataSources = mock(GraphBuilderDataSources.class);
    when(dataSources.listCachedDataSources()).thenReturn(List.of());

    var ctx = new PhaseContext(true)
      .registerInstance(BuildConfig.class, BuildConfig.DEFAULT)
      .registerInstance(Graph.class, new Graph())
      .registerInstance(OsmInfoGraphBuildRepository.class, new DefaultOsmInfoGraphBuildRepository())
      .registerInstance(StreetDetailsRepository.class, new DefaultStreetDetailsRepository())
      .registerInstance(StreetRepository.class, new DefaultStreetRepository())
      .registerInstance(TimetableRepository.class, new TimetableRepository())
      .registerInstance(
        TransferRepository.class,
        new DefaultTransferRepository(new TransferIndex())
      )
      .registerInstance(WorldEnvelopeRepository.class, new DefaultWorldEnvelopeRepository())
      .registerInstance(VehicleParkingRepository.class, new DefaultVehicleParkingRepository())
      .registerInstance(FareServiceFactory.class, new NoopFareServiceFactory())
      // GraphBuilderDataSources opens the OTP data store; it is mocked here because the lazy-init
      // context never reads it for the beans asserted below.
      .registerInstance(GraphBuilderDataSources.class, dataSources)
      // The optional repositories are intentionally NOT registered, so the @Nullable graph-builder
      // providers must return null.
      .registerNullableInstance(StopConsolidationRepository.class, null)
      .registerNullableInstance(EmissionRepository.class, null)
      .registerNullableInstance(EmpiricalDelayRepository.class, null)
      .registerConfig(
        DataOverlayParameterBindingsModule.class,
        EdgeNamerModule.class,
        EmissionGraphBuilderModule.class,
        EmpiricalDelayGraphBuilderModule.class,
        org.opentripplanner.graph_builder.configure.GraphBuilderModule.class,
        GraphBuilderModules.class,
        OsmInfoGraphBuildServiceModule.class,
        VertexLinkerGraphBuildingModule.class
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
  void contextRefreshesAndResolvesCoreModules() {
    context = buildContext();

    assertThat(context.get(GraphBuilder.class)).isNotNull();
    assertThat(context.get(VertexLinker.class)).isNotNull();
    assertThat(context.get(CalculateWorldEnvelopeModule.class)).isNotNull();
    // The @Binds-style interface binding resolves to the @Import-ed implementation.
    assertThat(context.get(OsmInfoGraphBuildService.class)).isNotNull();
  }

  @Test
  void optionalGraphBuildersAreNullWhenRepositoryAbsent() {
    context = buildContext();

    // @Nullable providers: with no emission/empirical-delay repository registered they yield null,
    // and the lazy bean must therefore not be present in the context.
    assertThat(context.getNullable(EmissionGraphBuilder.class)).isNull();
    assertThat(context.getNullable(EmpiricalDelayGraphBuilder.class)).isNull();
  }
}
