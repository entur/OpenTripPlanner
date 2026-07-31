package org.opentripplanner.updater.trip.siri.updater;

import org.opentripplanner.updater.spi.PollingGraphUpdaterParameters;
import org.opentripplanner.updater.trip.UrlUpdaterParameters;

public interface SiriETUpdaterParameters
  extends UrlUpdaterParameters, PollingGraphUpdaterParameters {
  String url();

  boolean blockReadinessUntilInitialized();

  boolean fuzzyTripMatching();

  /**
   * Whether to use the new unified updater implementation shared by SIRI-ET and GTFS-RT.
   * When false (default), uses the legacy SiriRealTimeTripUpdateAdapter.
   */
  boolean useNewUpdaterImplementation();
}
