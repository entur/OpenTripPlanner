package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.preference.BikePreferences;

public class WalkRequest {

  public double speed() {
    return 1;
  }

  public double reluctance() {
    return 1;
  }

  public double stairsTimeFactor() {
    return 1;
  }

  public double safetyFactor() {
    return 1;
  }

  public Cost mountDismountCost() {
    return Cost.costOfSeconds(120);
  }

  public Duration mountDismountTime() {
    return Duration.ofSeconds(10);
  }

  public double stairsReluctance() {
    return 0;
  }

  public EscalatorRequest escalator() {
    return null;
  }
}
