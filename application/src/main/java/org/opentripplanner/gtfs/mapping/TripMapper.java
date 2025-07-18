package org.opentripplanner.gtfs.mapping;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.routing.api.request.framework.TimePenalty;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.utils.collection.MapUtils;

/** Responsible for mapping GTFS TripMapper into the OTP model. */
class TripMapper {

  private final IdFactory idFactory;
  private final RouteMapper routeMapper;
  private final DirectionMapper directionMapper;
  private final TranslationHelper translationHelper;

  private final Map<org.onebusaway.gtfs.model.Trip, Trip> mappedTrips = new HashMap<>();
  private final Map<Trip, TimePenalty> flexSafeTimePenalties = new HashMap<>();

  TripMapper(
    IdFactory idFactory,
    RouteMapper routeMapper,
    DirectionMapper directionMapper,
    TranslationHelper translationHelper
  ) {
    this.idFactory = idFactory;
    this.routeMapper = routeMapper;
    this.directionMapper = directionMapper;
    this.translationHelper = translationHelper;
  }

  Collection<Trip> map(Collection<org.onebusaway.gtfs.model.Trip> trips) {
    return MapUtils.mapToList(trips, this::map);
  }

  Trip map(org.onebusaway.gtfs.model.Trip orginal) {
    return orginal == null ? null : mappedTrips.computeIfAbsent(orginal, this::doMap);
  }

  Collection<Trip> getMappedTrips() {
    return mappedTrips.values();
  }

  /**
   * The map of flex duration factors per flex trip.
   */
  Map<Trip, TimePenalty> flexSafeTimePenalties() {
    return flexSafeTimePenalties;
  }

  private Trip doMap(org.onebusaway.gtfs.model.Trip rhs) {
    var lhs = Trip.of(idFactory.createId(rhs.getId(), "trip"));

    lhs.withRoute(routeMapper.map(rhs.getRoute()));
    lhs.withServiceId(idFactory.createId(rhs.getServiceId(), "trip's service"));
    lhs.withShortName(rhs.getTripShortName());
    I18NString tripHeadsign = null;
    if (rhs.getTripHeadsign() != null) {
      tripHeadsign = translationHelper.getTranslation(
        org.onebusaway.gtfs.model.Trip.class,
        "tripHeadsign",
        rhs.getId().getId(),
        rhs.getTripHeadsign()
      );
    }
    lhs.withHeadsign(tripHeadsign);
    lhs.withDirection(directionMapper.map(rhs.getDirectionId(), lhs.getId()));
    lhs.withGtfsBlockId(rhs.getBlockId());
    lhs.withShapeId(idFactory.createNullableId(rhs.getShapeId()));
    lhs.withWheelchairBoarding(WheelchairAccessibilityMapper.map(rhs.getWheelchairAccessible()));
    lhs.withBikesAllowed(BikeAccessMapper.mapForTrip(rhs));
    lhs.withCarsAllowed(CarAccessMapper.mapForTrip(rhs));

    var trip = lhs.build();
    mapSafeTimePenalty(rhs).ifPresent(f -> flexSafeTimePenalties.put(trip, f));
    return trip;
  }

  private Optional<TimePenalty> mapSafeTimePenalty(org.onebusaway.gtfs.model.Trip rhs) {
    if (rhs.getSafeDurationFactor() == null && rhs.getSafeDurationOffset() == null) {
      return Optional.empty();
    } else {
      var offset = rhs.getSafeDurationOffset() == null
        ? Duration.ZERO
        : Duration.ofSeconds(rhs.getSafeDurationOffset().longValue());
      var factor = rhs.getSafeDurationFactor() == null
        ? 1d
        : rhs.getSafeDurationFactor().doubleValue();
      return Optional.of(TimePenalty.of(offset, factor));
    }
  }
}
