package org.opentripplanner.transfer.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transit.model.site.StopLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TransferIndex {

  private static final Logger LOG = LoggerFactory.getLogger(TransferIndex.class);

  private final Multimap<StopLocation, PathTransfer> transfersToStop = ArrayListMultimap.create();

  private final Multimap<StopLocation, PathTransfer> transfersFromStop = ArrayListMultimap.create();

  TransferIndex(DefaultTransferRepository transferRepository) {
    LOG.info("Transfer repository index init...");
    if (OTPFeature.FlexRouting.isOn()) {
      // Flex transfers should only use WALK mode transfers.
      for (PathTransfer transfer : transferRepository.findTransfersByMode(StreetMode.WALK)) {
        transfersToStop.put(transfer.to, transfer);
        transfersFromStop.put(transfer.from, transfer);
      }
    }
    LOG.info("Transfer repository index init complete.");
  }

  Collection<PathTransfer> getTransfersToStop(StopLocation stopLocation) {
    return transfersToStop.get(stopLocation);
  }

  Collection<PathTransfer> getTransfersFromStop(StopLocation stopLocation) {
    return transfersFromStop.get(stopLocation);
  }
}
