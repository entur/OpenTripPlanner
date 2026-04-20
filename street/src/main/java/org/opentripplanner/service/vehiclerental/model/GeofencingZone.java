package org.opentripplanner.service.vehiclerental.model;

import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A geofencing zone describing restrictions for traversing with a rental vehicle or dropping it off
 * inside the zone. Each zone corresponds to a single rule from a GBFS geofencing zone feature.
 * <p>
 * The {@code businessArea} flag and restriction fields ({@code dropOffBanned}, {@code traversalBanned},
 * {@code rideStartBanned}) are set at GBFS mapping time. A zone is a business area when all
 * ride/traversal booleans are permissive — fields like {@code maximumSpeedKph} and
 * {@code vehicleTypeIds} are orthogonal to business area classification.
 */
public record GeofencingZone(
  FeedScopedId id,
  @Nullable I18NString name,
  Geometry geometry,
  boolean dropOffBanned,
  boolean traversalBanned,
  boolean rideStartBanned,
  boolean businessArea,
  @Nullable List<String> vehicleTypeIds,
  @Nullable Integer maximumSpeedKph,
  int priority
) {
  /**
   * Convenience constructor for zones with only drop-off and traversal restrictions.
   * Sets {@code rideStartBanned} to false, infers {@code businessArea}, and uses default priority.
   */
  public GeofencingZone(
    FeedScopedId id,
    @Nullable I18NString name,
    Geometry geometry,
    boolean dropOffBanned,
    boolean traversalBanned
  ) {
    this(
      id,
      name,
      geometry,
      dropOffBanned,
      traversalBanned,
      false,
      !dropOffBanned && !traversalBanned,
      null,
      null,
      0
    );
  }

  /**
   * Whether the zone has any restriction that bans riding or dropping off.
   */
  public boolean hasRestriction() {
    return dropOffBanned || traversalBanned || rideStartBanned;
  }

  /**
   * Whether this zone describes a general business operating area. Riders can travel freely inside
   * but cannot leave the area. Enforced separately via {@code BusinessAreaBorder}.
   * <p>
   * This flag is set explicitly at GBFS mapping time rather than inferred from absence of
   * restrictions, because zones with only non-ride restrictions (e.g., speed limits) would
   * otherwise be misclassified as business areas.
   */
  public boolean isBusinessArea() {
    return businessArea;
  }
}
