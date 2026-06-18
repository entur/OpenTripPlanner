package org.opentripplanner.ext.fares.configure;

import org.opentripplanner.ext.fares.FaresConfiguration;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.standalone.config.BuildConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FareModule {

  @Bean
  public FareServiceFactory factory(BuildConfig config) {
    return FaresConfiguration.fromConfig(config.fareConfig);
  }
}
