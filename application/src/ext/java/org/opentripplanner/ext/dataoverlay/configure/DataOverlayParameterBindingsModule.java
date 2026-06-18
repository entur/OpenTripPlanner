package org.opentripplanner.ext.dataoverlay.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.dataoverlay.configuration.DataOverlayParameterBindings;
import org.opentripplanner.standalone.config.BuildConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides {@link DataOverlayParameterBindings} from the build configuration. This configuration is
 * registered by both the graph-building and the construct/serve phases.
 */
@Configuration(proxyBeanMethods = false)
public class DataOverlayParameterBindingsModule {

  @Bean
  @Nullable
  static DataOverlayParameterBindings provideDataOverlayParameterBindings(BuildConfig config) {
    return config.dataOverlay != null ? config.dataOverlay.getParameterBindings() : null;
  }
}
