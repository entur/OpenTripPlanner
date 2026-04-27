package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import static org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache.BucketGrid.BIKE_SPEED_BUCKET;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache.BucketGrid.WALK_SPEED_BUCKET;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache.BucketGrid.reluctanceStep;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache.BucketGrid.snapToStep;

import javax.annotation.Nullable;
import org.opentripplanner.street.search.request.BikeRequest;
import org.opentripplanner.street.search.request.CarRequest;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.request.StreetSearchRequestBuilder;
import org.opentripplanner.street.search.request.WalkRequest;

/**
 * Snaps a {@link StreetSearchRequest}'s client-supplied speeds and reluctances onto the
 * {@link BucketGrid} so close-but-distinct values share a transfer-cache key. Real-world
 * speeds and reluctances cluster around many close-but-distinct values
 * (e.g. walk speed: 1.30, 1.31, 1.38, 1.39, ...); each distinct value would otherwise
 * trigger a ~900 ms transfer-index rebuild on first touch. Rounding to the nearest value
 * on a coarser grid (half-up on ties) collapses neighbouring values into a shared cache
 * entry.
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
 * The resulting small deviation in pre-computed transfer times is acceptable; actual
 * access/egress/direct paths still use the caller's un-bucketed values.
 * <p>
 * Used as a transient context object: the builder is lazily allocated on the first field
 * that needs to change, so a request with multiple off-grid fields incurs at most one
 * {@code copyOf} + {@code build} round trip rather than one per field.
 */
final class TransferRequestBucketer {

  private final StreetSearchRequest request;

  @Nullable
  private StreetSearchRequestBuilder builder;

  static StreetSearchRequest bucket(StreetSearchRequest request) {
    return new TransferRequestBucketer(request).run();
  }

  private TransferRequestBucketer(StreetSearchRequest request) {
    this.request = request;
  }

  private StreetSearchRequest run() {
    bucketWalk();
    bucketBike();
    bucketCar();
    bucketTurnReluctance();
    return builder == null ? request : builder.build();
  }

  private void bucketWalk() {
    if (!request.mode().includesWalking()) {
      return;
    }
    WalkRequest walk = request.walk();
    double speed = snapToStep(walk.speed(), WALK_SPEED_BUCKET);
    double reluctance = snapToStep(walk.reluctance(), reluctanceStep(walk.reluctance()));
    if (speed == walk.speed() && reluctance == walk.reluctance()) {
      return;
    }
    ensureBuilder().withWalk(b -> b.withSpeed(speed).withReluctance(reluctance));
  }

  private void bucketBike() {
    if (!request.mode().includesBiking()) {
      return;
    }
    BikeRequest bike = request.bike();
    double speed = snapToStep(bike.speed(), BIKE_SPEED_BUCKET);
    double reluctance = snapToStep(bike.reluctance(), reluctanceStep(bike.reluctance()));
    if (speed == bike.speed() && reluctance == bike.reluctance()) {
      return;
    }
    ensureBuilder().withBike(b -> b.withSpeed(speed).withReluctance(reluctance));
  }

  private void bucketCar() {
    if (!request.mode().includesDriving()) {
      return;
    }
    CarRequest car = request.car();
    double reluctance = snapToStep(car.reluctance(), reluctanceStep(car.reluctance()));
    if (reluctance == car.reluctance()) {
      return;
    }
    ensureBuilder().withCar(b -> b.withReluctance(reluctance));
  }

  private void bucketTurnReluctance() {
    double current = request.turnReluctance();
    double reluctance = snapToStep(current, reluctanceStep(current));
    if (reluctance == current) {
      return;
    }
    ensureBuilder().withTurnReluctance(reluctance);
  }

  private StreetSearchRequestBuilder ensureBuilder() {
    if (builder == null) {
      builder = StreetSearchRequest.copyOf(request);
    }
    return builder;
  }
}
