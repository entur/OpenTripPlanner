package org.opentripplanner.standalone.configure;

import java.io.File;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.framework.di.TransitServicePeriod;
import org.opentripplanner.core.model.time.LocalDateInterval;
import org.opentripplanner.datastore.OtpDataStore;
import org.opentripplanner.datastore.api.OtpBaseDirectory;
import org.opentripplanner.graph_builder.GraphBuilderDataSources;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.config.CommandLineParameters;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.openinghours.OpeningHoursCalendarService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the load-phase beans that Dagger created via just-in-time {@code @Inject} bindings (no
 * {@code @Module} declared them). Spring does not auto-create such beans, so they are registered
 * here as explicit {@code @Bean} factory methods, using the empty/no-arg constructors that exist
 * precisely for the empty load phase.
 */
@Configuration(proxyBeanMethods = false)
public class LoadModelConfig {

  /**
   * The opening-hours calendar service is part of the empty {@link Graph} built during graph build
   * (e.g. {@code OsmModule} uses it). Dagger supplied it via {@code Graph}'s {@code @Inject} ctor;
   * Spring must do the same — using {@code new Graph()} would leave it {@code null} and NPE the
   * build.
   */
  @Bean
  OpeningHoursCalendarService openingHoursCalendarService(
    DeduplicatorService deduplicator,
    @TransitServicePeriod LocalDateInterval transitServicePeriod
  ) {
    return new OpeningHoursCalendarService(deduplicator, transitServicePeriod);
  }

  @Bean
  Graph emptyGraph(OpeningHoursCalendarService openingHoursCalendarService) {
    return new Graph(openingHoursCalendarService);
  }

  @Bean
  TimetableRepository emptyTimetableRepository() {
    return new TimetableRepository();
  }

  @Bean
  GraphBuilderDataSources graphBuilderDataSources(
    CommandLineParameters cli,
    BuildConfig bc,
    OtpDataStore store,
    @OtpBaseDirectory File baseDir
  ) {
    return new GraphBuilderDataSources(cli, bc, store, baseDir);
  }
}
