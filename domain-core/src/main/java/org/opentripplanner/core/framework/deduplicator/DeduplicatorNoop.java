package org.opentripplanner.core.framework.deduplicator;

import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

class DeduplicatorNoop implements DeduplicatorService {

  @Nullable
  @Override
  public BitSet deduplicateBitSet(BitSet original) {
    return original;
  }

  @Override
  public int @Nullable[] deduplicateIntArray(int[] original) {
    return original;
  }

  @Nullable
  @Override
  public String deduplicateString(String original) {
    return original;
  }

  @Override
  public String @Nullable[] deduplicateStringArray(String[] original) {
    return original;
  }

  @Override
  public String @Nullable[][] deduplicateString2DArray(String[][] original) {
    return original;
  }

  @Nullable
  @Override
  public <T> T deduplicateObject(Class<T> cl, T original) {
    return original;
  }

  @Override
  public <T> T @Nullable[] deduplicateObjectArray(Class<T> type, T[] original) {
    return original;
  }

  @Nullable
  @Override
  public <T> List<T> deduplicateImmutableList(Class<T> clazz, List<T> original) {
    return original;
  }
}
