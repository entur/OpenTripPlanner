package org.opentripplanner.ext.stopconsolidation.configure;

import org.opentripplanner.ext.stopconsolidation.StopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The repository is used during application loading phase, so we need to provide
 * a module for the repository.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultStopConsolidationRepository.class)
public class StopConsolidationRepositoryModule {

  @Bean
  @Primary
  StopConsolidationRepository bindRepository(DefaultStopConsolidationRepository repo) {
    return repo;
  }
}
