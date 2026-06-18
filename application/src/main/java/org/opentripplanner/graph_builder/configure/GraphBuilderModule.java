package org.opentripplanner.graph_builder.configure;

import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.flex.AreaStopsToVerticesMapper;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.GraphCoherencyCheckerModule;
import org.opentripplanner.graph_builder.module.OsmBoardingLocationsModule;
import org.opentripplanner.graph_builder.module.TimeZoneAdjusterModule;
import org.opentripplanner.graph_builder.module.TripPatternNamer;
import org.opentripplanner.graph_builder.module.cache.GraphBuildCacheManager;
import org.opentripplanner.graph_builder.module.geometry.CalculateWorldEnvelopeModule;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the {@link GraphBuilder} orchestrator together with the graph-build modules that
 * have a plain {@code @Inject} constructor. Dagger created those implicitly as just-in-time
 * bindings; Spring needs them registered explicitly, which is what the {@link Import} does.
 */
@Configuration(proxyBeanMethods = false)
@Import(
  {
    AreaStopsToVerticesMapper.class,
    CalculateWorldEnvelopeModule.class,
    GraphCoherencyCheckerModule.class,
    OsmBoardingLocationsModule.class,
    TimeZoneAdjusterModule.class,
    TripPatternNamer.class,
  }
)
public class GraphBuilderModule {

  @Bean
  GraphBuilder provideGraphBuilder(
    Graph baseGraph,
    DeduplicatorService deduplicator,
    TimetableRepository timetableRepository,
    DataImportIssueStore issueStore,
    GraphBuilderDataSources closeDataSourcesHandle,
    GraphBuildCacheManager cacheManager
  ) {
    return new GraphBuilder(
      baseGraph,
      deduplicator,
      timetableRepository,
      issueStore,
      closeDataSourcesHandle,
      cacheManager
    );
  }
}
