package org.opentripplanner.service.vehicleparking.configure;

import org.opentripplanner.service.vehicleparking.VehicleParkingRepository;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@Import(DefaultVehicleParkingRepository.class)
public class VehicleParkingRepositoryModule {

  @Bean
  @Primary
  VehicleParkingRepository bind(DefaultVehicleParkingRepository repo) {
    return repo;
  }
}
