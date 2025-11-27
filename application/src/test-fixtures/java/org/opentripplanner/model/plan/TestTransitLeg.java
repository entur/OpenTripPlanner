package org.opentripplanner.model.plan;

import static org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory.id;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.model.plan.leg.LegCallTime;
import org.opentripplanner.routing.alertpatch.TransitAlert;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.site.StopType;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Many methods in this class throw {@link NotImplementedException}. Please implement them when
 * you need them.
 */
public class TestTransitLeg implements TransitLeg {

  private final ZonedDateTime startTime;
  private final ZonedDateTime endTime;
  private final Trip trip;

  public TestTransitLeg(TestTransitLegBuilder builder) {
    this.startTime = builder.startTime;
    this.endTime = builder.endTime;
    this.trip = builder.trip;
  }

  @Override
  public Agency agency() {
    return trip.getRoute().getAgency();
  }

  @Override
  public Route route() {
    return trip.getRoute();
  }

  @Nullable
  @Override
  public Trip trip() {
    return trip;
  }

  @Override
  public TransitMode mode() {
    throw new NotImplementedException();
  }

  @Override
  public TransitLeg decorateWithAlerts(Set<TransitAlert> alerts) {
    throw new NotImplementedException();
  }

  @Override
  public TransitLeg decorateWithFareOffers(List<FareOffer> fares) {
    throw new NotImplementedException();
  }

  @Override
  public LegCallTime start() {
    return LegCallTime.ofStatic(startTime);
  }

  @Override
  public LegCallTime end() {
    return LegCallTime.ofStatic(endTime);
  }

  @Override
  public ZonedDateTime startTime() {
    return startTime;
  }

  @Override
  public ZonedDateTime endTime() {
    return endTime;
  }

  @Override
  public double distanceMeters() {
    return 0;
  }

  @Override
  public Place from() {
    return Place.forStop(new TestStopLocation());
  }

  @Override
  public Place to() {
    return Place.forStop(new TestStopLocation());
  }

  @Override
  public @Nullable LineString legGeometry() {
    throw new NotImplementedException();
  }

  @Override
  public Set<TransitAlert> listTransitAlerts() {
    throw new NotImplementedException();
  }

  @Override
  public @Nullable Emission emissionPerPerson() {
    throw new NotImplementedException();
  }

  @Override
  public @Nullable Leg withEmissionPerPerson(Emission emissionPerPerson) {
    throw new NotImplementedException();
  }

  @Override
  public int generalizedCost() {
    return 0;
  }

  @Override
  public List<FareOffer> fareOffers() {
    return List.of();
  }

  public static TestTransitLegBuilder of() {
    return new TestTransitLegBuilder();
  }

  class TestStopLocation implements StopLocation {

    @Override
    public FeedScopedId getId() {
      return id("s1");
    }

    @Override
    public int getIndex() {
      return -999;
    }

    @Override
    public @Nullable I18NString getName() {
      return null;
    }

    @Override
    public @Nullable I18NString getDescription() {
      return null;
    }

    @Override
    public @Nullable I18NString getUrl() {
      return null;
    }

    @Override
    public StopType getStopType() {
      return null;
    }

    @Override
    public WgsCoordinate getCoordinate() {
      return null;
    }

    @Override
    public @Nullable Geometry getGeometry() {
      return null;
    }

    @Override
    public boolean isPartOfStation() {
      return false;
    }

    @Override
    public boolean isPartOfSameStationAs(StopLocation alternativeStop) {
      return false;
    }
  }
}
