package org.opentripplanner.ext.empiricaldelay.internal.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.ext.empiricaldelay.model.EmpiricalDelay;

/** Utility class to store trip-time delays for a given service id. */
public class EmpiricalDelayByServiceId implements Serializable {

  private Map<String, List<EmpiricalDelay>> delaysByServiceId = new HashMap<>();

  public Optional<EmpiricalDelay> get(String serviceId, int stopPosInPattern) {
    List<EmpiricalDelay> delayPerStop = delaysByServiceId.get(serviceId);
    // Check if empirical data for the serviceId (serviceDay) exist
    if (delayPerStop == null) {
      return Optional.empty();
    }
    return Optional.of(delayPerStop.get(stopPosInPattern));
  }

  public void put(String serviceId, List<EmpiricalDelay> dalaysForEachStop) {
    delaysByServiceId.put(serviceId, dalaysForEachStop);
  }
}
