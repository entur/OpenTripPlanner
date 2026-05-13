package org.opentripplanner.inspector.vector.astar;

import static org.opentripplanner.inspector.vector.KeyValue.kv;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.inspector.vector.KeyValue;

public class AStarTraceEdgePropertyMapper extends PropertyMapper<AStarTraceEvent.TraverseEvent> {

  private final int maxSeq;

  public AStarTraceEdgePropertyMapper(int maxSeq) {
    this.maxSeq = Math.max(maxSeq, 1);
  }

  @Override
  protected Collection<KeyValue> map(AStarTraceEvent.TraverseEvent event) {
    var props = new java.util.ArrayList<>(List.of(
      kv("seq", event.seq()),
      kv("seqNorm", (double) event.seq() / maxSeq),
      kv("edgeClass", event.edgeClass()),
      kv("resultCount", event.resultCount()),
      kv("blocked", event.resultCount() == 0),
      kv("fork", event.resultCount() > 1),
      kv("fromVertex", event.fromVertexId()),
      kv("toVertex", event.toVertexId())
    ));
    if (event.resultSummary() != null) {
      props.add(kv("results", event.resultSummary()));
    }
    return props;
  }
}
