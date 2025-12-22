package org.opentripplanner.ext.gbfsgeofencing.parameters;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Parameters for a single GBFS geofencing feed.
 */
public record GbfsGeofencingFeedParameters(
  String url,
  @Nullable String network,
  @Nullable String language,
  Map<String, String> httpHeaders
) {
  public GbfsGeofencingFeedParameters {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("GBFS feed URL is required");
    }
    if (httpHeaders == null) {
      httpHeaders = Map.of();
    }
  }
}
