package org.opentripplanner.ext.flex;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transfer.internal.TransferIndex;
import org.opentripplanner.transit.model.site.StopLocation;

public class FlexTransferIndex extends TransferIndex {

  private final Multimap<StopLocation, PathTransfer> transfersToStop = ArrayListMultimap.create();

  private final Multimap<StopLocation, PathTransfer> transfersFromStop = ArrayListMultimap.create();

  public FlexTransferIndex(TransferRepository transferRepository) {
    super();
    // Flex transfers should only use WALK mode transfers.
    for (PathTransfer transfer : transferRepository.findTransfersByMode(StreetMode.WALK)) {
      transfersToStop.put(transfer.to, transfer);
      transfersFromStop.put(transfer.from, transfer);
    }
  }

  @Override
  public Collection<PathTransfer> findWalkTransfersToStop(StopLocation stopLocation) {
    return transfersToStop.get(stopLocation);
  }

  @Override
  public Collection<PathTransfer> findWalkTransfersFromStop(StopLocation stopLocation) {
    return transfersFromStop.get(stopLocation);
  }
}
