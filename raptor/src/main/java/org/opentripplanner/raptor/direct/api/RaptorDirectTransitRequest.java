package org.opentripplanner.raptor.direct.api;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.raptor.api.model.GeneralizedCostRelaxFunction;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.model.RaptorConstants;
import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * All input parameters to do a direct search.
 */
public final class RaptorDirectTransitRequest {

  private final int earliestDepartureTime;
  private final int searchWindowInSeconds;
  private final RelaxFunction relaxC1;
  private final Collection<RaptorAccessEgress> accessPaths;
  private final Collection<RaptorAccessEgress> egressPaths;

  private RaptorDirectTransitRequest() {
    this.earliestDepartureTime = RaptorConstants.TIME_NOT_SET;
    this.searchWindowInSeconds = RaptorConstants.NOT_SET;
    // TODO: Is this the right place?
    this.relaxC1 = GeneralizedCostRelaxFunction.of(2, 20 * 60 * 100);
    this.accessPaths = List.of();
    this.egressPaths = List.of();
  }

  public static RaptorDirectTransitRequest defaults() {
    return new RaptorDirectTransitRequest();
  }

  public static RaptorDirectTransitRequestBuilder of() {
    return new RaptorDirectTransitRequestBuilder(defaults());
  }

  public RaptorDirectTransitRequest(
    int earliestDepartureTime,
    int searchWindowInSeconds,
    RelaxFunction relaxC1,
    Collection<RaptorAccessEgress> accessPaths,
    Collection<RaptorAccessEgress> egressPaths
  ) {
    this.earliestDepartureTime = earliestDepartureTime;
    this.searchWindowInSeconds = searchWindowInSeconds;
    this.relaxC1 = Objects.requireNonNull(relaxC1);
    this.accessPaths = Objects.requireNonNull(accessPaths);
    this.egressPaths = Objects.requireNonNull(egressPaths);
    verify();
  }

  /**
   * The earliest a journey can depart from the origin. The unit is seconds since midnight.
   * Inclusive.
   * <p>
   * In the case of a 'depart after' search this is a required. In the case of a 'arrive by' search
   * this is optional, but it will improve performance if it is set.
   */
  public int earliestDepartureTime() {
    return earliestDepartureTime;
  }

  public boolean isEarliestDepartureTimeSet() {
    return earliestDepartureTime != RaptorConstants.TIME_NOT_SET;
  }

  /**
   * The time window used to search. The unit is seconds.
   * <p>
   * For a *depart-by-search*, this is added to the 'earliestDepartureTime' to find the
   * 'latestDepartureTime'.
   * <p>
   * For an *arrive-by-search* this is used to calculate the 'earliestArrivalTime'. The algorithm
   * will find all optimal travels within the given time window.
   * <p>
   * Set the search window to 0 (zero) to run 1 iteration.
   * <p>
   * Required. Must be a positive integer or 0(zero).
   */
  public int searchWindowInSeconds() {
    return searchWindowInSeconds;
  }

  public boolean isSearchWindowSet() {
    return searchWindowInSeconds != RaptorConstants.NOT_SET;
  }

  public boolean searchOneIterationOnly() {
    return searchWindowInSeconds == 0;
  }

  /**
   * TODO: JavaDoc
   * @return
   */
  public RelaxFunction relaxC1() {
    return relaxC1;
  }

  /**
   * List of access paths from the origin to all transit stops using the street network.
   * <p/>
   * Required, at least one access path must exist.
   */
  public Collection<RaptorAccessEgress> accessPaths() {
    return accessPaths;
  }

  /**
   * List of all possible egress paths to reach the destination using the street network.
   * <p>
   * NOTE! The {@link RaptorTransfer#stop()} is the stop where the egress path start, NOT the
   * destination - think of it as a reversed path.
   * <p/>
   * Required, at least one egress path must exist.
   */
  public Collection<RaptorAccessEgress> egressPaths() {
    return egressPaths;
  }

  /**
   * Get the maximum duration of any access or egress path in seconds.
   */
  public int accessEgressMaxDurationSeconds() {
    return Math.max(
      accessPaths.stream().mapToInt(RaptorAccessEgress::durationInSeconds).max().orElse(0),
      egressPaths.stream().mapToInt(RaptorAccessEgress::durationInSeconds).max().orElse(0)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      earliestDepartureTime,
      searchWindowInSeconds,
      relaxC1,
      accessPaths,
      egressPaths
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof RaptorDirectTransitRequest that) {
      return (
        earliestDepartureTime == that.earliestDepartureTime &&
        searchWindowInSeconds == that.searchWindowInSeconds &&
        relaxC1.equals(that.relaxC1) &&
        accessPaths.equals(that.accessPaths) &&
        egressPaths.equals(that.egressPaths)
      );
    }
    return false;
  }

  @Override
  public String toString() {
    var dft = defaults();
    return ToStringBuilder.of(RaptorDirectTransitRequest.class)
      .addServiceTime("earliestDepartureTime", earliestDepartureTime, dft.earliestDepartureTime)
      .addDurationSec("searchWindow", searchWindowInSeconds, dft.searchWindowInSeconds)
      .addObj("relaxC1", relaxC1, dft.relaxC1)
      .addCollection("accessPaths", accessPaths, 5, RaptorAccessEgress::defaultToString)
      .addCollection("egressPaths", egressPaths, 5, RaptorAccessEgress::defaultToString)
      .toString();
  }

  /* private methods */
  void verify() {
    assertProperty(isEarliestDepartureTimeSet(), "'earliestDepartureTime' is required.");
    assertProperty(!accessPaths.isEmpty(), "At least one 'accessPath' is required.");
    assertProperty(!egressPaths.isEmpty(), "At least one 'egressPath' is required.");
  }

  static void assertProperty(boolean predicate, String message) {
    if (!predicate) {
      throw new IllegalArgumentException(message);
    }
  }
}
