package org.opentripplanner.raptor.api.request.via;

import java.util.Objects;
import org.opentripplanner.raptor.api.model.RaptorValueType;
import org.opentripplanner.raptor.spi.RaptorStopNameResolver;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.utils.time.DurationUtils;

/**
 * See {@link ViaConnection}
 */
public final class RaptorTransferViaConnection extends ViaConnection {

  private final int minimumWaitTime;
  private final RaptorTransfer transfer;

  RaptorTransferViaConnection(int fromStop, int minimumWaitTime, RaptorTransfer transfer) {
    super(fromStop);
    this.transfer = Objects.requireNonNull(transfer);
    this.minimumWaitTime = minimumWaitTime;
  }

  public RaptorTransfer transfer() {
    return transfer;
  }

  /**
   * Stop index where the connection ends - only transfers have this.
   */
  public int toStop() {
    return transfer.stop();
  }

  public int durationInSeconds() {
    return minimumWaitTime + transfer.durationInSeconds();
  }

  int c1() {
    return transfer.c1();
  }

  @Override
  public boolean isBetterOrEqual(ViaConnection other) {
    if (!super.equalsTo(other, getClass())) {
      return false;
    }
    var o = (RaptorTransferViaConnection) other;
    if (toStop() != o.toStop()) {
      return false;
    }
    if (durationInSeconds() < o.durationInSeconds() || c1() < o.c1()) {
      return true;
    }
    return durationInSeconds() == o.durationInSeconds() && c1() == o.c1();
  }

  @Override
  public boolean equals(Object other) {
    if (!super.equalsTo(other, getClass())) {
      return false;
    }
    var o = (RaptorTransferViaConnection) other;
    return toStop() == o.toStop() && durationInSeconds() == o.durationInSeconds() && c1() == o.c1();
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromStop(), toStop(), durationInSeconds(), c1());
  }

  @Override
  public final String toString(RaptorStopNameResolver stopNameResolver) {
    return new StringBuilder("(transfer ")
      .append(stopNameResolver.apply(fromStop()))
      .append(" ~ ")
      .append(stopNameResolver.apply(toStop()))
      .append(" ")
      .append(DurationUtils.durationToStr(durationInSeconds()))
      .append(" ")
      .append(RaptorValueType.C1.format(c1()))
      .append(')')
      .toString();
  }
}
