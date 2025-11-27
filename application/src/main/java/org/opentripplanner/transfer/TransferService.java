package org.opentripplanner.transfer;

import java.util.Collection;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Access transfers during OTP server runtime. It provides a frozen view of all these elements at a
 * point in time, which is not affected by ongoing transfer updates, allowing results to remain
 * stable over the course of a request.
 */
public interface TransferService {
  Collection<PathTransfer> getTransfersByStop(StopLocation stop);

  Collection<PathTransfer> getTransfersFromStop(StopLocation stopLocation);

  Collection<PathTransfer> getTransfersToStop(StopLocation stopLocation);
}
