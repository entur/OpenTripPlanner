package org.opentripplanner.service.streetdecorator.model;

public record EdgeLevelInfo(VertexLevelInfo lowerVertexInfo, VertexLevelInfo upperVertexInfo) {
  public EdgeLevelInfo {
    if (lowerVertexInfo == null) {
      throw new IllegalArgumentException("lowerVertexInfo can not be null");
    }
    if (upperVertexInfo == null) {
      throw new IllegalArgumentException("upperVertexInfo can not be null");
    }
  }

  public boolean matchesNodes(long firstOsmNodeId, long secondOsmNodeId) {
    return (
      (lowerVertexInfo.osmNodeId() == firstOsmNodeId &&
        upperVertexInfo.osmNodeId() == secondOsmNodeId) ||
      (lowerVertexInfo.osmNodeId() == secondOsmNodeId &&
        upperVertexInfo.osmNodeId() == firstOsmNodeId)
    );
  }
}
