package org.opentripplanner.transfer.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transit.model.site.StopLocation;

public class DefaultTransferRepository implements TransferRepository {

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
      index = new TransferIndex(this);
    }
  }

  @Override
  public Collection<PathTransfer> getTransfersToStop(StopLocation stopLocation) {
    if (index == null) {
      // ToDo: Or better throw an exception?
      return Collections.emptyList();
    }
    return index.getTransfersToStop(stopLocation);
  }

  @Override
  public Collection<PathTransfer> getTransfersFromStop(StopLocation stopLocation) {
    if (index == null) {
      // ToDo: Or better throw an exception?
      return Collections.emptyList();
    }
    return index.getTransfersFromStop(stopLocation);
  }

  private void invalidateIndex() {
    index = null;
  }
}
