package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.graphfinder.NearbyStop;

public interface AccessEgressTripFilter {

  boolean acceptsAccessEgress(
    CarpoolTrip trip,
    WgsCoordinate coordinateOfPassenger, // Can be place of departure or arrival for passenger
    Instant passengerDepartureTime,
    Duration searchWindow
  );

  boolean acceptsAccessEgressWithNearbyStop(
    CarpoolTrip trip,
    WgsCoordinate coordinateOfPassenger,
    NearbyStop accessOrEgressPoint,
    AccessEgressType accessEgressType
  );

}
