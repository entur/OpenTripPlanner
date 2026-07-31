package org.opentripplanner.updater.trip.gtfs.model;

import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_SEQUENCE;
import static org.opentripplanner.updater.trip.gtfs.model.GtfsRealtimeMapper.mapWheelchairAccessible;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.utils.lang.StringUtils;

/**
 * A real-time update for trip, which may contain updated stop times and trip properties.
 * Instances of this class are validated and ready for further processing.
 */
public final class TripUpdate {

  private final String feedId;
  private final com.google.transit.realtime.GtfsRealtime.TripUpdate tripUpdate;
  private final TripDescriptor tripDescriptor;
  private final Supplier<LocalDate> localDateNow;
  private LocalDate startDate;

  public TripUpdate(
    String feedId,
    GtfsRealtime.TripUpdate tripUpdate,
    Supplier<LocalDate> localDateNow
  ) {
    this.feedId = feedId;
    this.tripUpdate = tripUpdate;
    this.tripDescriptor = new TripDescriptor(tripUpdate.getTrip());
    this.localDateNow = localDateNow;
  }

  public TripDescriptor descriptor() {
    return tripDescriptor;
  }

  public List<StopTimeUpdate> stopTimeUpdates() {
    return tripUpdate
      .getStopTimeUpdateList()
      .stream()
      .map(StopTimeUpdate::new)
      .collect(Collectors.toList());
  }

  public Optional<I18NString> tripHeadsign() {
    return tripProperties()
      .filter(p -> p.hasTripHeadsign())
      .map(p -> I18NString.of(p.getTripHeadsign()));
  }

  public Optional<String> tripShortName() {
    return tripProperties()
      .filter(p -> p.hasTripShortName())
      .map(p -> p.getTripShortName());
  }

  public Optional<Accessibility> wheelchairAccessibility() {
    return vehicle()
      .filter(d -> d.hasWheelchairAccessible())
      .flatMap(vehicleDescriptor ->
        mapWheelchairAccessible(vehicleDescriptor.getWheelchairAccessible())
      );
  }

  /**
   * The service date to apply this update on: the one the feed reported, or the current date as a
   * guess when it reported none.
   */
  public LocalDate startDate() {
    if (startDate != null) {
      return startDate;
    }
    // TODO: figure out the correct service date. For the special case that a trip
    // starts for example at 40:00, yesterday would probably be a better guess.
    startDate = tripDescriptor.startDate().orElse(localDateNow.get());
    return startDate;
  }

  /**
   * The service date the feed reported, empty if it left {@code start_date} out.
   * <p>
   * Not the same question as {@link #startDate()}, which answers with a guess rather than nothing so
   * that the update can be applied at all. Anything that identifies a trip <em>by</em> its date -
   * fuzzy matching - has to ask this one instead: a guessed date would identify whichever trip
   * happens to run today.
   */
  public Optional<LocalDate> reportedStartDate() {
    return tripDescriptor.startDate();
  }

  public ScheduleRelationship scheduleRelationship() {
    return tripDescriptor.scheduleRelationship();
  }

  public FeedScopedId tripId() {
    return tripDescriptor
      .tripId()
      .map(id -> new FeedScopedId(feedId, id))
      // this should never happen because an empty trip id will lead to an exception in the
      // constructor.
      .orElseThrow(() ->
        new IllegalStateException(
          "Trip ID is missing from trip update. This indicates a programming error."
        )
      );
  }

  /**
   * The trip id the message names, or {@code null} when it names none. A message without a trip id
   * may still identify its trip by route, direction, start time and start date - fuzzy trip
   * matching - so a missing id is an answer here, not an error as in {@link #tripId()}.
   */
  @Nullable
  public FeedScopedId tripIdOrNull() {
    return tripDescriptor
      .tripId()
      .map(id -> new FeedScopedId(feedId, id))
      .orElse(null);
  }

  public void validate() throws DataValidationException, UpdateException {
    if (tripDescriptor.tripId().isEmpty()) {
      throw UpdateException.noTripId(INVALID_INPUT_STRUCTURE);
    }
    validateWithoutTripId();
  }

  /**
   * Everything {@link #validate()} checks except the presence of a trip id, for a caller that
   * accepts a message naming its trip another way - by the identifiers fuzzy trip matching reads.
   */
  public void validateWithoutTripId() throws DataValidationException, UpdateException {
    // exercise the getter, would throw an UpdateException if start date is malformed
    tripDescriptor.startDate();

    var lastStopSequence = -1;
    for (StopTimeUpdate update : stopTimeUpdates()) {
      // validate stop sequence
      OptionalInt stopSequence = update.stopSequence();
      if (stopSequence.isPresent()) {
        var seq = stopSequence.getAsInt();
        if (seq < 0) {
          throw UpdateException.of(tripIdOrNull(), INVALID_STOP_SEQUENCE);
        }
        if (seq <= lastStopSequence) {
          throw UpdateException.of(tripIdOrNull(), INVALID_STOP_SEQUENCE);
        }
        lastStopSequence = seq;
      }
    }
  }

  /// Validates the requirement for the schedule relationship DUPLICATED.
  public void validateDuplicated() throws DataValidationException {
    if (tripDescriptor.startDate().isEmpty() || tripDescriptor.startTime().isEmpty()) {
      throw UpdateException.of(tripIdOrNull(), INVALID_INPUT_STRUCTURE);
    }
  }

  public Optional<LocalTime> startTime() {
    return tripDescriptor.startTime();
  }

  public Optional<FeedScopedId> routeId() {
    return tripDescriptor.routeId().map(id -> new FeedScopedId(feedId, id));
  }

  private Optional<GtfsRealtime.TripUpdate.TripProperties> tripProperties() {
    return tripUpdate.hasTripProperties()
      ? Optional.of(tripUpdate.getTripProperties())
      : Optional.empty();
  }

  public Optional<GtfsRealtime.VehicleDescriptor> vehicle() {
    return tripUpdate.hasVehicle() ? Optional.of(tripUpdate.getVehicle()) : Optional.empty();
  }

  public Optional<String> vehicleId() {
    return vehicle()
      .filter(v -> StringUtils.hasValue(v.getId()))
      .map(GtfsRealtime.VehicleDescriptor::getId);
  }
}
