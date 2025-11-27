package org.opentripplanner.transfer.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transfer.internal.DefaultTransferRepository;

@Module
public class TransferRepositoryModule {

  @Provides
  @Singleton
  public TransferRepository provideTransferRepository() {
    return new DefaultTransferRepository();
  }
}
