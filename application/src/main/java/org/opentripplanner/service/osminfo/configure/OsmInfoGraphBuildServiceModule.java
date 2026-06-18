package org.opentripplanner.service.osminfo.configure;

import org.opentripplanner.service.osminfo.OsmInfoGraphBuildService;
import org.opentripplanner.service.osminfo.internal.DefaultOsmInfoGraphBuildService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultOsmInfoGraphBuildService.class)
public class OsmInfoGraphBuildServiceModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type), mirroring Dagger's {@code @Binds}
   * that exposes only the interface.
   */
  @Bean
  @Primary
  OsmInfoGraphBuildService bind(DefaultOsmInfoGraphBuildService service) {
    return service;
  }
}
