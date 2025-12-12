package org.opentripplanner.transfer.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.ext.flex.FlexTransferIndex;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transit.model.site.StopLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTransferRepository implements TransferRepository {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTransferRepository.class);

  private final Multimap<StopLocation, PathTransfer> transfersByStop = HashMultimap.create();

  private transient TransferIndex index;

  @Override
  public Collection<PathTransfer> findTransfersByStop(StopLocation stop) {
    return transfersByStop.get(stop);
  }

  /** Pre-generated transfers between all stops filtered based on the modes in the PathTransfer. */
  @Override
  public List<PathTransfer> findTransfersByMode(StreetMode mode) {
    return transfersByStop
      .values()
      .stream()
      .filter(pathTransfer -> pathTransfer.getModes().contains(mode))
      .toList();
  }

  @Override
  public Collection<PathTransfer> listPathTransfers() {
    return transfersByStop.values();
  }

  @Override
  public void addAllTransfersByStops(Multimap<StopLocation, PathTransfer> transfersByStop) {
    invalidateIndex();
    this.transfersByStop.putAll(transfersByStop);
  }

  @Override
  public void index() {
    if (index == null) {
      LOG.info("Transfer repository index init...");
      if (OTPFeature.FlexRouting.isOn()) {
        index = new FlexTransferIndex(this);
      } else {
        index = new DefaultTransferIndex();
      }
      LOG.info("Transfer repository index init complete.");
    }
  }

  @Override
  public Collection<PathTransfer> findWalkTransfersToStop(StopLocation toStop) {
    if (index == null) {
      throw new IllegalStateException("The transfer index is needed but not initialized");
    }
    return index.findWalkTransfersToStop(toStop);
  }

  @Override
  public Collection<PathTransfer> findWalkTransfersFromStop(StopLocation fromStop) {
    if (index == null) {
      throw new IllegalStateException("The transfer index is needed but not initialized");
    }
    return index.findWalkTransfersFromStop(fromStop);
  }

  private void invalidateIndex() {
    index = null;
  }
}
