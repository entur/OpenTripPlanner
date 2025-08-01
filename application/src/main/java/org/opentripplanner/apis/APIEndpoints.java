package org.opentripplanner.apis;

import static org.opentripplanner.framework.application.OTPFeature.APIServerInfo;
import static org.opentripplanner.framework.application.OTPFeature.APIUpdaterStatus;
import static org.opentripplanner.framework.application.OTPFeature.ActuatorAPI;
import static org.opentripplanner.framework.application.OTPFeature.DebugRasterTiles;
import static org.opentripplanner.framework.application.OTPFeature.DebugUi;
import static org.opentripplanner.framework.application.OTPFeature.GtfsGraphQlApi;
import static org.opentripplanner.framework.application.OTPFeature.ReportApi;
import static org.opentripplanner.framework.application.OTPFeature.SandboxAPIGeocoder;
import static org.opentripplanner.framework.application.OTPFeature.SandboxAPIMapboxVectorTilesApi;
import static org.opentripplanner.framework.application.OTPFeature.SandboxAPIParkAndRideApi;
import static org.opentripplanner.framework.application.OTPFeature.TransmodelGraphQlApi;
import static org.opentripplanner.framework.application.OTPFeature.TriasApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opentripplanner.api.resource.ServerInfo;
import org.opentripplanner.api.resource.UpdaterStatusResource;
import org.opentripplanner.apis.gtfs.GtfsGraphQLAPI;
import org.opentripplanner.apis.transmodel.TransmodelAPI;
import org.opentripplanner.apis.vectortiles.DebugVectorTilesResource;
import org.opentripplanner.ext.actuator.ActuatorAPI;
import org.opentripplanner.ext.debugrastertiles.api.resource.DebugRasterTileResource;
import org.opentripplanner.ext.geocoder.GeocoderResource;
import org.opentripplanner.ext.parkAndRideApi.ParkAndRideResource;
import org.opentripplanner.ext.reportapi.resource.ReportResource;
import org.opentripplanner.ext.trias.trias.TriasResource;
import org.opentripplanner.ext.vectortiles.VectorTilesResource;
import org.opentripplanner.framework.application.OTPFeature;

/**
 * Configure API resource endpoints.
 */
public class APIEndpoints {

  private final List<Class<?>> resources = new ArrayList<>();

  private APIEndpoints() {
    // Add feature enabled APIs, these can be enabled by default, some is not.
    // See the OTPFeature enum for details.
    addIfEnabled(APIServerInfo, ServerInfo.class);
    addIfEnabled(APIUpdaterStatus, UpdaterStatusResource.class);
    addIfEnabled(DebugUi, DebugVectorTilesResource.class);
    addIfEnabled(GtfsGraphQlApi, GtfsGraphQLAPI.class);
    // scheduled to be removed and only here for backwards compatibility
    addIfEnabled(GtfsGraphQlApi, GtfsGraphQLAPI.GtfsGraphQLAPIOldPath.class);
    addIfEnabled(TransmodelGraphQlApi, TransmodelAPI.class);
    // scheduled to be removed and only here for backwards compatibility
    addIfEnabled(TransmodelGraphQlApi, TransmodelAPI.TransmodelAPIOldPath.class);

    // Sandbox extension APIs
    addIfEnabled(ActuatorAPI, ActuatorAPI.class);
    addIfEnabled(DebugRasterTiles, DebugRasterTileResource.class);
    addIfEnabled(ReportApi, ReportResource.class);
    addIfEnabled(SandboxAPIMapboxVectorTilesApi, VectorTilesResource.class);
    addIfEnabled(SandboxAPIParkAndRideApi, ParkAndRideResource.class);
    addIfEnabled(SandboxAPIGeocoder, GeocoderResource.class);
    addIfEnabled(TriasApi, TriasResource.class);
  }

  /**
   * List all mandatory and feature enabled endpoints as Jersey resource classes: define web
   * services, i.e. an HTTP APIs.
   * <p>
   * Some of the endpoints can be turned on/off using {@link OTPFeature}s, this method check if an
   * endpoint is enabled before adding it to the list.
   *
   * @return all mandatory and feature enabled endpoints are returned.
   */
  public static Collection<? extends Class<?>> listAPIEndpoints() {
    return Collections.unmodifiableCollection(new APIEndpoints().resources);
  }

  /**
   * Add feature to list of classes if the feature is enabled (turned on).
   *
   * @param apiFeature the feature to check.
   * @param resource   the resource to enable if feature is enabled.
   */
  private void addIfEnabled(OTPFeature apiFeature, Class<?> resource) {
    if (apiFeature.isOn()) {
      add(resource);
    }
  }

  private void add(Class<?> resource) {
    resources.add(resource);
  }
}
