package org.opentripplanner.ext.dataoverlay.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.dataoverlay.configuration.DataOverlayParameterBindings;
import org.opentripplanner.standalone.config.BuildConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dagger module that provides DataOverlayParameterBindings from the build configuration. This
 * module is included in both the graph building and runtime Dagger components.
 * <p>
 * This module is dual-bridged during the Dagger&rarr;Spring migration: the graph-building phase
 * still reads its Dagger annotations while the construct/serve phase reads the Spring ones.
 */
@Module
@Configuration(proxyBeanMethods = false)
public class DataOverlayParameterBindingsModule {

  @Provides
  @Bean
  @Singleton
  @Nullable
  static DataOverlayParameterBindings provideDataOverlayParameterBindings(BuildConfig config) {
    return config.dataOverlay != null ? config.dataOverlay.getParameterBindings() : null;
  }
}
