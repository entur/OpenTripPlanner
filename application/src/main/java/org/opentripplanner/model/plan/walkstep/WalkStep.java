package org.opentripplanner.model.plan.walkstep;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.model.plan.leg.ElevationProfile;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.note.StreetNote;
import org.opentripplanner.transit.model.site.Entrance;
import org.opentripplanner.utils.lang.DoubleUtils;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Represents one instruction in walking directions. Three examples from New York City:
 * <p>
 * Turn onto Broadway from W 57th St (coming from 7th Ave): <br> distance = 100 (say) <br>
 * walkDirection = RIGHT <br> streetName = Broadway <br> everything else null/false <br>
 * </p>
 * <p>
 * Now, turn from Broadway onto Central Park S via Columbus Circle <br> distance = 200 (say) <br>
 * walkDirection = CIRCLE_COUNTERCLOCKWISE <br> streetName = Central Park S <br> exit = 1 (first
 * exit) <br> immediately everything else false <br>
 * </p>
 * <p>
 * Instead, go through the circle to continue on Broadway <br> distance = 100 (say) <br>
 * walkDirection = CIRCLE_COUNTERCLOCKWISE <br> streetName = Broadway <br> exit = 3 <br> stayOn =
 * true <br> everything else false <br>
 * </p>
 */
public final class WalkStep {

  private final WgsCoordinate startLocation;
  private final double distance;
  private final RelativeDirection relativeDirection;
  private final I18NString directionText;
  private final AbsoluteDirection absoluteDirection;

  private final Set<StreetNote> streetNotes;

  private final boolean area;
  private final boolean nameIsDerived;
  private final double angle;
  private final boolean walkingBike;

  private final String highwayExit;
  private final Entrance entrance;
  private final ElevationProfile elevationProfile;
  private final boolean stayOn;

  private final List<Edge> edges;

  WalkStep(
    WgsCoordinate startLocation,
    RelativeDirection relativeDirection,
    AbsoluteDirection absoluteDirection,
    I18NString directionText,
    Set<StreetNote> streetNotes,
    String highwayExit,
    Entrance entrance,
    ElevationProfile elevationProfile,
    boolean nameIsDerived,
    boolean walkingBike,
    boolean area,
    boolean stayOn,
    double angle,
    double distance,
    List<Edge> edges
  ) {
    this.distance = distance;
    this.relativeDirection = Objects.requireNonNull(relativeDirection);
    this.absoluteDirection = absoluteDirection;
    this.directionText = directionText;
    this.streetNotes = Set.copyOf(Objects.requireNonNull(streetNotes));
    this.startLocation = Objects.requireNonNull(startLocation);
    this.nameIsDerived = nameIsDerived;
    this.angle = DoubleUtils.roundTo2Decimals(angle);
    this.walkingBike = walkingBike;
    this.area = area;
    this.highwayExit = highwayExit;
    this.entrance = entrance;
    this.elevationProfile = elevationProfile;
    this.stayOn = stayOn;
    this.edges = List.copyOf(Objects.requireNonNull(edges));
  }

  public ElevationProfile getElevationProfile() {
    return elevationProfile;
  }

  public Set<StreetNote> getStreetNotes() {
    return streetNotes;
  }

  /**
   * The distance in meters that this step takes.
   */
  public double getDistance() {
    return distance;
  }

  /**
   * The relative direction of this step.
   */
  public RelativeDirection getRelativeDirection() {
    return relativeDirection;
  }

  /**
   * A piece of information that {@link WalkStep#getRelativeDirection()} relates to.
   * This could be the name of the street ("turn right at Main Street") but also a
   * station entrance ("enter station at Entrance 4B") or what is on a sign
   * ("follow signs for Platform 9").
   */
  public I18NString getDirectionText() {
    return directionText;
  }

  /**
   * The absolute direction of this step.
   * <p>
   * There are steps, like riding on an elevator, that don't have an absolute direction and
   * therefore the value is optional.
   */
  public Optional<AbsoluteDirection> getAbsoluteDirection() {
    return Optional.ofNullable(absoluteDirection);
  }

  /**
   * When exiting a highway or traffic circle, the exit name/number.
   */
  public Optional<String> highwayExit() {
    return Optional.ofNullable(highwayExit);
  }

  /**
   * Get information about a subway station entrance or exit.
   */
  public Optional<Entrance> entrance() {
    return Optional.ofNullable(entrance);
  }

  /**
   * Indicates whether a street changes direction at an intersection.
   */
  public boolean isStayOn() {
    return stayOn;
  }

  /**
   * This step is on an open area, such as a plaza or train platform, and thus the directions should
   * say something like "cross"
   */
  public boolean getArea() {
    return area;
  }

  /**
   * The name of this street was generated by the system, so we should only display it once, and
   * generally just display right/left directions
   * @see Edge#nameIsDerived()
   */
  public boolean nameIsDerived() {
    return nameIsDerived;
  }

  /**
   * The coordinate of start of the step
   */
  public WgsCoordinate getStartLocation() {
    return startLocation;
  }

  public double getAngle() {
    return angle;
  }

  /**
   * Is this step walking with a bike?
   */
  public boolean isWalkingBike() {
    return walkingBike;
  }

  /**
   * The street edges that make up this walkStep. Used only in generating the streetEdges array in
   * StreetSegment; not serialized.
   */
  public List<Edge> getEdges() {
    return edges;
  }

  public static WalkStepBuilder builder() {
    return new WalkStepBuilder();
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(this.getClass())
      .addEnum("absoluteDirection", absoluteDirection)
      .addEnum("relativeDirection", relativeDirection)
      .addStr("streetName", directionText.toString())
      .addNum("distance", distance)
      .toString();
  }
}
