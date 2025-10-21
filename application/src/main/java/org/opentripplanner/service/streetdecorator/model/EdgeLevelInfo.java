package org.opentripplanner.service.streetdecorator.model;

public record EdgeLevelInfo(VertexLevelInfo lowerVertexInfo, VertexLevelInfo upperVertexInfo) {
  public boolean matchesNodes(long firstOsmNodeId, long secondOsmNodeId) {
    return (
      (lowerVertexInfo.osmNodeId() == firstOsmNodeId &&
        upperVertexInfo.osmNodeId() == secondOsmNodeId) ||
      (lowerVertexInfo.osmNodeId() == secondOsmNodeId &&
        upperVertexInfo.osmNodeId() == firstOsmNodeId)
    );
  }
}
