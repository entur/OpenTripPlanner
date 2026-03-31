package org.opentripplanner.raptor.rangeraptor.transit;

import static java.util.stream.Collectors.groupingBy;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.raptor.api.request.via.AbstractViaConnection;

public class ViaConnections {

  private final TIntObjectMap<List<AbstractViaConnection>> groupByFromStop;

  public ViaConnections(Collection<AbstractViaConnection> viaConnections) {
    this.groupByFromStop = new TIntObjectHashMap<>();
    viaConnections
      .stream()
      .collect(groupingBy(AbstractViaConnection::fromStop))
      .forEach(groupByFromStop::put);
  }

  public TIntObjectMap<List<AbstractViaConnection>> byFromStop() {
    return groupByFromStop;
  }
}
