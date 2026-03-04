package org.opentripplanner.ext.gbfsgeofencing.parameters;

import javax.annotation.Nullable;
import org.opentripplanner.framework.io.HttpHeaders;
import org.opentripplanner.gbfs.GbfsDataSourceParameters;

/**
 * Parameters for a single GBFS geofencing feed.
 * Implements {@link GbfsDataSourceParameters} so it can be passed directly to
 * {@link org.opentripplanner.gbfs.GbfsFeedLoaderAndMapper}.
 */
public record GbfsGeofencingFeedParameters(
  String url,
  @Nullable String network,
  HttpHeaders httpHeaders
) implements GbfsDataSourceParameters {
  public GbfsGeofencingFeedParameters {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("GBFS feed URL is required");
    }
    if (httpHeaders == null) {
      httpHeaders = HttpHeaders.empty();
    }
  }

  @Override
  public String language() {
    return "en";
  }

  @Override
  public boolean allowKeepingRentedVehicleAtDestination() {
    return false;
  }

  @Override
  public boolean geofencingZones() {
    return true;
  }

  @Override
  public boolean overloadingAllowed() {
    return false;
  }

  @Override
  public boolean allowStationRental() {
    return true;
  }

  @Override
  public boolean allowFreeFloatingRental() {
    return true;
  }
}
