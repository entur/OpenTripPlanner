package org.opentripplanner.ext.updater.trip.unified.regression;

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
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares what the primary (legacy) adapter did with a trip update against what the shadow
 * (new/unified) adapter did with the same update. Divergences are logged as warnings so that
 * operators can verify the new implementation without any routing impact.
 * <p>
 * The comparison is driven by an {@link AdapterOutcome} per side rather than a nullable record, so
 * that the three ways of not producing a record stay distinguishable: only "both adapters produced
 * the same record" counts as a match, "both rejected the update" is tallied on its own, and an
 * adapter that threw is always reported even when the other adapter produced nothing either.
 * <p>
 * When an output directory is configured, detailed reports (including the input message and per-stop
 * detail for both sides) are written to a file.
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
  private int bothRejected = 0;
  private int rejectedForDifferentReasons = 0;
  private int onlyPrimaryPublished = 0;
  private int onlyShadowPublished = 0;
  private int shadowCrashes = 0;
  private int primaryCrashes = 0;

  public RealTimeTripUpdateComparator(@Nullable Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  /**
   * Compare what the primary (legacy) adapter did with a single trip update against what the shadow
   * (unified) adapter did with the same update.
   *
   * @param primary              what the primary (legacy) adapter did
   * @param shadow               what the shadow (unified) adapter did
   * @param tripId               a human-readable trip identifier for logging
   * @param inputMessageSupplier lazy supplier that serializes the input message; only evaluated
   *                             when a divergence is detected and file output is enabled
   */
  public void compare(
    AdapterOutcome primary,
    AdapterOutcome shadow,
    String tripId,
    Supplier<String> inputMessageSupplier
  ) {
    totalCompared++;

    if (
      primary instanceof AdapterOutcome.Published primaryPublished &&
      shadow instanceof AdapterOutcome.Published shadowPublished
    ) {
      comparePublished(primaryPublished, shadowPublished, tripId, inputMessageSupplier);
      return;
    }

    if (
      primary instanceof AdapterOutcome.Rejected(var primaryReason) &&
      shadow instanceof AdapterOutcome.Rejected(var shadowReason)
    ) {
      compareRejections(primaryReason, shadowReason, primary, shadow, tripId, inputMessageSupplier);
      return;
    }

    reportDivergentOutcomes(primary, shadow, tripId, inputMessageSupplier);
  }

  /**
   * Both adapters produced a record: the records themselves are compared.
   */
  private void comparePublished(
    AdapterOutcome.Published primary,
    AdapterOutcome.Published shadow,
    String tripId,
    Supplier<String> inputMessageSupplier
  ) {
    var primaryEncoding = encode(primary.update());
    var shadowEncoding = encode(shadow.update());

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
      bufferReport(tripId, primary, shadow, inputMessageSupplier, "MISMATCH");
    }
  }

  /**
   * Both adapters rejected the update. Agreeing on the reason is not a match — nothing was compared
   * — so it is tallied separately. Disagreeing on the reason is a divergence: the same input was
   * declined for two different causes, which regularly means one of the two got there by accident.
   */
  private void compareRejections(
    UpdateErrorType primaryReason,
    UpdateErrorType shadowReason,
    AdapterOutcome primary,
    AdapterOutcome shadow,
    String tripId,
    Supplier<String> inputMessageSupplier
  ) {
    if (primaryReason == shadowReason) {
      // Real feeds produce these in bulk (negative hop times, unknown stops), so they are counted
      // but not reported.
      bothRejected++;
      return;
    }
    rejectedForDifferentReasons++;
    LOG.warn(
      "Shadow comparison: both adapters rejected trip {}, but for different reasons: primary {}, shadow {}",
      tripId,
      primaryReason,
      shadowReason
    );
    bufferReport(tripId, primary, shadow, inputMessageSupplier, "REJECTION MISMATCH");
  }

  /**
   * One adapter produced a record the other did not, or an adapter threw. A crash is always counted
   * against the adapter that threw, even when the other adapter also produced nothing.
   */
  private void reportDivergentOutcomes(
    AdapterOutcome primary,
    AdapterOutcome shadow,
    String tripId,
    Supplier<String> inputMessageSupplier
  ) {
    var shadowCrashed = shadow instanceof AdapterOutcome.Crashed;
    var primaryCrashed = primary instanceof AdapterOutcome.Crashed;

    if (shadowCrashed) {
      shadowCrashes++;
    }
    if (primaryCrashed) {
      primaryCrashes++;
    }
    if (!shadowCrashed && !primaryCrashed) {
      if (primary instanceof AdapterOutcome.Published) {
        onlyPrimaryPublished++;
      } else {
        onlyShadowPublished++;
      }
    }

    LOG.warn(
      "Shadow comparison divergence for trip {}: primary {}, shadow {}",
      tripId,
      primary.describe(),
      shadow.describe()
    );
    bufferReport(tripId, primary, shadow, inputMessageSupplier, divergenceLabel(primary, shadow));
  }

  /**
   * The report header label for a divergence, naming the side at fault. A crash takes precedence: it
   * is a defect in that adapter rather than a difference of opinion about the input.
   */
  private static String divergenceLabel(AdapterOutcome primary, AdapterOutcome shadow) {
    if (shadow instanceof AdapterOutcome.Crashed) {
      return "SHADOW ERROR: " + shadow.describe();
    }
    if (primary instanceof AdapterOutcome.Crashed) {
      return "PRIMARY ERROR: " + primary.describe();
    }
    return shadow instanceof AdapterOutcome.Published
      ? "PRIMARY ERROR: " + primary.describe()
      : "SHADOW ERROR: " + shadow.describe();
  }

  /**
   * The comparison tally for the current message batch. Exposed so that callers and tests can
   * assert on the distribution of outcomes instead of parsing the log line.
   *
   * @param total                       the number of trip updates compared
   * @param matched                     both adapters produced the same record
   * @param mismatched                  both produced a record, but they differ
   * @param bothRejected                both rejected the update, for the same reason
   * @param rejectedForDifferentReasons both rejected the update, but for different reasons
   * @param onlyPrimaryPublished        the primary produced a record, the shadow rejected the update
   * @param onlyShadowPublished         the shadow produced a record, the primary rejected the update
   * @param shadowCrashes               the shadow adapter threw
   * @param primaryCrashes              the primary adapter threw
   */
  public record Summary(
    int total,
    int matched,
    int mismatched,
    int bothRejected,
    int rejectedForDifferentReasons,
    int onlyPrimaryPublished,
    int onlyShadowPublished,
    int shadowCrashes,
    int primaryCrashes
  ) {}

  public Summary summary() {
    return new Summary(
      totalCompared,
      matched,
      mismatched,
      bothRejected,
      rejectedForDifferentReasons,
      onlyPrimaryPublished,
      onlyShadowPublished,
      shadowCrashes,
      primaryCrashes
    );
  }

  /**
   * Log a summary of comparison statistics for the current message batch. If any divergences were
   * detected and an output directory is configured, writes the detailed reports to a file.
   */
  public void logSummary() {
    LOG.info(
      "Shadow comparison summary: total={}, matched={}, mismatched={}, bothRejected={}, " +
        "rejectedForDifferentReasons={}, onlyPrimaryPublished={}, onlyShadowPublished={}, " +
        "shadowCrashes={}, primaryCrashes={}",
      totalCompared,
      matched,
      mismatched,
      bothRejected,
      rejectedForDifferentReasons,
      onlyPrimaryPublished,
      onlyShadowPublished,
      shadowCrashes,
      primaryCrashes
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

    var deleteFrom = update.hideTripInScheduledPattern();
    sb.append(" deleteFrom=").append(deleteFrom != null ? deleteFrom.getId() : "null");

    sb.append(" tripCreation=").append(update.tripCreation());
    sb.append(" routeCreation=").append(update.routeCreation());
    sb.append(" producer=").append(update.producer());
    sb.append(" wheelchair=").append(update.updatedTripTimes().getWheelchairAccessibility());
    sb.append(" vehicleId=").append(vehicleId(update.updatedTripTimes()));
    if (update.tripCreation()) {
      sb.append(" ").append(encodeCreatedEntities(update));
    }
    sb.append(" ").append(encodeAddedTrip(update.addedTripOnServiceDate()));

    sb.append(" ");
    sb.append(encodeTripTimes(update.updatedTripTimes(), update.pattern()));

    return sb.toString();
  }

  /**
   * The vehicle id carried by the trip times, or {@code null} for scheduled times that cannot hold
   * one. Only {@link RealTimeTripTimes} tracks a vehicle id.
   */
  @Nullable
  private static String vehicleId(TripTimes tripTimes) {
    if (tripTimes instanceof RealTimeTripTimes realTime) {
      return realTime.getVehicleId().orElse(null);
    }
    return null;
  }

  /**
   * Encode the attributes the created trip and the route it runs on are stamped with. A created trip
   * is the one case where an adapter invents transit entities rather than updating existing ones, so
   * these attributes are an output of its own - the trip id alone does not show that the two
   * adapters named, moded or operated the trip differently.
   * <p>
   * Only encoded for a trip creation: every other update writes times onto a trip and a route that
   * already exist and that neither adapter touches, so encoding it there would only add a constant
   * to the comparison of the ordinary updates. The route is taken from the trip rather than from the
   * pattern, because the trip's route is the one that was resolved or created for it - a pattern
   * shared with an earlier added trip carries that trip's route.
   */
  private static String encodeCreatedEntities(RealTimeTripUpdate update) {
    var trip = update.updatedTripTimes().getTrip();
    var route = trip.getRoute();
    return (
      "tripAttrs=[shortName=" +
      trip.getShortName() +
      " operator=" +
      operatorId(trip.getOperator()) +
      " mode=" +
      trip.getMode() +
      " submode=" +
      trip.getNetexSubMode() +
      " headsign=" +
      trip.getHeadsign() +
      "] routeAttrs=[shortName=" +
      route.getShortName() +
      " operator=" +
      operatorId(route.getOperator()) +
      " mode=" +
      route.getMode() +
      " submode=" +
      route.getNetexSubmode() +
      "]"
    );
  }

  @Nullable
  private static FeedScopedId operatorId(@Nullable Operator operator) {
    return operator != null ? operator.getId() : null;
  }

  /**
   * Encode the added/replacement trip metadata (alteration and the list of trips it replaces) so
   * that divergences in this data are caught, not only the {@code tripCreation} boolean.
   */
  private static String encodeAddedTrip(@Nullable TripOnServiceDate addedTrip) {
    if (addedTrip == null) {
      return "addedTrip=null";
    }
    var replacementFor = addedTrip
      .getReplacementFor()
      .stream()
      .map(r -> r.getTrip().getId().toString())
      .sorted()
      .collect(Collectors.joining(",", "[", "]"));
    return (
      "addedTrip=" +
      addedTrip.getTrip().getId() +
      // The id of the trip on service date is not the id of the trip: SIRI identifies it by the
      // DatedServiceJourney of the message, and it is the key the trip is indexed under.
      " id=" +
      addedTrip.getId() +
      " alteration=" +
      addedTrip.getTripAlteration() +
      " replacementFor=" +
      replacementFor
    );
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

    var deleteFrom = update.hideTripInScheduledPattern();
    sb
      .append("  deleteFrom   : ")
      .append(deleteFrom != null ? deleteFrom.getId() : "null")
      .append('\n');

    sb.append("  tripCreation : ").append(update.tripCreation()).append('\n');
    if (update.tripCreation()) {
      sb.append("  created      : ").append(encodeCreatedEntities(update)).append('\n');
    }
    sb.append("  routeCreation: ").append(update.routeCreation()).append('\n');
    sb.append("  producer     : ").append(update.producer()).append('\n');
    sb.append("  realTimeState: ").append(summarizeTripTimesState(tripTimes)).append('\n');
    sb.append("  wheelchair   : ").append(tripTimes.getWheelchairAccessibility()).append('\n');
    sb.append("  vehicleId    : ").append(vehicleId(tripTimes)).append('\n');

    var addedTrip = update.addedTripOnServiceDate();
    if (addedTrip != null) {
      sb
        .append("  addedTripOnServiceDate: ")
        .append(addedTrip.getTrip().getId())
        .append(" id=")
        .append(addedTrip.getId())
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
      if (tripTimes.isCanceledStop(i)) {
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
        .append(" (")
        .append(stops.get(i).getId())
        .append(", ")
        .append(pattern.getBoardType(i))
        .append("/")
        .append(pattern.getAlightType(i))
        .append(")")
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
    AdapterOutcome primary,
    AdapterOutcome shadow,
    Supplier<String> inputMessageSupplier,
    String reason
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

    if (!(primary instanceof AdapterOutcome.Published)) {
      sb.append("\n--- PRIMARY FAILURE REASON ---\n");
      sb.append("  ").append(primary.describe()).append('\n');
    }

    if (!(shadow instanceof AdapterOutcome.Published)) {
      sb.append("\n--- SHADOW FAILURE REASON ---\n");
      sb.append("  ").append(shadow.describe()).append('\n');
    }

    sb.append("\n--- INPUT MESSAGE ---\n");
    try {
      sb.append(inputMessageSupplier.get()).append('\n');
    } catch (Exception e) {
      sb.append("<error serializing input: ").append(e.getMessage()).append(">\n");
    }

    sb.append("\n--- PRIMARY RealTimeTripUpdate ---\n");
    appendOutcomeDetail(sb, primary);

    sb.append("\n--- SHADOW RealTimeTripUpdate ---\n");
    appendOutcomeDetail(sb, shadow);

    sb.append('\n');
    mismatchReports.add(sb.toString());
  }

  private static void appendOutcomeDetail(StringBuilder sb, AdapterOutcome outcome) {
    switch (outcome) {
      case AdapterOutcome.Published(var update) -> sb.append(encodeDetailed(update));
      case AdapterOutcome.Rejected(var reason) -> sb
        .append("  (rejected: ")
        .append(reason)
        .append(")\n");
      case AdapterOutcome.Crashed(var detail) -> sb
        .append("  (crashed: ")
        .append(detail)
        .append(")\n");
    }
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

  /**
   * A one-letter code for a pickup or dropoff value, so that adding it for every stop keeps the
   * comparison string readable on long patterns: (S)cheduled, (N)one, call (A)gency,
   * (C)oordinate with driver, cancelled (X).
   */
  private static char pickDropCode(PickDrop pickDrop) {
    return switch (pickDrop) {
      case SCHEDULED -> 'S';
      case NONE -> 'N';
      case CALL_AGENCY -> 'A';
      case COORDINATE_WITH_DRIVER -> 'C';
      case CANCELLED -> 'X';
    };
  }

  /**
   * Encode trip times and stop information as a compact string for comparison.
   * Format: "STATE_FLAGS | stopId boardAlight [FLAGS] arrivalTime departureTime | ..."
   * <p>
   * The stop <em>id</em> and the pickup/dropoff pair are part of the comparison because both are
   * load-bearing outputs the two adapters can diverge on: a stop substitution or a pick/drop
   * difference yields a different {@link org.opentripplanner.transit.model.network.StopPattern},
   * hence a different real-time pattern, while the stop names stay the same. Names carry no
   * information the id does not, so they are reserved for {@link #encodeDetailed}.
   */
  private static String encodeTripTimes(TripTimes tripTimes, TripPattern pattern) {
    var stops = pattern.getStops();
    if (tripTimes.getNumStops() != stops.size()) {
      throw new IllegalArgumentException(
        "TripTimes and TripPattern have different number of stops"
      );
    }
    var s = new StringBuilder(summarizeTripTimesState(tripTimes));
    for (int i = 0; i < tripTimes.getNumStops(); i++) {
      var depart = tripTimes.getDepartureTime(i);
      var arrive = tripTimes.getArrivalTime(i);
      var flags = new ArrayList<String>();
      if (tripTimes.isCanceledStop(i)) {
        flags.add("C");
      }
      if (tripTimes.hasArrived(i)) {
        if (tripTimes.hasDeparted(i)) {
          flags.add("R");
        } else {
          flags.add("A");
        }
      }
      if (tripTimes.isPredictionInaccurate(i)) {
        flags.add("PI");
      }
      if (tripTimes.isNoDataStop(i)) {
        flags.add("ND");
      }
      if (tripTimes.isExtraCall(i)) {
        flags.add("EC");
      }
      s.append(" | ").append(stops.get(i).getId());
      s
        .append(" ")
        .append(pickDropCode(pattern.getBoardType(i)))
        .append(pickDropCode(pattern.getAlightType(i)));
      if (!flags.isEmpty()) {
        s.append(" [").append(String.join(",", flags)).append("]");
      }
      s
        .append(" ")
        .append(TimeUtils.timeToStrCompact(arrive))
        .append(" ")
        .append(TimeUtils.timeToStrCompact(depart));

      var occupancy = tripTimes.getOccupancyStatus(i);
      if (occupancy != null) {
        s.append(" occ=").append(occupancy);
      }
      var headsign = tripTimes.getHeadsign(i);
      if (headsign != null) {
        s.append(" hs=").append(headsign);
      }
    }
    return s.toString();
  }

  /**
   * Summarize the real-time state of a trip as a short string.
   * Mirrors TripTimesStateDecoder (test utility) but lives in main source.
   */
  private static String summarizeTripTimesState(TripTimes tripTimes) {
    var sb = new StringBuilder();
    if (tripTimes.isAdded()) {
      sb.append("A ");
    }
    if (tripTimes.isCanceled()) {
      sb.append("C ");
    }
    if (tripTimes.isTripPatternModified()) {
      sb.append("P ");
    }
    if (tripTimes.isDeleted()) {
      sb.append("D ");
    }
    if (tripTimes.hasAnyUpdates()) {
      sb.append("U ");
    } else {
      sb.append("S ");
    }
    return sb.toString().trim();
  }
}
