package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import java.util.ArrayList;
import java.util.BitSet;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.raptor.spi.IntIterator;
import org.opentripplanner.raptor.spi.IntIterators;
import org.opentripplanner.raptor.spi.RaptorRoute;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.spi.RaptorTripPattern;
import org.opentripplanner.raptor.spi.RaptorTripScheduleSearch;
import org.opentripplanner.raptor.spi.SearchDirection;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.frequency.TripFrequencyAlightSearch;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.frequency.TripFrequencyBoardSearch;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A collection of all the TripSchedules active on a range of consecutive days. The outer list of
 * tripSchedulesByDay refers to days in order.
 */
public class TripPatternForDates
  implements
    RaptorRoute<TripSchedule>,
    RaptorTimeTable<TripSchedule>,
    RaptorTripPattern,
    TripSearchTimetable<TripSchedule>
{

  private static final int FIRST_STOP_POS_IN_PATTERN = 0;

  /** A flag in {@link #stops}: boarding is possible at the stop position. */
  private static final int BOARDING_POSSIBLE = 1 << 30;

  /** A flag in {@link #stops}: alighting is possible at the stop position. */
  private static final int ALIGHTING_POSSIBLE = 1 << 29;

  private static final int STOP_INDEX_MASK = ALIGHTING_POSSIBLE - 1;

  private final RoutingTripPattern tripPattern;

  private final TripPatternForDate[] tripPatternForDates;

  private final int[] offsets;

  private final int numberOfTripSchedules;

  private final int numberOfStops;

  /**
   * The stop index of each stop position, with the flags {@link #BOARDING_POSSIBLE} and
   * {@link #ALIGHTING_POSSIBLE} in its highest bits. The flags are those of the trip pattern,
   * potentially filtered by wheelchair accessibility. The three are read for every stop of every
   * visit of the route in a search, so they are kept together: one read from memory, not three.
   */
  private final int[] stops;

  /**
   * The slack index and the transit reluctance factor index of the trip pattern. They are read for
   * every visit of the route in a search, so they are kept here: reading them from the trip
   * pattern is one more object to fetch from memory.
   */
  private final int slackIndex;

  private final int transitReluctanceFactorIndex;

  private final boolean isFrequencyBased;

  /**
   * The arrival times in a numberOfTripSchedules * nStops sized array. The times are stored first
   * by trip index and then by the stop position, so with stops 1 and 2, and trips A and B, the
   * order is [A1, A2, B1, B2]. A trip that is boarded is followed from stop to stop, and the times
   * of a trip are next to each other in memory.
   */
  private final int[] arrivalTimes;

  /**
   * The arrival times in a nStops * numberOfTripSchedules sized array. The order is the same as in
   * arrivalTimes.
   */
  private final int[] departureTimes;

  private final boolean arrivalsInTripOrder;
  private final boolean departuresInTripOrder;

  private final Accessibility[] wheelchairBoardings;

  private final int priorityGroupId;

  TripPatternForDates(
    RoutingTripPattern tripPattern,
    TripPatternForDate[] tripPatternForDates,
    int[] offsets,
    BitSet boardingPossible,
    BitSet alightningPossible,
    int priorityGroupId
  ) {
    this.tripPattern = tripPattern;
    this.tripPatternForDates = tripPatternForDates;
    this.offsets = offsets;
    this.priorityGroupId = priorityGroupId;

    int numberOfTripSchedules = 0;
    boolean hasFrequencies = false;
    for (TripPatternForDate tripPatternForDate : this.tripPatternForDates) {
      numberOfTripSchedules += tripPatternForDate.numberOfTripSchedules();
      if (tripPatternForDate.hasFrequencies()) {
        hasFrequencies = true;
      }
    }
    this.numberOfTripSchedules = numberOfTripSchedules;
    this.isFrequencyBased = hasFrequencies;

    this.wheelchairBoardings = new Accessibility[numberOfTripSchedules];

    final int nStops = tripPattern.numberOfStopsInPattern();
    this.numberOfStops = nStops;
    this.stops = new int[nStops];
    for (int s = 0; s < nStops; s++) {
      int stopIndex = tripPattern.stopIndex(s);
      if ((stopIndex & ~STOP_INDEX_MASK) != 0) {
        throw new IllegalArgumentException("The stop index is out of range: " + stopIndex);
      }
      this.stops[s] =
        stopIndex |
        (boardingPossible.get(s) ? BOARDING_POSSIBLE : 0) |
        (alightningPossible.get(s) ? ALIGHTING_POSSIBLE : 0);
    }
    this.slackIndex = tripPattern.slackIndex();
    this.transitReluctanceFactorIndex = tripPattern.transitReluctanceFactorIndex();
    this.arrivalTimes = new int[nStops * numberOfTripSchedules];
    this.departureTimes = new int[nStops * numberOfTripSchedules];

    var tripIndex = createTripTimesForDaysIndex(tripPatternForDates, offsets);
    boolean arrivalsInTripOrder = true;
    boolean departuresInTripOrder = true;

    for (int i = 0; i < tripIndex.size(); ++i) {
      int day = tripIndex.day(i);
      int offset = this.offsets[day];
      var tt = tripPatternForDates[day].tripTimes().get(tripIndex.tripIndexForDay(i));

      wheelchairBoardings[i] = tt.getWheelchairAccessibility();
      for (int s = 0; s < nStops; s++) {
        int index = i * nStops + s;
        int arrivalTime = tt.getArrivalTime(s) + offset;
        int departureTime = tt.getDepartureTime(s) + offset;
        this.arrivalTimes[index] = arrivalTime;
        this.departureTimes[index] = departureTime;

        // The same stop of the trip before this one is nStops elements back in the arrays
        if (i > 0) {
          arrivalsInTripOrder &= this.arrivalTimes[index - nStops] <= arrivalTime;
          departuresInTripOrder &= this.departureTimes[index - nStops] <= departureTime;
        }
      }
    }
    this.arrivalsInTripOrder = arrivalsInTripOrder;
    this.departuresInTripOrder = departuresInTripOrder;
  }

  public RoutingTripPattern getTripPattern() {
    return tripPattern;
  }

  /* Support for frequency based routing */

  public IntIterator tripPatternForDatesIndexIterator(boolean ascendingOnDate) {
    return ascendingOnDate
      ? IntIterators.intIncIterator(0, tripPatternForDates.length)
      : IntIterators.intDecIterator(tripPatternForDates.length, 0);
  }

  public TripPatternForDate tripPatternForDate(int dayIndex) {
    return tripPatternForDates[dayIndex];
  }

  /**
   * @deprecated This is exposed because it is needed in the TripFrequencyNnnSearch classes, but is
   *             an implementation detail that should not leak outside the class.
   */
  @Deprecated
  public int tripPatternForDateOffsets(int dayIndex) {
    return offsets[dayIndex];
  }

  // Implementing RaptorRoute
  @Override
  public RaptorTimeTable<TripSchedule> timetable() {
    return this;
  }

  @Override
  public RaptorTripPattern pattern() {
    return this;
  }

  /* Implementing RaptorTripPattern */

  @Override
  public int patternIndex() {
    return tripPattern.patternIndex();
  }

  @Override
  public int numberOfStopsInPattern() {
    return numberOfStops;
  }

  @Override
  public int stopIndex(int stopPositionInPattern) {
    return stops[stopPositionInPattern] & STOP_INDEX_MASK;
  }

  @Override
  public boolean boardingPossibleAt(int stopPositionInPattern) {
    return (stops[stopPositionInPattern] & BOARDING_POSSIBLE) != 0;
  }

  @Override
  public boolean alightingPossibleAt(int stopPositionInPattern) {
    return (stops[stopPositionInPattern] & ALIGHTING_POSSIBLE) != 0;
  }

  @Override
  public int slackIndex() {
    return slackIndex;
  }

  @Override
  public int priorityGroupId() {
    return priorityGroupId;
  }

  @Override
  public String debugInfo() {
    return tripPattern.debugInfo();
  }

  /*  Implementing RaptorTimeTable */

  @Override
  public RaptorTripScheduleSearch<TripSchedule> tripSearch(SearchDirection direction) {
    if (useCustomizedTripSearch()) {
      return createCustomizedTripSearch(direction);
    }
    return TripScheduleSearchFactory.create(direction, this);
  }

  @Override
  public TripSchedule getTripSchedule(int tripIndex) {
    return new TripScheduleWithOffset(this, tripIndex);
  }

  @Override
  public int arrivalTime(int stopPositionInPattern, int tripIndex) {
    return arrivalTimes[tripIndex * numberOfStops + stopPositionInPattern];
  }

  @Override
  public int departureTime(int stopPositionInPattern, int tripIndex) {
    return departureTimes[tripIndex * numberOfStops + stopPositionInPattern];
  }

  @Override
  public boolean departuresInTripOrder() {
    return departuresInTripOrder;
  }

  @Override
  public boolean arrivalsInTripOrder() {
    return arrivalsInTripOrder;
  }

  @Override
  public int numberOfTripSchedules() {
    return numberOfTripSchedules;
  }

  public int transitReluctanceFactorIndex() {
    return transitReluctanceFactorIndex;
  }

  public Route route() {
    return tripPattern.route();
  }

  /**
   * Raptor provides a trips search for regular trip schedules, but in some cases it makes sense to
   * be able to override this - for example for frequency based trips.
   *
   * @return {@code true} If you do not want to use the built-in trip search and instead will
   *         provide your own. Make sure to implement the
   *         {@link #createCustomizedTripSearch(SearchDirection)} for both forward and reverse
   *         searches.
   */
  public boolean useCustomizedTripSearch() {
    return isFrequencyBased;
  }

  /**
   * Factory method to provide an alternative trip search in Raptor.
   *
   * @see #useCustomizedTripSearch()
   */
  public RaptorTripScheduleSearch<TripSchedule> createCustomizedTripSearch(
    SearchDirection direction
  ) {
    return direction.isForward()
      ? new TripFrequencyBoardSearch<>(this)
      : new TripFrequencyAlightSearch<>(this);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(TripPatternForDates.class)
      .addObj("pattern", debugInfo())
      .addServiceTimeSchedule("offsets", offsets)
      .addNum("nTrips", numberOfTripSchedules)
      .toString();
  }

  public Accessibility wheelchairBoardingForTrip(int tripIndex) {
    return wheelchairBoardings[tripIndex];
  }

  /**
   * Return a list with all departure times for the first stop for each trip per day.
   *
   * There are no unit-tests on this method, so the surface to {@link TripPatternForDate} should be
   * kept as thin as possible.
   */
  private static TripTimesForDaysIndex createTripTimesForDaysIndex(
    TripPatternForDate[] tripPatternForDates,
    int[] offsets
  ) {
    var departureTimes = new ArrayList<int[]>(tripPatternForDates.length);
    for (var tripPatternForDate : tripPatternForDates) {
      var times = new int[tripPatternForDate.numberOfTripSchedules()];
      for (int i = 0; i < times.length; i++) {
        times[i] = tripPatternForDate.getTripTimes(i).getDepartureTime(FIRST_STOP_POS_IN_PATTERN);
      }
      departureTimes.add(times);
    }
    return TripTimesForDaysIndex.ofTripTimesForDay(departureTimes, offsets);
  }
}
