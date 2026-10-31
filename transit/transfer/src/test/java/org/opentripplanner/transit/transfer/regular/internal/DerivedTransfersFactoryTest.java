package org.opentripplanner.transit.transfer.regular.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.basic.Reluctance;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.data.transfers.regular.DerivedTransferStore;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.transit.transfer.regular.FakeRequestMapper;
import org.opentripplanner.transit.transfer.regular.api.maxduration.MaxDurationsPreference;
import org.opentripplanner.transit.transfer.regular.api.preferences.WalkPreferences;
import org.opentripplanner.transit.transfer.regular.spi.PathCriteria;
import org.opentripplanner.transit.transfer.regular.spi.TransferPath;
import org.opentripplanner.transit.transfer.regular.spi.TransferSearch;

/**
 * Strategy 1a: a path the search can not represent by components is costed exactly for every
 * request, and the derived transfers are the same as the exact ones, also after a patch.
 */
class DerivedTransfersFactoryTest {

  private static final int N_STOPS = 20;
  private static final WalkPreferences PROFILE = reluctance(1.0);

  /**
   * A path: the cost is {@code cost * reluctance} for a plain path (derivable), and
   * {@code cost * reluctance^2} for an {@code irregular} one, which the search can not represent.
   * A path with a negative cost can not be traversed under a reluctance above 2 (only used for
   * irregular paths, a derived transfer is never dropped).
   */
  private record Path(int duration, int cost, boolean irregular) {}

  private static final class FakeSearch implements TransferSearch<Path> {

    private final double reluctance;
    int derivableCalls = 0;

    FakeSearch(double reluctance) {
      this.reluctance = reluctance;
    }

    @Override
    public Collection<TransferPath<Path>> findNearbyStops(
      FeedScopedId from,
      MaxDurationsPreference m
    ) {
      return List.of();
    }

    @Override
    public Optional<PathCriteria> computePathCriteria(Path p) {
      if (p.cost() < 0 && reluctance > 2.0) {
        return Optional.empty();
      }
      double r = p.irregular() ? reluctance * reluctance : reluctance;
      return Optional.of(new PathCriteria((int) Math.round(Math.abs(p.cost()) * r), p.duration()));
    }

    @Override
    public float[] components(Path p) {
      if (p.irregular()) {
        return NOT_DERIVABLE;
      }
      // A path that is not traversable for the profile is not stored
      return computePathCriteria(p).isEmpty() ? null : new float[] { p.duration(), p.cost() };
    }

    @Override
    public TransferValueFunction valueFunction() {
      return (payload, offset, length) -> {
        ++derivableCalls;
        return TransferValueFunction.pack(
          (int) payload[offset],
          (int) Math.round(payload[offset + 1] * reluctance)
        );
      };
    }
  }

  private final List<FakeSearch> searches = new ArrayList<>();

  private final RaptorTransfersFactory<Path, WalkPreferences> exact = new RaptorTransfersFactory<>(
    N_STOPS,
    request -> {
      var search = new FakeSearch(request.reluctance().value());
      searches.add(search);
      return search;
    },
    FakeRequestMapper.INSTANCE,
    Map.of(PROFILE, PROFILE)
  );

  private static WalkPreferences reluctance(double value) {
    return WalkPreferences.of().withReluctance(Reluctance.of(value)).build();
  }

  private OverlaidTemplatePaths<Path> paths(Map<Integer, Map<Integer, Path>> byFromStop) {
    var base = new TemplatePathsByUserProfile<Path>();
    byFromStop.forEach((from, row) -> base.setPaths(PROFILE, from, row));
    return new OverlaidTemplatePaths<>(base);
  }

  private static Map<Integer, Map<Integer, Path>> graph() {
    return Map.of(
      0,
      Map.of(1, new Path(100, 100, false), 2, new Path(60, 200, true), 3, new Path(80, 50, false)),
      1,
      Map.of(2, new Path(30, 40, false), 3, new Path(10, 70, true)),
      2,
      Map.of(0, new Path(90, 300, true))
    );
  }

  private static List<String> read(RaptorRegularTransferService service, boolean from) {
    var result = new ArrayList<String>();
    for (int stop = 0; stop < N_STOPS; ++stop) {
      var it = from ? service.getTransfersFromStop(stop) : service.getTransfersToStop(stop);
      var row = new ArrayList<String>();
      int last = -1;
      while (it.hasNext()) {
        var t = it.next();
        assertThat(t.durationInSeconds()).isAtLeast(last);
        last = t.durationInSeconds();
        row.add(t.stop() + "/" + t.durationInSeconds() + "/" + t.c1());
      }
      row.sort(String::compareTo);
      result.add(stop + ":" + row);
    }
    return result;
  }

  private static void assertSame(
    RaptorRegularTransferService actual,
    RaptorRegularTransferService expected
  ) {
    assertThat(read(actual, true)).containsExactlyElementsIn(read(expected, true));
    assertThat(read(actual, false)).containsExactlyElementsIn(read(expected, false));
  }

