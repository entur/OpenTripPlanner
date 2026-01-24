package org.opentripplanner.updater.trip.gtfs.model;

import static org.opentripplanner.updater.trip.gtfs.model.GtfsRealtimeMapper.mapWheelchairAccessible;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.Accessibility;

/**
 * A real-time update for trip, which may contain updated stop times and trip properties.
 */
public final class TripUpdate {

  private final com.google.transit.realtime.GtfsRealtime.TripUpdate tripUpdate;
  private final TripDescriptor tripDescriptor;

  public TripUpdate(
    GtfsRealtime.TripUpdate tripUpdate,
    String feedId,
    Supplier<LocalDate> localDateNow
  ) {
    this.tripUpdate = tripUpdate;
    this.tripDescriptor = new TripDescriptor(tripUpdate.getTrip(), feedId, localDateNow);
  }

  public TripDescriptor tripDescriptor() {
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

  public LocalDate serviceDate() {
    return tripDescriptor.serviceDate();
  }

  public ScheduleRelationship scheduleRelationship() {
    return tripDescriptor.scheduleRelationship();
  }

  public FeedScopedId tripId() {
    return tripDescriptor.tripId();
  }

  private Optional<GtfsRealtime.TripUpdate.TripProperties> tripProperties() {
    return tripUpdate.hasTripProperties()
      ? Optional.of(tripUpdate.getTripProperties())
      : Optional.empty();
  }

  public Optional<GtfsRealtime.VehicleDescriptor> vehicle() {
    return tripUpdate.hasVehicle() ? Optional.of(tripUpdate.getVehicle()) : Optional.empty();
  }
}
