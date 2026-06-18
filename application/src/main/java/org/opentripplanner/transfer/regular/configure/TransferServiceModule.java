package org.opentripplanner.transfer.regular.configure;

import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.internal.DefaultTransferService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TransferServiceModule {

  @Bean
  public RegularTransferService provideTransferService(TransferRepository transferRepository) {
    return new DefaultTransferService(transferRepository);
  }
}
