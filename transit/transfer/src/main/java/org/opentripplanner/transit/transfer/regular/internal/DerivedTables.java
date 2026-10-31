package org.opentripplanner.transit.transfer.regular.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;

/**
 * The {@link DerivedTransfersFactory.Entry tables} of the profiles of one snapshot. A table is
 * created when it is first needed, from the paths of the snapshot. The tables of the next snapshot
 * are patched from these for the stops whose transfers changed, see {@link #next}, so the cost of
 * a real-time update does not depend on the number of cached transfers.
 * <p>
 * This class is thread-safe.
 */
final class DerivedTables<P> {

  private final Map<AbstractUserPreferences<?>, DerivedTransfersFactory.Entry<P>> entries =
    new HashMap<>();

  /** The entry of the profile, created from {@code paths} the first time. {@code null} if none. */
  @Nullable
  synchronized DerivedTransfersFactory.Entry<P> entry(
    OverlaidTemplatePaths<P> paths,
    AbstractUserPreferences<?> profile,
    DerivedTransfersFactory<P, ?> factory
  ) {
    if (entries.containsKey(profile)) {
      return entries.get(profile);
    }
    var entry = factory.buildEntry(paths, profile);
    entries.put(profile, entry);
    return entry;
  }

  /**
   * The tables of the next snapshot: the table of a profile with changed stops is patched, the
   * others are shared. A profile without a table is not created, it is built when it is needed.
   */
  synchronized DerivedTables<P> next(
    OverlaidTemplatePaths<P> paths,
    Map<AbstractUserPreferences<?>, Set<Integer>> changedFromStops,
    DerivedTransfersFactory<P, ?> factory
  ) {
    var next = new DerivedTables<P>();
    entries.forEach((profile, entry) -> {
      var changed = changedFromStops.getOrDefault(profile, Set.of());
      if (entry == null || changed.isEmpty()) {
        next.entries.put(profile, entry);
      } else {
        next.entries.put(profile, factory.patchEntry(paths, profile, entry, changed));
      }
    });
    return next;
  }
}
