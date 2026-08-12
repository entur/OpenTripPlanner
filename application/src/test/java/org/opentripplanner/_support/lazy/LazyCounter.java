package org.opentripplanner._support.lazy;

import java.util.function.Supplier;

/**
 * Wraps a {@link Supplier} so tests can assert how many times the wrapped value was actually
 * constructed versus how many times it was merely requested — the two differ whenever a value is
 * lazily memoized after its first access.
 */
public final class LazyCounter<T> implements Supplier<T> {

  private final Supplier<T> delegate;
  private int constructions = 0;
  private int accesses = 0;
  private T value;

  public LazyCounter(Supplier<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public T get() {
    accesses++;
    if (constructions == 0) {
      value = delegate.get();
      constructions++;
    }
    return value;
  }

  /** How many times the delegate supplier actually ran (0 or 1, never more). */
  public int constructions() {
    return constructions;
  }

  /** How many times {@link #get()} was called, regardless of whether it constructed anything. */
  public int accesses() {
    return accesses;
  }
}
