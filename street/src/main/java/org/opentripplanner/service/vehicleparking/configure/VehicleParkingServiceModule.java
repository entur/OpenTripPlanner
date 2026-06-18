package org.opentripplanner.service.vehicleparking.configure;

import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultVehicleParkingService.class)
public class VehicleParkingServiceModule {

  /**
   * {@code @Primary} disambiguates between this interface binding and the {@code @Import}-ed
   * implementation bean (which also matches the interface type), mirroring Dagger's {@code @Binds}
   * that exposes only the interface.
   */
  @Bean
  @Primary
  VehicleParkingService bind(DefaultVehicleParkingService service) {
    return service;
  }
}
