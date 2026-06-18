package org.opentripplanner.ext.geocoder.configure;

import javax.annotation.Nullable;
import org.opentripplanner.ext.geocoder.LuceneIndex;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationService;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.transit.service.TimetableRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This module builds the Lucene geocoder based on whether the feature flag is on or off.
 */
@Configuration(proxyBeanMethods = false)
public class GeocoderModule {

  @Bean
  @Nullable
  LuceneIndex luceneIndex(
    TimetableRepository timetableRepository,
    @Nullable StopConsolidationService stopConsolidationService
  ) {
    if (OTPFeature.SandboxAPIGeocoder.isOn()) {
      return new LuceneIndex(timetableRepository, stopConsolidationService);
    } else {
      return null;
    }
  }
}
