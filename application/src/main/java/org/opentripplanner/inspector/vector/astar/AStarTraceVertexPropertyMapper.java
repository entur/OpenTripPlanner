package org.opentripplanner.inspector.vector.astar;

import static org.opentripplanner.inspector.vector.KeyValue.kv;

import java.util.ArrayList;
import java.util.Collection;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.inspector.vector.KeyValue;

public class AStarTraceVertexPropertyMapper extends PropertyMapper<AStarTraceEvent> {

  @Override
  protected Collection<KeyValue> map(AStarTraceEvent event) {
    var props = new ArrayList<KeyValue>();

    props.add(kv("seq", event.seq()));

    switch (event) {
      case AStarTraceEvent.DequeueEvent e -> {
        props.add(kv("eventType", "DEQUEUE"));
        props.add(kv("weight", e.weight()));
        props.add(kv("mode", e.mode()));
        props.add(kv("rentalState", e.rentalState()));
        props.add(kv("network", e.network()));
        props.add(kv("vertex", e.vertexId()));
        props.add(kv("parentVertex", e.parentVertexId()));
        props.add(kv("parentLat", e.parentLat()));
        props.add(kv("parentLon", e.parentLon()));
        props.add(kv("isGoal", false));
      }
      case AStarTraceEvent.EnqueueEvent e -> {
        props.add(kv("eventType", "ENQUEUE"));
        props.add(kv("weight", e.weight()));
        props.add(kv("estimate", e.estimate()));
        props.add(kv("mode", e.mode()));
        props.add(kv("rentalState", e.rentalState()));
        props.add(kv("network", e.network()));
        props.add(kv("vertex", e.vertexId()));
        props.add(kv("isGoal", false));
      }
      case AStarTraceEvent.RejectEvent e -> {
        props.add(kv("eventType", "REJECT"));
        props.add(kv("weight", e.weight()));
        props.add(kv("vertex", e.vertexId()));
        props.add(kv("isGoal", false));
      }
      case AStarTraceEvent.GoalEvent e -> {
        props.add(kv("eventType", "GOAL"));
        props.add(kv("weight", e.weight()));
        props.add(kv("vertex", e.vertexId()));
        props.add(kv("isGoal", true));
      }
      default -> {}
    }

    return props;
  }
}
