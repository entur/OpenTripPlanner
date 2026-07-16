package org.opentripplanner.utils.lang;

import java.util.function.Function;

/**
 * Utility constants/methods for DIV functions like {@link Runnable}s, {@link Function}
 * .
 */
public class RunnableUtils {

  private RunnableUtils() {}

  /**
   * A runnable that does nothing.
   */
  public static final Runnable NOOP = () -> {};
}
