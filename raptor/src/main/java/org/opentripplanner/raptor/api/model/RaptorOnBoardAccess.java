package org.opentripplanner.raptor.api.model;

/**
 * An 'access' that happens on-board, meaning that one is already on the vehicle when the path
 * starts. This access is defined by a specific trip within a specific route, starting at a specific
 * stop. The trip and route is identified with the {@link #routeIndex} and {@link #boardingTime}.
 */
public interface RaptorOnBoardAccess extends RaptorAccessEgress {
  /**
   * The index of the boarded route
   */
  int routeIndex();

  /*
    Jeg tror vi bør bruke følgende til å finne boarding:

    int routeIndex();
    // Ny
    int stopPosisionInPattern();
    // Ny
    int tipScheduleIndex();

    Tror earliestDepartureTime() or latestArrivalTime() kan kaste UnsupportedOperationException,
    disse metodene skal kun brukes dersom man time-shifter access/egress og det skal ikke skje for
    RaptorOnBoardAccess.

    We bør støtte `numberOfRides()`, men det legger vi må i neste iterasjon/støtte for via søk.
    Ser at du har gjort noe på det, så da er det bare å beholde det du har gjort.
   */



  /**
   * The time of boarding {@link #stop}. Since this is an on-board access, the actual ride may have
   * started already before {@link #stop} and at a time earlier than this boarding time. However,
   * raptor will consider this as a boarding event on {@link #stop} at this time.
   */
  int boardingTime();

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
    if (requestedDepartureTime > boardingTime()) {
      // TODO. Maybe just ignore requestedDepartureTime and fall back to boardingTime()
      throw new RuntimeException("I think this doesn't make sense. Throwing might not be appropriate though.");
    }
    return boardingTime();
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
