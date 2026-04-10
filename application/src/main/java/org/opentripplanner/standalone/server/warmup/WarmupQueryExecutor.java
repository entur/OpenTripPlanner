package org.opentripplanner.standalone.server.warmup;

import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.geometry.WgsCoordinate;

/** Strategy for executing warmup queries against a specific GraphQL API. */
interface WarmupQueryExecutor {
  /** The number of distinct access/egress mode combinations to cycle through. */
  int modeCombinationCount();

  void execute(
    OtpServerRequestContext context,
    WgsCoordinate from,
    WgsCoordinate to,
    boolean arriveBy,
    int modeIndex
  );
}
