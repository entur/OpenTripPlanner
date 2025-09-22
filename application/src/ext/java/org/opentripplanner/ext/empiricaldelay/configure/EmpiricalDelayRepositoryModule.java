package org.opentripplanner.ext.empiricaldelay.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.internal.DefaultEmpiricalDelayRepository;
import org.opentripplanner.framework.application.OTPFeature;

@Module
public class EmpiricalDelayRepositoryModule {

  @Provides
  @Singleton
  @Nullable
  static EmpiricalDelayRepository provideEmpiricalDelayRepository() {
    return OTPFeature.EmpiricalDelay.isOff() ? null : new DefaultEmpiricalDelayRepository();
  }
}
