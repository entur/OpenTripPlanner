package org.opentripplanner.street.model.edge;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The main purpose of this class is to be able to return the two new street edges after an edge
 * is split. The first part is called <em>head</em> and the last part is called the <em>tail</em>.
 * <p>
 * This class is NOT part of the model, just used as a return type when splitting an edge.
 */
public record SplitStreetEdge(StreetEdge head, StreetEdge tail) {
  /**
   * Returns all non-null edges as an iterable.
   */
  public Iterable<StreetEdge> asIterable() {
    return Stream.of(head, tail).filter(Objects::nonNull).toList();
  }
}
