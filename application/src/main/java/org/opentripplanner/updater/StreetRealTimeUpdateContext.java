package org.opentripplanner.updater;

import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;
import org.opentripplanner.updater.trip.siri.EntityResolver;

/**
 * Context for write tasks in the street write domain. It exposes the street model only: street
 * tasks run on the street domain's writer thread, and reading the mutable timetable snapshot from
 * that thread would race with the transit domain's writer thread. All timetable accessors
 * therefore fail fast.
 */
public class StreetRealTimeUpdateContext implements RealTimeUpdateContext {

  private final Graph graph;

  public StreetRealTimeUpdateContext(Graph graph) {
    this.graph = graph;
  }

  @Override
  public Graph graph() {
    return graph;
  }

  @Override
  public MutableTimetableSnapshot mutableSnapshot() {
    throw timetableDataNotAccessible();
  }

  @Override
  public TransitService transitService() {
    throw timetableDataNotAccessible();
  }

  @Override
  public GtfsRealtimeFuzzyTripMatcher gtfsRealtimeFuzzyTripMatcher() {
    throw timetableDataNotAccessible();
  }

  @Override
  public EntityResolver entityResolver(String feedId) {
    throw timetableDataNotAccessible();
  }

  private static UnsupportedOperationException timetableDataNotAccessible() {
    return new UnsupportedOperationException(
      "Timetable data is not accessible from the street write domain. " +
        "Updaters that need it must use WriteDomain.TRANSIT."
    );
  }
}
