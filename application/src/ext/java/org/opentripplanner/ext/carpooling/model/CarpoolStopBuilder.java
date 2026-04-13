package org.opentripplanner.ext.carpooling.model;

import java.time.ZonedDateTime;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.framework.AbstractEntityBuilder;

/**
 * Builder for {@link CarpoolStop} instances.
 */
public class CarpoolStopBuilder extends AbstractEntityBuilder<CarpoolStop, CarpoolStopBuilder> {

  private WgsCoordinate coordinate;

  private ZonedDateTime expectedArrivalTime;
  private ZonedDateTime aimedArrivalTime;
  private ZonedDateTime expectedDepartureTime;
  private ZonedDateTime aimedDepartureTime;
  private int sequenceNumber;
  private int onboardCount = CarpoolStop.DEFAULT_ONBOARD_COUNT;

  CarpoolStopBuilder(FeedScopedId id) {
    super(id);
  }

  CarpoolStopBuilder(CarpoolStop original) {
    super(original);
    this.coordinate = original.getCoordinate();
    this.sequenceNumber = original.getSequenceNumber();

    this.expectedArrivalTime = original.getExpectedArrivalTime();
    this.aimedArrivalTime = original.getAimedArrivalTime();
    this.expectedDepartureTime = original.getExpectedDepartureTime();
    this.aimedDepartureTime = original.getAimedDepartureTime();
    this.onboardCount = original.getOnboardCount();
  }

  @Override
  protected CarpoolStop buildFromValues() {
    return new CarpoolStop(this);
  }

  public CarpoolStopBuilder withCoordinate(WgsCoordinate coordinate) {
    this.coordinate = coordinate;
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

  public CarpoolStopBuilder withOnboardCount(int onboardCount) {
    this.onboardCount = onboardCount;
    return this;
  }

  public WgsCoordinate coordinate() {
    return coordinate;
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

  public int onboardCount() {
    return onboardCount;
  }
}
