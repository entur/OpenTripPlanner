package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ExistingTripCommand;
import org.opentripplanner.updater.trip.model.ModifyTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ReviseTrip;
import org.opentripplanner.updater.trip.model.TripModification;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the fully specified change for an {@link ExistingTripCommand} by resolving it against
 * the transit model: UPDATE_EXISTING ({@link ReviseTrip}) becomes a {@link TripRevision},
 * MODIFY_TRIP ({@link ModifyTrip}) becomes a {@link TripModification}.
 * <p>
 * Resolution includes:
 * <ul>
 *   <li>Service date (from explicit date, TripOnServiceDate, or aimed departure)</li>
 *   <li>Trip (from trip ID or TripOnServiceDate)</li>
 *   <li>Pattern (the pattern containing the trip)</li>
 *   <li>Scheduled pattern (original if pattern is modified)</li>
 *   <li>Trip times (from scheduled timetable)</li>
 * </ul>
 * The two update types share all of that, but not their preconditions. Each change validates
 * itself on construction, so the factory also rejects a message that cannot describe the
 * operation it was asked for. The entry point the caller picks - not a runtime check - decides
 * which invariants apply.
 */
public class ExistingTripChangeFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ExistingTripChangeFactory.class);

  private final TransitEditorService transitService;
  private final TripResolver tripResolver;
  private final ServiceDateResolver serviceDateResolver;
  private final StopResolver stopResolver;

  private final FuzzyTripMatcher fuzzyTripMatcher;

  private final ZoneId timeZone;

  public ExistingTripChangeFactory(
    TransitEditorService transitService,
    TripResolver tripResolver,
    ServiceDateResolver serviceDateResolver,
    StopResolver stopResolver,
    FuzzyTripMatcher fuzzyTripMatcher,
    ZoneId timeZone
  ) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.tripResolver = Objects.requireNonNull(tripResolver, "tripResolver must not be null");
    this.serviceDateResolver = Objects.requireNonNull(
      serviceDateResolver,
      "serviceDateResolver must not be null"
    );
    this.stopResolver = Objects.requireNonNull(stopResolver, "stopResolver must not be null");
    this.fuzzyTripMatcher = Objects.requireNonNull(
      fuzzyTripMatcher,
      "fuzzyTripMatcher must not be null"
    );
    this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
  }

  /**
   * Create the revision of the real-time times of an existing scheduled trip, for the
   * {@link TripReviser}.
   *
   * @throws UpdateException if resolution fails or the update violates the preconditions of an
   *                         update to an existing trip
   */
  public TripRevision create(ReviseTrip command) {
    var resolution = doResolve(command);
    return new TripRevision(
      command,
      resolution.serviceDate(),
      resolution.trip(),
      resolution.pattern(),
      resolution.scheduledPattern(),
      resolution.scheduledTripTimes(),
      resolution.stopTimeUpdates()
    );
  }

  /**
   * Create the modification of the stop pattern of an existing trip, for the {@link TripModifier}.
   *
   * @throws UpdateException if resolution fails or the update violates the preconditions of a
   *                         trip modification
   */
  public TripModification create(ModifyTrip command) {
    var resolution = doResolve(command);
    return new TripModification(
      command,
      resolution.serviceDate(),
      resolution.trip(),
      resolution.scheduledPattern(),
      // The scheduled times of the trip already run on the calendar of the trip's service id, so
      // this is the same code as a lookup by service id - without the nullable Integer that
      // TripCalendars returns for an unregistered service.
      resolution.scheduledTripTimes().getServiceCode(),
      resolution.stopTimeUpdates()
    );
  }

  /**
   * Resolve everything the two update types resolve the same way, before each is turned into the
   * change of its own use case and validated against its invariants.
   *
   * @throws UpdateException if resolution fails
   */
  private ExistingTripResolution doResolve(ExistingTripCommand command) {
    // Resolve service date
    LocalDate serviceDate = serviceDateResolver.resolveServiceDate(command);

    var tripReference = command.tripReference();

    // Resolve trip and pattern
    TripAndPattern tripAndPattern;
    try {
      tripAndPattern = resolveTripWithPattern(command, serviceDate);
    } catch (UpdateException e) {
      LOG.debug("Could not resolve trip for update: {}", tripReference);
      throw e;
    }

    Trip trip = tripAndPattern.trip();
    TripPattern pattern = tripAndPattern.tripPattern();

    // Validate service date is valid for this trip
    validateServiceDate(trip, serviceDate);

    // Get the scheduled pattern. When the pattern is modified (e.g. a cancelled stop created a
    // new RT pattern), look up the trip's own scheduled pattern from the index rather than
    // trusting getOriginalTripPattern(). The TripPatternCache can share RT patterns across
    // trips from different routes when the modified StopPattern happens to match, causing
    // getOriginalTripPattern() to return the wrong pattern.
    TripPattern scheduledPattern = pattern.isModified()
      ? transitService.findPattern(trip)
      : pattern;

    // Get trip times from scheduled timetable
    TripTimes tripTimes = scheduledPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn(
        "No trip times found for trip {} in pattern {}",
        trip.getId(),
        scheduledPattern.getId()
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    }

    // Resolve stop time updates now that service date is known
    var resolvedStopTimeUpdates = ResolvedStopTimeUpdate.resolveAll(
      command.stopTimeUpdates(),
      serviceDate,
      timeZone,
      stopResolver
    );

    return new ExistingTripResolution(
      serviceDate,
      trip,
      pattern,
      scheduledPattern,
      tripTimes,
      resolvedStopTimeUpdates
    );
  }

  /** Everything the two existing-trip update types resolve the same way. */
  private record ExistingTripResolution(
    LocalDate serviceDate,
    Trip trip,
    TripPattern pattern,
    TripPattern scheduledPattern,
    TripTimes scheduledTripTimes,
    List<ResolvedStopTimeUpdate> stopTimeUpdates
  ) {}

  /**
   * Resolve a Trip and its TripPattern from a TripUpdateCommand.
   * Supports both exact matching and fuzzy matching (if configured).
   */
  private TripAndPattern resolveTripWithPattern(
    ExistingTripCommand command,
    LocalDate serviceDate
  ) {
    TripReference reference = command.tripReference();

    // Try exact match first
    try {
      Trip trip = tripResolver.resolveTrip(reference);
      TripPattern pattern = transitService.findPattern(trip, serviceDate);
      if (pattern == null) {
        pattern = transitService.findPattern(trip);
      }
      if (pattern != null) {
        return new TripAndPattern(trip, pattern);
      }
      LOG.warn("Trip {} found but no pattern available", trip.getId());
      throw UpdateException.of(reference.tripId(), UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN);
    } catch (UpdateException exactMatchException) {
      // Exact match failed - try fuzzy matching if enabled
      if (fuzzyTripMatcher.isEnabled()) {
        LOG.debug("Exact match failed for {}, trying fuzzy matching", reference);
        return fuzzyTripMatcher.match(reference, command, serviceDate);
      }

      // Return the original exact match error
      throw exactMatchException;
    }
  }

  /**
   * Validate that the service date is valid for the trip's service.
   */
  private void validateServiceDate(Trip trip, LocalDate serviceDate) {
    var serviceId = trip.getServiceId();
    var serviceDates = transitService.getTripCalendars().listServiceDates(serviceId);
    if (!serviceDates.contains(serviceDate)) {
      LOG.debug(
        "Trip {} has service date {} for which trip's service is not valid, skipping.",
        trip.getId(),
        serviceDate
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.NO_SERVICE_ON_DATE);
    }
  }
}
