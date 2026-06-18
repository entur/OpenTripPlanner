package org.opentripplanner.ext.emission.configure;

import static org.opentripplanner.ext.emission.model.CarEmissionUtil.calculateCarCo2EmissionPerMeterPerPerson;

import org.opentripplanner.ext.emission.EmissionRepository;
import org.opentripplanner.ext.emission.internal.DefaultEmissionRepository;
import org.opentripplanner.standalone.config.BuildConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmissionRepositoryModule {

  @Bean
  EmissionRepository provideEmissionRepository(BuildConfig config) {
    var repository = new DefaultEmissionRepository();
    // Init car passenger emission data
    {
      repository.setCarAvgCo2PerMeter(
        calculateCarCo2EmissionPerMeterPerPerson(config.emission.car())
      );
    }
    return repository;
  }
}
