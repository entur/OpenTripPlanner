package org.opentripplanner.service.osminfo.configure;

import org.opentripplanner.service.osminfo.OsmInfoGraphBuildRepository;
import org.opentripplanner.service.osminfo.internal.DefaultOsmInfoGraphBuildRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultOsmInfoGraphBuildRepository.class)
public class OsmInfoGraphBuildRepositoryModule {

  @Bean
  @Primary
  OsmInfoGraphBuildRepository bind(DefaultOsmInfoGraphBuildRepository repository) {
    return repository;
  }
}
