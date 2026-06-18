package org.opentripplanner.ext.empiricaldelay.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.internal.DefaultEmpiricalDelayRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmpiricalDelayRepositoryModule {

  @Bean
  @Nullable
  EmpiricalDelayRepository provideEmpiricalDelayRepository() {
    return new DefaultEmpiricalDelayRepository();
  }
}
