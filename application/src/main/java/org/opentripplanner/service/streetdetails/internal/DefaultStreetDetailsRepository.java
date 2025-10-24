package org.opentripplanner.service.streetdetails.internal;

import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.utils.tostring.ToStringBuilder;

public class DefaultStreetDetailsRepository implements StreetDetailsRepository, Serializable {

  private final Map<Edge, EdgeLevelInfo> edgeInformation = new HashMap<>();

  @Inject
  public DefaultStreetDetailsRepository() {}

  @Override
  public void addEdgeLevelInformation(Edge edge, EdgeLevelInfo edgeLevelInfo) {
    Objects.requireNonNull(edge);
    this.edgeInformation.put(edge, edgeLevelInfo);
  }

  @Override
  public Optional<EdgeLevelInfo> findEdgeInformation(Edge edge) {
    return Optional.ofNullable(edgeInformation.get(edge));
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(DefaultStreetDetailsRepository.class)
      .addNum("Edges with level information", edgeInformation.size())
      .toString();
  }
}
