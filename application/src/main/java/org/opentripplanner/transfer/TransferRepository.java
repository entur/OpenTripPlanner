package org.opentripplanner.transfer;

import com.google.common.collect.Multimap;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * This repository holds all transfers that are calculated between different stops for different
 * modes. It is populated during the graph build process and saved into the serialized graph object.
 * It should only be accessed directly during graph building, not during OTP server runtime. Use the
 * {@link TransferService} instead, once transfer updates are implemented it will handle mutability
 * correctly.
 */
public interface TransferRepository extends Serializable {
  Collection<PathTransfer> findTransfersByStop(StopLocation stop);

  List<PathTransfer> findTransfersByMode(StreetMode mode);

  Collection<PathTransfer> listPathTransfers();

  /**
   * This is called to fill the repository with data. Calling this method results in invalidating
   * the index. For full functionality {@link #index()} has to be called after.
   *
   * @param transfersByStop transfers to be added, grouped by the stop set as from-stop
   */
  void addAllTransfersByStops(Multimap<StopLocation, PathTransfer> transfersByStop);

  /**
   * Initialize the index. This needs to be called before
   * {@link #findWalkTransfersFromStop(StopLocation)} and {@link #findWalkTransfersToStop(StopLocation)}
   */
  void index();

  /**
   * @param toStop {@code StopLocation} that is set as a to-stop
   * @return all walk mode {@code PathTransfer}s with the specified {@code StopLocation} as a
   * to-stop
   * @throws UnsupportedOperationException if flex routing is not activated
   * @throws IllegalStateException         if the index was not initialized
   */
  Collection<PathTransfer> findWalkTransfersToStop(StopLocation toStop);

  /**
   * @param fromStop {@code StopLocation} that is set as a from-stop
   * @return all walk mode {@code PathTransfer}s with the specified {@code StopLocation} as a
   * from-stop
   * @throws UnsupportedOperationException if flex routing is not activated
   * @throws IllegalStateException         if the index was not initialized
   */
  Collection<PathTransfer> findWalkTransfersFromStop(StopLocation fromStop);
}
