package org.opentripplanner.inspector.vector.astar;

import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opentripplanner.inspector.vector.LayerBuilder;
import org.opentripplanner.inspector.vector.LayerParameters;

/**
 * Vector tile layer showing vertices visited during an A* search trace.
 * Each feature is a point representing a state event (dequeue, enqueue,
 * reject, or goal) with properties like weight, mode, and rental state.
 */
public class AStarTraceVertexLayerBuilder extends LayerBuilder<AStarTraceEvent> {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  @Nullable
  private final AStarTraceStore store;

  public AStarTraceVertexLayerBuilder(
    @Nullable AStarTraceStore store,
    LayerParameters<?> layerParameters
  ) {
    super(
      new AStarTraceVertexPropertyMapper(),
      layerParameters.name(),
      layerParameters.expansionFactor()
    );
    this.store = store;
  }

  @Override
  protected List<Geometry> getGeometries(Envelope query) {
    if (store == null) {
      return List.of();
    }
    return store
      .getActiveTrace()
      .map(trace ->
        trace
          .vertexEvents()
          .stream()
          .filter(e -> {
            var coord = eventCoordinate(e);
            return coord != null && query.contains(coord);
          })
          .map(e -> {
            var coord = eventCoordinate(e);
            Geometry geom = GEOMETRY_FACTORY.createPoint(coord);
            geom.setUserData(e);
            return geom;
          })
          .toList()
      )
      .orElse(List.of());
  }

  @Nullable
  private static Coordinate eventCoordinate(AStarTraceEvent event) {
    return switch (event) {
      case AStarTraceEvent.DequeueEvent e -> new Coordinate(e.lon(), e.lat());
      case AStarTraceEvent.EnqueueEvent e -> new Coordinate(e.lon(), e.lat());
      case AStarTraceEvent.RejectEvent e -> new Coordinate(e.lon(), e.lat());
      case AStarTraceEvent.GoalEvent e -> new Coordinate(e.lon(), e.lat());
      default -> null;
    };
  }
}
