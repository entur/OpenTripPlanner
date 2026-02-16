package org.opentripplanner.updater.trip.siri.updater;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.PollingGraphUpdaterParameters;
import org.opentripplanner.updater.trip.UrlUpdaterParameters;

public interface SiriETUpdaterParameters
  extends UrlUpdaterParameters, PollingGraphUpdaterParameters {
  String url();

  boolean blockReadinessUntilInitialized();

  boolean fuzzyTripMatching();

  /**
   * Whether to use the new updater implementation based on common trip update handlers.
   * When true, uses the new DefaultTripUpdateApplier with SiriTripUpdateParser.
   * When false (default), uses the legacy SiriRealTimeTripUpdateAdapter.
   */
  boolean useNewUpdaterImplementation();

  /**
   * Whether to run the shadow comparison mode. When true, both the legacy and unified adapters
   * run on every trip, comparing their outputs. The legacy adapter remains primary (writes to
   * the snapshot) and the unified adapter is shadow (read-only). Mismatches are logged as warnings.
   */
  default boolean shadowComparison() {
    return false;
  }

  /**
   * Directory to write detailed shadow comparison mismatch reports to. When null (default),
   * no report files are written and mismatches are only logged.
   */
  @Nullable
  default Path shadowComparisonReportDirectory() {
    return null;
  }
}
