package org.opentripplanner.ext.ojp.mapping;

import de.vdv.ojp20.PlaceStructure;
import de.vdv.ojp20.PlacesStructure;
import de.vdv.ojp20.ResponseContextStructure;
import de.vdv.ojp20.StopPointStructure;
import java.util.Objects;
import java.util.stream.Stream;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.TripPlan;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.utils.collection.StreamUtils;

class TripResponseContextMapper {

  private final StopPointRefMapper stopPointRefMapper;

  TripResponseContextMapper(StopPointRefMapper stopPointRefMapper) {
    this.stopPointRefMapper = stopPointRefMapper;
  }

  ResponseContextStructure map(TripPlan tripPlan) {
    var stopLocations = tripPlan.itineraries
      .stream()
      .flatMap(TripResponseContextMapper::stopLocations)
      .filter(Objects::nonNull)
      .distinct()
      .map(this::location)
      .toList();

    return new ResponseContextStructure()
      .withPlaces(new PlacesStructure().withPlace(stopLocations));
  }

  private PlaceStructure location(StopLocation stopLocation) {
    return new PlaceStructure()
      .withStopPoint(
        new StopPointStructure()
          .withStopPointRef(stopPointRefMapper.stopPointRef(stopLocation))
          .withStopPointName(TextMapper.internationalText(stopLocation.getName()))
          .withPlannedQuay(TextMapper.internationalText(stopLocation.getPlatformCode()))
      )
      .withGeoPosition(LocationMapper.map(stopLocation.getCoordinate()));
  }

  private static Stream<StopLocation> stopLocations(Itinerary itinerary) {
    return itinerary.legs().stream().flatMap(TripResponseContextMapper::stopLocations);
  }

  private static Stream<StopLocation> stopLocations(Leg leg) {
    var fromTo = Stream.of(leg.from().stop, leg.to().stop);
    var intermediate = StreamUtils.ofNullableCollection(leg.listIntermediateStops()).map(sa ->
      sa.place.stop
    );
    return Stream.concat(fromTo, intermediate);
  }
}
