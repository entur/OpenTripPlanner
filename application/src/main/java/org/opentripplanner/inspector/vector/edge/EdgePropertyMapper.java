package org.opentripplanner.inspector.vector.edge;

import static org.opentripplanner.inspector.vector.KeyValue.kv;
import static org.opentripplanner.utils.lang.DoubleUtils.roundTo2Decimals;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.inspector.vector.KeyValue;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.model.InclinedEdgeLevelInfo;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.ElevatorAlightEdge;
import org.opentripplanner.street.model.edge.ElevatorBoardEdge;
import org.opentripplanner.street.model.edge.ElevatorHopEdge;
import org.opentripplanner.street.model.edge.EscalatorEdge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.utils.collection.ListUtils;

public class EdgePropertyMapper extends PropertyMapper<Edge> {

  private final StreetDetailsService streetDetailsService;

  public EdgePropertyMapper(StreetDetailsService streetDetailsService) {
    this.streetDetailsService = streetDetailsService;
  }

  @Override
  protected Collection<KeyValue> map(Edge input) {
    var baseProps = List.of(kv("class", input.getClass().getSimpleName()));
    List<KeyValue> properties =
      switch (input) {
        case StreetEdge e -> mapStreetEdge(e);
        case EscalatorEdge e -> mapEscalatorEdge(e);
        case ElevatorHopEdge e -> List.of(
          kv("permission", e.getPermission()),
          kv("levels", e.getLevels()),
          kv("wheelchairAccessible", e.isWheelchairAccessible()),
          kv("travelTime", e.getTravelTime().map(Duration::toString).orElse(null)),
          kv("fromNodeLabel", e.getFromVertex().getLabel().toString()),
          kv("toNodeLabel", e.getToVertex().getLabel().toString())
        );
        case ElevatorBoardEdge e -> List.of(
          kv(
            "levelValue",
            streetDetailsService.findHorizontalEdgeLevelInfo(e).map(l -> l.level()).orElse(null)
          ),
          kv(
            "levelName",
            streetDetailsService.findHorizontalEdgeLevelInfo(e).map(l -> l.name()).orElse(null)
          ),
          kv("fromNodeLabel", e.getFromVertex().getLabel().toString()),
          kv("toNodeLabel", e.getToVertex().getLabel().toString())
        );
        case ElevatorAlightEdge e -> List.of(
          kv("fromNodeLabel", e.getFromVertex().getLabel().toString()),
          kv("toNodeLabel", e.getToVertex().getLabel().toString())
        );
        default -> List.of();
      };
    return ListUtils.combine(baseProps, properties);
  }

  private List<KeyValue> mapEscalatorEdge(EscalatorEdge ee) {
    var props = Lists.newArrayList(
      kv("distance", ee.getDistanceMeters()),
      kv("duration", ee.getDuration().map(Duration::toString).orElse(null)),
      kv("fromNodeLabel", ee.getFromVertex().getLabel().toString()),
      kv("toNodeLabel", ee.getToVertex().getLabel().toString())
    );
    var inclinedEdgeLevelInfoOptional = streetDetailsService.findInclinedEdgeLevelInfo(ee);
    if (inclinedEdgeLevelInfoOptional.isPresent()) {
      props.addAll(getLevelInfoList(inclinedEdgeLevelInfoOptional.get()));
    }
    return props;
  }

  private List<KeyValue> mapStreetEdge(StreetEdge se) {
    var props = Lists.newArrayList(
      kv("permission", streetPermissionAsString(se.getPermission())),
      kv("bicycleSafetyFactor", roundTo2Decimals(se.getBicycleSafetyFactor())),
      kv("walkSafetyFactor", roundTo2Decimals(se.getWalkSafetyFactor())),
      kv("noThruTraffic", noThruTrafficAsString(se)),
      kv("wheelchairAccessible", se.isWheelchairAccessible()),
      kv("maximumSlope", roundTo2Decimals(se.getMaxSlope())),
      kv("fromNodeLabel", se.getFromVertex().getLabel().toString()),
      kv("toNodeLabel", se.getToVertex().getLabel().toString())
    );
    if (se.nameIsDerived()) {
      props.addFirst(kv("name", "%s (generated)".formatted(se.getName().toString())));
    } else {
      props.addFirst(kv("name", se.getName().toString()));
    }
    if (se.isStairs()) {
      props.add(kv("isStairs", true));
      var inclinedEdgeLevelInfoOptional = streetDetailsService.findInclinedEdgeLevelInfo(se);
      if (inclinedEdgeLevelInfoOptional.isPresent()) {
        props.addAll(getLevelInfoList(inclinedEdgeLevelInfoOptional.get()));
      }
    }
    return props;
  }

  private List<KeyValue> getLevelInfoList(InclinedEdgeLevelInfo inclinedEdgeLevelInfo) {
    return List.of(
      kv("lowerLevelNodeId", inclinedEdgeLevelInfo.lowerVertexInfo().osmNodeId()),
      kv(
        "lowerLevelValue",
        inclinedEdgeLevelInfo.lowerVertexInfo().level() != null
          ? inclinedEdgeLevelInfo.lowerVertexInfo().level().level()
          : null
      ),
      kv(
        "lowerLevelName",
        inclinedEdgeLevelInfo.lowerVertexInfo().level() != null
          ? inclinedEdgeLevelInfo.lowerVertexInfo().level().name()
          : null
      ),
      kv("upperLevelNodeId", inclinedEdgeLevelInfo.upperVertexInfo().osmNodeId()),
      kv(
        "upperLevelValue",
        inclinedEdgeLevelInfo.upperVertexInfo().level() != null
          ? inclinedEdgeLevelInfo.upperVertexInfo().level().level()
          : null
      ),
      kv(
        "upperLevelName",
        inclinedEdgeLevelInfo.upperVertexInfo().level() != null
          ? inclinedEdgeLevelInfo.upperVertexInfo().level().name()
          : null
      )
    );
  }

  public static String streetPermissionAsString(StreetTraversalPermission permission) {
    return permission.name().replace("_AND_", " ");
  }

  private static String noThruTrafficAsString(StreetEdge se) {
    var noThruPermission = StreetTraversalPermission.NONE;
    if (se.isWalkNoThruTraffic()) {
      noThruPermission = noThruPermission.add(StreetTraversalPermission.PEDESTRIAN);
    }
    if (se.isBicycleNoThruTraffic()) {
      noThruPermission = noThruPermission.add(StreetTraversalPermission.BICYCLE);
    }
    if (se.isMotorVehicleNoThruTraffic()) {
      noThruPermission = noThruPermission.add(StreetTraversalPermission.CAR);
    }
    return streetPermissionAsString(noThruPermission);
  }
}
