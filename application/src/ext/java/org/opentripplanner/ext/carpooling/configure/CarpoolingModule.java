package org.opentripplanner.ext.carpooling.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.carpooling.CarpoolingRepository;
import org.opentripplanner.ext.carpooling.CarpoolingService;
import org.opentripplanner.ext.carpooling.internal.DefaultCarpoolingRepository;
import org.opentripplanner.ext.carpooling.service.DefaultCarpoolingService;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.service.TransitService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class CarpoolingModule {

  @Bean
  @Nullable
  public CarpoolingRepository provideCarpoolingRepository() {
    if (OTPFeature.CarPooling.isOff()) {
      return null;
    }
    return new DefaultCarpoolingRepository();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Nullable
  public CarpoolingService provideCarpoolingService(
    @Nullable CarpoolingRepository repository,
    StreetLimitationParametersService streetLimitationParametersService,
    TransitService transitService,
    VertexCreationService vertexCreationService
  ) {
    if (OTPFeature.CarPooling.isOff()) {
      return null;
    }
    return new DefaultCarpoolingService(
      repository,
      streetLimitationParametersService,
      transitService,
      vertexCreationService
    );
  }
}
