package org.opentripplanner.transit.transfer.regular.spi;

import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.transit.transfer.regular.api.maxduration.MaxDurationsPreference;

/**
 * The street search and re-costing of transfer paths under one set of user preferences, see
 * {@link TransferPathProvider#searchFor}. Creating a search is expensive compared to re-costing
 * one path with it, so a search should be reused for all paths under the same preferences.
 * <p>
 * Implementations are immutable and thread-safe.
 *
 * @param <P> the street path type
 */
public interface TransferSearch<P> {
  /**
   * Discover candidate paths from {@code fromStop}, each paired with the {@link PathCriteria} it
   * was found under. There may be several candidates to the same stop.
   *
   * @param maxDurations how far to search, the limit of the profile type. It bounds this
   *                     discovery search only, see {@link #computePathCriteria}.
   */
  Collection<TransferPath<P>> findNearbyStops(
    FeedScopedId fromStop,
    MaxDurationsPreference maxDurations
  );

  /**
   * Re-cost a previously discovered path, which may have been found under other user preferences
   * than the ones of this search. Empty if the path can not be traversed with this search. The max
   * duration of the profile type is not applied, it bounds the discovery search only.
   */
  Optional<PathCriteria> computePathCriteria(P path);

  /**
   * The result of {@link #components} for a path that can not be represented exactly by numbers
   * independent of the user preferences, for example a path with an edge whose cost depends on
   * them in a way the implementation does not model. Compare by length ({@code length == 0}). The
   * transfers of such a path must be costed with {@link #computePathCriteria}.
   */
  float[] NOT_DERIVABLE = new float[0];

  /**
   * The numbers the duration and the cost of {@code path} depend on, independent of the user
   * preferences, see {@link #valueFunction()}. The layout is private to the implementation.
   *
   * @return the numbers, {@link #NOT_DERIVABLE} if the path can not be represented exactly,
   *         {@code null} if the search does not support components at all, or if the path can not
   *         be traversed (then {@link #computePathCriteria} is empty too)
   */
  @Nullable
  default float[] components(P path) {
    return null;
  }

  /**
   * Compute the duration and the cost of a path from its {@link #components} under the user
   * preferences of this search, the same values as {@link #computePathCriteria} within the rounding
   * of the stored numbers. It is not defined for {@link #NOT_DERIVABLE} paths. {@code null} if the search does not support it, for example for the
   * preferences of a wheelchair.
   */
  @Nullable
  default TransferValueFunction valueFunction() {
    return null;
  }
}
