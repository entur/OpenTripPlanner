package org.opentripplanner.ext.empiricaldelay.model;

import java.io.Serializable;

/**
 * Empirical dalay values in seconds.
 */
public record EmpiricalDelay(int minPercentile, int maxPercentile) implements Serializable {
  @Override
  public String toString() {
    return "[" + minPercentile + "s, " + maxPercentile + "s]";
  }
}
