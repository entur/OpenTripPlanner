package org.opentripplanner.ext.gbfsgeofencing.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.internal.DefaultGbfsGeofencingRepository;
import org.opentripplanner.framework.application.OTPFeature;

@Module
public class GbfsGeofencingRepositoryModule {

  @Provides
  @Singleton
  @Nullable
  static GbfsGeofencingRepository provideGbfsGeofencingRepository() {
    if (OTPFeature.GbfsGeofencingBuildTime.isOn()) {
      return new DefaultGbfsGeofencingRepository();
    }
    return null;
  }
}
