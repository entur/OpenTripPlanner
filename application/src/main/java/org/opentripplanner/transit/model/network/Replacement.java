package org.opentripplanner.transit.model.network;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class Replacement {

  private final boolean isReplacement;
  private final List<TripOnServiceDate> replacementFor;

  public Replacement(boolean isReplacement, Collection<TripOnServiceDate> replacementFor) {
    this.isReplacement = isReplacement;
    this.replacementFor = List.copyOf(replacementFor);
  }

  public boolean getIsReplacement() {
    return isReplacement;
  }

  public Collection<TripOnServiceDate> getReplacementFor() {
    return replacementFor;
  }
}
