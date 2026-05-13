package org.opentripplanner.inspector.vector.astar;

/**
 * Lightweight summary of a trace for listing purposes.
 * The timestamp is serialized as a string to avoid Jackson Instant serialization issues.
 */
public record AStarTraceSummary(
  String id,
  String timestamp,
  String description,
  int eventCount,
  int maxSeq
) {}
