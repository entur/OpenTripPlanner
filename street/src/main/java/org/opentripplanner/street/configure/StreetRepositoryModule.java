package org.opentripplanner.street.configure;

import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.internal.DefaultStreetRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultStreetRepository.class)
public class StreetRepositoryModule {

  @Bean
  @Primary
  StreetRepository bindStreetRepository(DefaultStreetRepository repository) {
    return repository;
  }
}
