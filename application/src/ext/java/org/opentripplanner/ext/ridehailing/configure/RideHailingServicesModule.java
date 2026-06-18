package org.opentripplanner.ext.ridehailing.configure;

import java.util.List;
import org.opentripplanner.ext.ridehailing.RideHailingService;
import org.opentripplanner.ext.ridehailing.service.uber.UberService;
import org.opentripplanner.standalone.config.RouterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This module converts the ride hailing configurations into ride hailing services to be used by the
 * application context.
 */
@Configuration(proxyBeanMethods = false)
public class RideHailingServicesModule {

  @Bean
  List<RideHailingService> services(RouterConfig config) {
    return config
      .rideHailingServiceParameters()
      .stream()
      .map(p -> (RideHailingService) new UberService(p))
      .toList();
  }
}
