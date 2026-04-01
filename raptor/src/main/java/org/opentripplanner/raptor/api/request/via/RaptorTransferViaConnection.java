package org.opentripplanner.raptor.api.request.via;

import java.util.Objects;
import org.opentripplanner.raptor.api.model.RaptorValueType;
import org.opentripplanner.raptor.spi.RaptorStopNameResolver;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.utils.time.DurationUtils;

/**
 * A via-connection is used to define one of the physical locations in a via location Raptor must
 * visit. At least one connection in a {@link RaptorViaLocation} must be used. A connection can be
 * a single stop or a stop and a transfer to another stop. The last is useful if you want to use
 * the connection to visit something other than a stop, like a street location. This is not an
 * alternative to transfers. Raptor supports several use-cases through via-connections:
 *
 * <h4>Route via a pass-through-stop</h4>
 * Raptor will allow a path to go through a pass-through-stop. The stop can be visited on-board
 * transit, or at the alight- or board-stop. The from-stop and to-stop must be the same, and the
 * minimum-wait-time must be zero.
 *
 * <h4>Route via a single stop with a minimum-wait-time</h4>
 * Raptor will allow a path to go through a single stop, if the from-stop and to-stop is the
 * same. If the minimum-wait-time is greater than zero(0) the path will either alight or board
 * transit at this stop, and the minimum-wait-time criteria is enforced.
 *
 * <h4>Route via a coordinate</h4>
 *
 * To route through a coordinate you need to find all nearby stops, then find all access and egress
 * paths to and from the street location. Then combine all access and egress paths to form
 * complete transfers. Raptor does not know/see the actual via street location, it only uses the
 * connection from a stop to another, the total time it takes and the total cost. You must generate
 * a transfer with two "legs" in it. One leg going from the 'from-stop' to the street location, and
 * one leg going back to the 'to-stop'. If you have 10 stops around the via street location, then
 * you must combine all ten access paths and egress paths.
 *
 * The min-wait-time in the {@link RaptorViaLocation} is added to the transfers
 * {@code durationInSeconds}. The calculation of {@code c1} should include the walk time, but not
 * the min-wait-time (assuming all connections have the same minimum wait time).
 */
public final class RaptorTransferViaConnection extends AbstractViaConnection {

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
  public boolean isBetterOrEqual(AbstractViaConnection other) {
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
