package org.opentripplanner.ext.carpooling.model;

import java.time.ZonedDateTime;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.framework.AbstractTransitEntity;

/**
 * Represents a stop along a carpool trip route with occupancy and timing information.
 * Stops are ordered sequentially along the route.
 */
public class CarpoolStop extends AbstractTransitEntity<CarpoolStop, CarpoolStopBuilder> {

  /** Default onboard count per stop (1 = driver only) when no occupancy information is provided. */
  public static final int DEFAULT_ONBOARD_COUNT = 1;

  private final WgsCoordinate coordinate;
  private final ZonedDateTime expectedArrivalTime;
  private final ZonedDateTime aimedArrivalTime;
  private final ZonedDateTime expectedDepartureTime;
  private final ZonedDateTime aimedDepartureTime;
  private final int sequenceNumber;
  private final int onboardCount;

  public CarpoolStop(CarpoolStopBuilder builder) {
    super(builder.getId());
    this.coordinate = Objects.requireNonNull(builder.coordinate());
    this.expectedArrivalTime = builder.expectedArrivalTime();
    this.aimedArrivalTime = builder.aimedArrivalTime();
    this.expectedDepartureTime = builder.expectedDepartureTime();
    this.aimedDepartureTime = builder.aimedDepartureTime();
    this.sequenceNumber = builder.sequenceNumber();
    this.onboardCount = builder.onboardCount();
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

  /**
   * @return The 0-based position of this stop in the trip's stop sequence
   */
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
   * @return The number of passengers onboard (including the driver) when departing this stop
   */
  public int getOnboardCount() {
    return onboardCount;
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
