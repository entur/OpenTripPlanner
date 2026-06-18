package org.opentripplanner.service.realtimevehicles.configure;

import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The service is used during application serve phase, not loading, so we need to provide
 * a module for the service without the repository, which is injected from the loading phase.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultRealtimeVehicleService.class)
public class RealtimeVehicleServiceModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type). The single {@code @Singleton}
   * implementation is shared with {@link RealtimeVehicleRepositoryModule}.
   */
  @Bean
  @Primary
  RealtimeVehicleService bindService(DefaultRealtimeVehicleService service) {
    return service;
  }
}
