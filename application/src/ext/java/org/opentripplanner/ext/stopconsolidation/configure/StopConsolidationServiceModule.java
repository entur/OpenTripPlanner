package org.opentripplanner.ext.stopconsolidation.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationService;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StopConsolidationServiceModule {

  @Bean
  @Nullable
  StopConsolidationService service(
    @Nullable StopConsolidationRepository repo,
    TimetableRepository tm
  ) {
    if (repo == null) {
      return null;
    } else {
      return new DefaultStopConsolidationService(repo, tm);
    }
  }
}
