package org.opentripplanner.service.worldenvelope.configure;

import org.opentripplanner.service.worldenvelope.WorldEnvelopeService;
import org.opentripplanner.service.worldenvelope.internal.DefaultWorldEnvelopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The service is used during application serve phase, not loading, so we need to provide
 * a module for the service without the repository, which is injected from the loading phase.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultWorldEnvelopeService.class)
public class WorldEnvelopeServiceModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type), mirroring Dagger's {@code @Binds}
   * that exposes only the interface.
   */
  @Bean
  @Primary
  WorldEnvelopeService bindService(DefaultWorldEnvelopeService service) {
    return service;
  }
}
