package org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingFeedParameters;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingParameters;
import org.opentripplanner.framework.io.OtpHttpClientFactory;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graph builder module that loads GBFS v3 geofencing zones at build time and applies
 * them to street edges.
 * <p>
 * This is a standalone implementation that does not depend on the vehicle rental
 * updater infrastructure.
 * <p>
 * <strong>Note:</strong> Only GBFS v3.0 feeds are supported. GBFS v2.x feeds are not supported.
 */
public class GbfsGeofencingGraphBuilder implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(GbfsGeofencingGraphBuilder.class);

  private final GbfsGeofencingParameters parameters;
  private final GbfsGeofencingRepository repository;
  private final Graph graph;

  public GbfsGeofencingGraphBuilder(
    GbfsGeofencingParameters parameters,
    GbfsGeofencingRepository repository,
    Graph graph
  ) {
    this.parameters = parameters;
    this.repository = repository;
    this.graph = graph;
  }

  @Override
  public void buildGraph() {
    LOG.info(
      "Loading GBFS v3 geofencing zones at build time from {} feed(s)",
      parameters.feeds().size()
    );

    List<GeofencingZone> allZones = new ArrayList<>();

    try (var httpClientFactory = new OtpHttpClientFactory()) {
      for (var feedParams : parameters.feeds()) {
        try {
          var zones = loadGeofencingZonesFromFeed(feedParams, httpClientFactory);
          allZones.addAll(zones);
          LOG.info(
            "Loaded {} geofencing zones from GBFS v3 feed: {}",
            zones.size(),
            feedParams.url()
          );
        } catch (Exception e) {
          LOG.error(
            "Failed to load geofencing zones from GBFS feed: {}. Error: {}",
            feedParams.url(),
            e.getMessage()
          );
        }
      }
    }

    if (allZones.isEmpty()) {
      LOG.info("No geofencing zones loaded from any GBFS feeds");
      return;
    }

    // Apply zones using standalone applier
    var applier = new GeofencingZoneApplier(graph::findEdges);
    var modifiedEdges = applier.applyGeofencingZones(allZones);

    repository.addGeofencingZones(allZones);
    repository.setModifiedEdgeCount(modifiedEdges.size());

    LOG.info(
      "Applied {} geofencing zones to {} street edges at build time",
      allZones.size(),
      modifiedEdges.size()
    );
  }

  private List<GeofencingZone> loadGeofencingZonesFromFeed(
    GbfsGeofencingFeedParameters feedParams,
    OtpHttpClientFactory httpClientFactory
  ) {
    var httpClient = httpClientFactory.create(LOG);
    var headers = feedParams.httpHeaders();
    var gbfsClient = new GbfsClient(httpClient, headers);
    var gbfsUri = URI.create(feedParams.url());

    // Determine network ID
    String network = feedParams.network() != null
      ? feedParams.network()
      : extractNetworkFromUrl(feedParams.url());

    Optional<URI> zonesUrl = gbfsClient.findGeofencingZonesUrl(gbfsUri);
    if (zonesUrl.isEmpty()) {
      LOG.warn("No geofencing zones feed found in GBFS v3 discovery file: {}", gbfsUri);
      return List.of();
    }

    var zones = gbfsClient.fetchGeofencingZones(zonesUrl.get());
    var mapper = new GeofencingZoneMapper(network);
    return mapper.mapGeofencingZones(zones);
  }

  /**
   * Extract a network identifier from the GBFS URL.
   * For example, "https://api.example.com/gbfs/voistavanger/gbfs.json" -> "voistavanger"
   */
  private String extractNetworkFromUrl(String url) {
    var parts = url.split("/");
    for (int i = parts.length - 1; i >= 0; i--) {
      var part = parts[i];
      if (!part.isEmpty() && !part.equals("gbfs.json") && !part.equals("gbfs")) {
        return part;
      }
    }
    return "unknown";
  }
}
