package org.opentripplanner.standalone.config.configure;

import java.util.Set;
import org.opentripplanner.raptor.api.request.RaptorEnvironment;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.DebugUiConfig;
import org.opentripplanner.standalone.config.OtpConfig;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.standalone.config.routerconfig.RaptorEnvironmentFactory;
import org.springframework.beans.factory.annotation.CustomAutowireConfigurer;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Map {@link ConfigModel} into more specific types like {@link BuildConfig} to simplify
 * dependency injection in other configurations.
 */
@Configuration(proxyBeanMethods = false)
public class ConfigModule {

  /**
   * Spring only recognizes {@code org.springframework.beans.factory.annotation.Qualifier} and
   * {@code jakarta.inject.Qualifier} as qualifier meta-annotations out of the box. Several OTP
   * qualifier annotations (e.g. {@code @GtfsSchema}, {@code @TransmodelSchema}, {@code @OtpBaseDirectory})
   * are meta-annotated with the legacy {@code javax.inject.Qualifier}; register it here so Spring
   * resolves them.
   */
  @Bean
  static CustomAutowireConfigurer customAutowireConfigurer() {
    var configurer = new CustomAutowireConfigurer();
    configurer.setCustomQualifierTypes(Set.of(javax.inject.Qualifier.class));
    return configurer;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static OtpConfig provideOtpConfig(ConfigModel model) {
    return model.otpConfig();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static BuildConfig provideBuildConfig(ConfigModel model) {
    return model.buildConfig();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static RouterConfig provideRouterConfig(ConfigModel model) {
    return model.routerConfig();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static DebugUiConfig provideDebugUiConfig(ConfigModel model) {
    return model.debugUiConfig();
  }

  @Bean
  static RaptorConfig<TripSchedule> providesRaptorConfig(
    RouterConfig routerConfig,
    RaptorEnvironment environment
  ) {
    return new RaptorConfig<>(routerConfig.transitTuningConfig(), environment);
  }

  @Bean
  static RaptorEnvironment providesRaptorEnvironment(RouterConfig routerConfig) {
    int searchThreadPoolSize = routerConfig.transitTuningConfig().searchThreadPoolSize();
    return RaptorEnvironmentFactory.create(searchThreadPoolSize);
  }
}
