package org.opentripplanner.transit.transfer.regular.internal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.raptor.data.transfers.regular.LazyRaptorTransferStore;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;
import org.opentripplanner.transit.transfer.regular.spi.TransferSearch;

/**
 * Loads the transfers of one stop for a {@link LazyRaptorTransferStore}: each path of the stop is
 * re-costed with {@link TransferSearch#computePathCriteria}, exactly as the eager creation does.
 * <p>
 * This class is immutable and thread-safe.
 */
final class LazyTransferRows<P> implements LazyRaptorTransferStore.RowLoader {

  private final OverlaidTemplatePaths<P> paths;

  @Nullable
  private final AbstractUserPreferences<?> profile;

  @Nullable
  private final TransferSearch<P> search;

  /** Derive a to-row from the from-rows of its neighbors, instead of re-costing its paths. */
  private final boolean toViaFromRows;

  LazyTransferRows(
    OverlaidTemplatePaths<P> paths,
    @Nullable AbstractUserPreferences<?> profile,
    @Nullable TransferSearch<P> search,
    boolean toViaFromRows
  ) {
    this.paths = paths;
    this.profile = profile;
    this.search = search;
    this.toViaFromRows = toViaFromRows;
  }

  @Override
  public int[] loadFromRow(int fromStop) {
    if (profile == null) {
      return new int[0];
    }
    var byToStop = paths.pathsFrom(profile, fromStop);
    var buffer = new int[byToStop.size() * 3];
    int n = 0;
    for (var e : byToStop.entrySet()) {
      var criteria = search.computePathCriteria(e.getValue());
      if (criteria.isPresent()) {
        buffer[n++] = e.getKey();
        buffer[n++] = criteria.get().durationInSeconds();
        buffer[n++] = criteria.get().c1();
      }
    }
    return n == buffer.length ? buffer : Arrays.copyOf(buffer, n);
  }

  @Override
  public int[] loadToRow(int toStop, RaptorRegularTransferService fromRows) {
    if (profile == null) {
      return new int[0];
    }
    var fromStops = paths.fromStopsTo(profile, toStop);
    var buffer = new int[fromStops.length * 3];
    int n = 0;
    for (int fromStop : fromStops) {
      if (toViaFromRows) {
        var it = fromRows.getTransfersFromStop(fromStop);
        while (it.hasNext()) {
          var tx = it.next();
          if (tx.stop() == toStop) {
            buffer[n++] = fromStop;
            buffer[n++] = tx.durationInSeconds();
            buffer[n++] = tx.c1();
          }
        }
      } else {
        var criteria = search.computePathCriteria(paths.pathsFrom(profile, fromStop).get(toStop));
        if (criteria.isPresent()) {
          buffer[n++] = fromStop;
          buffer[n++] = criteria.get().durationInSeconds();
          buffer[n++] = criteria.get().c1();
        }
      }
    }
    return n == buffer.length ? buffer : Arrays.copyOf(buffer, n);
  }

  /** The to-stops of the paths from {@code fromStop}. */
  Set<Integer> targetsOf(int fromStop) {
    return profile == null ? Set.of() : paths.pathsFrom(profile, fromStop).keySet();
  }

  /**
   * The rows a patch must drop, if the transfers from {@code changedFromStops} changed since the
   * paths of {@code previous}: the from-rows of the stops, and the to-rows of every stop that
   * was or is a target of them.
   */
  Set<Integer> affectedToStops(Set<Integer> changedFromStops, LazyTransferRows<P> previous) {
    var affected = new HashSet<Integer>();
    for (int fromStop : changedFromStops) {
      affected.addAll(targetsOf(fromStop));
      affected.addAll(previous.targetsOf(fromStop));
    }
    return affected;
  }
}
