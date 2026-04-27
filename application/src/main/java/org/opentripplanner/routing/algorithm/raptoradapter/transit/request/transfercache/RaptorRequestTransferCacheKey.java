package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.search.request.BikeRequest;
import org.opentripplanner.street.search.request.CarRequest;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.request.WalkRequest;
import org.opentripplanner.street.search.request.WheelchairRequest;
import org.opentripplanner.streetadapter.StreetSearchRequestMapper;
import org.opentripplanner.transfer.regular.model.Transfer;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Request parameters are bucketed to reduce transfer-cache key cardinality. Real-world
 * speeds and reluctances are client-supplied per request and cluster around many
 * close-but-distinct values (e.g. walk speed: 1.30, 1.31, 1.38, 1.39, ...); each distinct
 * value would otherwise trigger a ~900 ms transfer-index rebuild on first touch.
 * Rounding to the nearest value on a coarser grid (half-up on ties) collapses neighbouring
 * values into a shared cache entry.
 * <p>
 * Bucketing is applied symmetrically to the mode sub-request that the current transfer
 * mode includes. A deployment whose clients vary walk parameters benefits from the walk
 * buckets; a deployment whose clients vary bike speed sees the same benefit on the bike
 * buckets, and so on.
 * <ul>
 *   <li>walk speed:        step 0.05 m/s (mode includes walking)</li>
 *   <li>walk reluctance:   tiered step (mode includes walking)</li>
 *   <li>bike speed:        step 0.1 m/s (mode includes biking)</li>
 *   <li>bike reluctance:   tiered step (mode includes biking)</li>
 *   <li>car reluctance:    tiered step (mode includes driving)</li>
 *   <li>turn reluctance:   tiered step (always applied)</li>
 * </ul>
 * Tiered reluctance step: 0.1 below 3.0, 0.5 in [3.0, 10.0), 1.0 at 10.0 and above.
 * <p>
 * The resulting small deviation in pre-computed transfer times is acceptable; actual
 * access/egress/direct paths still use the caller's un-bucketed values.
 */
class RaptorRequestTransferCacheKey {

  private static final double WALK_SPEED_BUCKET = 0.05;
  private static final double BIKE_SPEED_BUCKET = 0.1;

  private static final double LOW_RELUCTANCE_BREAKPOINT = 3.0;
  private static final double HIGH_RELUCTANCE_BREAKPOINT = 10.0;
  private static final double LOW_RELUCTANCE_STEP = 0.1;
  private static final double MID_RELUCTANCE_STEP = 0.5;
  private static final double HIGH_RELUCTANCE_STEP = 1.0;

  private final List<List<Transfer>> transfersByStopIndex;
  private final StreetSearchRequest request;
  private final StreetRelevantOptions options;

  public RaptorRequestTransferCacheKey(
    List<List<Transfer>> transfersByStopIndex,
    RouteRequest request
  ) {
    this.transfersByStopIndex = transfersByStopIndex;
    var req = StreetSearchRequestMapper.mapToTransferRequest(request).build();
    req = bucketWalk(req);
    req = bucketBike(req);
    req = bucketCar(req);
    req = bucketTurnReluctance(req);
    this.request = req;
    this.options = new StreetRelevantOptions(this.request);
  }

  private static StreetSearchRequest bucketWalk(StreetSearchRequest request) {
    if (!request.mode().includesWalking()) {
      return request;
    }
    WalkRequest walk = request.walk();
    double currentSpeed = walk.speed();
    double bucketedSpeed = bucketTo(currentSpeed, WALK_SPEED_BUCKET);
    double currentReluctance = walk.reluctance();
    double bucketedReluctance = bucketTo(currentReluctance, reluctanceStep(currentReluctance));
    if (bucketedSpeed == currentSpeed && bucketedReluctance == currentReluctance) {
      return request;
    }
    return StreetSearchRequest.copyOf(request)
      .withWalk(b -> b.withSpeed(bucketedSpeed).withReluctance(bucketedReluctance))
      .build();
  }

  private static StreetSearchRequest bucketBike(StreetSearchRequest request) {
    if (!request.mode().includesBiking()) {
      return request;
    }
    BikeRequest bike = request.bike();
    double currentSpeed = bike.speed();
    double bucketedSpeed = bucketTo(currentSpeed, BIKE_SPEED_BUCKET);
    double currentReluctance = bike.reluctance();
    double bucketedReluctance = bucketTo(currentReluctance, reluctanceStep(currentReluctance));
    if (bucketedSpeed == currentSpeed && bucketedReluctance == currentReluctance) {
      return request;
    }
    return StreetSearchRequest.copyOf(request)
      .withBike(b -> b.withSpeed(bucketedSpeed).withReluctance(bucketedReluctance))
      .build();
  }

