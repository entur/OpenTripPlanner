package org.opentripplanner.service.worldenvelope.configure;

import org.opentripplanner.service.worldenvelope.WorldEnvelopeRepository;
import org.opentripplanner.service.worldenvelope.internal.DefaultWorldEnvelopeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The repository is used during application loading phase, so we need to provide
 * a module for the repository as well as the service.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultWorldEnvelopeRepository.class)
public class WorldEnvelopeRepositoryModule {

  @Bean
  @Primary
  WorldEnvelopeRepository bindRepository(DefaultWorldEnvelopeRepository repository) {
    return repository;
  }
}
