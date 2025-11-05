package org.opentripplanner.street.search.request;

import java.time.Instant;
import java.util.function.Consumer;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.StreetMode;

public class StreetSearchRequestBuilder {

  Instant startTime;
  StreetMode mode;
  boolean arriveBy;
  boolean wheelchairEnabled;
  GenericLocation from;
  GenericLocation to;
  boolean geoidElevation;
  double turnReluctance;
  WalkRequest walk;
  BikeRequest bike;
  CarRequest car;
  WheelchairRequest wheelchair;
  ScooterRequest scooter;
  ElevatorRequest elevator;

  StreetSearchRequestBuilder(StreetSearchRequest original) {
    this.startTime = original.startTime();
    this.mode = original.mode();
    this.arriveBy = original.arriveBy();
    this.wheelchairEnabled = original.wheelchairEnabled();
    this.from = original.from();
    this.to = original.to();
    this.geoidElevation = original.geoidElevation();
    this.turnReluctance = original.turnReluctance();
    this.walk = original.walk();
    this.bike = original.bike();
    this.car = original.car();
    this.scooter = original.scooter();
    this.wheelchair = original.wheelchair();
    this.elevator = original.elevator();
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

  public StreetSearchRequestBuilder withWheelchairEnabled(boolean wheelchair) {
    this.wheelchairEnabled = wheelchair;
    return this;
  }

  public StreetSearchRequestBuilder withWheelchair(WheelchairRequest request) {
    this.wheelchair = request;
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

  public StreetSearchRequestBuilder withUseRentalAvailability(boolean b) {
    return this;
  }

  public StreetSearchRequestBuilder withWalk(WalkRequest request) {
    this.walk = request;
    return this;
  }

  public StreetSearchRequestBuilder withWalk(Consumer<WalkRequest.Builder> body) {
    this.walk = this.walk.copyOf().apply(body).build();
    return this;
  }

  public StreetSearchRequestBuilder withBike(BikeRequest bike) {
    this.bike = bike;
    return this;
  }

  public StreetSearchRequestBuilder withBike(Consumer<BikeRequest.Builder> body) {
    this.bike = this.bike.copyOf().apply(body).build();
    return this;
  }

  public StreetSearchRequestBuilder withCar(Consumer<CarRequest.Builder> body) {
    this.car = this.car.copyOf().apply(body).build();
    return this;
  }

  public StreetSearchRequestBuilder withScooter(Consumer<ScooterRequest.Builder> body) {
    this.scooter = this.scooter.copyOf().apply(body).build();
    return this;
  }

  public StreetSearchRequestBuilder withScooter(ScooterRequest request) {
    this.scooter = request;
    return this;
  }

  public StreetSearchRequestBuilder withCar(CarRequest carRequest) {
    this.car = carRequest;
    return this;
  }

  Instant startTimeOrNow() {
    return startTime == null ? Instant.now() : startTime;
  }

  public StreetSearchRequest build() {
    return new StreetSearchRequest(this);
  }
}
