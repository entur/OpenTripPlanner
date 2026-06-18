package org.opentripplanner.standalone.config.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
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
 * DI in other modules.
 * <p>
 * This module is dual-bridged during the Dagger&rarr;Spring migration: the config-load phase
 * (Dagger {@code LoadConfigModule}/{@code LoadApplicationFactory}) still reads its Dagger
 * annotations while the construct/serve phase reads the Spring ones.
 */
@Module
@Configuration(proxyBeanMethods = false)
public class ConfigModule {

  /**
   * Spring 7 only recognizes {@code org.springframework.beans.factory.annotation.Qualifier} and
   * {@code jakarta.inject.Qualifier} as qualifier meta-annotations out of the box. Several OTP
   * qualifier annotations (e.g. {@code @GtfsSchema}, {@code @TransmodelSchema}) are still
   * meta-annotated with the legacy {@code javax.inject.Qualifier}; register it here so Spring
   * resolves them like Dagger did. (Spring-only; Dagger ignores non-{@code @Provides} methods.)
   */
  @Bean
  static CustomAutowireConfigurer customAutowireConfigurer() {
    var configurer = new CustomAutowireConfigurer();
    configurer.setCustomQualifierTypes(Set.of(javax.inject.Qualifier.class));
    return configurer;
  }

  @Provides
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static OtpConfig provideOtpConfig(ConfigModel model) {
    return model.otpConfig();
  }

  @Provides
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static BuildConfig provideBuildConfig(ConfigModel model) {
    return model.buildConfig();
  }

  @Provides
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static RouterConfig provideRouterConfig(ConfigModel model) {
    return model.routerConfig();
  }

  @Provides
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  static DebugUiConfig provideDebugUiConfig(ConfigModel model) {
    return model.debugUiConfig();
  }

  @Provides
  @Bean
  @Singleton
  static RaptorConfig<TripSchedule> providesRaptorConfig(
    RouterConfig routerConfig,
    RaptorEnvironment environment
  ) {
    return new RaptorConfig<>(routerConfig.transitTuningConfig(), environment);
  }

  @Provides
  @Bean
  @Singleton
  static RaptorEnvironment providesRaptorEnvironment(RouterConfig routerConfig) {
    int searchThreadPoolSize = routerConfig.transitTuningConfig().searchThreadPoolSize();
    return RaptorEnvironmentFactory.create(searchThreadPoolSize);
  }
}
