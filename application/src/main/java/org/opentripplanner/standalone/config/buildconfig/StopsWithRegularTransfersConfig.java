package org.opentripplanner.standalone.config.buildconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_10;

import java.util.List;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * Parses the {@code transfers.stopsWithRegularTransfers} parameter of the build config, the stops
 * that always have regular transfers, even if no scheduled trip visits them.
 */
public class StopsWithRegularTransfersConfig {

  public static Set<FeedScopedId> map(NodeAdapter transfers) {
    return Set.copyOf(
      transfers
        .of("stopsWithRegularTransfers")
        .since(V2_10)
        .summary(
          "Stops that should always have regular transfers computed, even without scheduled trips."
        )
        .description(
          """
          List of stop IDs for which regular transfers are always pre-computed during graph build,
          even if the stop has no scheduled trips. Remember to include _feedId_ like this
          `"RB:NSR:Quay:102541"`.

          This is useful for stops that are unused in static transit data, but may be visited by
          real-time updates (e.g. a platform that a train can be re-routed to at runtime). Without
          this configuration, stops with no scheduled trips are excluded from transfer
          pre-computation and become unreachable islands when a real-time update routes a trip to
          them.

          Note! This parameter should be replaced with an automatic update to regular transfers
          based on real-time updates.
          """
        )
        .experimentalFeature()
        .asFeedScopedIds(List.of())
    );
  }
}
