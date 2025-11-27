package org.opentripplanner.transfer;

import org.opentripplanner.transfer.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.internal.DefaultTransferService;

public class TransferServiceTestFactory {

  public static TransferService defaultTransferService() {
    return new DefaultTransferService(new DefaultTransferRepository());
  }

  public static TransferService transferService(TransferRepository transferRepository) {
    return new DefaultTransferService(transferRepository);
  }

  public static TransferRepository defaultTransferRepository() {
    return new DefaultTransferRepository();
  }
}
