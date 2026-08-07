package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * The parameters selecting which trip update adapter a trip updater runs: the legacy adapter
 * (the default), the new unified adapter, or the shadow-comparison adapter running both in
 * parallel. Every trip updater type shares this block, so it is parsed - and documented - in one
 * place.
 */
public class TripUpdateAdapterSelectionConfig {

  public static Selection create(NodeAdapter c) {
    var selection = new Selection(
      c
        .of("useNewUpdaterImplementation")
        .since(V2_9)
        .summary("Use the new unified trip update implementation.")
        .description(
          """
          When `true`, trip updates are applied through the new format-independent implementation
          shared by SIRI-ET and GTFS-RT. This is experimental and should be used with caution.
          When `false` (the default), the legacy implementation for this updater's format is used.
          Mutually exclusive with `shadowComparison`.
          """
        )
        .asBoolean(false),
      c
        .of("shadowComparison")
        .since(V2_9)
        .summary(
          "Run the legacy and unified trip update implementations in parallel, comparing their outputs."
        )
        .description(
          """
          The legacy implementation stays in charge and writes to the timetable snapshot; the
          unified implementation runs read-only in its shadow, and mismatches between the two are
          logged as warnings. Mutually exclusive with `useNewUpdaterImplementation` - shadow
          comparison always serves the legacy implementation.
          """
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
    if (selection.useNewUpdaterImplementation() && selection.shadowComparison()) {
      throw c.createException(
        "The parameters 'useNewUpdaterImplementation' and 'shadowComparison' are mutually " +
          "exclusive - shadow comparison always serves the legacy implementation, so an explicit " +
          "request to serve the unified implementation cannot be honored. Set only one of them.",
        "shadowComparison"
      );
    }
    return selection;
  }

  private static Path optionalPath(@Nullable String value) {
    return value != null ? Path.of(value) : null;
  }

  public record Selection(
    boolean useNewUpdaterImplementation,
    boolean shadowComparison,
    @Nullable Path shadowComparisonReportDirectory
  ) {}
}
