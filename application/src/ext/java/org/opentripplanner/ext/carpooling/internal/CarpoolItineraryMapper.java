package org.opentripplanner.ext.carpooling.internal;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.core.model.basic.Cost;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.LocalizedString;
import org.opentripplanner.ext.carpooling.model.CarpoolLeg;
import org.opentripplanner.ext.carpooling.routing.CarpoolAccessEgress;
import org.opentripplanner.ext.carpooling.routing.InsertionCandidate;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.leg.StreetLeg;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Maps carpooling insertion candidates to OTP itineraries for API responses.
 * <p>
 * This mapper bridges between the carpooling domain model ({@link InsertionCandidate}) and
 * OTP's standard itinerary model ({@link Itinerary}). It extracts the passenger's journey
 * portion from the complete driver route and constructs an itinerary with timing, geometry,
 * and cost information.
 *
 * <h2>Itinerary Shape</h2>
 * <p>
 * Both direct-mode and access/egress itineraries have between one and three legs, emitted in
 * chronological order:
 * <ol>
 *   <li>An optional leading {@link StreetLeg} (WALK) from the passenger-side location (origin
 *       for direct/access, transit stop for egress) to the snapped carpool pickup vertex.</li>
 *   <li>A {@link CarpoolLeg} covering the shared ride between pickup and dropoff. The
 *       boarding dwell at the pickup is included in this leg's duration, not added before it.</li>
 *   <li>An optional trailing {@link StreetLeg} (WALK) from the snapped dropoff vertex to the
 *       dropoff-side location (destination for direct/egress, transit stop for access).</li>
 * </ol>
 *
 * <h2>Time Calculation</h2>
 * <p>
 * The carpool leg's start time is the moment the driver arrives at the pickup
 * (trip start + pickup travel). If a leading walk leg exists, its start is back-shifted by the
 * walk's duration; the trailing walk leg's end is forward-shifted similarly. The itinerary is
 * <em>not</em> shifted to match the passenger's requested departure time — the driver is on a
 * committed schedule and cannot wait. Whether the passenger should show up early or be matched
 * at all is a filtering concern upstream of this mapper.
 *
 * <h2>Place naming</h2>
 * <p>
 * The itinerary's outermost endpoints (first leg's {@code from}, last leg's {@code to}) are
 * resolved with this precedence so they match what regular WALK/CAR_PICKUP itineraries show
 * for the same passenger location:
 * <ol>
 *   <li>If the endpoint is a transit stop (access/egress), label with the stop's name via
 *       {@link Place#forStop(StopLocation)}.</li>
 *   <li>Else if the endpoint is the user's input location, label via
 *       {@link Place#forGenericLocation(GenericLocation, I18NString)} so the user-supplied
 *       label is used when set, falling back to the localized {@code "origin"} /
 *       {@code "destination"} names that the standard request flow produces.</li>
 *   <li>Else (intermediate boundaries between walk and carpool legs) fall back to vertex-based
 *       naming via {@link #makePlace(Vertex)}: {@link StreetVertex#getIntersectionName()} for
 *       street intersections (composed from OSM street names; mode-agnostic), the vertex's
 *       pre-set name for {@link TemporaryStreetLocation}s.</li>
 * </ol>
 *
 * <h2>Package Location</h2>
 * <p>
 * This class is in the {@code internal} package because it's an implementation detail of
 * the carpooling service. API consumers interact with {@link Itinerary} objects, not this mapper.
 *
 * @see InsertionCandidate for the source data structure
 * @see CarpoolLeg for the carpool-specific leg type
 * @see Itinerary for the OTP itinerary model
 */
public class CarpoolItineraryMapper {

  private static final I18NString ORIGIN_DEFAULT_NAME = new LocalizedString("origin");
  private static final I18NString DESTINATION_DEFAULT_NAME = new LocalizedString("destination");

  /**
   * Converts an insertion candidate into an OTP itinerary representing the passenger's journey.
   * The itinerary contains a {@link CarpoolLeg} for the shared ride, optionally preceded by a
   * WALK {@link StreetLeg} from the passenger's origin to the snapped pickup vertex and
   * optionally followed by a WALK leg from the snapped dropoff vertex to the destination.
   *
   * @param candidate the insertion candidate containing route segments, trip details, and
   *        optional walk paths around the carpool pickup/dropoff
   * @param carpoolReluctance multiplier applied to ride seconds when computing the carpool leg's
   *        {@code generalizedCost}.
   * @param fromLocation the request's {@code from} location (passenger origin), used to label
   *        the first leg's {@code from} place. May be {@code null}, in which case the boundary
   *        falls back to vertex-based naming.
   * @param toLocation the request's {@code to} location (passenger destination), used to label
   *        the last leg's {@code to} place. May be {@code null}, in which case the boundary
   *        falls back to vertex-based naming.
   * @return an itinerary for the passenger's journey, or {@code null} if the candidate has no
   *         shared segments (a safety check that should not trigger for valid candidates)
   */
  @Nullable
  public Itinerary toItinerary(
    InsertionCandidate candidate,
    double carpoolReluctance,
    @Nullable GenericLocation fromLocation,
    @Nullable GenericLocation toLocation
  ) {
    var sharedSegments = candidate.getSharedSegments();
    if (sharedSegments.isEmpty()) {
      return null;
    }
    var carpoolStart = candidate.trip().startTime().plus(candidate.getDurationUntilPickupArrival());
    var carpoolEnd = carpoolStart.plus(candidate.getPassengerRideDuration());
    return buildItinerary(
      sharedSegments,
      candidate.walkToPickup(),
      candidate.walkFromDropoff(),
      carpoolStart,
      carpoolEnd,
      candidate.getPassengerRideWeight(carpoolReluctance),
      null,
      null,
      fromLocation,
      toLocation
    );
  }

  private static CarpoolLeg buildCarpoolLeg(
    List<GraphPath<State, Edge, Vertex>> sharedSegments,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    double rideWeight,
    Place fromPlace,
    Place toPlace
  ) {
    var allEdges = sharedSegments
      .stream()
      .flatMap(seg -> seg.edges.stream())
      .toList();

    return CarpoolLeg.of()
      .withStartTime(startTime)
      .withEndTime(endTime)
      .withFrom(fromPlace)
      .withTo(toPlace)
      .withGeometry(GeometryUtils.concatenateLineStrings(allEdges, Edge::getGeometry))
      .withDistanceMeters(allEdges.stream().mapToDouble(Edge::getDistanceMeters).sum())
      .withGeneralizedCost((int) rideWeight)
      .build();
  }

  private static StreetLeg buildWalkLeg(
    GraphPath<State, Edge, Vertex> walkPath,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    Place fromPlace,
    Place toPlace
  ) {
    LineString geometry = GeometryUtils.concatenateLineStrings(walkPath.edges, Edge::getGeometry);
    double distance = walkPath.edges.stream().mapToDouble(Edge::getDistanceMeters).sum();

    return StreetLeg.of()
      .withMode(TraverseMode.WALK)
      .withStartTime(startTime)
      .withEndTime(endTime)
      .withFrom(fromPlace)
      .withTo(toPlace)
      .withGeometry(geometry)
      .withDistanceMeters(distance)
      .withGeneralizedCost((int) walkPath.getWeight())
      .build();
  }

  /**
   * Converts a Raptor carpool access/egress leg back into an OTP itinerary for the response. Same
   * shape as {@link #toItinerary(InsertionCandidate, double)} — an optional WALK leg, the carpool
   * leg, and an optional WALK leg — but driven from the {@link CarpoolAccessEgress} accessors so
   * the displayed times and ride cost agree with the values Raptor used during the search.
   *
   * @return the itinerary, or {@code null} if the access/egress has no shared segments (a safety
   *         check that should not trigger for valid legs)
   */
  @Nullable
  public Itinerary toItinerary(CarpoolAccessEgress accessEgress) {
    var sharedSegments = accessEgress.sharedSegments();
    if (sharedSegments.isEmpty()) {
      return null;
    }
    return buildItinerary(
      sharedSegments,
      accessEgress.walkToPickup(),
      accessEgress.walkFromDropoff(),
      accessEgress.getCarpoolStart(),
      accessEgress.getCarpoolEnd(),
      accessEgress.getPassengerRideWeight(),
      accessEgress.startStop(),
      accessEgress.endStop(),
      accessEgress.startLocation(),
      accessEgress.endLocation()
    );
  }

  /**
   * Assembles a WALK + CARPOOL + WALK itinerary around the shared ride. Walk legs are omitted
   * when their corresponding path is {@code null}; the walk legs' outer times are derived from
   * {@code carpoolStart}/{@code carpoolEnd} plus the walk durations.
   * <p>
   * Boundary-place precedence at the chain's outermost endpoints: a non-null {@code startStop}/
   * {@code endStop} (access/egress transit-stop side) wins; else a non-null {@code fromLocation}/
   * {@code toLocation} (passenger origin/destination) labels via
   * {@link Place#forGenericLocation(GenericLocation, I18NString)}; else the underlying vertex
   * is named via {@link #makePlace(Vertex)}.
   */
  private static Itinerary buildItinerary(
    List<GraphPath<State, Edge, Vertex>> sharedSegments,
    @Nullable GraphPath<State, Edge, Vertex> walkToPickup,
    @Nullable GraphPath<State, Edge, Vertex> walkFromDropoff,
    ZonedDateTime carpoolStart,
    ZonedDateTime carpoolEnd,
    double rideWeight,
    @Nullable StopLocation startStop,
    @Nullable StopLocation endStop,
    @Nullable GenericLocation fromLocation,
    @Nullable GenericLocation toLocation
  ) {
    Vertex pickupVertex = sharedSegments.getFirst().states.getFirst().getVertex();
    Vertex dropoffVertex = sharedSegments.getLast().states.getLast().getVertex();
    Place pickupPlace = makePlace(pickupVertex);
    Place dropoffPlace = makePlace(dropoffVertex);

    Vertex startBoundaryVertex = walkToPickup != null
      ? walkToPickup.states.getFirst().getVertex()
      : pickupVertex;
    Vertex endBoundaryVertex = walkFromDropoff != null
      ? walkFromDropoff.states.getLast().getVertex()
      : dropoffVertex;

    Place itineraryStart = boundaryPlace(
      startStop,
      fromLocation,
      ORIGIN_DEFAULT_NAME,
      startBoundaryVertex
    );
    Place itineraryEnd = boundaryPlace(
      endStop,
      toLocation,
      DESTINATION_DEFAULT_NAME,
      endBoundaryVertex
    );

    Place carpoolFromPlace = walkToPickup != null ? pickupPlace : itineraryStart;
    Place carpoolToPlace = walkFromDropoff != null ? dropoffPlace : itineraryEnd;

    List<Leg> legs = new ArrayList<>(3);
    if (walkToPickup != null) {
      var walkStart = carpoolStart.minusSeconds(walkToPickup.getDuration());
      legs.add(buildWalkLeg(walkToPickup, walkStart, carpoolStart, itineraryStart, pickupPlace));
    }
    legs.add(
      buildCarpoolLeg(
        sharedSegments,
        carpoolStart,
        carpoolEnd,
        rideWeight,
        carpoolFromPlace,
        carpoolToPlace
      )
    );
    if (walkFromDropoff != null) {
      var walkEnd = carpoolEnd.plusSeconds(walkFromDropoff.getDuration());
      legs.add(buildWalkLeg(walkFromDropoff, carpoolEnd, walkEnd, dropoffPlace, itineraryEnd));
    }

    int totalCost = legs.stream().mapToInt(Leg::generalizedCost).sum();
    return Itinerary.ofDirect(legs).withGeneralizedCost(Cost.costOfSeconds(totalCost)).build();
  }

  /**
   * Resolves the outermost endpoint of an itinerary using the precedence transit stop &gt;
   * user input location &gt; vertex-derived name. The first two are needed because the
   * underlying State chain doesn't always carry a vertex with the right name — the transit-side
   * end of an access/egress walk is a regular street vertex, and when no walk is needed at all
   * the State chain skips the user's temporary origin/destination vertex and starts straight at
   * the snapped pickup/dropoff (which has only an OSM intersection name).
   */
  private static Place boundaryPlace(
    @Nullable StopLocation stop,
    @Nullable GenericLocation location,
    I18NString locationDefaultName,
    Vertex fallbackVertex
  ) {
    if (stop != null) {
      return Place.forStop(stop);
    }
    if (location != null) {
      return Place.forGenericLocation(location, locationDefaultName);
    }
    return makePlace(fallbackVertex);
  }

  /**
   * Builds a {@link Place} from a vertex using the same naming logic the core street-leg
   * mapper applies for any mode. {@link StreetVertex} intersections get the localized
   * "corner of X and Y" name composed from the outgoing OSM street names (mode-agnostic);
   * {@link TemporaryStreetLocation}s (passenger origin/destination) keep the name they were
   * created with; everything else falls back to {@link Vertex#getName()}.
   */
  private static Place makePlace(Vertex vertex) {
    if (vertex instanceof StreetVertex sv && !(vertex instanceof TemporaryStreetLocation)) {
      return Place.normal(vertex, sv.getIntersectionName());
    }
    return Place.normal(vertex, vertex.getName());
  }
}
