package org.opentripplanner.ext.ojp.mapping;

import static org.opentripplanner.ext.ojp.mapping.JaxbElementMapper.jaxbElement;
import static org.opentripplanner.ext.ojp.mapping.TextMapper.internationalText;

import de.vdv.ojp20.ContinuousLegStructure;
import de.vdv.ojp20.LegAlightStructure;
import de.vdv.ojp20.LegBoardStructure;
import de.vdv.ojp20.LegIntermediateStructure;
import de.vdv.ojp20.LegStructure;
import de.vdv.ojp20.OJP;
import de.vdv.ojp20.OJPResponseStructure;
import de.vdv.ojp20.OJPTripDeliveryStructure;
import de.vdv.ojp20.PlaceRefStructure;
import de.vdv.ojp20.ServiceArrivalStructure;
import de.vdv.ojp20.ServiceDepartureStructure;
import de.vdv.ojp20.TimedLegStructure;
import de.vdv.ojp20.TripResultStructure;
import de.vdv.ojp20.TripStructure;
import jakarta.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.leg.LegCallTime;
import org.opentripplanner.model.plan.leg.ScheduledTransitLeg;
import org.opentripplanner.model.plan.leg.StopArrival;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.ojp.time.XmlDateTime;
import org.opentripplanner.routing.api.response.RoutingResponse;

public class TripResponseMapper {

  private final StopPointRefMapper stopPointRefMapper;
  private final TripResponseContextMapper contextMapper;

  public TripResponseMapper(FeedScopedIdMapper idMapper) {
    this.stopPointRefMapper = new StopPointRefMapper(idMapper);
    this.contextMapper = new TripResponseContextMapper(stopPointRefMapper);
  }

  public OJP mapTripPlan(RoutingResponse otpResponse, ZonedDateTime timestamp) {
    List<JAXBElement<?>> tripResults = otpResponse
      .getTripPlan()
      .itineraries.stream()
      .map(this::mapItinerary)
      .map(JaxbElementMapper::jaxbElement)
      .collect(Collectors.toList());

    var context = jaxbElement(contextMapper.map(otpResponse.getTripPlan()));
    var tripDelivery = new OJPTripDeliveryStructure().withRest(context).withRest(tripResults);

    var serviceDelivery = ServiceDeliveryMapper.serviceDelivery(
      timestamp
    ).withAbstractFunctionalServiceDelivery(jaxbElement(tripDelivery));

    return new OJP()
      .withOJPResponse(new OJPResponseStructure().withServiceDelivery(serviceDelivery));
  }

  private TripResultStructure mapItinerary(Itinerary itinerary) {
    var tr = new TripResultStructure();

    var legs = itinerary.legs().stream().map(this::mapLeg).toList();
    tr.withTrip(
      new TripStructure()
        .withDuration(Duration.between(itinerary.startTime(), itinerary.endTime()))
        .withTransfers(BigInteger.valueOf(itinerary.legs().size() - 1))
        .withLeg(legs)
    );
    return tr;
  }

  private LegStructure mapLeg(Leg leg) {
    return switch (leg) {
      case ScheduledTransitLeg tl -> mapTransitLeg(tl);
      case StreetLeg sl -> mapStreetLeg(sl);
      default -> throw new IllegalStateException(
        "Unexpected leg type : " + leg.getClass().getSimpleName()
      );
    };
  }

  private LegStructure mapStreetLeg(StreetLeg sl) {
    return baseLeg(sl).withContinuousLeg(
      new ContinuousLegStructure()
        .withLegStart(placeRef(sl.from()))
        .withLegEnd(placeRef(sl.to()))
        .withTimeWindowStart(new XmlDateTime(sl.startTime()))
        .withTimeWindowEnd(new XmlDateTime(sl.endTime()))
        .withDuration(sl.duration())
        .withLength(BigInteger.valueOf((long) sl.distanceMeters()))
    );
  }

  private LegStructure mapTransitLeg(ScheduledTransitLeg tl) {
    var scheduledDeparture = new XmlDateTime(tl.start().scheduledTime());
    var realtimeDeparture = estimatedTime(tl.start());
    var scheduledArrival = new XmlDateTime(tl.end().scheduledTime());
    var realtimeArrival = estimatedTime(tl.end());

    return baseLeg(tl).withTimedLeg(
      new TimedLegStructure()
        .withLegBoard(
          new LegBoardStructure()
            .withStopPointRef(stopPointRefMapper.stopPointRef(tl.from().stop))
            .withStopPointName(internationalText(tl.from().stop.getName()))
            .withServiceDeparture(
              new ServiceDepartureStructure()
                .withTimetabledTime(scheduledDeparture)
                .withEstimatedTime(realtimeDeparture)
            )
        )
        .withLegIntermediate(mapIntermediateStop(tl.listIntermediateStops()))
        .withLegAlight(
          new LegAlightStructure()
            .withStopPointRef(stopPointRefMapper.stopPointRef(tl.to().stop))
            .withStopPointName(internationalText(tl.to().stop.getName()))
            .withServiceArrival(
              new ServiceArrivalStructure()
                .withTimetabledTime(scheduledArrival)
                .withEstimatedTime(realtimeArrival)
            )
        )
    );
  }

  private List<LegIntermediateStructure> mapIntermediateStop(List<StopArrival> sas) {
    return sas
      .stream()
      .map(sa ->
        new LegIntermediateStructure()
          .withStopPointRef(stopPointRefMapper.stopPointRef(sa.place.stop))
          .withStopPointName(internationalText(sa.place.stop.getName()))
          .withServiceArrival(
            new ServiceArrivalStructure()
              .withTimetabledTime(new XmlDateTime(sa.arrival.scheduledTime()))
          )
          .withServiceDeparture(
            new ServiceDepartureStructure()
              .withTimetabledTime(new XmlDateTime(sa.departure.scheduledTime()))
          )
      )
      .toList();
  }

  private PlaceRefStructure placeRef(Place place) {
    var ref = new PlaceRefStructure();
    if (place.stop != null) {
      return ref.withStopPointRef(stopPointRefMapper.stopPointRef(place.stop));
    } else {
      return ref
        .withName(internationalText(place.name))
        .withGeoPosition(LocationStructureMapper.map(place.coordinate));
    }
  }

  @Nullable
  private static XmlDateTime estimatedTime(@Nullable LegCallTime time) {
    if (time.estimated() == null) {
      return null;
    } else {
      return new XmlDateTime(time.estimated().time());
    }
  }

  private static LegStructure baseLeg(Leg leg) {
    return new LegStructure().withDuration(leg.duration());
  }
}
