package org.opentripplanner.service.streetdetails.configure;

import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultStreetDetailsRepository.class)
public class StreetDetailsRepositoryModule {

  @Bean
  @Primary
  StreetDetailsRepository bind(DefaultStreetDetailsRepository repository) {
    return repository;
  }
}
