package org.opentripplanner.raptor.api.request.via;

import java.util.ArrayList;
import java.util.List;

public abstract sealed class AbstractBuilder<T extends AbstractBuilder>
  permits ViaVisitBuilder, PassThroughBuilder {

  protected final String label;
  protected final List<AbstractViaConnection> connections = new ArrayList<>();

  public AbstractBuilder(String label) {
    this.label = label;
  }

  T addConnection(AbstractViaConnection connection) {
    this.connections.add(connection);
    return (T) this;
  }
}
