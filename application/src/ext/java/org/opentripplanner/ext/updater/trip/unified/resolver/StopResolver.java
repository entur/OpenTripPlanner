package org.opentripplanner.ext.updater.trip.unified.resolver;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.TransitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the stops a {@link StopReference} names.
 * <p>
 * A reference names up to two stops, and they are resolved separately because they mean different
 * things: {@link #resolveReferencedStop} looks up the stop the call reports it is at (the key a
 * format that matches calls by stop id searches the pattern with), while
 * {@link #resolveAssignedStop} looks up the stop assigned in its place (a replacement in the stop
 * pattern). Collapsing them into one value made a stop assignment change which call an update was
 * about.
 * <p>
 * This class holds the multi-stage lookup of the referenced stop: a SIRI-ET
 * {@code StopPointRef} is a scheduled stop point that may map to a quay, so
 * {@link StopResolutionStrategy#SCHEDULED_STOP_POINT_FIRST} tries that mapping before the direct
 * lookup a GTFS-RT {@code stop_id} needs.
 * <p>
 * Only regular stops resolve: an id that names a flex stop is treated as unknown, like the legacy
 * updaters treat it. A trip update describes a call at a fixed stop, so accepting anything else
 * would insert a flex stop into a fixed-stop trip pattern.
 * <p>
 * This follows the pattern established in {@link TripResolver} but operates
 * on the parsed {@link StopReference} rather than raw message objects.
 */
public class StopResolver {

  private static final Logger LOG = LoggerFactory.getLogger(StopResolver.class);

  private final TransitService transitService;

  public StopResolver(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve the stop the call reports it is at, from the reference's own stop id and according to
   * its {@link StopResolutionStrategy}. The assigned stop, if any, is deliberately ignored: it is
   * not the stop this call is scheduled at, so it can never identify the call.
   *
   * @return the referenced stop, or {@code null} if the reference names none or names one the
   *         transit model does not know
   */
  @Nullable
  public RegularStop resolveReferencedStop(StopReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    if (!reference.hasStopId()) {
      return null;
    }
    if (reference.resolutionStrategy() == StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST) {
      // Try scheduled stop point mapping first, then fall back to direct lookup
      var stop = transitService.findStopByScheduledStopPoint(reference.stopId()).orElse(null);
      if (stop != null) {
        return stop;
      }
    }
    return transitService.getRegularStop(reference.stopId());
  }

  /**
   * Resolve the stop assigned in place of the scheduled one (a GTFS-RT {@code assigned_stop_id}).
   *
   * @return the assigned stop, or {@code null} if the reference assigns none. An assignment the
   *         transit model cannot resolve also returns {@code null}: the legacy updaters drop such an
   *         assignment and keep the rest of the update, so the trip keeps its scheduled stop rather
   *         than losing its real-time times.
   */
  @Nullable
  public RegularStop resolveAssignedStop(StopReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    if (!reference.hasAssignedStopId()) {
      return null;
    }
    var stop = transitService.getRegularStop(reference.assignedStopId());
    if (stop == null) {
      LOG.debug(
        "Stop {} assigned to a call is not known, keeping the scheduled stop",
        reference.assignedStopId()
      );
    }
    return stop;
  }
}
