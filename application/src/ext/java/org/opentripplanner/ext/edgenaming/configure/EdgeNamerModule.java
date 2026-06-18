package org.opentripplanner.ext.edgenaming.configure;

import org.opentripplanner.ext.edgenaming.EdgeNamerFactory;
import org.opentripplanner.graph_builder.services.osm.EdgeNamer;
import org.opentripplanner.standalone.config.BuildConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EdgeNamerModule {

  @Bean
  public EdgeNamer provideNamer(BuildConfig config) {
    return EdgeNamerFactory.fromConfig(config.edgeNamer);
  }
}
