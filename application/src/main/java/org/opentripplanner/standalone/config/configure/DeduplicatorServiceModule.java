package org.opentripplanner.standalone.config.configure;

import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DeduplicatorServiceModule {

  @Bean
  public static DeduplicatorService provideDeduplicatorService() {
    return new Deduplicator();
  }
}
