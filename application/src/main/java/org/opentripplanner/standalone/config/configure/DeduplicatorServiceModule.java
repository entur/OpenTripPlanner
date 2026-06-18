package org.opentripplanner.standalone.config.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This module is dual-bridged during the Dagger&rarr;Spring migration: the load phase still reads
 * its Dagger annotations while the construct/serve phase reads the Spring ones.
 */
@Module
@Configuration(proxyBeanMethods = false)
public class DeduplicatorServiceModule {

  @Provides
  @Bean
  @Singleton
  public static DeduplicatorService provideDeduplicatorService() {
    return new Deduplicator();
  }
}
