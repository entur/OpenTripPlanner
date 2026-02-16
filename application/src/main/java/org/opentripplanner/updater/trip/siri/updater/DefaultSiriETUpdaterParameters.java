package org.opentripplanner.updater.trip.siri.updater;

import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;
import org.opentripplanner.updater.spi.HttpHeaders;

public record DefaultSiriETUpdaterParameters(
  String configRef,
  String feedId,
  boolean blockReadinessUntilInitialized,
  String url,
  Duration frequency,
  String requestorRef,
  Duration timeout,
  Duration previewInterval,
  boolean fuzzyTripMatching,
  HttpHeaders httpRequestHeaders,
  boolean producerMetrics,
  boolean useNewUpdaterImplementation,
  boolean shadowComparison,
  @Nullable Path shadowComparisonReportDirectory
) implements SiriETUpdaterParameters, SiriETHttpTripUpdateSource.Parameters {}
