package org.opentripplanner.routing.algorithm.mapping._support.mapping;

import static org.opentripplanner.routing.algorithm.mapping._support.mapping.ElevationMapper.mapElevation;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.apis.gtfs.mapping.LocalDateMapper;
import org.opentripplanner.framework.geometry.EncodedPolyline;
import org.opentripplanner.framework.i18n.I18NStringMapper;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.StreetLeg;
import org.opentripplanner.model.plan.TransitLeg;
import org.opentripplanner.routing.algorithm.mapping._support.model.ApiLeg;

@Deprecated
class LegMapper {

  private final WalkStepMapper walkStepMapper;
  private final PlaceMapper placeMapper;
  private final boolean addIntermediateStops;

  private final I18NStringMapper i18NStringMapper;

  public LegMapper(Locale locale, boolean addIntermediateStops) {
    this.walkStepMapper = new WalkStepMapper(locale);
    this.placeMapper = new PlaceMapper(locale);
    this.addIntermediateStops = addIntermediateStops;
    this.i18NStringMapper = new I18NStringMapper(locale);
  }

  public List<ApiLeg> mapLegs(List<Leg> domain) {
    if (domain == null) {
      return null;
    }

    List<ApiLeg> apiLegs = new ArrayList<>();

    final int size = domain.size();
    final int lastIdx = size - 1;

    for (int i = 0; i < size; ++i) {
      ZonedDateTime arrivalTimeFromPlace = (i == 0) ? null : domain.get(i - 1).getEndTime();
      ZonedDateTime departureTimeToPlace = (i == lastIdx) ? null : domain.get(i + 1).getStartTime();

      apiLegs.add(mapLeg(domain.get(i), arrivalTimeFromPlace, departureTimeToPlace));
    }
    return apiLegs;
  }

  public ApiLeg mapLeg(
    Leg domain,
    ZonedDateTime arrivalTimeFromPlace,
    ZonedDateTime departureTimeToPlace
  ) {
    if (domain == null) {
      return null;
    }
    ApiLeg api = new ApiLeg();
    api.startTime = GregorianCalendar.from(domain.getStartTime());
    api.endTime = GregorianCalendar.from(domain.getEndTime());

    // Set the arrival and departure times, even if this is redundant information
    api.from = placeMapper.mapPlace(
      domain.getFrom(),
      arrivalTimeFromPlace,
      domain.getStartTime(),
      domain.getBoardStopPosInPattern(),
      domain.getBoardingGtfsStopSequence()
    );
    api.to = placeMapper.mapPlace(
      domain.getTo(),
      domain.getEndTime(),
      departureTimeToPlace,
      domain.getAlightStopPosInPattern(),
      domain.getAlightGtfsStopSequence()
    );

    api.departureDelay = domain.getDepartureDelay();
    api.arrivalDelay = domain.getArrivalDelay();
    api.realTime = domain.isRealTimeUpdated();
    api.isNonExactFrequency = domain.getNonExactFrequency();
    api.headway = domain.getHeadway();
    api.distance = round3Decimals(domain.getDistanceMeters());
    api.generalizedCost = domain.getGeneralizedCost();
    api.agencyTimeZoneOffset = domain.getAgencyTimeZoneOffset();

    if (domain instanceof TransitLeg trLeg) {
      api.transitLeg = true;
      var agency = domain.getAgency();
      api.agencyId = FeedScopedIdMapper.mapToApi(agency.getId());
      api.agencyName = agency.getName();
      api.agencyUrl = agency.getUrl();
      api.mode = ModeMapper.mapToApi(trLeg.getMode());

      var route = domain.getRoute();
      api.route = i18NStringMapper.mapToApi(route.getLongName());
      api.routeColor = route.getColor();
      api.routeType = domain.getRouteType();
      api.routeId = FeedScopedIdMapper.mapToApi(route.getId());
      api.routeShortName = route.getShortName();
      api.routeLongName = i18NStringMapper.mapToApi(route.getLongName());
      api.routeTextColor = route.getTextColor();

      var trip = domain.getTrip();
      api.tripId = FeedScopedIdMapper.mapToApi(trip.getId());
      api.tripShortName = trip.getShortName();
      api.tripBlockId = trip.getGtfsBlockId();
    } else if (domain instanceof StreetLeg streetLeg) {
      api.transitLeg = false;
      api.mode = ModeMapper.mapToApi(streetLeg.getMode());

      // TODO OTP2 - This should be set to the street name according to the JavaDoc
      api.route = "";
    }

    api.interlineWithPreviousLeg = domain.isInterlinedWithPreviousLeg();
    api.headsign = i18NStringMapper.mapToApi(domain.getHeadsign());
    api.serviceDate = LocalDateMapper.mapToApi(domain.getServiceDate());
    api.routeBrandingUrl = domain.getRouteBrandingUrl();
    if (addIntermediateStops) {
      api.intermediateStops = placeMapper.mapStopArrivals(domain.getIntermediateStops());
    }
    api.legGeometry = EncodedPolyline.encode(domain.getLegGeometry());
    api.legElevation = mapElevation(domain.getElevationProfile());
    api.steps = walkStepMapper.mapWalkSteps(domain.getWalkSteps());

    api.rentedBike = domain.getRentedVehicle();
    api.walkingBike = domain.getWalkingBike();

    return api;
  }

  private Double round3Decimals(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
