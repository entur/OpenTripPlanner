package org.opentripplanner.transfer.regular.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.transfer.regular.model.DefaultRaptorTransfer;
import org.opentripplanner.transfer.regular.model.PathTransfer;

class OnDemandRaptorTransferIndex implements RaptorTransferIndex {

  private final List<List<PathTransfer>> forwardTransfers;
  private List<List<PathTransfer>> reversedTransfers;
  private final Collection<DefaultRaptorTransfer>[] forwardRaptorTransfers;

  private final Collection<DefaultRaptorTransfer>[] reversedRaptorTransfers;

  private final StreetSearchRequest request;

  OnDemandRaptorTransferIndex(
    List<List<PathTransfer>> transfersByStopIndex,
    StreetSearchRequest request
  ) {
    this.request = request;
    forwardTransfers = transfersByStopIndex;

    //noinspection unchecked
    forwardRaptorTransfers = new Collection[transfersByStopIndex.size()];
    //noinspection unchecked
    reversedRaptorTransfers = new Collection[transfersByStopIndex.size()];
  }

  private synchronized void initializeReversedTransfers() {
    if (reversedTransfers == null) {
      reversedTransfers = new ArrayList<>(forwardTransfers.size());
      for (int i = 0; i < forwardTransfers.size(); i++) {
        reversedTransfers.add(new ArrayList<>());
      }

      for (List<PathTransfer> transfers : forwardTransfers) {
        for (var transfer : transfers) {
          reversedTransfers.get(transfer.to.getIndex()).add(transfer);
        }
      }
    }
  }

  @Override
  public Collection<DefaultRaptorTransfer> getForwardTransfers(int stopIndex) {
    // This block is not fully thread safe as there may be a race condition between the check
    // and the assignment. However, the assignment is an atomic operation and the assigned value
    // should always be the same, so we don't think that it will be a major problem.
    // We don't think that the overhead of locking is worthwhile for the occasional chance of two
    // threads generating the transfers at the same time.
    if (forwardRaptorTransfers[stopIndex] == null) {
      forwardRaptorTransfers[stopIndex] = RaptorTransferIndex.getRaptorTransfers(
        request,
        forwardTransfers.get(stopIndex)
      );
    }

    return forwardRaptorTransfers[stopIndex];
  }

  @Override
  public Collection<DefaultRaptorTransfer> getReversedTransfers(int stopIndex) {
    initializeReversedTransfers();

    if (reversedRaptorTransfers[stopIndex] == null) {
      reversedRaptorTransfers[stopIndex] = RaptorTransferIndex.getRaptorTransfers(
        request,
        reversedTransfers.get(stopIndex)
      )
        .stream()
        .map(t -> t.reverseOf(stopIndex))
        .toList();
    }

    return reversedRaptorTransfers[stopIndex];
  }
}
