package org.opentripplanner.raptor.api.request.via;

import java.util.ArrayList;
import java.util.List;

abstract sealed class AbstractBuilder<T extends AbstractBuilder>
  permits ViaVisitBuilder, PassThroughBuilder {

  protected final String label;
  protected final List<ViaConnection> connections = new ArrayList<>();

  public AbstractBuilder(String label) {
    this.label = label;
  }

  T addConnection(ViaConnection connection) {
    this.connections.add(connection);
    return (T) this;
  }
}
