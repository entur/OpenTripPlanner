package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopResolutionStrategy;

/**
 * Resolves a {@link StopLocation} from a {@link StopReference}.
 * <p>
 * This class handles the multi-stage stop resolution logic:
 * <ol>
 *   <li>If {@code assignedStopId} is present, use it directly</li>
 *   <li>If {@code stopId} is present with {@code SCHEDULED_STOP_POINT_FIRST} strategy,
 *       try scheduled stop point mapping first, then fall back to direct lookup</li>
 *   <li>If {@code stopId} is present with {@code DIRECT} strategy, use it directly</li>
 * </ol>
 * <p>
 * This follows the pattern established in {@link TripResolver} but operates
 * on the parsed {@link StopReference} rather than raw message objects.
 */
public class StopResolver {

  private final TransitService transitService;

  public StopResolver(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
  }

  /**
   * Resolve a {@link StopLocation} from a {@link StopReference}.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>If {@code assignedStopId} is present, look up the stop directly</li>
   *   <li>If {@code stopId} is present with {@code SCHEDULED_STOP_POINT_FIRST} strategy,
   *       try scheduled stop point mapping first, then direct lookup</li>
   *   <li>If {@code stopId} is present with {@code DIRECT} strategy, look up directly</li>
   * </ol>
   *
   * @param reference the stop reference containing identification information
   * @return the resolved StopLocation, or null if not found
   */
  @Nullable
  public StopLocation resolve(StopReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");

    // If there's an assigned stop ID, use it directly (highest priority)
    if (reference.hasAssignedStopId()) {
      return transitService.getStopLocation(reference.assignedStopId());
    }

    // If there's a stop ID, resolve based on strategy
    if (reference.hasStopId()) {
      if (reference.resolutionStrategy() == StopResolutionStrategy.SCHEDULED_STOP_POINT_FIRST) {
        // Try scheduled stop point mapping first, then fall back to direct lookup
        var stop = transitService.findStopByScheduledStopPoint(reference.stopId()).orElse(null);
        if (stop != null) {
          return stop;
        }
      }
      return transitService.getStopLocation(reference.stopId());
    }

    return null;
  }
}
