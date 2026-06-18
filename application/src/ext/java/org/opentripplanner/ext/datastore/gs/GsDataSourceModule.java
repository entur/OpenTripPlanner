package org.opentripplanner.ext.datastore.gs;

import javax.annotation.Nullable;
import org.opentripplanner.datastore.api.GoogleStorageDSRepository;
import org.opentripplanner.datastore.api.OtpDataStoreConfig;
import org.opentripplanner.datastore.base.DataSourceRepository;
import org.opentripplanner.framework.application.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GsDataSourceModule {

  private static final Logger LOG = LoggerFactory.getLogger(GsDataSourceModule.class);

  @Bean
  @Nullable
  @GoogleStorageDSRepository
  DataSourceRepository provideGoogleStorageDataSourceRepository(OtpDataStoreConfig config) {
    if (OTPFeature.GoogleCloudStorage.isOff()) {
      return null;
    }
    LOG.info("Google Cloud Store Repository enabled - GS resources detected.");
    return new GsDataSourceRepository(config.gsParameters());
  }
}
