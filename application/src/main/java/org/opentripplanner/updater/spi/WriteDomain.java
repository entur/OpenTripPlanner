package org.opentripplanner.updater.spi;

/**
 * The write domain an updater's write tasks belong to. Each domain owns a disjoint set of
 * mutable data and has its own single writer thread, so updaters working on unrelated domains
 * run in parallel instead of queueing behind each other.
 * <p>
 * A write task submitted through {@link WriteToGraphCallback} may only modify data owned by the
 * updater's domain, and may not read another domain's uncommitted (mutable) state.
 */
public enum WriteDomain {
  /**
   * Timetable data, alerts and realtime vehicles. Tasks in this domain receive a
   * {@link org.opentripplanner.updater.RealTimeUpdateContext} with access to the mutable
   * timetable snapshot.
   */
  TRANSIT,
  /**
   * The street graph and the vehicle-rental and vehicle-parking repositories. Tasks in this
   * domain may not touch timetable data.
   */
  STREET,
}
