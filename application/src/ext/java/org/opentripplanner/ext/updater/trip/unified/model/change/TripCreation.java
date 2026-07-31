package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripBuilder;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * The creation of a brand new trip that does not exist in the transit model,
 * neither in the scheduled data nor as a previously added real-time trip.
 * <p>
 * The creation applies itself through {@link #apply}: the trip, its pattern and its
 * real-time times are all built from the resolved state.
 * {@link org.opentripplanner.ext.updater.trip.unified.service.TripCreator} drives it.
 */
public final class TripCreation extends TripAddition {

  /** The service id valid for the created trip's service date. */
  private final FeedScopedId serviceId;

  /** The service code corresponding to {@link #serviceId}. */
  private final int serviceCode;

  /** The route the created trip runs on - found in the transit model or created for this trip. */
  private final Route route;

  /**
   * Whether {@link #route} must be registered with the transit model as part of this update -
   * because it was created for this trip, or (GTFS-RT) because a full-dataset batch re-registers
   * every route it references.
   */
  private final boolean isNewRoute;

  /** The dated trips the created trip replaces. References to unknown trips are dropped. */
  private final List<TripOnServiceDate> replacedTrips;

  /**
   * The id identifying the added trip on its service date. SIRI names the dated instance of a
   * journey separately - the DatedServiceJourney - and that id identifies the added trip on
   * service date. GTFS-RT names no such entity, so the added trip on service date takes the trip
   * id instead: an added trip is held once per id (realTimeAddedTrips), and a repeat of that id
   * revises the same trip rather than adding a second service date, so the two can never collide.
   */
  private final FeedScopedId tripOnServiceDateId;

  @Nullable
  private final TransitMode mode;

  @Nullable
  private final String submode;

  @Nullable
  private final String shortName;

  public TripCreation(
    AddTrip command,
    LocalDate serviceDate,
    List<ResolvedStopTimeUpdate> resolvedStopTimeUpdates,
    FeedScopedId serviceId,
    int serviceCode,
    Route route,
    boolean isNewRoute,
    List<TripOnServiceDate> replacedTrips
  ) {
    super(command, serviceDate, resolvedStopTimeUpdates);
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.serviceCode = serviceCode;
    this.route = Objects.requireNonNull(route, "route must not be null");
    this.isNewRoute = isNewRoute;
    this.replacedTrips = List.copyOf(replacedTrips);
    var creationInfo = command.tripCreationInfo();
    this.tripOnServiceDateId = creationInfo.tripOnServiceDateId() != null
      ? creationInfo.tripOnServiceDateId()
      : creationInfo.tripId();
    this.mode = creationInfo.mode();
    this.submode = creationInfo.submode();
    this.shortName = creationInfo.shortName();
    validate();
  }

  /**
   * Create the new trip, its pattern and its real-time times from the resolved state, and return
   * them as an update that brings the trip into the transit model for the first time.
   *
   * @param deduplicator       deduplicates the scheduled trip times built as the baseline for the
   *                           real-time times
   * @param patternIdGenerator generates the id of the created pattern from the created trip -
   *                           injects {@code TripPatternCache#generatePatternId}
   * @throws DataValidationException if the resulting trip times are invalid
   */
  public TripUpdateResult apply(
    DeduplicatorService deduplicator,
    Function<Trip, FeedScopedId> patternIdGenerator
  ) {
    // Filter stop time updates (GTFS-RT: filter unknown stops, SIRI: fail on unknown stops)
    var filteredUpdates = stopTimeUpdatesWithKnownStops();

    // Check minimum stops
    if (filteredUpdates.updates().size() < 2) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }

    // Create the trip
    Trip trip = createTrip();

    // Build stop pattern from stop time updates
    var stopTimesAndPattern = NewStopPatternFactory.buildNewStopPattern(
      trip,
      filteredUpdates.updates(),
      formatPolicy().firstLastStopTime()
    );

    // Create scheduled trip times
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      deduplicator
    ).withServiceCode(serviceCode);

    scheduledTripTimes.validateNonIncreasingTimes();

    // Create the new pattern
    // For SIRI (INCLUDE), we add scheduled trip times so queries for aimed times work
    // For GTFS-RT (EXCLUDE), we don't add to scheduled timetable
    boolean includeScheduledData = formatPolicy().scheduledData().includesScheduledData();

    TripPattern pattern;
    if (includeScheduledData) {
      // SIRI-style: include scheduled times
      pattern = TripPattern.of(patternIdGenerator.apply(trip))
        .withRoute(route)
        .withMode(trip.getMode())
        .withNetexSubmode(trip.getNetexSubMode())
        .withStopPattern(stopTimesAndPattern.stopPattern())
        .withRealTimeAddedTrip()
        .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(scheduledTripTimes))
        .build();
    } else {
      // GTFS-RT style: no scheduled times
      pattern = TripPattern.of(patternIdGenerator.apply(trip))
        .withRoute(route)
        .withMode(trip.getMode())
        .withNetexSubmode(trip.getNetexSubMode())
        .withStopPattern(stopTimesAndPattern.stopPattern())
        .withRealTimeStopPatternModified()
        .build();
    }

    // Create real-time trip times
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    applyJourneyDescription(builder);
    StopTimeUpdates.applyRealTimeUpdates(builder, filteredUpdates.updates());
    // Extra journeys always retain the "added" flag, even when all stops are cancelled,
    // because they were never part of the static schedule.
    builder.withAdded();
    if (isCancelled()) {
      builder.withCanceled();
    }

    // Create TripOnServiceDate for lookup by dated vehicle journey
    TripOnServiceDate tripOnServiceDate = TripOnServiceDate.of(tripOnServiceDateId)
      .withTrip(trip)
      .withServiceDate(serviceDate())
      .withRealtimeExtraJourney(true)
      .withReplacementFor(replacedTrips)
      .build();

    // tripCreation=true since we're creating a new trip
    var realTimeTripUpdate = RealTimeTripUpdate.of(pattern, builder.build(), serviceDate())
      .withAddedTripOnServiceDate(tripOnServiceDate)
      .withTripCreation(true)
      .withRouteCreation(isNewRoute)
      .withProducer(dataSource())
      .build();

    return new TripUpdateResult(realTimeTripUpdate, filteredUpdates.warnings());
  }

  /**
   * Create the new trip. The headsign comes from the update itself rather than the creation data:
   * it is the headsign the trip displays today, and the same value is applied to the real-time
   * trip times.
   */
  private Trip createTrip() {
    var builder = Trip.of(tripId());
    builder.withRoute(route);
    builder.withServiceId(serviceId);
    if (tripHeadsign() != null) {
      builder.withHeadsign(tripHeadsign());
    }
    applyTripDescription(builder);
    return builder.build();
  }

  /**
   * Apply the mode, submode and short name the message describes the created trip with. Falls back
   * to the mode of {@link #route} when the message states none. The headsign is not creation
   * data - see {@link #tripHeadsign()}.
   */
  private void applyTripDescription(TripBuilder builder) {
    builder.withMode(mode != null ? mode : route.getMode());
    if (submode != null) {
      builder.withNetexSubmode(submode);
    }
    if (shortName != null) {
      builder.withShortName(shortName);
    }
  }

  /**
   * A trip can only be created from a journey that calls at least twice, and - in FAIL mode - only
   * from calls at stops the transit model knows.
   * <p>
   * IGNORE-mode filtering and the minimum-stop check on the filtered calls stay in
   * {@link #apply}: they judge the outcome of a transformation, not the message as it arrived.
   *
   * @throws UpdateException if the message cannot describe a trip
   */
  private void validate() {
    var calls = stopTimeUpdates();

    if (formatPolicy().unknownStop().failOnUnknownStop()) {
      for (int i = 0; i < calls.size(); i++) {
        if (calls.get(i).stop() == null) {
          throw UpdateException.of(tripId(), UpdateErrorType.UNKNOWN_STOP, i);
        }
      }
    }

    if (calls.size() < 2) {
      throw UpdateException.of(tripId(), UpdateErrorType.TOO_FEW_STOPS);
    }
  }

  @Override
  public String toString() {
    return ("TripCreation{" + "tripId=" + tripId() + ", serviceDate=" + serviceDate() + '}');
  }
}
