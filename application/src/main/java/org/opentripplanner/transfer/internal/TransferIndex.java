package org.opentripplanner.transfer.internal;

import java.util.Collection;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transit.model.site.StopLocation;

public class TransferIndex {

  public Collection<PathTransfer> findWalkTransfersToStop(StopLocation stopLocation) {
    throw new UnsupportedOperationException("The transfer index used does not support this method");
  }

  public Collection<PathTransfer> findWalkTransfersFromStop(StopLocation stopLocation) {
    throw new UnsupportedOperationException("The transfer index used does not support this method");
  }

  public void invalidate() {}

  public void index(TransferRepository transferRepository) {}
}
