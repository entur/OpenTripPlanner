package org.opentripplanner.updater.trip.gtfs.updater.http;

import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.HttpHeaders;
import org.opentripplanner.updater.spi.PollingGraphUpdaterParameters;
import org.opentripplanner.updater.trip.UrlUpdaterParameters;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;

public record PollingTripUpdaterParameters(
  String configRef,
  Duration frequency,
  boolean fuzzyTripMatching,
  ForwardsDelayPropagationType forwardsDelayPropagationType,
  BackwardsDelayPropagationType backwardsDelayPropagationType,
  String feedId,
  String url,
  HttpHeaders headers,
  boolean useNewUpdaterImplementation,
  boolean shadowComparison,
  @Nullable Path shadowComparisonReportDirectory
) implements PollingGraphUpdaterParameters, UrlUpdaterParameters {}
