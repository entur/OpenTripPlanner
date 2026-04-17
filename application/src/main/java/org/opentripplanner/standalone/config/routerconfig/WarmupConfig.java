package org.opentripplanner.standalone.config.routerconfig;

import static org.opentripplanner.standalone.config.framework.json.EnumMapper.docEnumValueList;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_10;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.doc.DocumentedEnum;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.StreetMode;

/**
 * Configuration for application warmup during startup. When configured, OTP runs transit routing
 * queries between the given locations during startup to warm up the application (JIT compilation,
 * GraphQL schema caches, routing data structures, etc.) before production traffic arrives.
 * <p>
 * Queries start after the Raptor transit data is created and stop when all updaters are
 * primed (the health endpoint would return "UP"). If no updaters are configured, no warmup
 * queries are run.
 */
public final class WarmupConfig {

  public enum Api implements DocumentedEnum<Api> {
    TRANSMODEL("Use the TransModel GraphQL API for warmup queries."),
    GTFS("Use the GTFS GraphQL API for warmup queries.");

    private final String description;

    Api(String description) {
      this.description = description;
    }

    @Override
    public String typeDescription() {
      return "Which GraphQL API to use for warmup queries.";
    }

    @Override
    public String enumValueDescription() {
      return description;
    }
  }

  private static final List<StreetMode> DEFAULT_ACCESS_MODES = List.of(
    StreetMode.WALK,
    StreetMode.CAR_TO_PARK
  );
  private static final List<StreetMode> DEFAULT_EGRESS_MODES = List.of(
    StreetMode.WALK,
    StreetMode.WALK
  );

  private final Api api;
  private final WgsCoordinate from;
  private final WgsCoordinate to;
  private final List<StreetMode> accessModes;
  private final List<StreetMode> egressModes;

  private WarmupConfig(
    Api api,
    WgsCoordinate from,
    WgsCoordinate to,
    List<StreetMode> accessModes,
    List<StreetMode> egressModes
  ) {
    this.api = api;
    this.from = from;
    this.to = to;
    this.accessModes = accessModes;
    this.egressModes = egressModes;
  }

  @Nullable
  public static WarmupConfig mapWarmupConfig(String parameterName, NodeAdapter root) {
    if (!root.exist(parameterName)) {
      return null;
    }

    var c = root
      .of(parameterName)
      .since(V2_10)
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
      .since(V2_10)
      .summary("Which GraphQL API to use for warmup queries.")
      .description(docEnumValueList(Api.values()))
      .asEnum(Api.TRANSMODEL);

    var from = c.of("from").since(V2_10).summary("Origin location for warmup searches.").asObject();
    double fromLat = from.of("lat").since(V2_10).summary("Latitude of the origin.").asDouble();
    double fromLng = from.of("lon").since(V2_10).summary("Longitude of the origin.").asDouble();

    var to = c
      .of("to")
      .since(V2_10)
      .summary("Destination location for warmup searches.")
      .asObject();
    double toLat = to.of("lat").since(V2_10).summary("Latitude of the destination.").asDouble();
    double toLng = to.of("lon").since(V2_10).summary("Longitude of the destination.").asDouble();

    var accessModeStrings = c
      .of("accessModes")
      .since(V2_10)
      .summary("Access modes to cycle through in warmup queries.")
      .description(
        "Ordered list of `StreetMode` values used as access modes. " +
          "Each entry is paired with the egress mode at the same index. " +
          docEnumValueList(StreetMode.values())
      )
      .asStringList(DEFAULT_ACCESS_MODES.stream().map(Enum::name).toList());
    List<StreetMode> accessModes = accessModeStrings
      .stream()
      .map(s -> StreetMode.valueOf(s))
      .toList();

    var egressModeStrings = c
      .of("egressModes")
      .since(V2_10)
      .summary("Egress modes to cycle through in warmup queries.")
      .description(
        "Ordered list of `StreetMode` values used as egress modes. " +
          "Each entry is paired with the access mode at the same index. " +
          docEnumValueList(StreetMode.values())
      )
      .asStringList(DEFAULT_EGRESS_MODES.stream().map(Enum::name).toList());
    List<StreetMode> egressModes = egressModeStrings
      .stream()
      .map(s -> StreetMode.valueOf(s))
      .toList();

    if (accessModes.size() != egressModes.size()) {
      throw new IllegalArgumentException(
        "warmup.accessModes and warmup.egressModes must have the same number of entries, " +
          "got %d access modes and %d egress modes.".formatted(
            accessModes.size(),
            egressModes.size()
          )
      );
    }

    return new WarmupConfig(
      api,
      new WgsCoordinate(fromLat, fromLng),
      new WgsCoordinate(toLat, toLng),
      accessModes,
      egressModes
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

  public List<StreetMode> accessModes() {
    return accessModes;
  }

  public List<StreetMode> egressModes() {
    return egressModes;
  }
}
