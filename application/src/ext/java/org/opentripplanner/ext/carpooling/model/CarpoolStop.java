package org.opentripplanner.ext.carpooling.model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.framework.AbstractTransitEntity;

/**
 * Represents a stop along a carpool trip route with passenger pickup/drop-off information.
 * Each stop tracks the passenger delta (number of passengers picked up or dropped off).
 * Stops are ordered sequentially along the route.
 */
public class CarpoolStop extends AbstractTransitEntity<CarpoolStop, CarpoolStopBuilder> {

  private final WgsCoordinate coordinate;
  private final CarpoolStopType carpoolStopType;
  private final ZonedDateTime expectedArrivalTime;
  private final ZonedDateTime aimedArrivalTime;
  private final ZonedDateTime expectedDepartureTime;
  private final ZonedDateTime aimedDepartureTime;
  private final int sequenceNumber;
  private final int passengerDelta;
  private final Duration deviationBudget;

  public CarpoolStop(CarpoolStopBuilder builder) {
    super(builder.getId());
    this.coordinate = Objects.requireNonNull(builder.coordinate());
    this.carpoolStopType = builder.carpoolStopType();
    this.expectedArrivalTime = builder.expectedArrivalTime();
    this.aimedArrivalTime = builder.aimedArrivalTime();
    this.expectedDepartureTime = builder.expectedDepartureTime();
    this.aimedDepartureTime = builder.aimedDepartureTime();
    this.sequenceNumber = builder.sequenceNumber();
    this.passengerDelta = builder.passengerDelta();
    this.deviationBudget = builder.deviationBudget();
  }

  public static CarpoolStopBuilder of(FeedScopedId id) {
    return new CarpoolStopBuilder(id);
  }

  public static CarpoolStopBuilder of(CarpoolStop carpoolStop) {
    return new CarpoolStopBuilder(carpoolStop);
  }

  public WgsCoordinate getCoordinate() {
    return coordinate;
  }

  /**
   * @return The type of carpool operation allowed at this stop
   */
  public CarpoolStopType getCarpoolStopType() {
    return carpoolStopType;
  }

  /**
   * @return The expected arrival time, or null if not applicable (e.g., origin stop)
   */
  @Nullable
  public ZonedDateTime getExpectedArrivalTime() {
    return expectedArrivalTime;
  }

  /**
   * @return The aimed arrival time, or null if not applicable (e.g., origin stop)
   */
  @Nullable
  public ZonedDateTime getAimedArrivalTime() {
    return aimedArrivalTime;
  }

  /**
   * @return The expected departure time, or null if not applicable (e.g., destination stop)
   */
  @Nullable
  public ZonedDateTime getExpectedDepartureTime() {
    return expectedDepartureTime;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  /**
   * @return The aimed departure time, or null if not applicable (e.g., destination stop)
   */
  @Nullable
  public ZonedDateTime getAimedDepartureTime() {
    return aimedDepartureTime;
  }

  /**
   * Returns the primary timing for this stop, preferring aimed arrival time.
   * This provides backward compatibility for code that expects a single time value.
   *
   * @return The aimed arrival time if set, otherwise aimed departure time
   */
  @Nullable
  public ZonedDateTime getEstimatedTime() {
    return aimedArrivalTime != null ? aimedArrivalTime : aimedDepartureTime;
  }

  public int getPassengerDelta() {
    return passengerDelta;
  }

  public Duration getDeviationBudget() {
    return deviationBudget;
  }

  @Override
  public boolean sameAs(CarpoolStop other) {
    return false;
  }

  @Override
  public CarpoolStopBuilder copy() {
    return new CarpoolStopBuilder(this);
  }
}
