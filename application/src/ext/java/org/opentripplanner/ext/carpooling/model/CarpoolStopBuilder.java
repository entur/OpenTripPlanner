package org.opentripplanner.ext.carpooling.model;

import java.time.Duration;
import java.time.ZonedDateTime;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.framework.AbstractEntityBuilder;

public class CarpoolStopBuilder extends AbstractEntityBuilder<CarpoolStop, CarpoolStopBuilder> {

  private WgsCoordinate coordinate;

  private CarpoolStopType carpoolStopType;
  private ZonedDateTime expectedArrivalTime;
  private ZonedDateTime aimedArrivalTime;
  private ZonedDateTime expectedDepartureTime;
  private ZonedDateTime aimedDepartureTime;
  private int sequenceNumber;
  private int passengerDelta;
  private Duration deviationBudget;

  CarpoolStopBuilder(FeedScopedId id) {
    super(id);
  }

  CarpoolStopBuilder(CarpoolStop original) {
    super(original);
    this.coordinate = original.getCoordinate();
    this.sequenceNumber = original.getSequenceNumber();

    this.carpoolStopType = original.getCarpoolStopType();
    this.expectedArrivalTime = original.getExpectedArrivalTime();
    this.aimedArrivalTime = original.getAimedArrivalTime();
    this.expectedDepartureTime = original.getExpectedDepartureTime();
    this.aimedDepartureTime = original.getAimedDepartureTime();
    this.passengerDelta = original.getPassengerDelta();
    this.deviationBudget = original.getDeviationBudget();
  }

  @Override
  protected CarpoolStop buildFromValues() {
    return new CarpoolStop(this);
  }

  public CarpoolStopBuilder withCoordinate(WgsCoordinate coordinate) {
    this.coordinate = coordinate;
    return this;
  }

  public CarpoolStopBuilder withCarpoolStopType(CarpoolStopType carpoolStopType) {
    this.carpoolStopType = carpoolStopType;
    return this;
  }

  public CarpoolStopBuilder withExpectedArrivalTime(ZonedDateTime expectedArrivalTime) {
    this.expectedArrivalTime = expectedArrivalTime;
    return this;
  }

  public CarpoolStopBuilder withAimedArrivalTime(ZonedDateTime aimedArrivalTime) {
    this.aimedArrivalTime = aimedArrivalTime;
    return this;
  }

  public CarpoolStopBuilder withExpectedDepartureTime(ZonedDateTime expectedDepartureTime) {
    this.expectedDepartureTime = expectedDepartureTime;
    return this;
  }

  public CarpoolStopBuilder withAimedDepartureTime(ZonedDateTime aimedDepartureTime) {
    this.aimedDepartureTime = aimedDepartureTime;
    return this;
  }

  public CarpoolStopBuilder withSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
    return this;
  }

  public CarpoolStopBuilder withPassengerDelta(int passengerDelta) {
    this.passengerDelta = passengerDelta;
    return this;
  }

  public CarpoolStopBuilder withDeviationBudget(Duration deviationBudget) {
    this.deviationBudget = deviationBudget;
    return this;
  }

  public WgsCoordinate coordinate() {
    return coordinate;
  }

  public CarpoolStopType carpoolStopType() {
    return carpoolStopType;
  }

  public ZonedDateTime expectedArrivalTime() {
    return expectedArrivalTime;
  }

  public ZonedDateTime aimedArrivalTime() {
    return aimedArrivalTime;
  }

  public ZonedDateTime expectedDepartureTime() {
    return expectedDepartureTime;
  }

  public ZonedDateTime aimedDepartureTime() {
    return aimedDepartureTime;
  }

  public int sequenceNumber() {
    return sequenceNumber;
  }

  public int passengerDelta() {
    return passengerDelta;
  }

  public Duration deviationBudget() {
    return deviationBudget;
  }
}
