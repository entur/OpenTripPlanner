package org.opentripplanner.updater.trip.regression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.TripTimesStringBuilder;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares {@link RealTimeTripUpdate} records produced by the primary (legacy) adapter against
 * those produced by the shadow (new/unified) adapter. Mismatches are logged as warnings so that
 * operators can verify the new implementation without any routing impact.
 * <p>
 * When an output directory is configured, detailed mismatch reports (including the input message
 * and per-stop detail for both primary and shadow records) are written to a file.
 * <p>
 * Used exclusively by the shadow comparison mode.
 */
public class RealTimeTripUpdateComparator {

  private static final Logger LOG = LoggerFactory.getLogger(RealTimeTripUpdateComparator.class);
  private static final String SEPARATOR = "==========================================";

  @Nullable
  private final Path outputDirectory;

  private final List<String> mismatchReports = new ArrayList<>();

  private int totalCompared = 0;
  private int matched = 0;
  private int mismatched = 0;
  private int shadowErrors = 0;
  private int primaryErrors = 0;

  public RealTimeTripUpdateComparator() {
    this(null);
  }

  public RealTimeTripUpdateComparator(@Nullable Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  /**
   * Compare a primary record against a shadow record for the same trip.
   *
   * @param primary              the record produced by the primary (legacy) adapter, or null if
   *                             the primary produced an error
   * @param shadow               the record produced by the shadow (new) adapter, or null if the
   *                             shadow produced an error
   * @param tripId               a human-readable trip identifier for logging
   * @param inputMessageSupplier lazy supplier that serializes the input message; only evaluated
   *                             when a mismatch is detected and file output is enabled
   */
  public void compare(
    @Nullable RealTimeTripUpdate primary,
    @Nullable RealTimeTripUpdate shadow,
    String tripId,
    Supplier<String> inputMessageSupplier
  ) {
    compare(primary, shadow, tripId, inputMessageSupplier, null, null);
  }

  /**
   * Compare a primary record against a shadow record for the same trip, with optional failure
   * reasons for diagnostics.
   *
   * @param primary              the record produced by the primary (legacy) adapter, or null if
   *                             the primary produced an error
   * @param shadow               the record produced by the shadow (new) adapter, or null if the
   *                             shadow produced an error
   * @param tripId               a human-readable trip identifier for logging
   * @param inputMessageSupplier lazy supplier that serializes the input message; only evaluated
   *                             when a mismatch is detected and file output is enabled
   * @param primaryFailureReason the reason the primary adapter failed, or null if it succeeded
   * @param shadowFailureReason  the reason the shadow adapter failed, or null if it succeeded
   */
  public void compare(
    @Nullable RealTimeTripUpdate primary,
    @Nullable RealTimeTripUpdate shadow,
    String tripId,
    Supplier<String> inputMessageSupplier,
    @Nullable String primaryFailureReason,
    @Nullable String shadowFailureReason
  ) {
    totalCompared++;

    if (primary == null && shadow == null) {
      matched++;
      return;
    }

    if (primary == null) {
      primaryErrors++;
      LOG.warn(
        "Shadow comparison: primary error but shadow succeeded for trip {}: {}",
        tripId,
        primaryFailureReason != null ? primaryFailureReason : "(unknown)"
      );
      bufferReport(
        tripId,
        null,
        shadow,
        inputMessageSupplier,
        primaryFailureReason != null
          ? "PRIMARY ERROR: " + primaryFailureReason
          : "PRIMARY ERROR (null)",
        primaryFailureReason,
        shadowFailureReason
      );
      return;
    }

    if (shadow == null) {
      shadowErrors++;
      LOG.warn(
        "Shadow comparison: shadow error but primary succeeded for trip {}: {}",
        tripId,
        shadowFailureReason != null ? shadowFailureReason : "(unknown)"
      );
      bufferReport(
        tripId,
        primary,
        null,
        inputMessageSupplier,
        shadowFailureReason != null
          ? "SHADOW ERROR: " + shadowFailureReason
          : "SHADOW ERROR (null)",
        primaryFailureReason,
        shadowFailureReason
      );
      return;
    }

    var primaryEncoding = encode(primary);
    var shadowEncoding = encode(shadow);

    if (primaryEncoding.equals(shadowEncoding)) {
      matched++;
    } else {
      mismatched++;
      LOG.warn(
        "Shadow comparison MISMATCH for trip {}:\n  PRIMARY: {}\n  SHADOW:  {}",
        tripId,
        primaryEncoding,
        shadowEncoding
      );
      bufferReport(tripId, primary, shadow, inputMessageSupplier, "MISMATCH", null, null);
    }
  }

  /**
   * Log a summary of comparison statistics for the current message batch. If any mismatches were
   * detected and an output directory is configured, writes the detailed reports to a file.
   */
  public void logSummary() {
    LOG.info(
      "Shadow comparison summary: total={}, matched={}, mismatched={}, shadowErrors={}, primaryErrors={}",
      totalCompared,
      matched,
      mismatched,
      shadowErrors,
      primaryErrors
    );

    if (!mismatchReports.isEmpty() && outputDirectory != null) {
      writeReportsToFile();
    }
  }

  /**
   * Encode a {@link RealTimeTripUpdate} as a deterministic string for comparison.
   */
  static String encode(RealTimeTripUpdate update) {
    var sb = new StringBuilder();

    var tripId = update.updatedTripTimes().getTrip().getId();
    sb.append("trip=").append(tripId);
    sb.append(" serviceDate=").append(update.serviceDate());
    sb.append(" pattern=").append(normalizePatternId(update.pattern().getId()));
    sb.append(" revert=").append(update.revertPreviousRealTimeUpdates());

    var deleteFrom = update.scheduledPatternToDeleteFrom();
    sb.append(" deleteFrom=").append(deleteFrom != null ? deleteFrom.getId() : "null");

    sb.append(" tripCreation=").append(update.tripCreation());
    sb.append(" routeCreation=").append(update.routeCreation());
    sb.append(" producer=").append(update.producer());

    sb.append(" ");
    sb.append(TripTimesStringBuilder.encodeTripTimes(update.updatedTripTimes(), update.pattern()));

    return sb.toString();
  }

  /**
   * Normalize RT pattern IDs by replacing the sequence counter with a placeholder.
   * RT patterns have format "{routeId}:{direction}:{counter}:RT" where the counter
   * is a meaningless sequence number that differs between primary and shadow adapters.
   */
  static FeedScopedId normalizePatternId(FeedScopedId patternId) {
    var id = patternId.getId();
    if (id.endsWith(":RT")) {
      var normalized = id.replaceFirst(":\\d+:RT$", ":NNN:RT");
      return new FeedScopedId(patternId.getFeedId(), normalized);
    }
    return patternId;
  }

  /**
   * Encode a {@link RealTimeTripUpdate} with full per-stop detail for mismatch reports.
   */
  static String encodeDetailed(RealTimeTripUpdate update) {
    var sb = new StringBuilder();
    var tripTimes = update.updatedTripTimes();
    var pattern = update.pattern();
    var route = pattern.getRoute();
    var stops = pattern.getStops();

    sb.append("  trip         : ").append(tripTimes.getTrip().getId()).append('\n');
    sb.append("  serviceDate  : ").append(update.serviceDate()).append('\n');
    sb
      .append("  pattern      : ")
      .append(pattern.getId())
      .append(" (route=")
      .append(route.getId())
      .append(", mode=")
      .append(pattern.getMode())
      .append(")")
      .append('\n');
    sb.append("  revert       : ").append(update.revertPreviousRealTimeUpdates()).append('\n');

    var deleteFrom = update.scheduledPatternToDeleteFrom();
    sb
      .append("  deleteFrom   : ")
      .append(deleteFrom != null ? deleteFrom.getId() : "null")
      .append('\n');

    sb.append("  tripCreation : ").append(update.tripCreation()).append('\n');
    sb.append("  routeCreation: ").append(update.routeCreation()).append('\n');
    sb.append("  producer     : ").append(update.producer()).append('\n');
    sb.append("  realTimeState: ").append(tripTimes.getRealTimeState()).append('\n');
    sb.append("  wheelchair   : ").append(tripTimes.getWheelchairAccessibility()).append('\n');

    var addedTrip = update.addedTripOnServiceDate();
    if (addedTrip != null) {
      sb
        .append("  addedTripOnServiceDate: ")
        .append(addedTrip.getTrip().getId())
        .append(" alteration=")
        .append(addedTrip.getTripAlteration())
        .append(" replacementFor=")
        .append(
          addedTrip
            .getReplacementFor()
            .stream()
            .map(r -> r.getTrip().getId().toString())
            .collect(Collectors.joining(", ", "[", "]"))
        )
        .append('\n');
    } else {
      sb.append("  addedTripOnServiceDate: null\n");
    }

    sb.append("  stops:\n");
    for (int i = 0; i < tripTimes.getNumStops(); i++) {
      var stopName = stops.get(i).getName();
      var schedArr = TimeUtils.timeToStrCompact(tripTimes.getScheduledArrivalTime(i));
      var schedDep = TimeUtils.timeToStrCompact(tripTimes.getScheduledDepartureTime(i));
      var rtArr = TimeUtils.timeToStrCompact(tripTimes.getArrivalTime(i));
      var rtDep = TimeUtils.timeToStrCompact(tripTimes.getDepartureTime(i));
      var delayArr = tripTimes.getArrivalDelay(i);
      var delayDep = tripTimes.getDepartureDelay(i);

      var flags = new ArrayList<String>();
      if (tripTimes.isCancelledStop(i)) {
        flags.add("C");
      }
      if (tripTimes.hasArrived(i)) {
        flags.add("A");
      }
      if (tripTimes.hasDeparted(i)) {
        flags.add("D");
      }
      if (tripTimes.isNoDataStop(i)) {
        flags.add("ND");
      }
      if (tripTimes.isPredictionInaccurate(i)) {
        flags.add("PI");
      }
      if (tripTimes.isExtraCall(i)) {
        flags.add("EC");
      }

      var occupancy = tripTimes.getOccupancyStatus(i);
      var headsign = tripTimes.getHeadsign(i);

      sb
        .append("    #")
        .append(i)
        .append(" ")
        .append(stopName)
        .append("  sched ")
        .append(schedArr)
        .append("/")
        .append(schedDep)
        .append("  rt ")
        .append(rtArr)
        .append("/")
        .append(rtDep)
        .append("  delay ")
        .append(formatDelay(delayArr))
        .append("/")
        .append(formatDelay(delayDep));

      if (!flags.isEmpty()) {
        sb.append("  [").append(String.join(",", flags)).append("]");
      }
      if (occupancy != null) {
        sb.append("  occ=").append(occupancy);
      }
      if (headsign != null) {
        sb.append("  hs=").append(headsign);
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  private void bufferReport(
    String tripId,
    @Nullable RealTimeTripUpdate primary,
    @Nullable RealTimeTripUpdate shadow,
    Supplier<String> inputMessageSupplier,
    String reason,
    @Nullable String primaryFailureReason,
    @Nullable String shadowFailureReason
  ) {
    if (outputDirectory == null) {
      return;
    }

    var sb = new StringBuilder();
    sb.append(SEPARATOR).append('\n');
    sb.append("SHADOW ").append(reason).append(" REPORT\n");
    sb.append(SEPARATOR).append('\n');
    sb.append("Timestamp : ").append(Instant.now()).append('\n');
    sb.append("Trip ID   : ").append(tripId).append('\n');

    if (primaryFailureReason != null) {
      sb.append("\n--- PRIMARY FAILURE REASON ---\n");
      sb.append("  ").append(primaryFailureReason).append('\n');
    }

    if (shadowFailureReason != null) {
      sb.append("\n--- SHADOW FAILURE REASON ---\n");
      sb.append("  ").append(shadowFailureReason).append('\n');
    }

    sb.append("\n--- INPUT MESSAGE ---\n");
    try {
      sb.append(inputMessageSupplier.get()).append('\n');
    } catch (Exception e) {
      sb.append("<error serializing input: ").append(e.getMessage()).append(">\n");
    }

    sb.append("\n--- PRIMARY RealTimeTripUpdate ---\n");
    if (primary != null) {
      sb.append(encodeDetailed(primary));
    } else {
      sb.append("  (null)\n");
    }

    sb.append("\n--- SHADOW RealTimeTripUpdate ---\n");
    if (shadow != null) {
      sb.append(encodeDetailed(shadow));
    } else {
      sb.append("  (null)\n");
    }

    sb.append('\n');
    mismatchReports.add(sb.toString());
  }

  private void writeReportsToFile() {
    var timestamp = Instant.now().toString().replace(":", "-");
    var fileName = "shadow-mismatch-" + timestamp + ".txt";
    var filePath = outputDirectory.resolve(fileName);

    try {
      Files.createDirectories(outputDirectory);
      Files.writeString(
        filePath,
        String.join("", mismatchReports),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE
      );
      LOG.info(
        "Shadow comparison: wrote {} mismatch reports to {}",
        mismatchReports.size(),
        filePath
      );
    } catch (IOException e) {
      LOG.error("Shadow comparison: failed to write mismatch report to {}", filePath, e);
    }
  }

  private static String formatDelay(int delaySeconds) {
    if (delaySeconds >= 0) {
      return "+" + delaySeconds;
    }
    return String.valueOf(delaySeconds);
  }
}
