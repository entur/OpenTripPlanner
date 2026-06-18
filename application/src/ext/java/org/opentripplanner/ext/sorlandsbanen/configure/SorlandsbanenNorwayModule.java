package org.opentripplanner.ext.sorlandsbanen.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.sorlandsbanen.SorlandsbanenNorwayService;
import org.opentripplanner.framework.application.OTPFeature;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
public class SorlandsbanenNorwayModule {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Nullable
  SorlandsbanenNorwayService providesSorlandsbanenNorwayService() {
    return OTPFeature.Sorlandsbanen.isOn() ? new SorlandsbanenNorwayService() : null;
  }
}
