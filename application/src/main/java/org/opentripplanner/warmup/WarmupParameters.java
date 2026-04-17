package org.opentripplanner.warmup;

import java.util.List;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;

/**
 * Parameters for the application warmup feature.
 * <p>
 * Produced by the JSON config mapper and consumed by {@link
 * org.opentripplanner.warmup.configure.WarmupModule} to construct the warmup worker.
 */
public interface WarmupParameters {
  /** Which GraphQL API to use for warmup queries. */
  WarmupApi api();

  /** Origin location for warmup searches. */
  WgsCoordinate from();

  /** Destination location for warmup searches. */
  WgsCoordinate to();

  /**
   * Ordered list of access modes cycled through by warmup queries. Paired with {@link
   * #egressModes()} by index: entry {@code i} of each list forms a pair.
   */
  List<StreetMode> accessModes();

  /**
   * Ordered list of egress modes cycled through by warmup queries. Paired with {@link
   * #accessModes()} by index.
   */
  List<StreetMode> egressModes();
}
