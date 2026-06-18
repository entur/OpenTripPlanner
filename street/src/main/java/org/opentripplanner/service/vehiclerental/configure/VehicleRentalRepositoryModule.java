package org.opentripplanner.service.vehiclerental.configure;

import org.opentripplanner.service.vehiclerental.VehicleRentalRepository;
import org.opentripplanner.service.vehiclerental.internal.DefaultVehicleRentalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The service is used during application serve phase, not loading, so we need to provide
 * a module for the service without the repository, which is injected from the loading phase.
 */
@Configuration(proxyBeanMethods = false)
@Import(DefaultVehicleRentalService.class)
public class VehicleRentalRepositoryModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type). The single {@code @Singleton}
   * implementation is shared with {@link VehicleRentalServiceModule}.
   */
  @Bean
  @Primary
  VehicleRentalRepository bindService(DefaultVehicleRentalService service) {
    return service;
  }
}
