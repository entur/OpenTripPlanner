package org.opentripplanner.standalone.config.routerconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import javax.annotation.Nullable;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * Configuration for application warmup during startup. When configured, OTP runs transit routing
 * queries between the given locations during startup to warm up the application (JIT compilation,
 * GraphQL schema caches, routing data structures, etc.) before production traffic arrives.
 * <p>
 * Queries start after the Raptor transit data is created and stop when all updaters are
 * primed (the health endpoint would return "UP"). If no updaters are configured, no warmup
 * queries are run.
 */
public class WarmupConfig {

  public enum Api {
    TRANSMODEL,
    GTFS,
  }

  private final Api api;
  private final WgsCoordinate from;
  private final WgsCoordinate to;

  private WarmupConfig(Api api, WgsCoordinate from, WgsCoordinate to) {
    this.api = api;
    this.from = from;
    this.to = to;
  }

  @Nullable
  public static WarmupConfig mapWarmupConfig(String parameterName, NodeAdapter root) {
    if (!root.exist(parameterName)) {
      return null;
    }

    var c = root
      .of(parameterName)
      .since(V2_9)
      .summary("Configure application warmup by running transit searches during startup.")
      .description(
        """
        When configured, OTP runs transit routing queries between the given locations
        during startup. This warms up the application (JIT compilation, GraphQL schema
        caches, routing data structures, etc.) before production traffic arrives.
        Queries start after the Raptor transit data is created and stop when all updaters
        are primed (the health endpoint would return "UP").
        If no updaters are configured, no warmup queries are run.
        """
      )
      .asObject();

    Api api = c
      .of("api")
      .since(V2_9)
      .summary("Which GraphQL API to use for warmup queries.")
      .description("Use `transmodel` for the TransModel API or `gtfs` for the GTFS API.")
      .asEnum(Api.TRANSMODEL);

    var from = c.of("from").since(V2_9).summary("Origin location for warmup searches.").asObject();
    double fromLat = from.of("lat").since(V2_9).summary("Latitude of the origin.").asDouble();
    double fromLng = from.of("lon").since(V2_9).summary("Longitude of the origin.").asDouble();

    var to = c.of("to").since(V2_9).summary("Destination location for warmup searches.").asObject();
    double toLat = to.of("lat").since(V2_9).summary("Latitude of the destination.").asDouble();
    double toLng = to.of("lon").since(V2_9).summary("Longitude of the destination.").asDouble();

    return new WarmupConfig(
      api,
      new WgsCoordinate(fromLat, fromLng),
      new WgsCoordinate(toLat, toLng)
    );
  }

  public Api api() {
    return api;
  }

  public WgsCoordinate from() {
    return from;
  }

  public WgsCoordinate to() {
    return to;
  }
}
