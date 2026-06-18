package org.opentripplanner.warmup.configure;

import javax.annotation.Nullable;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.warmup.WarmupLauncher;
import org.opentripplanner.warmup.api.WarmupParameters;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Wiring for the application warmup feature.
 * <p>
 * Provides the {@link WarmupParameters} binding (mapped from the JSON config section by {@code
 * WarmupConfig}) and the {@link WarmupLauncher} that {@link
 * org.opentripplanner.standalone.configure.ConstructApplication} uses to start the warmup thread
 * after Raptor transit data and updaters have been set up.
 */
@Configuration(proxyBeanMethods = false)
public class WarmupModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Nullable
  WarmupParameters provideWarmupParameters(RouterConfig routerConfig) {
    return routerConfig.warmupParameters();
  }

  @Bean
  WarmupLauncher provideWarmupLauncher(
    @Nullable WarmupParameters parameters,
    OtpServerRequestContext serverContext,
    TimetableRepository timetableRepository
  ) {
    return new WarmupLauncher(parameters, serverContext, timetableRepository);
  }
}
