package org.opentripplanner.service.streetdecorator.model;

/**
 * Represents level information for an edge. The information is represented as two
 * {@link VertexLevelInfo} objects for the first and last vertices of an edge. The lower vertex
 * is represented by lowerVertexInfo and the higher one by upperVertexInfo.
 */
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
