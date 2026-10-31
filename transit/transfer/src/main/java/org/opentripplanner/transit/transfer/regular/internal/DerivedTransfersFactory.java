package org.opentripplanner.transit.transfer.regular.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.raptor.data.transfers.regular.DerivedTransferStore;
import org.opentripplanner.raptor.data.transfers.regular.DerivedTransferTable;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.raptor.data.transfers.regular.RaptorTransferStore;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;
import org.opentripplanner.transit.transfer.regular.spi.TransferSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives the Raptor transfers of any user preferences from a table of preference independent
 * numbers per path (a {@link DerivedTransferTable}) instead of re-costing every path, see issue
 * #8153, strategy 1. There are two ways to fill the table:
 * <ul>
 *   <li>{@link Kind#COMPONENTS} (1a): the street path is split into components by the path
 *       provider, and the duration and the cost are computed from them.</li>
 *   <li>{@link Kind#INTERPOLATE} (1b): the exact criteria of the path are computed for a few
 *       anchor preferences and interpolated, see {@link InterpolationAnchors}.</li>
 * </ul>
 * User preferences that the chosen way does not support fall back to {@link RaptorTransfersFactory}.
 * <p>
 * This class is stateless and thread-safe, the tables are held by {@link DerivedTables}.
 */
final class DerivedTransfersFactory<P, R> {

  private static final Logger LOG = LoggerFactory.getLogger(DerivedTransfersFactory.class);

  enum Kind {
    COMPONENTS,
    INTERPOLATE,
  }

  private final Kind kind;
  private final DerivedTransferStore.Mode mode;
  private final int nStops;
  private final RaptorTransfersFactory<P, R> exact;

  DerivedTransfersFactory(
    Kind kind,
    DerivedTransferStore.Mode mode,
    int nStops,
    RaptorTransfersFactory<P, R> exact
  ) {
    this.kind = kind;
    this.mode = mode;
    this.nStops = nStops;
    this.exact = exact;
  }

  /** From the system properties of the experiment, {@code null} if exact re-costing is used. */
  @Nullable
  static <P, R> DerivedTransfersFactory<P, R> fromSystemProperties(
    int nStops,
    RaptorTransfersFactory<P, R> exact
  ) {
    var strategy = System.getProperty("otp.transfers.strategy", "exact");
    var kind = switch (strategy) {
      case "1a" -> Kind.COMPONENTS;
      case "1b" -> Kind.INTERPOLATE;
      default -> null;
    };
    if (kind == null) {
      return null;
    }
    var mode = DerivedTransferStore.Mode.valueOf(
      System.getProperty("otp.transfers.mode", "lazy").toUpperCase().replace("ONREAD", "ON_READ")
    );
    return new DerivedTransfersFactory<>(kind, mode, nStops, exact);
  }

  // ---------------------------------------------------------------- create and patch

  /**
   * Create the transfers of {@code userPreferences}: a store derived from the table of the profile
   * if the preferences are supported, the exact transfers if not.
   */
  RaptorRegularTransferService create(
    OverlaidTemplatePaths<P> paths,
    DerivedTables<P> tables,
    AbstractUserPreferences<?> userPreferences,
    @Nullable AbstractUserPreferences<?> profile
  ) {
    var derived = derive(paths, tables, userPreferences, profile);
    if (derived != null) {
      return derived;
    }
    LOG.info("TRANSFERS-STRATEGY fallback exact for {}", userPreferences);
    return exact.create(paths, userPreferences, profile);
  }

  @Nullable
  private RaptorRegularTransferService derive(
    OverlaidTemplatePaths<P> paths,
    DerivedTables<P> tables,
    AbstractUserPreferences<?> userPreferences,
    @Nullable AbstractUserPreferences<?> profile
  ) {
    if (profile == null) {
      return null;
    }
    var entry = tables.entry(paths, profile, this);
    if (entry == null) {
      return null;
    }
    var function = entry.encoding().function(userPreferences);
    if (function == null) {
      return null;
    }
    return DerivedTransferStore.of(entry.table(), function, mode);
  }

  /**
   * Patch {@code previous}, the transfers of {@code userPreferences} of an earlier snapshot, for
   * the stops whose transfers changed.
   */
  RaptorRegularTransferService patch(
    OverlaidTemplatePaths<P> paths,
    DerivedTables<P> tables,
    AbstractUserPreferences<?> userPreferences,
    @Nullable AbstractUserPreferences<?> profile,
    RaptorRegularTransferService previous,
    Set<Integer> changedFromStops
  ) {
    if (previous instanceof DerivedTransferStore store && profile != null) {
      var entry = tables.entry(paths, profile, this);
      if (entry != null) {
        var function = entry.encoding().function(userPreferences);
        if (function != null) {
          return store.patch(entry.table(), function, changedFromStops);
        }
      }
    }
    if (previous instanceof RaptorTransferStore exactStore) {
      return exact.patch(paths, userPreferences, profile, exactStore, changedFromStops);
    }
    return create(paths, tables, userPreferences, profile);
  }

  // ---------------------------------------------------------------- tables

  /** The encoding and the table of a profile. */
  record Entry<P>(PathEncoding<P> encoding, DerivedTransferTable table) {}

  /** How the numbers of a path are computed, and the function to evaluate them. */
  interface PathEncoding<P> {
    /** @return the payload of the path, {@code null} if the path can not be traversed */
    @Nullable
    float[] encode(P path);

    @Nullable
    TransferValueFunction function(AbstractUserPreferences<?> userPreferences);

    /** The number of paths that are costed exactly for every request. */
    default int exactPaths() {
      return 0;
    }

    /** The encoding to encode the paths of a patch with, it must not change this encoding. */
    default PathEncoding<P> forPatch() {
      return this;
    }

    /** True if the table should be built again, to drop what the replaced paths left behind. */
    default boolean isStale() {
      return false;
    }
  }

  /** @return {@code null} if no path of the profile can be encoded */
  @Nullable
  Entry<P> buildEntry(OverlaidTemplatePaths<P> paths, AbstractUserPreferences<?> profile) {
    var encoding = encodingFor(profile);
    if (encoding == null) {
      return null;
    }
    long start = System.nanoTime();
    var builder = DerivedTransferTable.of(nStops);
    int[] skipped = new int[1];
    paths.forEachPath(profile, (from, to, path) -> {
      var payload = encoding.encode(path);
      if (payload == null) {
        ++skipped[0];
      } else {
        builder.addTransfer(from, to, payload);
      }
    });
    var table = builder.build();
    LOG.info(
      "TRANSFERS-STRATEGY built the table of {} with {} transfers ({} skipped, {} exact fallback paths = {} %) in {} ms",
      profile,
      table.size(),
      skipped[0],
      encoding.exactPaths(),
      String.format("%.4f", (100.0 * encoding.exactPaths()) / Math.max(1, table.size())),
      (System.nanoTime() - start) / 1_000_000
    );
    return new Entry<>(encoding, table);
  }

  Entry<P> patchEntry(
    OverlaidTemplatePaths<P> paths,
    AbstractUserPreferences<?> profile,
    Entry<P> previous,
    Set<Integer> changedFromStops
  ) {
    var encoding = previous.encoding().forPatch();
    var patch = previous.table().patch();
    for (int fromStop : changedFromStops) {
      patch.replaceTransfersFrom(fromStop);
      paths.pathsFrom(profile, fromStop).forEach((toStop, path) -> {
        var payload = encoding.encode(path);
        if (payload != null) {
          patch.addTransfer(fromStop, toStop, payload);
        }
      });
    }
    var patched = new Entry<>(encoding, patch.build());
    // The replaced paths that are costed exactly stay in the encoding until the table is built again
    return encoding.isStale() ? buildEntry(paths, profile) : patched;
  }

  @Nullable
  private PathEncoding<P> encodingFor(AbstractUserPreferences<?> profile) {
    if (kind == Kind.COMPONENTS) {
      var search = exact.searchFor(profile, profile);
      if (search.valueFunction() != null) {
        return new ComponentsEncoding<>(exact, search, profile);
      }
    }
    var anchors = InterpolationAnchors.of(profile);
    if (anchors == null) {
      return null;
    }
    var searches = anchors
      .anchors()
      .stream()
      .map(anchor -> exact.searchFor(anchor, profile))
      .toList();
    return new AnchorEncoding<>(anchors, searches);
  }

  /**
   * The components of the paths from the path provider. A path the provider can not represent
   * exactly is costed with the exact calculation for every request: the payload of its transfer
   * is {@code [FALLBACK, index]}, the index is the position of the path in {@link #exactPaths}.
   * The payload of a path with components is never of length 2.
   */
  private static final class ComponentsEncoding<P, R> implements PathEncoding<P> {

    private static final float FALLBACK = -1f;
    private static final int MAX_EXACT_PATHS = 1 << 24;

    private final RaptorTransfersFactory<P, R> exact;
    private final TransferSearch<P> profileSearch;
    private final AbstractUserPreferences<?> profile;
    private final ArrayList<P> exactPaths;
    private final int sizeAtBuild;

    ComponentsEncoding(
      RaptorTransfersFactory<P, R> exact,
      TransferSearch<P> profileSearch,
      AbstractUserPreferences<?> profile
    ) {
      this(exact, profileSearch, profile, new ArrayList<>(), -1);
    }

    private ComponentsEncoding(
      RaptorTransfersFactory<P, R> exact,
      TransferSearch<P> profileSearch,
      AbstractUserPreferences<?> profile,
      ArrayList<P> exactPaths,
      int sizeAtBuild
    ) {
      this.exact = exact;
      this.profileSearch = profileSearch;
      this.profile = profile;
      this.exactPaths = exactPaths;
      this.sizeAtBuild = sizeAtBuild;
    }

    @Override
    public float[] encode(P path) {
      var components = profileSearch.components(path);
      if (components == null || components.length > 0) {
        return components;
      }
      if (exactPaths.size() >= MAX_EXACT_PATHS) {
        throw new IllegalStateException("Too many paths to cost exactly");
      }
      exactPaths.add(path);
      return new float[] { FALLBACK, exactPaths.size() - 1 };
    }

    @Override
    public int exactPaths() {
      return exactPaths.size();
    }

    @Override
    public PathEncoding<P> forPatch() {
      // Entries of older snapshots keep their list, so a patch appends to a copy
      return new ComponentsEncoding<>(
        exact,
        profileSearch,
        profile,
        new ArrayList<>(exactPaths),
        // The size after the table was built, the first patch fixes it
        sizeAtBuild < 0 ? exactPaths.size() : sizeAtBuild
      );
    }

    @Override
    public boolean isStale() {
      return sizeAtBuild >= 0 && exactPaths.size() > 2 * sizeAtBuild + 1024;
    }

    @Override
    public TransferValueFunction function(AbstractUserPreferences<?> userPreferences) {
      var search = exact.searchFor(userPreferences, profile);
      var derived = search.valueFunction();
      if (derived == null || exactPaths.isEmpty()) {
        return derived;
      }
      var paths = Collections.unmodifiableList(exactPaths);
      return (payload, offset, length) -> {
        if (length == 2 && payload[offset] == FALLBACK) {
          return search
            .computePathCriteria(paths.get((int) payload[offset + 1]))
            .map(c -> TransferValueFunction.pack(c.durationInSeconds(), c.c1()))
            .orElse(TransferValueFunction.NONE);
        }
        return derived.evaluate(payload, offset, length);
      };
    }
  }

  private static final class AnchorEncoding<P> implements PathEncoding<P> {

    private final InterpolationAnchors anchors;
    private final List<TransferSearch<P>> searches;

    AnchorEncoding(InterpolationAnchors anchors, List<TransferSearch<P>> searches) {
      this.anchors = anchors;
      this.searches = searches;
    }

    @Override
    public float[] encode(P path) {
      int n = searches.size();
      int[] duration = new int[n];
      int[] c1 = new int[n];
      for (int i = 0; i < n; ++i) {
        var criteria = searches.get(i).computePathCriteria(path);
        if (criteria.isEmpty()) {
          return null;
        }
        duration[i] = criteria.get().durationInSeconds();
        c1[i] = criteria.get().c1();
      }
      return anchors.payload(duration, c1);
    }

    @Override
    public TransferValueFunction function(AbstractUserPreferences<?> userPreferences) {
      return anchors.function(userPreferences);
    }
  }
}