  private static StreetSearchRequest bucketCar(StreetSearchRequest request) {
    if (!request.mode().includesDriving()) {
      return request;
    }
    CarRequest car = request.car();
    double currentReluctance = car.reluctance();
    double bucketedReluctance = bucketTo(currentReluctance, reluctanceStep(currentReluctance));
    if (bucketedReluctance == currentReluctance) {
      return request;
    }
    return StreetSearchRequest.copyOf(request)
      .withCar(b -> b.withReluctance(bucketedReluctance))
      .build();
  }

  private static StreetSearchRequest bucketTurnReluctance(StreetSearchRequest request) {
    double currentReluctance = request.turnReluctance();
    double bucketedReluctance = bucketTo(currentReluctance, reluctanceStep(currentReluctance));
    if (bucketedReluctance == currentReluctance) {
      return request;
    }
    return StreetSearchRequest.copyOf(request).withTurnReluctance(bucketedReluctance).build();
  }

  private static double reluctanceStep(double reluctance) {
    if (reluctance < LOW_RELUCTANCE_BREAKPOINT) {
      return LOW_RELUCTANCE_STEP;
    }
    if (reluctance < HIGH_RELUCTANCE_BREAKPOINT) {
      return MID_RELUCTANCE_STEP;
    }
    return HIGH_RELUCTANCE_STEP;
  }

  /**
   * Round {@code value} to the nearest multiple of {@code step}, with ties rounded up.
   * BigDecimal is used to avoid floating-point bias at bucket boundaries (e.g. so that
   * {@code 2.05 / 0.1} is treated as exactly 20.5 and rounds to 2.1 rather than drifting
   * to 2.0 through IEEE-754 representation of 0.1).
   */
  private static double bucketTo(double value, double step) {
    return BigDecimal.valueOf(value)
      .divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(step))
      .doubleValue();
  }

  public List<List<Transfer>> transfersByStopIndex() {
    return transfersByStopIndex;
  }

  public StreetSearchRequest request() {
    return request;
  }

  public StreetRelevantOptions options() {
    return options;
  }

  @Override
  public int hashCode() {
    // transfersByStopIndex is ignored on purpose since it should not change (there is only
    // one instance per graph) and calculating the hashCode() would be expensive
    return options.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RaptorRequestTransferCacheKey cacheKey = (RaptorRequestTransferCacheKey) o;
    // transfersByStopIndex is checked using == on purpose since the instance should not change
    // (there is only one instance per graph)
    return (
      transfersByStopIndex == cacheKey.transfersByStopIndex && options.equals(cacheKey.options)
    );
  }

  /**
   * This contains an extract of the parameters which may influence transfers.
   * <p>
   */
  private static class StreetRelevantOptions {

    private final StreetMode transferMode;
    private final boolean wheelchairEnabled;
    private final WalkRequest walk;
    private final BikeRequest bike;
    private final CarRequest car;
    private final WheelchairRequest wheelchair;
    private final double turnReluctance;

    public StreetRelevantOptions(StreetSearchRequest request) {
      this.transferMode = request.mode();
      this.wheelchairEnabled = request.wheelchairEnabled();

      this.walk = transferMode.includesWalking() ? request.walk() : WalkRequest.DEFAULT;
      this.bike = transferMode.includesBiking() ? request.bike() : BikeRequest.DEFAULT;
      this.car = transferMode.includesDriving() ? request.car() : CarRequest.DEFAULT;
      this.turnReluctance = request.turnReluctance();
      this.wheelchair = request.wheelchairEnabled()
        ? request.wheelchair()
        : WheelchairRequest.DEFAULT;
    }

    @Override
    public String toString() {
      return ToStringBuilder.of(StreetRelevantOptions.class)
        .addEnum("transferMode", transferMode)
        .addBoolIfTrue("wheelchairEnabled", wheelchairEnabled)
        .addObj("walk", walk, WalkRequest.DEFAULT)
        .addObj("bike", bike, BikeRequest.DEFAULT)
        .addObj("car", car, CarRequest.DEFAULT)
        .addNum("turnReluctance", turnReluctance)
        .addObj("wheelchair", wheelchair, WheelchairRequest.DEFAULT)
        .toString();
    }

    @Override
    public int hashCode() {
      return Objects.hash(
        transferMode,
        wheelchairEnabled,
        walk,
        bike,
        car,
        turnReluctance,
        wheelchair
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof StreetRelevantOptions that)) {
        return false;
      }
      return (
        transferMode == that.transferMode &&
        wheelchairEnabled == that.wheelchairEnabled &&
        Objects.equals(that.walk, walk) &&
        Objects.equals(that.bike, bike) &&
        Objects.equals(that.car, car) &&
        Objects.equals(that.turnReluctance, turnReluctance) &&
        Objects.equals(that.wheelchair, wheelchair)
      );
    }
  }
}
