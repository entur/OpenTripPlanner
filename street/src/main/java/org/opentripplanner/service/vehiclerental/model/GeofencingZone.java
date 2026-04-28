package org.opentripplanner.service.vehiclerental.model;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A geofencing zone describing restrictions for traversing with a rental vehicle or dropping it off
 * inside the zone. Each zone corresponds to a single rule from a GBFS geofencing zone feature.
 * <p>
 * Restriction fields ({@code dropOffBanned}, {@code traversalBanned}, {@code rideStartBanned}) are
 * nullable: {@code null} means the zone does not specify that field, allowing lower-priority zones
 * to contribute a value via per-field precedence resolution. {@code true} means banned,
 * {@code false} means explicitly allowed.
 * <p>
 * The {@code businessArea} flag is set at GBFS mapping time. A zone is a business area when all
 * ride/traversal booleans are permissive — fields like {@code maximumSpeedKph} and
 * {@code vehicleTypeIds} are orthogonal to business area classification.
 */
public record GeofencingZone(
  FeedScopedId id,
  @Nullable I18NString name,
  Geometry geometry,
  @Nullable Boolean dropOffBanned,
  @Nullable Boolean traversalBanned,
  @Nullable Boolean rideStartBanned,
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
      (Boolean) dropOffBanned,
      (Boolean) traversalBanned,
      Boolean.FALSE,
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
    return (
      Boolean.TRUE.equals(dropOffBanned) ||
      Boolean.TRUE.equals(traversalBanned) ||
      Boolean.TRUE.equals(rideStartBanned)
    );
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

  /**
   * Two zones are equal if they have the same id and priority. The geometry and restriction
   * fields are intentionally excluded — they are expensive to compare (JTS Geometry processes
   * all coordinates) and the id+priority pair uniquely identifies a zone within a feed.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GeofencingZone other)) {
      return false;
    }
    return priority == other.priority && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return 31 * id.hashCode() + priority;
  }

  /**
   * Resolve the effective value of a restriction field across overlapping zones for a given
   * network, using per-field precedence. For each field independently, the highest-priority zone
   * (lowest priority value) that specifies (non-null) the field wins.
   *
   * @param zones the set of zones currently containing the state
   * @param network the committed rental network (null returns false)
   * @param fieldAccessor accessor for the field to resolve
   * @return true if the resolved value is TRUE, false if no zone specifies the field or if the
   *     resolved value is FALSE
   */
  public static boolean resolveField(
    Set<GeofencingZone> zones,
    @Nullable String network,
    Function<GeofencingZone, Boolean> fieldAccessor
  ) {
    if (zones.isEmpty() || network == null) {
      return false;
    }
    Boolean bestValue = null;
    int bestPriority = Integer.MAX_VALUE;
    for (var zone : zones) {
      if (!zone.id().getFeedId().equals(network)) {
        continue;
      }
      Boolean value = fieldAccessor.apply(zone);
      if (value != null && zone.priority() < bestPriority) {
        bestPriority = zone.priority();
        bestValue = value;
      }
    }
    return Boolean.TRUE.equals(bestValue);
  }
}
