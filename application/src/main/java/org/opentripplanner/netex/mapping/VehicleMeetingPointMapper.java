package org.opentripplanner.netex.mapping;

import org.entur.netex2.model.LocationStructure;
import org.entur.netex2.model.VehicleMeetingPoint;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.netex.mapping.support.FeedScopedIdFactory;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.SiteRepositoryBuilder;

/**
 * Maps a NeTEx Part 5 {@code VehicleMeetingPoint} to an OTP {@link RegularStop}.
 * <p>
 * A carpool meeting point is "the car park outside the grocery store": a named coordinate created
 * by a service provider, short-lived, in large numbers, and with no attributes worth registering.
 * It deliberately has no StopPlace and no Quay, and therefore no parent station in OTP - which is
 * also what the GTFS carpool feeds produce, where the same points are a handful of columns in
 * {@code stops.txt}.
 */
class VehicleMeetingPointMapper {

  private final FeedScopedIdFactory idFactory;
  private final SiteRepositoryBuilder siteRepositoryBuilder;
  private final DataImportIssueStore issueStore;

  VehicleMeetingPointMapper(
    FeedScopedIdFactory idFactory,
    SiteRepositoryBuilder siteRepositoryBuilder,
    DataImportIssueStore issueStore
  ) {
    this.idFactory = idFactory;
    this.siteRepositoryBuilder = siteRepositoryBuilder;
    this.issueStore = issueStore;
  }

  @Nullable
  RegularStop map(VehicleMeetingPoint meetingPoint) {
    var coordinate = mapCoordinate(meetingPoint.getLocation());
    if (coordinate == null) {
      issueStore.add(
        "VehicleMeetingPointWithoutCoordinates",
        "VehicleMeetingPoint %s has no valid location and is dropped.",
        meetingPoint.getId()
      );
      return null;
    }
    var id = idFactory.createId(meetingPoint.getId());
    return siteRepositoryBuilder.computeRegularStopIfAbsent(id, it ->
      siteRepositoryBuilder
        .regularStop(it)
        .withName(
          NonLocalizedString.ofNullable(
            meetingPoint.getName(),
            MultilingualStringMapper::getStringValue,
            meetingPoint.getId()
          )
        )
        // NeTEx has no Description on a VehicleMeetingPoint - a meeting point carries its whole
        // identity in the name, e.g. "Strandveien - the car park at Nyhavna".
        .withCoordinate(coordinate)
        .build()
    );
  }

  @Nullable
  private static WgsCoordinate mapCoordinate(@Nullable LocationStructure location) {
    if (location == null || location.getLatitude() == null || location.getLongitude() == null) {
      return null;
    }
    return new WgsCoordinate(
      location.getLatitude().doubleValue(),
      location.getLongitude().doubleValue()
    );
  }
}
