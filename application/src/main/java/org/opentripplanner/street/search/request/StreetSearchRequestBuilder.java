package org.opentripplanner.street.search.request;

import java.time.Instant;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.StreetMode;

public class StreetSearchRequestBuilder {

  Instant startTime;
  StreetMode mode;
  boolean arriveBy;
  boolean wheelchair;
  GenericLocation from;
  GenericLocation to;
  boolean geoidElevation;
  double turnReluctance;
  public RentalRequest rental;

  StreetSearchRequestBuilder(StreetSearchRequest original) {
    this.startTime = original.startTime();
    this.mode = original.mode();
    this.arriveBy = original.arriveBy();
    this.wheelchair = original.wheelchairEnabled();
    this.from = original.from();
    this.to = original.to();
    this.geoidElevation = original.geoidElevation();
    this.turnReluctance = original.turnReluctance();
  }

  public StreetSearchRequestBuilder withStartTime(Instant startTime) {
    this.startTime = startTime;
    return this;
  }

  public StreetSearchRequestBuilder withMode(StreetMode mode) {
    this.mode = mode;
    return this;
  }

  public StreetSearchRequestBuilder withArriveBy(boolean arriveBy) {
    this.arriveBy = arriveBy;
    return this;
  }

  public StreetSearchRequestBuilder withWheelchair(boolean wheelchair) {
    this.wheelchair = wheelchair;
    return this;
  }

  public StreetSearchRequestBuilder withFrom(GenericLocation from) {
    this.from = from;
    return this;
  }

  public StreetSearchRequestBuilder withTo(GenericLocation to) {
    this.to = to;
    return this;
  }

  public StreetSearchRequestBuilder withGeoidElevation(boolean value) {
    this.geoidElevation = value;
    return this;
  }

  public StreetSearchRequestBuilder withTurnReluctance(double v) {
    this.turnReluctance = v;
    return this;
  }

  public StreetSearchRequestBuilder withRental(RentalRequest rentalRequest) {
    this.rental = rentalRequest;
    return this;
  }

  public StreetSearchRequestBuilder withUseRentalAvailability(boolean b) {
    return null;
  }

  Instant startTimeOrNow() {
    return startTime == null ? Instant.now() : startTime;
  }

  public StreetSearchRequest build() {
    return new StreetSearchRequest(this);
  }
}
