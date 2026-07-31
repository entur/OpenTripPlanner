package org.opentripplanner.ext.updater.trip.unified.model.change;

import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * The resolved counterpart of a {@link StopReference}: the two stops a call can name, each looked
 * up in the transit model on its own.
 * <p>
 * They answer different questions and must not be substituted for one another. The referenced stop
 * says <em>which scheduled call this update is about</em> and is therefore the key a format that
 * matches by stop id searches the pattern with. The assigned stop says <em>which stop the vehicle
 * will actually use instead</em> and is therefore a replacement in the stop pattern - by definition
 * not a stop of the scheduled pattern.
 *
 * @param referencedStop the stop the call reports it is at, or {@code null} if the message names
 *                       none or names one the transit model does not know
 * @param assignedStop   the stop assigned in place of the scheduled one, or {@code null} if the
 *                       message assigns none or assigns one the transit model does not know. An
 *                       unresolvable assignment is deliberately indistinguishable from an absent
 *                       one: both mean "no stop replacement", which is what the legacy updaters do.
 */
public record ResolvedStopReference(
  @Nullable StopLocation referencedStop,
  @Nullable StopLocation assignedStop
) {
  /** A reference that resolved to nothing but the stop the call is at. */
  public static ResolvedStopReference ofReferencedStop(@Nullable StopLocation referencedStop) {
    return new ResolvedStopReference(referencedStop, null);
  }
}
