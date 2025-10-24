package org.opentripplanner.service.streetdetails.internal;

import jakarta.inject.Inject;
import java.util.Optional;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.streetdetails.model.EdgeLevelInfo;
import org.opentripplanner.street.model.edge.Edge;

public class DefaultStreetDetailsService implements StreetDetailsService {

  private final StreetDetailsRepository repository;

  @Inject
  public DefaultStreetDetailsService(StreetDetailsRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<EdgeLevelInfo> findEdgeInformation(Edge edge) {
    return repository.findEdgeInformation(edge);
  }

  @Override
  public String toString() {
    return "DefaultStreetDetailsService{ repository=" + repository + '}';
  }
}
