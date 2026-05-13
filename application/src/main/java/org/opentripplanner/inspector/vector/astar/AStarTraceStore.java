package org.opentripplanner.inspector.vector.astar;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe store holding the most recent A* search traces for debug
 * visualization. Bounded to {@link #MAX_TRACES} entries (LRU eviction).
 * The "active" trace is the one currently rendered by the vector tile layers.
 */
public class AStarTraceStore {

  private static final int MAX_TRACES = 10;

  private final ConcurrentLinkedDeque<AStarTraceData> traces = new ConcurrentLinkedDeque<>();
  private final AtomicReference<String> activeTraceId = new AtomicReference<>();

  public void addTrace(AStarTraceData trace) {
    traces.addFirst(trace);
    activeTraceId.set(trace.id());

    // Evict oldest if over limit
    while (traces.size() > MAX_TRACES) {
      traces.removeLast();
    }
  }

  public List<AStarTraceSummary> listTraces() {
    return traces.stream().map(AStarTraceData::toSummary).toList();
  }

  public void setActiveTrace(String traceId) {
    // Verify the trace exists
    if (traces.stream().anyMatch(t -> t.id().equals(traceId))) {
      activeTraceId.set(traceId);
    }
  }

  public Optional<AStarTraceData> getTrace(String traceId) {
    return traces.stream().filter(t -> t.id().equals(traceId)).findFirst();
  }

  public Optional<AStarTraceData> getActiveTrace() {
    String id = activeTraceId.get();
    if (id == null) {
      return Optional.empty();
    }
    return traces
      .stream()
      .filter(t -> t.id().equals(id))
      .findFirst();
  }

  public Optional<String> getActiveTraceId() {
    return Optional.ofNullable(activeTraceId.get());
  }
}
