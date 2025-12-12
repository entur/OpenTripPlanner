package org.opentripplanner.transfer.internal;

import java.util.Collection;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.transit.model.site.StopLocation;

public interface TransferIndex {
  Collection<PathTransfer> findWalkTransfersToStop(StopLocation stopLocation);

  Collection<PathTransfer> findWalkTransfersFromStop(StopLocation stopLocation);
}
