package org.opentripplanner.service.streetdetails.configure;

import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultStreetDetailsService.class)
public class StreetDetailsServiceModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type), mirroring Dagger's {@code @Binds}
   * that exposes only the interface.
   */
  @Bean
  @Primary
  StreetDetailsService bind(DefaultStreetDetailsService service) {
    return service;
  }
}
