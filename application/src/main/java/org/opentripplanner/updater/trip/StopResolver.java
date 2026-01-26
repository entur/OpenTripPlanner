package org.opentripplanner.updater.trip;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.model.StopReference;

/**
 * Resolves a {@link StopLocation} from a {@link StopReference}.
 * <p>
 * This class handles the multi-stage stop resolution logic:
 * <ol>
 *   <li>If {@code assignedStopId} is present, use it directly</li>
 *   <li>If {@code stopId} is present, use it directly</li>
 *   <li>If {@code stopPointRef} is present (SIRI-style), resolve via scheduled stop point
 *       mapping first, then fall back to regular stop lookup</li>
 * </ol>
 * <p>
 * This follows the pattern established in {@link TripIdResolver} but operates
 * on the parsed {@link StopReference} rather than raw message objects.
 */
public class StopResolver {

  private final TransitService transitService;
  private final String feedId;

  public StopResolver(TransitService transitService, String feedId) {
    this.transitService = Objects.requireNonNull(transitService, "transitService must not be null");
    this.feedId = Objects.requireNonNull(feedId, "feedId must not be null");
  }

  /**
   * Resolve a {@link StopLocation} from a {@link StopReference}.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>If {@code assignedStopId} is present, look up the stop directly</li>
   *   <li>If {@code stopId} is present, look up the stop directly</li>
   *   <li>If {@code stopPointRef} is present, try scheduled stop point mapping first,
   *       then regular stop lookup</li>
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

    // If there's a direct stop ID, use it
    if (reference.hasStopId()) {
      return transitService.getStopLocation(reference.stopId());
    }

    // If there's a stop point ref (SIRI-style), resolve it
    if (reference.hasStopPointRef()) {
      FeedScopedId stopId = new FeedScopedId(feedId, reference.stopPointRef());
      // Try scheduled stop point mapping first, then regular stop
      RegularStop stop = transitService.findStopByScheduledStopPoint(stopId).orElse(null);
      if (stop != null) {
        return stop;
      }
      return transitService.getRegularStop(stopId);
    }

    return null;
  }
}
