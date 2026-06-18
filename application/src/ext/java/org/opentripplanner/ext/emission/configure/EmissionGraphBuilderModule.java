package org.opentripplanner.ext.emission.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.internal.graphbuilder.EmissionGraphBuilder;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmissionGraphBuilderModule {

  @Bean
  @Nullable
  EmissionGraphBuilder provideEmissionGraphBuilder(
    GraphBuilderDataSources dataSources,
    BuildConfig config,
    @Nullable EmissionRepository emissionRepository,
    TimetableRepository timetableRepository,
    DataImportIssueStore issueStore
  ) {
    if (emissionRepository == null) {
      return null;
    }

    return new EmissionGraphBuilder(
      dataSources.getGtfsConfiguredDataSource(),
      dataSources.getEmissionConfiguredDataSource(),
      config.emission,
      emissionRepository,
      timetableRepository,
      issueStore
    );
  }
}
