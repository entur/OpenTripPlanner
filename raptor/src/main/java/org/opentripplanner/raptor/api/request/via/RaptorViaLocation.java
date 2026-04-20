package org.opentripplanner.raptor.api.request.via;

import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.raptor.spi.RaptorStopNameResolver;
import org.opentripplanner.utils.lang.IntUtils;

/**
 * Defines a via location which Raptor will force the path through. The concrete location is
 * called a connection. A location must have at least one connection, but can have more than
 * on alternative. Raptor will force the path through one of the connections. So, if there
 * are two connections, stop A and B, then Raptor will force the path through A or B. If the
 * path goes through A, it may or may not go through B.
 */
public final class RaptorViaLocation {

  private static final Duration MAX_WAIT_TIME = Duration.ofHours(24);
  private static final Duration MIN_WAIT_TIME = Duration.ZERO;

  private final String label;
  private final List<ViaConnection> connections;

  RaptorViaLocation(String label, List<ViaConnection> connections) {
    this.label = label;
    this.connections = connections;

    if (connections.isEmpty()) {
      throw new IllegalArgumentException("At least on connection must exist!");
    }
  }

  /**
   * Force the path through a set of stops, either on-board or as an alight or board stop.
   */
  public static PassThroughBuilder passThrough(@Nullable String label) {
    return new PassThroughBuilder(label);
  }

  /**
   * Force the path through one of the listed connections. To visit a stop, the path must board or
   * alight transit at the given stop, on-board visits do not count, see
   * {@link #passThrough(String)}.
   */
  public static ViaVisitBuilder viaVisit(@Nullable String label) {
    return viaVisit(label, MIN_WAIT_TIME);
  }

  /**
   * Force the path through one of the listed connections, and wait the given minimum-wait-time
   * before continuing. To visit a stop, the path must board or alight transit at the given stop,
   * on-board visits do not count, see {@link #passThrough(String)}.
   */
  public static ViaVisitBuilder viaVisit(@Nullable String label, Duration minimumWaitTime) {
    return new ViaVisitBuilder(label, minimumWaitTime);
  }

  @Nullable
  public String label() {
    return label;
  }

  private boolean isPassThroughSearch() {
    return connections.stream().anyMatch(it -> it instanceof RaptorPassThroughViaConnection);
  }

  public List<ViaConnection> connections() {
    return connections;
  }

  /**
   * This is a convenient accessor method used inside Raptor. It converts the list stops to a
   * bit-set. Add other access methods if needed.
   */
  public BitSet asBitSet() {
    return connections
      .stream()
      .mapToInt(ViaConnection::fromStop)
      .collect(BitSet::new, BitSet::set, BitSet::or);
  }

  /// Compare all pairs to check for duplicates and non-optimal connections Avoid usage of this
  /// method because it can have a really bad performance.
  public void validateDuplicateConnections() {
    for (int i = 0; i < connections.size(); ++i) {
      var a = connections.get(i);
      for (int j = i + 1; j < connections.size(); ++j) {
        var b = connections.get(j);
        if (a.isBetterOrEqual(b) || b.isBetterOrEqual(a)) {
          throw new IllegalArgumentException(
            "All connection need to be pareto-optimal: (" + a + ") <-> (" + b + ")"
          );
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    throw new UnsupportedOperationException("No need to compare " + getClass());
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException("No need for hashCode of " + getClass());
  }

  @Override
  public String toString() {
    return toString(Integer::toString);
  }

  public String toString(RaptorStopNameResolver stopNameResolver) {
    var buf = new StringBuilder(getClass().getSimpleName()).append('{');
    buf.append(isPassThroughSearch() ? "pass-through " : "via-visit ");
    if (label != null) {
      buf.append(label).append(" ");
    }
    buf
      .append(connections.size() <= 10 ? ": " : "(10/" + connections.size() + "): ")
      .append(
        connections
          .stream()
          .limit(10)
          .map(it -> it.toString(stopNameResolver))
          .toList()
      );
    return buf.append("}").toString();
  }

  static int validateMinimumWaitTime(Duration minimumWaitTime) {
    return IntUtils.requireInRange(
      (int) minimumWaitTime.toSeconds(),
      (int) MIN_WAIT_TIME.toSeconds(),
      (int) MAX_WAIT_TIME.toSeconds(),
      "minimumWaitTime"
    );
  }
}
