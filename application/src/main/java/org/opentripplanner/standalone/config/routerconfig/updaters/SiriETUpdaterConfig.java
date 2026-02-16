package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_0;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_3;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_7;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import java.nio.file.Path;
import java.time.Duration;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.updater.trip.siri.updater.DefaultSiriETUpdaterParameters;

public class SiriETUpdaterConfig {

  public static DefaultSiriETUpdaterParameters create(String configRef, NodeAdapter c) {
    return new DefaultSiriETUpdaterParameters(
      configRef,
      c.of("feedId").since(V2_0).summary("The ID of the feed to apply the updates to.").asString(),
      c
        .of("blockReadinessUntilInitialized")
        .since(V2_0)
        .summary(
          "Whether catching up with the updates should block the readiness check from returning a 'ready' result."
        )
        .asBoolean(false),
      c
        .of("url")
        .since(V2_0)
        .summary("The URL to send the HTTP requests to.")
        .description(SiriSXUpdaterConfig.URL_DESCRIPTION)
        .asString(),
      c
        .of("frequency")
        .since(V2_0)
        .summary("How often the updates should be retrieved.")
        .asDuration(Duration.ofMinutes(1)),
      c.of("requestorRef").since(V2_0).summary("The requester reference.").asString(null),
      c
        .of("timeout")
        .since(V2_0)
        .summary("The HTTP timeout to download the updates.")
        .asDuration(Duration.ofSeconds(15)),
      c.of("previewInterval").since(V2_0).summary("TODO").asDuration(null),
      c
        .of("fuzzyTripMatching")
        .since(V2_0)
        .summary("If the fuzzy trip matcher should be used to match trips.")
        .asBoolean(false),
      HttpHeadersConfig.headers(c, V2_3),
      c
        .of("producerMetrics")
        .since(V2_7)
        .summary("If failure, success, and warning metrics should be collected per producer.")
        .asBoolean(false),
      c
        .of("useNewUpdaterImplementation")
        .since(V2_9)
        .summary(
          "Use the new unified trip update implementation. " +
            "When true, uses the new DefaultTripUpdateApplier with common handlers. " +
            "When false (default), uses the legacy SiriRealTimeTripUpdateAdapter."
        )
        .asBoolean(false),
      c
        .of("shadowComparison")
        .since(V2_9)
        .summary(
          "Run the legacy and unified adapters in parallel, comparing their outputs. " +
            "The legacy adapter writes to the snapshot; the unified adapter is shadow (read-only). " +
            "Mismatches are logged as warnings."
        )
        .asBoolean(false),
      optionalPath(
        c
          .of("shadowComparisonReportDirectory")
          .since(V2_9)
          .summary("Directory to write detailed shadow comparison mismatch reports to.")
          .asString(null)
      )
    );
  }

  private static Path optionalPath(String value) {
    return value != null ? Path.of(value) : null;
  }
}
