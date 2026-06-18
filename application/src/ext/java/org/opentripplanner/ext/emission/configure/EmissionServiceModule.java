package org.opentripplanner.ext.emission.configure;

import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.internal.DefaultEmissionService;
import org.opentripplanner.ext.emission.internal.itinerary.EmissionItineraryDecorator;
import org.opentripplanner.routing.algorithm.filterchain.ext.EmissionDecorator;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.ItineraryDecorator;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * The service is used during application serve phase, not loading, so we need to provide
 * a module for the service without the repository, which is injected from the loading phase.
 */
@Configuration(proxyBeanMethods = false)
public class EmissionServiceModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @EmissionDecorator
  public ItineraryDecorator provideEmissionService(EmissionRepository emissionRepository) {
    return new EmissionItineraryDecorator(new DefaultEmissionService(emissionRepository));
  }
}
