package org.opentripplanner.raptor.api.model;

public interface RaptorOnBoardAccess extends RaptorAccessEgress {
  /**
   * The index of the boarded route
   */
  int routeIndex();

  /**
   * The first stop in the journey, where the access path just arrived at. Since this is an on-board
   * access, this stop represents the most recently visited stop on the currently boarded trip.
   * The next stop position after this is the first you can alight.
   * {@inheritDoc}
   */
  @Override
  int stop();

  /**
   * Since this is an on-board access, the duration until ({@link #stop}) is 0 seconds.
   * {@inheritDoc}
   */
  @Override
  default int durationInSeconds() {
    return 0;
  }

  @Override
  default int earliestDepartureTime(int requestedDepartureTime) {
    return requestedDepartureTime;
  }

  @Override
  default int latestArrivalTime(int requestedArrivalTime) {
    return requestedArrivalTime;
  }

  @Override
  default boolean hasOpeningHours() {
    return false;
  }

  @Override
  default boolean stopReachedByWalking() {
    return false;
  }
}
