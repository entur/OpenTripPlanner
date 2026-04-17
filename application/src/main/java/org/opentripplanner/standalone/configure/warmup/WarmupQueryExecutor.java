package org.opentripplanner.standalone.configure.warmup;

import org.opentripplanner.street.geometry.WgsCoordinate;

/** Strategy for executing warmup queries against a specific GraphQL API. */
interface WarmupQueryExecutor {
  /** The number of distinct access/egress mode combinations to cycle through. */
  int modeCombinationCount();

  /** @return true if the query executed without GraphQL errors. */
  boolean execute(WgsCoordinate from, WgsCoordinate to, boolean arriveBy, int modeIndex);
}
