package org.opentripplanner.raptor.api.request.via;

import java.util.stream.IntStream;

public final class PassThroughBuilder extends AbstractBuilder<PassThroughBuilder> {

  public PassThroughBuilder(String label) {
    super(label);
  }

  public PassThroughBuilder addStop(int stop) {
    return addConnection(new RaptorPassThroughViaConnection(stop));
  }

  public PassThroughBuilder addStop(int... stops) {
    IntStream.of(stops).forEach(this::addStop);
    return this;
  }

  public RaptorViaLocation build() {
    return new RaptorViaLocation(label, connections);
  }
}
