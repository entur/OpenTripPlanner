package org.opentripplanner.updater.spi;

import java.util.List;
import org.opentripplanner.updater.StreetRealTimeUpdateContext;
import org.opentripplanner.updater.TransitRealTimeUpdateContext;

/**
 * The write domain an updater's write tasks belong to. Each domain owns a disjoint set of
 * mutable data and has its own single writer thread, so updaters working on unrelated domains
 * run in parallel instead of queueing behind each other.
 * <p>
 * A write task submitted through {@link WriteToGraphCallback} may only modify data owned by the
 * updater's domain, and may not read another domain's uncommitted (mutable) state.
 * <p>
 * The type parameter ties the domain to its update context at compile time: an updater
 * parameterized with one domain's context cannot declare the other domain, because the wrong
 * token does not type-check in {@link GraphUpdater#writeDomain()}.
 *
 * @param <C> the update context handed to this domain's write tasks
 */
public final class WriteDomain<C> {

  /**
   * Timetable data, alerts and realtime vehicles. Tasks in this domain receive a
   * {@link TransitRealTimeUpdateContext} with access to the mutable timetable snapshot.
   */
  public static final WriteDomain<TransitRealTimeUpdateContext> TRANSIT = new WriteDomain<>(
    "TRANSIT"
  );

  /**
   * The street graph and the vehicle-rental and vehicle-parking repositories. Tasks in this
   * domain may not touch timetable data.
   */
  public static final WriteDomain<StreetRealTimeUpdateContext> STREET = new WriteDomain<>("STREET");

  private static final List<WriteDomain<?>> ALL = List.<WriteDomain<?>>of(TRANSIT, STREET);

  private final String name;

  private WriteDomain(String name) {
    this.name = name;
  }

  public static List<WriteDomain<?>> values() {
    return ALL;
  }

  @Override
  public String toString() {
    return name;
  }
}
