package org.opentripplanner.ext.empiricaldelay.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayService;
import org.opentripplanner.ext.empiricaldelay.internal.DefaultEmpiricalDelayService;
import org.opentripplanner.framework.application.OTPFeature;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * The service is used during application serve phase, not loading, so we need to provide
 * a module for the service without the repository, which is injected from the loading phase.
 */
@Configuration(proxyBeanMethods = false)
public class EmpiricalDelayServiceModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Nullable
  public EmpiricalDelayService provideEmpiricalDelayService(
    @Nullable EmpiricalDelayRepository repository
  ) {
    // The repository could be null if the feature is turned of after graph serialization
    if (OTPFeature.EmpiricalDelay.isOff() || repository == null) {
      return null;
    }
    return new DefaultEmpiricalDelayService(repository);
  }
}
