package org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.mobilitydata.gbfs.v3_0.gbfs.GBFSFeed;
import org.mobilitydata.gbfs.v3_0.gbfs.GBFSGbfs;
import org.mobilitydata.gbfs.v3_0.geofencing_zones.GBFSGeofencingZones;
import org.opentripplanner.framework.io.OtpHttpClient;
import org.opentripplanner.framework.json.ObjectMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple GBFS v3.0 HTTP client for fetching discovery and geofencing zone feeds.
 * This class uses OtpHttpClient directly to avoid dependencies on the vehicle rental updater.
 * <p>
 * Note: This sandbox module only supports GBFS v3.0 feeds. For v2.x support, use the
 * vehicle rental updater infrastructure.
 */
public class GbfsClient {

  private static final Logger LOG = LoggerFactory.getLogger(GbfsClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper MAPPER = ObjectMappers.ignoringExtraFields();

  private final OtpHttpClient httpClient;
  private final Map<String, String> headers;

  public GbfsClient(OtpHttpClient httpClient, Map<String, String> headers) {
    this.httpClient = httpClient;
    this.headers = headers;
  }

  /**
   * Fetches the geofencing zones URL from a GBFS v3.0 discovery file.
   */
  public Optional<URI> findGeofencingZonesUrl(URI gbfsUri) {
    try {
      var gbfs = httpClient.getAndMapAsJsonObject(
        gbfsUri,
        TIMEOUT,
        headers,
        MAPPER,
        GBFSGbfs.class
      );

      var data = gbfs.getData();
      if (data == null || data.getFeeds() == null) {
        return Optional.empty();
      }

      return data
        .getFeeds()
        .stream()
        .filter(f -> f.getName() == GBFSFeed.Name.GEOFENCING_ZONES)
        .map(f -> URI.create(f.getUrl()))
        .findFirst();
    } catch (Exception e) {
      LOG.warn("Failed to find geofencing zones URL from {}: {}", gbfsUri, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Fetches geofencing zones from a GBFS v3.0 feed.
   */
  public GBFSGeofencingZones fetchGeofencingZones(URI url) {
    return httpClient.getAndMapAsJsonObject(
      url,
      TIMEOUT,
      headers,
      MAPPER,
      GBFSGeofencingZones.class
    );
  }
}
