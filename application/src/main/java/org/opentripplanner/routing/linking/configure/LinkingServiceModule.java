package org.opentripplanner.routing.linking.configure;

import static org.opentripplanner.street.linking.VisibilityMode.COMPUTE_AREA_VISIBILITY_LINES;

import java.util.Optional;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.service.TransitService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class LinkingServiceModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  VertexLinker provideVertexLinker(
    Graph graph,
    VehicleRentalService vehicleRentalService,
    StreetLimitationParametersService streetLimitationParametersService
  ) {
    return new VertexLinker(
      graph,
      vehicleRentalService,
      COMPUTE_AREA_VISIBILITY_LINES,
      streetLimitationParametersService.maxAreaNodes(),
      OTPFeature.FlexRouting.isOn()
    );
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  VertexCreationService provideVertexCreationService(VertexLinker vertexLinker) {
    return new VertexCreationService(vertexLinker);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  LinkingContextFactory provideLinkingContextFactory(
    Graph graph,
    TransitService transitService,
    VertexCreationService vertexCreationService
  ) {
    return new LinkingContextFactory(
      graph,
      vertexCreationService,
      transitService::findStopOrChildIds,
      id -> {
        var group = transitService.getStopLocationsGroup(id);
        return Optional.ofNullable(group).map(locationsGroup -> locationsGroup.getCoordinate());
      }
    );
  }
}
