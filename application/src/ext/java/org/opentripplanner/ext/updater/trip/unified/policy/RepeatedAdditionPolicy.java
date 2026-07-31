package org.opentripplanner.ext.updater.trip.unified.policy;

/**
 * What a second message for a trip that has already been added in real-time does to it: revise the
 * trip on the pattern it was added to, or rebuild both from the calls the message carries.
 * <p>
 * The two formats answer differently because their added trips are held differently, see
 * {@link ScheduledDataPolicy}.
 */
public interface RepeatedAdditionPolicy {
  /**
   * Whether a repeat revises the trip already in the transit model, rather than rebuilding it.
   */
  boolean revisesInPlace();

  /**
   * SIRI-ET: an extra journey owns a pattern of its own, carrying the aimed times the message
   * stated, so a repeat revises that trip on that pattern. Legacy does the same: once the journey
   * has been added, {@code resolveTrip} finds it and the message goes to
   * {@code ModifiedTripBuilder}, the ordinary trip-update path.
   */
  RepeatedAdditionPolicy REVISE_IN_PLACE = () -> true;

  /**
   * GTFS-RT: an added trip carries no aimed times and shares its real-time pattern with every other
   * trip on the same stop list, so nothing ties it to the stops of the message that added it. Each
   * message therefore rebuilds the trip and its pattern from the calls it carries, which is what
   * legacy {@code NewTripHandler} does - it only guards against a trip of that id in the
   * <em>scheduled</em> data and otherwise builds everything anew.
   */
  RepeatedAdditionPolicy REBUILD_FROM_CALLS = () -> false;
}
