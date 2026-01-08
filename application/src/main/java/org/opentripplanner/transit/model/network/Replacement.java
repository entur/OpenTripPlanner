package org.opentripplanner.transit.model.network;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

/**
 * Model class containing replacement properties for a TripOnServiceDate. These properties
 * represent the way in which the TripOnServiceDate is a replacement for something. That
 * something may be unknown (and replacementFor is null), or known (and it is a non-null
 * List containing the replaced TripOnServiceDates), but in either case isReplacement tells
 * whether it is a replacement or not.
 */
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
