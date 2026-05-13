package org.opentripplanner.inspector.vector.astar;

import java.util.List;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.inspector.vector.LayerBuilder;
import org.opentripplanner.inspector.vector.LayerParameters;

/**
 * Vector tile layer showing edges traversed during an A* search trace.
 * Each feature is a line representing an edge traversal with properties
 * like sequence number, edge class, and result count.
 */
public class AStarTraceEdgeLayerBuilder extends LayerBuilder<AStarTraceEvent.TraverseEvent> {

  @Nullable
  private final AStarTraceStore store;

  public AStarTraceEdgeLayerBuilder(
    @Nullable AStarTraceStore store,
    LayerParameters<?> layerParameters
  ) {
    super(
      new AStarTraceEdgePropertyMapper(store != null ? store.getActiveTrace().map(AStarTraceData::maxSeq).orElse(1) : 1),
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
          .traverseEvents()
          .stream()
          .filter(e -> e.geometry() != null && e.geometry().getEnvelopeInternal().intersects(query))
          .map(e -> {
            Geometry geom = e.geometry().copy();
            geom.setUserData(e);
            return geom;
          })
          .toList()
      )
      .orElse(List.of());
  }
}
