package org.opentripplanner.transfer.internal;

import java.util.Collection;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.transit.model.site.StopLocation;

public class DefaultTransferIndex implements TransferIndex {

  @Override
  public Collection<PathTransfer> findWalkTransfersToStop(StopLocation stopLocation) {
    throw new UnsupportedOperationException("The transfer index used does not support this method");
  }

  @Override
  public Collection<PathTransfer> findWalkTransfersFromStop(StopLocation stopLocation) {
    throw new UnsupportedOperationException("The transfer index used does not support this method");
  }
}
