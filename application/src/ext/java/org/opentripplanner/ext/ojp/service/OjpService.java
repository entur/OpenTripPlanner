package org.opentripplanner.ext.ojp.service;

import de.vdv.ojp20.OJP;
import de.vdv.ojp20.OJPStopEventRequestStructure;
import de.vdv.ojp20.OJPTripRequestStructure;
import de.vdv.ojp20.PlaceContextStructure;
import de.vdv.ojp20.PlaceRefStructure;
import de.vdv.ojp20.siri.LocationStructure;
import de.vdv.ojp20.siri.StopPointRefStructure;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.ojp.mapping.ErrorMapper;
import org.opentripplanner.ext.ojp.mapping.StopEventResponseMapper;
import org.opentripplanner.ext.ojp.mapping.TripResponseMapper;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;

/**
 * Takes raw OJP requests, extracts information and forwards it to the underlying services.
 */
public class OjpService {

  private final CallAtStopService ojpService;
  private final RoutingService routingService;
  private final FeedScopedIdMapper idMapper;
  private final ZoneId zoneId;
  private final StopEventParamsMapper seMapper;

  public OjpService(
    CallAtStopService ojpService,
    RoutingService routingService,
    FeedScopedIdMapper idMapper,
    ZoneId zoneId
  ) {
    this.ojpService = ojpService;
    this.routingService = routingService;
    this.idMapper = idMapper;
    this.zoneId = zoneId;
    this.seMapper = new StopEventParamsMapper(zoneId, idMapper);
  }

  public OJP handleStopEventRequest(OJPStopEventRequestStructure ser) {
    var stopId = stopPointRef(ser);
    var coordinate = coordinate(ser);

    final var params = seMapper.extractStopEventParams(ser);

    List<CallAtStop> callsAtStop = List.of();
    if (stopId.isPresent()) {
      callsAtStop = ojpService.findCallsAtStop(stopId.get(), params);
    } else if (coordinate.isPresent()) {
      callsAtStop = ojpService.findCallsAtStop(coordinate.get(), params);
    }
    var optional = StopEventParamsMapper.mapOptionalFeatures(ser.getParams());
    var mapper = new StopEventResponseMapper(
      optional,
      zoneId,
      idMapper,
      ojpService::resolveLanguage
    );
    return mapper.mapCalls(callsAtStop, ZonedDateTime.now());
  }

  public OJP handleTripRequest(OJPTripRequestStructure tr) {
    if (tr.getDestination().size() == 1 && tr.getOrigin().size() == 1) {
      var from = toGenericLocation(tr.getOrigin().getFirst().getPlaceRef().getGeoPosition());
      var to = toGenericLocation(tr.getDestination().getFirst().getPlaceRef().getGeoPosition());
      var req = RouteRequest.defaultValue().copyOf().withFrom(from).withTo(to).buildRequest();
      var tripPlan = routingService.route(req);
      var mapper = new TripResponseMapper(idMapper);
      return mapper.mapTripPlan(tripPlan, ZonedDateTime.now());
    }
    return ErrorMapper.error("No coordinates", ZonedDateTime.now());
  }

  private GenericLocation toGenericLocation(LocationStructure geoPosition) {
    return GenericLocation.fromCoordinate(geoPosition.getLatitude(), geoPosition.getLongitude());
  }

  private Optional<FeedScopedId> stopPointRef(OJPStopEventRequestStructure ser) {
    return placeRefStructure(ser)
      .map(PlaceRefStructure::getStopPointRef)
      .map(StopPointRefStructure::getValue)
      .map(idMapper::parse);
  }

  private Optional<WgsCoordinate> coordinate(OJPStopEventRequestStructure ser) {
    return placeRefStructure(ser)
      .map(PlaceRefStructure::getGeoPosition)
      .map(c -> new WgsCoordinate(c.getLatitude(), c.getLongitude()));
  }

  private static Optional<PlaceRefStructure> placeRefStructure(OJPStopEventRequestStructure ser) {
    return Optional.ofNullable(ser.getLocation()).map(PlaceContextStructure::getPlaceRef);
  }
}
