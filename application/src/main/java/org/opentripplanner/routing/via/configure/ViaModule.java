package org.opentripplanner.routing.via.configure;

import org.opentripplanner.routing.via.ViaCoordinateTransferFactory;
import org.opentripplanner.routing.via.service.DefaultViaCoordinateTransferFactory;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TransitService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class ViaModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  ViaCoordinateTransferFactory providesViaTransferResolver(
    BuildConfig buildConfig,
    TransitService transitService,
    Graph graph
  ) {
    return new DefaultViaCoordinateTransferFactory(
      graph,
      transitService,
      buildConfig.regularTransferParameters().maxDuration()
    );
  }
}
