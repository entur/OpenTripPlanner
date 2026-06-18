package org.opentripplanner.routing.linking.configure;

import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.service.vehiclerental.GeofencingZoneService;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.linking.VisibilityMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the vertex linker for the graph build.
 */
@Configuration(proxyBeanMethods = false)
public class VertexLinkerGraphBuildingModule {

  /**
   * The linker doesn't need to be a singleton as all state is kept in the graph.
   *
   * <p>Geofencing zones are registered at runtime by the vehicle-rental updater, so the build-phase
   * linker uses an empty zone lookup.
   */
  @Bean
  VertexLinker linker(Graph graph, BuildConfig config) {
    var mode = VisibilityMode.ofBoolean(config.areaVisibility);
    return new VertexLinker(
      graph,
      GeofencingZoneService.EMPTY,
      mode,
      config.maxAreaNodes,
      OTPFeature.FlexRouting.isOn()
    );
  }
}
