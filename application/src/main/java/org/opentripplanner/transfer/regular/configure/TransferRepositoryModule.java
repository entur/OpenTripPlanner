package org.opentripplanner.transfer.regular.configure;

import org.opentripplanner.ext.flex.FlexTransferIndex;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TransferRepositoryModule {

  @Bean
  public TransferRepository provideTransferRepository() {
    TransferIndex index;
    if (OTPFeature.FlexRouting.isOn()) {
      index = new FlexTransferIndex();
    } else {
      index = new TransferIndex();
    }
    return new DefaultTransferRepository(index);
  }
}
