package org.opentripplanner.ext.empiricaldelay.configure;

import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.internal.graphbuilder.EmpiricalDelayGraphBuilder;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmpiricalDelayGraphBuilderModule {

  @Bean
  @Nullable
  EmpiricalDelayGraphBuilder provideEmpiricalDelayGraphBuilder(
    GraphBuilderDataSources dataSources,
    BuildConfig config,
    @Nullable EmpiricalDelayRepository empiricalDelayRepository,
    TimetableRepository timetableRepository,
    DataImportIssueStore issueStore,
    DeduplicatorService deduplicator
  ) {
    if (OTPFeature.EmpiricalDelay.isOff() || empiricalDelayRepository == null) {
      return null;
    }

    return new EmpiricalDelayGraphBuilder(
      dataSources.getEmpiricalDelayConfiguredDataSource(),
      deduplicator,
      issueStore,
      config.empiricalDelay,
      empiricalDelayRepository,
      timetableRepository
    );
  }
}
