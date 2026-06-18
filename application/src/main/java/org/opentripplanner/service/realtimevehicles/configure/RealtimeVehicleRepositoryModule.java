package org.opentripplanner.service.realtimevehicles.configure;

import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The repository is used during application loading phase, so we need to provide
 * a module for the repository as well as the service.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultRealtimeVehicleService.class)
public class RealtimeVehicleRepositoryModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type). The single {@code @Singleton}
   * implementation is shared with {@link RealtimeVehicleServiceModule}.
   */
  @Bean
  @Primary
  RealtimeVehicleRepository bindRepository(DefaultRealtimeVehicleService repository) {
    return repository;
  }
}
