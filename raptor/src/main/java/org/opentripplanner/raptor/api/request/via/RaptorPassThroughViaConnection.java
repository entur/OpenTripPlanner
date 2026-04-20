package org.opentripplanner.raptor.api.request.via;

import org.opentripplanner.raptor.spi.RaptorStopNameResolver;

/**
 * See {@link ViaConnection}
 */
public final class RaptorPassThroughViaConnection extends ViaConnection {

  RaptorPassThroughViaConnection(int fromStop) {
    super(fromStop);
  }

  @Override
  public boolean isBetterOrEqual(ViaConnection other) {
    return equals(other);
  }

  @Override
  public boolean equals(Object other) {
    return super.equalsTo(other, getClass());
  }

  @Override
  public int hashCode() {
    return 117 + fromStop();
  }

  public final String toString(RaptorStopNameResolver stopNameResolver) {
    return new StringBuilder()
      .append("(stop ")
      .append(stopNameResolver.apply(fromStop()))
      .append(')')
      .toString();
  }
}
