package org.opentripplanner.utils.collection;

import java.util.Objects;

/**
 * A small container for an element and its index in a list.
 */
public final class IndexedListElement<T> {

  private final int index;
  private final T element;

  public IndexedListElement(int index, T element) {
    this.index = index;
    this.element = element;
  }

  public int index() {
    return index;
  }

  public T element() {
    return element;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (IndexedListElement) obj;
    return this.index == that.index && Objects.equals(this.element, that.element);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, element);
  }
}
