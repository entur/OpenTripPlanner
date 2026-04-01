package org.opentripplanner.raptor.api.request.via;

import java.time.Duration;
import org.opentripplanner.raptor.spi.RaptorTransfer;

public final class ViaVisitBuilder extends AbstractBuilder<ViaVisitBuilder> {

  private final int minimumWaitTime;

  public ViaVisitBuilder(String label, Duration minimumWaitTime) {
    super(label);
    this.minimumWaitTime = RaptorViaLocation.validateMinimumWaitTime(minimumWaitTime);
  }

  public ViaVisitBuilder addStop(int stop) {
    return addConnection(new RaptorVisitStopViaConnection(stop, minimumWaitTime));
  }

  public ViaVisitBuilder addTransfer(int stop, RaptorTransfer transfer) {
    return addConnection(new RaptorTransferViaConnection(stop, minimumWaitTime, transfer));
  }

  public RaptorViaLocation build() {
    return new RaptorViaLocation(label, connections);
  }
}
