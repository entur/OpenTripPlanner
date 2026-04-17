package org.opentripplanner.warmup;

import java.util.List;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;

/**
 * Parameters for the application warmup feature.
 * <p>
 * Produced by the JSON config mapper ({@code WarmupConfig}) and consumed by {@link
 * org.opentripplanner.warmup.configure.WarmupModule} to construct the warmup worker.
 *
 * @param api Which GraphQL API to use for warmup queries.
 * @param from Origin location for warmup searches.
 * @param to Destination location for warmup searches.
 * @param accessModes Ordered list of access modes cycled through by warmup queries. Paired with
 *   {@link #egressModes()} by index: entry {@code i} of each list forms a pair.
 * @param egressModes Ordered list of egress modes cycled through by warmup queries. Paired with
 *   {@link #accessModes()} by index.
 */
public record WarmupParameters(
  WarmupApi api,
  WgsCoordinate from,
  WgsCoordinate to,
  List<StreetMode> accessModes,
  List<StreetMode> egressModes
) {
  public WarmupParameters {
    accessModes = List.copyOf(accessModes);
    egressModes = List.copyOf(egressModes);
  }
}