  @Test
  void theNotDerivablePathsAreCostedExactlyAndAreCounted() {
    for (var mode : DerivedTransferStore.Mode.values()) {
      var factory = new DerivedTransfersFactory<>(
        DerivedTransfersFactory.Kind.COMPONENTS,
        mode,
        N_STOPS,
        exact
      );
      var paths = paths(graph());
      var tables = new DerivedTables<Path>();
      for (double r : new double[] { 0.5, 1.0, 2.0, 3.7 }) {
        var user = reluctance(r);
        var derived = factory.create(paths, tables, user, PROFILE);
        assertSame(derived, exact.create(paths, user, PROFILE));
      }
      var entry = tables.entry(paths, PROFILE, factory);
      // 3 of 6 paths are irregular, all are stored
      assertThat(entry.encoding().exactPaths()).isEqualTo(3);
      assertThat(entry.table().size()).isEqualTo(6);
    }
  }

  @Test
  void aPathNotTraversableForTheUserIsLeftOutOfTheRowsWhenItIsCostedExactly() {
    var base = Map.of(0, Map.of(1, new Path(100, -100, true), 2, new Path(10, 10, false)));
    var factory = new DerivedTransfersFactory<>(
      DerivedTransfersFactory.Kind.COMPONENTS,
      DerivedTransferStore.Mode.LAZY,
      N_STOPS,
      exact
    );
    // The irregular path is stored (it can be traversed for the profile), and dropped for the
    // users under which the exact search drops it
    var paths = paths(base);
    var tables = new DerivedTables<Path>();
    var user = reluctance(3.0);
    assertSame(factory.create(paths, tables, user, PROFILE), exact.create(paths, user, PROFILE));
    assertThat(read(factory.create(paths, tables, user, PROFILE), true).get(0)).isEqualTo(
      "0:[2/10/30]"
    );
    var low = reluctance(1.5);
    assertSame(factory.create(paths, tables, low, PROFILE), exact.create(paths, low, PROFILE));
    assertThat(read(factory.create(paths, tables, low, PROFILE), true).get(0)).contains("1/100/");
  }

  @Test
  void aPatchedStoreEqualsTheExactTransfersOfTheNewPaths() {
    for (var mode : DerivedTransferStore.Mode.values()) {
      var factory = new DerivedTransfersFactory<>(
        DerivedTransfersFactory.Kind.COMPONENTS,
        mode,
        N_STOPS,
        exact
      );
      var paths = paths(graph());
      var tables = new DerivedTables<Path>();
      var user = reluctance(2.5);
      var before = factory.create(paths, tables, user, PROFILE);
      read(before, true);

      // Stop 0 gets new paths (one irregular), stop 2 loses all its paths, stop 5 gets one
      var next = paths.copy();
      next.replace(PROFILE, 0, Map.of(4, new Path(15, 25, true), 1, new Path(20, 20, false)));
      next.replace(PROFILE, 2, Map.of());
      next.replace(PROFILE, 5, Map.of(0, new Path(5, 5, true)));
      var changed = Set.of(0, 2, 5);
      var nextTables = tables.next(next, Map.of(PROFILE, changed), factory);

      var patched = factory.patch(next, nextTables, user, PROFILE, before, changed);
      assertSame(patched, exact.create(next, user, PROFILE));
      // The previous store is not changed by the patch
      assertSame(before, exact.create(paths, user, PROFILE));
      // The encoding of the patched table counts the exact paths of the new table
      assertThat(nextTables.entry(next, PROFILE, factory).encoding().exactPaths()).isAtLeast(3);
    }
  }

  @Test
  void theTableIsBuiltAgainWhenTheReplacedExactPathsPileUp() {
    var factory = new DerivedTransfersFactory<>(
      DerivedTransfersFactory.Kind.COMPONENTS,
      DerivedTransferStore.Mode.LAZY,
      N_STOPS,
      exact
    );
    var paths = paths(graph());
    var tables = new DerivedTables<Path>();
    tables.entry(paths, PROFILE, factory);
    var current = paths;
    for (int round = 0; round < 700; ++round) {
      var next = current.copy();
      var row = new HashMap<Integer, Path>();
      for (int to = 1; to < 6; ++to) {
        row.put(to, new Path(10 + round, 10, true));
      }
      next.replace(PROFILE, 0, row);
      tables = tables.next(next, Map.of(PROFILE, Set.of(0)), factory);
      current = next;
    }
    // Without the rebuild the encoding would hold 3 + 700 * 5 paths
    assertThat(tables.entry(current, PROFILE, factory).encoding().exactPaths()).isLessThan(2000);
    var user = reluctance(1.7);
    var store = factory.create(current, tables, user, PROFILE);
    assertSame(store, exact.create(current, user, PROFILE));
  }
}
