package org.opentripplanner.transit.speed_test;

import com.sun.management.ThreadMXBean;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.opentripplanner.core.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.core.model.transaction.RepositoryHandle;
import org.opentripplanner.core.model.transaction.RepositoryRegistry;
import org.opentripplanner.place.api.NearbyStop;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferencesBuilder;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.VehicleRoutingOptimizeType;
import org.opentripplanner.transfer.configure.StreetTransferFactory;
import org.opentripplanner.transit.service.SiteRepository;
import org.opentripplanner.transit.transfer.regular.RegularTransferRepository;
import org.opentripplanner.transit.transfer.regular.RegularTransferRepositorySnapshot;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.BicyclePreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.CarPreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.WalkPreferences;

/**
 * Benchmark and quality runner for the creation of the Raptor transfers of a new user preference
 * (a cache miss), see issue #8153. It is NOT a unit test, run it with {@link #main}.
 * <p>
 * Modes ({@code --mode=}):
 * <ul>
 *   <li>{@code bench}: for each case, the time of the miss, the allocated bytes of the miss, and the
 *       time to read the transfers of a sample of stops. After all cases, the heap retained per
 *       cache entry. Print one TSV line per case.</li>
 *   <li>{@code dump}: write the transfers of each case to {@code --out}. Run it on the baseline
 *       (the exact calculation) to create the reference.</li>
 *   <li>{@code compare}: compare the transfers of each case with the reference in {@code --ref},
 *       the from side and, if the reference has it, the to side. Exit code 1 if a transfer is
 *       outside the gate.</li>
 *   <li>{@code checkto}: every to row is the transposition of the from rows, and all rows are sorted
 *       by duration.</li>
 *   <li>{@code replay}, {@code heap}: the cost of a request that pays a miss, and the heap of an
 *       entry, for recorded read sets of Raptor requests.</li>
 * </ul>
 * Arguments: {@code --dir=<dir with graph.obj, otp-config.json>}, {@code --graph=<file>}
 * (default {@code <dir>/graph.obj}), {@code --profile=walk|bicycle|car|all} (default all),
 * {@code --repeat=<n>} (bench, default 3), {@code --set=base|extra|full} (the cases, default base:
 * the cases of the first review round, extra: the preferences of an independent review),
 * {@code --cache-size=<n>}, {@code --readsets=<file>}, {@code --rounds=<n>} (replay),
 * {@code --rt=delete|add|mixed} and {@code --stops=<n>} (the real-time modes).
 * <p>
 * The gate: for every transfer, {@code |estimate - exact| < 1% of exact + 1 unit}, for the
 * duration (1 unit = 1 s) and for c1 (1 unit = 100, which is 1 s of cost). A transfer that only
 * exists in one of the two is reported as missing/extra, and fails the gate.
 */
public class TransferMissBenchmark {

  private static final double GATE_RATIO = 0.01;
  private static final int DURATION_UNIT = 1;
  private static final int C1_UNIT = 100;
  private static final int SAMPLE_STOPS = 2_000;
  private static final int CACHE_SIZE = 10_000;

  /** One user preference to create the transfers for, the name is the stable id of the case. */
  private record Case(String name, String profile, RouteRequest request) {}

  public static void main(String[] args) throws Exception {
    Locale.setDefault(Locale.ROOT);
    var opts = new Opts(args);
    ConfigModel.initializeOtpFeatures(new OtpConfigLoader(opts.dir).loadOtpConfig());
    var model = SetupHelper.loadGraph(opts.dir, opts.graph);

    long heapBeforeWarmUp = usedHeapAfterGc();
    var registry = TransactionFactory.createRepositoryRegistry();
    var handle = StreetTransferFactory.registerRepository(
      registry,
      model.regularTransferBuildRepository(),
      model.buildConfig().transferProfiles(),
      model.graph(),
      model.transitRepository(),
      opts.cacheSize,
      false
    );
    var snapshot = handle.repositorySnapshot(registry.scope());
    int nStops = model.transitRepository().getSiteRepository().stopIndexSize();

    // Same as the server on startup: the profiles are in the cache before the first request
    long t0 = System.nanoTime();
    snapshot.warmUp();
    System.err.printf("Warm up (profiles): %d ms%n", (System.nanoTime() - t0) / 1_000_000);
    // Strategy 1: the heap the warmed up profiles retain (the entries of the profiles, or the tables)
    System.out.printf(
      "warmed_up_profiles_MB\t%.1f%n",
      (usedHeapAfterGc() - heapBeforeWarmUp) / 1e6
    );

    var cases = cases(opts.profile, opts.set);
    switch (opts.mode) {
      case "bench" -> bench(snapshot, cases, nStops, opts.repeat);
      case "dump" -> dump(snapshot, cases, nStops, opts.out);
      case "compare" -> System.exit(compare(snapshot, cases, nStops, opts.ref));
      case "read" -> read(snapshot, cases, nStops, opts.fraction);
      case "replay" -> replay(snapshot, cases, opts);
      case "heap" -> heap(snapshot, cases, opts);
      case "checkto" -> System.exit(checkTo(snapshot, cases, nStops));
      case "realtime-bench", "realtime-dump", "realtime-compare" -> System.exit(
        realtime(
          handle,
          registry,
          model.transitRepository().getSiteRepository(),
          snapshot,
          cases,
          nStops,
          opts
        )
      );
      default -> throw new IllegalArgumentException("Unknown mode: " + opts.mode);
    }
  }

  // ---------------------------------------------------------------- cases

  /**
   * Values in different steps of {@code Speed} and {@code Reluctance}, so each case is a distinct
   * cache entry (close values share one, see the issue). None is the value of a profile.
   */
  private static List<Case> cases(String profile, String set) {
    var list = new ArrayList<Case>();
    if (set.equals("base") || set.equals("full")) {
      list.addAll(baseCases(profile));
    }
    if (set.equals("extra") || set.equals("full")) {
      list.addAll(extraCases(profile));
    }
    return list;
  }

  private static List<Case> baseCases(String profile) {
    var list = new ArrayList<Case>();
    boolean all = profile.equals("all");
    if (all || profile.equals("walk")) {
      for (double v : new double[] { 0.8, 1.0, 1.1, 1.2, 1.4, 1.5, 1.6, 1.8 }) {
        list.add(walk("walk-speed-" + v, StreetMode.WALK, p -> p.withWalk(w -> w.withSpeed(v))));
      }
      for (double v : new double[] { 2.5, 3.0, 4.0 }) {
        list.add(
          walk("walk-reluctance-" + v, StreetMode.WALK, p -> p.withWalk(w -> w.withReluctance(v)))
        );
      }
      list.add(
        walk("walk-speed-reluctance", StreetMode.WALK, p ->
          p.withWalk(w -> w.withSpeed(1.6).withReluctance(3.0))
        )
      );
    }
    if (all || profile.equals("bicycle")) {
      for (double v : new double[] { 3.0, 4.0, 6.0, 8.0 }) {
        list.add(bike("bike-speed-" + v, p -> p.withBike(b -> b.withSpeed(v))));
      }
    }
    if (all || profile.equals("car")) {
      for (double v : new double[] { 1.5, 2.5 }) {
        list.add(
          new Case(
            "car-reluctance-" + v,
            "car",
            request(StreetMode.CAR, p -> p.withCar(c -> c.withReluctance(v)))
          )
        );
      }
    }
    return list;
  }

  /**
   * Cases of an independent review: preferences the base cases do not vary, in the dimensions of
   * the cache key (see {@code RegularTransferPreferencesMapper}): bicycle speed over the whole
   * range, bicycle reluctance, triangle, safe streets, walking with the bike, the safety factor
   * of walking, extreme walking speed and reluctance, and car reluctance.
   */
  private static List<Case> extraCases(String profile) {
    var list = new ArrayList<Case>();
    boolean all = profile.equals("all");
    if (all || profile.equals("bicycle")) {
      for (double v : new double[] { 2.5, 3.5, 5.0, 5.55, 7.0, 8.3, 9.0 }) {
        list.add(bike("xbike-speed-" + v, p -> p.withBike(b -> b.withSpeed(v))));
      }
      for (double v : new double[] { 1.7, 3.3, 6.1 }) {
        list.add(bike("xbike-reluctance-" + v, p -> p.withBike(b -> b.withReluctance(v))));
      }
      list.add(
        bike("xbike-reluctance-7.5-speed-5.3", p ->
          p.withBike(b -> b.withReluctance(7.5).withSpeed(5.3))
        )
      );
      list.add(
        bike("xbike-triangle", p ->
          p.withBike(b ->
            b
              .withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE)
              .withOptimizeTriangle(t -> t.withTime(0.3).withSlope(0.3).withSafety(0.4))
          )
        )
      );
      list.add(
        bike("xbike-safe", p ->
          p.withBike(b -> b.withOptimizeType(VehicleRoutingOptimizeType.SAFE_STREETS))
        )
      );
      list.add(
        bike("xbike-walkspeed", p ->
          p.withBike(b -> b.withWalking(w -> w.withSpeed(0.9).withReluctance(7.0)))
        )
      );
    }
    if (all || profile.equals("walk")) {
      list.add(
        walk("xwalk-safety-0.5", StreetMode.WALK, p -> p.withWalk(w -> w.withSafetyFactor(0.5)))
      );
      list.add(
        walk("xwalk-slow-0.5-rel-1.2", StreetMode.WALK, p ->
          p.withWalk(w -> w.withSpeed(0.5).withReluctance(1.2))
        )
      );
      list.add(
        walk("xwalk-fast-2.5-rel-9", StreetMode.WALK, p ->
          p.withWalk(w -> w.withSpeed(2.5).withReluctance(9.0))
        )
      );
      list.add(walk("xwalk-speed-3.5", StreetMode.WALK, p -> p.withWalk(w -> w.withSpeed(3.5))));
      list.add(
        walk("xwalk-safety-0.1-speed-1.1-rel-3", StreetMode.WALK, p ->
          p.withWalk(w -> w.withSafetyFactor(0.1).withSpeed(1.1).withReluctance(3.0))
        )
      );
    }
    if (all || profile.equals("car")) {
      for (double v : new double[] { 1.2, 3.0, 8.0 }) {
        list.add(
          new Case(
            "xcar-reluctance-" + v,
            "car",
            request(StreetMode.CAR, p -> p.withCar(c -> c.withReluctance(v)))
          )
        );
      }
    }
    return list;
  }

  private static Case bike(String name, Consumer<RoutingPreferencesBuilder> body) {
    return new Case(name, "bicycle", request(StreetMode.BIKE, body));
  }

  private static Case walk(String name, StreetMode mode, Consumer<RoutingPreferencesBuilder> body) {
    return new Case(name, "walk", request(mode, body));
  }

  private static RouteRequest request(
    StreetMode transferMode,
    Consumer<RoutingPreferencesBuilder> body
  ) {
    return RouteRequest.defaultValue()
      .copyOf()
      .withPreferences(body)
      .withJourney(j ->
        j.withModes(
          RequestModes.of()
            .withAccessMode(StreetMode.WALK)
            .withTransferMode(transferMode)
            .withEgressMode(StreetMode.WALK)
            .withDirectMode(StreetMode.NOT_SET)
            .build()
        )
      )
      .buildDefault();
  }

  // ---------------------------------------------------------------- bench

  private static void bench(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    int repeat
  ) {
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    var sample = sampleStops(nStops);

    System.out.println(
      "case\tmiss_ms\tmiss_alloc_MB\tsample_read_ms\tsample_read_alloc_MB\ttransfers_in_sample"
    );
    long heapBefore = usedHeapAfterGc();
    for (var c : cases) {
      long t = System.nanoTime();
      long a = threads.getThreadAllocatedBytes(tid);
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      long missAlloc = threads.getThreadAllocatedBytes(tid) - a;
      long missNanos = System.nanoTime() - t;

      // A miss is only measured once per case, the entry is cached afterwards. The read of the
      // sample is repeated, and the fastest is reported, to exclude noise of the first pass.
      long bestRead = Long.MAX_VALUE;
      long readAlloc = 0;
      int n = 0;
      for (int i = 0; i < repeat; ++i) {
        long ra = threads.getThreadAllocatedBytes(tid);
        long rt = System.nanoTime();
        n = readSample(service, sample);
        long dt = System.nanoTime() - rt;
        if (dt < bestRead) {
          bestRead = dt;
          readAlloc = threads.getThreadAllocatedBytes(tid) - ra;
        }
      }
      System.out.printf(
        "%s\t%.1f\t%.1f\t%.2f\t%.2f\t%d%n",
        c.name(),
        missNanos / 1e6,
        missAlloc / 1e6,
        bestRead / 1e6,
        readAlloc / 1e6,
        n
      );
    }
    long heapAfter = usedHeapAfterGc();
    System.out.printf(
      "retained heap: %.1f MB for %d cache entries, %.1f MB per entry%n",
      (heapAfter - heapBefore) / 1e6,
      cases.size(),
      (heapAfter - heapBefore) / 1e6 / cases.size()
    );
  }

  private static int[] sampleStops(int nStops) {
    return sampleStops(nStops, SAMPLE_STOPS);
  }

  private static int[] sampleStops(int nStops, int count) {
    var random = new Random(42);
    return random.ints(0, nStops).distinct().limit(Math.min(count, nStops)).toArray();
  }

  // ---------------------------------------------------------------- read

  /**
   * The cost of reading the transfers of a share ({@code --fraction}) of all stops, from and to,
   * like Raptor does: time and allocation of the first read (cold) and the second (warm), and the
   * heap the entry retains afterward. One line per case.
   */
  private static void read(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    double fraction
  ) {
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    var stops = sampleStops(nStops, (int) Math.round(nStops * fraction));
    System.out.println(
      "read\tcase\tfraction\tmiss_ms\tfrom_cold_ms\tfrom_cold_allocMB\tto_cold_ms\tto_cold_allocMB\tfrom_warm_ms\tfrom_warm_allocMB\tto_warm_ms\tto_warm_allocMB\tfrom_n\tto_n\tretained_MB"
    );
    for (var c : cases) {
      long heapBefore = usedHeapAfterGc();
      long t = System.nanoTime();
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      double missMs = (System.nanoTime() - t) / 1e6;
      double[] r = new double[8];
      int[] n = new int[2];
      for (int pass = 0; pass < 2; ++pass) {
        for (int dir = 0; dir < 2; ++dir) {
          long a = threads.getThreadAllocatedBytes(tid);
          long t1 = System.nanoTime();
          n[dir] = readAll(service, stops, dir == 0);
          r[pass * 4 + dir * 2] = (System.nanoTime() - t1) / 1e6;
          r[pass * 4 + dir * 2 + 1] = (threads.getThreadAllocatedBytes(tid) - a) / 1e6;
        }
      }
      long heapAfter = usedHeapAfterGc();
      System.out.printf(
        "read\t%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d\t%.1f%n",
        c.name(),
        fraction,
        missMs,
        r[0],
        r[1],
        r[2],
        r[3],
        r[4],
        r[5],
        r[6],
        r[7],
        n[0],
        n[1],
        (heapAfter - heapBefore) / 1e6
      );
    }
  }

  private static int readAll(RaptorRegularTransferService service, int[] stops, boolean from) {
    int count = 0;
    long sink = 0;
    for (int stop : stops) {
      var it = from ? service.getTransfersFromStop(stop) : service.getTransfersToStop(stop);
      while (it.hasNext()) {
        var tx = it.next();
        sink += tx.stop() + tx.durationInSeconds() + tx.c1();
        ++count;
      }
    }
    return sink == 42 ? -count : count;
  }

  // ---------------------------------------------------------------- real-time

  private static final Set<String> REALTIME_CASES = Set.of(
    "walk-speed-1.4",
    "walk-speed-reluctance",
    "bike-speed-6.0",
    "xbike-reluctance-3.3",
    "car-reluctance-2.5"
  );

  /**
   * The transfers from {@code --stops} random stops are replaced, like a real-time update does: each
   * of these stops loses the transfers to the stops with an odd index. The cache entries of the
   * cases are created, and read for a sample of stops, before the update. After the update, the
   * entries are patched by the first request. {@code realtime-bench} prints the time of the update
   * and of the patch; {@code realtime-dump} writes the transfers after the update, run it on the
   * exact baseline; {@code realtime-compare} compares them with the dump with the usual gate.
   *
   * @return the exit code, 0 for the bench and the dump
   */
  private static int realtime(
    RepositoryHandle<
      RegularTransferRepositorySnapshot<NearbyStop, RouteRequest>,
      RegularTransferRepository<NearbyStop>
    > handle,
    RepositoryRegistry registry,
    SiteRepository site,
    RegularTransferRepositorySnapshot<NearbyStop, RouteRequest> snapshot,
    List<Case> allCases,
    int nStops,
    Opts opts
  ) throws Exception {
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    var cases = allCases
      .stream()
      .filter(c -> REALTIME_CASES.contains(c.name()))
      .toList();
    var sample = sampleStops(nStops);
    // -Dbench.noprewarm=true: no entry exists at the update, so the transfers are created, not
    // patched. Use it to create the reference of the update with the exact calculation.
    if (!Boolean.getBoolean("bench.noprewarm")) {
      for (var c : cases) {
        var service = snapshot.forRequest(c.request()).raptorTransfers();
        readAll(service, sample, true);
        readAll(service, sample, false);
      }
    }

    // The new transfers: the same paths without the ones to odd stops
    var random = new Random(7);
    var changed = random.ints(0, nStops).distinct().limit(opts.stops).toArray();
    var walkRequest = cases
      .stream()
      .filter(c -> c.profile().equals("walk"))
      .findFirst()
      .get();
    var bikeRequest = cases
      .stream()
      .filter(c -> c.profile().equals("bicycle"))
      .findFirst()
      .get();
    var carRequest = cases
      .stream()
      .filter(c -> c.profile().equals("car"))
      .findFirst()
      .get();
    var updateManager = TransactionFactory.createUpdateManagerWithAtomicCommits(
      "benchmark",
      registry,
      Thread.ofPlatform().factory()
    );
    long t = System.nanoTime();
    updateManager
      .submit(ctx -> {
        var repository = ctx.repository(handle);
        for (int stop : changed) {
          replace(
            repository,
            snapshot,
            site,
            walkRequest,
            WalkPreferences.DEFAULT,
            stop,
            opts.rt,
            nStops
          );
          replace(
            repository,
            snapshot,
            site,
            bikeRequest,
            BicyclePreferences.DEFAULT,
            stop,
            opts.rt,
            nStops
          );
          replace(
            repository,
            snapshot,
            site,
            carRequest,
            CarPreferences.DEFAULT,
            stop,
            opts.rt,
            nStops
          );
        }
      })
      .get();
    double commitMs = (System.nanoTime() - t) / 1e6;
    updateManager.shutdown();
    var after = handle.repositorySnapshot(registry.scope());
    System.out.printf("realtime\tstops\t%d\tcommit_ms\t%.1f%n", changed.length, commitMs);

    var renamed = cases
      .stream()
      .map(c -> new Case(c.name() + "-rt-" + opts.rt + "-" + opts.stops, c.profile(), c.request()))
      .toList();
    switch (opts.mode) {
      case "realtime-bench" -> {
        System.out.println("realtime\tcase\tpatch_ms\tpatch_allocMB\tfirst_read_all_ms");
        for (var c : renamed) {
          long a = threads.getThreadAllocatedBytes(tid);
          long t1 = System.nanoTime();
          var service = after.forRequest(c.request()).raptorTransfers();
          double patchMs = (System.nanoTime() - t1) / 1e6;
          double patchAlloc = (threads.getThreadAllocatedBytes(tid) - a) / 1e6;
          long t2 = System.nanoTime();
          readAll(service, IntStream.range(0, nStops).toArray(), true);
          System.out.printf(
            "realtime\t%s\t%.2f\t%.1f\t%.1f%n",
            c.name(),
            patchMs,
            patchAlloc,
            (System.nanoTime() - t2) / 1e6
          );
        }
        return 0;
      }
      case "realtime-dump" -> {
        dump(after, renamed, nStops, opts.out);
        return 0;
      }
      default -> {
        return compare(after, renamed, nStops, opts.ref);
      }
    }
  }

  /**
   * The new transfers from {@code stop}. {@code delete}: the paths to the stops with an odd index
   * are removed. {@code add}: all paths are kept, and the paths of another stop (the donor) are
   * added for the targets the stop has no path to. {@code mixed}: both. A donor path starts at
   * the donor stop, it is only used for its edges, so the result is a new transfer with new
   * numbers.
   */
  private static void replace(
    RegularTransferRepository<NearbyStop> repository,
    RegularTransferRepositorySnapshot<NearbyStop, RouteRequest> snapshot,
    SiteRepository site,
    Case c,
    AbstractUserPreferences<?> profile,
    int stop,
    String rt,
    int nStops
  ) {
    var view = snapshot.forRequest(c.request());
    var kept = new HashMap<Integer, NearbyStop>();
    for (var path : view.findPathsFrom(stop)) {
      int to = site.getRegularStop(path.stopId()).getIndex();
      if (rt.equals("add") || to % 2 == 0) {
        kept.put(to, path);
      }
    }
    if (!rt.equals("delete")) {
      for (int i = 1; i < nStops; ++i) {
        int donor = (stop + 7919 * i) % nStops;
        if (donor == stop) {
          continue;
        }
        var paths = view.findPathsFrom(donor);
        if (paths.isEmpty()) {
          continue;
        }
        for (var path : paths) {
          int to = site.getRegularStop(path.stopId()).getIndex();
          if (to != stop) {
            kept.putIfAbsent(to, path);
          }
        }
        break;
      }
    }
    repository.replaceTransfersFrom(profile, stop, kept);
  }

  private static int readSample(RaptorRegularTransferService service, int[] stops) {
    int count = 0;
    long sink = 0;
    for (int stop : stops) {
      var it = service.getTransfersFromStop(stop);
      while (it.hasNext()) {
        var tx = it.next();
        sink += tx.stop() + tx.durationInSeconds() + tx.c1();
        ++count;
      }
    }
    // Use the sink, so the reads are not removed by the JIT
    return sink == 42 ? -count : count;
  }

  private static long usedHeapAfterGc() {
    for (int i = 0; i < 3; ++i) {
      System.gc();
    }
    var rt = Runtime.getRuntime();
    return rt.totalMemory() - rt.freeMemory();
  }

  // ---------------------------------------------------------------- dump and compare

  /** The transfers of a case: for each stop in order, the transfers sorted on the to stop. */
  private record Transfers(int[] counts, int[] to, int[] duration, int[] c1) {}

  /**
   * All transfers of the service from the from side ({@code from}) or the to side, the rows sorted
   * on the other stop. On the to side the {@code to} array holds the from stops.
   */
  private static Transfers read(RaptorRegularTransferService service, int nStops, boolean from) {
    var counts = new int[nStops];
    int total = 0;
    var rows = new ArrayList<int[]>();
    for (int stop = 0; stop < nStops; ++stop) {
      var row = new ArrayList<int[]>();
      var it = from ? service.getTransfersFromStop(stop) : service.getTransfersToStop(stop);
      while (it.hasNext()) {
        var tx = it.next();
        row.add(new int[] { tx.stop(), tx.durationInSeconds(), tx.c1() });
      }
      row.sort((x, y) -> Integer.compare(x[0], y[0]));
      counts[stop] = row.size();
      rows.addAll(row);
      total += row.size();
    }
    var t = new int[total];
    var d = new int[total];
    var c = new int[total];
    int i = 0;
    for (var r : rows) {
      t[i] = r[0];
      d[i] = r[1];
      c[i] = r[2];
      ++i;
    }
    return new Transfers(counts, t, d, c);
  }

  private static void dump(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    File out
  ) throws IOException {
    Files.createDirectories(out.toPath());
    for (var c : cases) {
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      write(read(service, nStops, true), new File(out, c.name() + ".bin"), nStops);
      var to = read(service, nStops, false);
      write(to, new File(out, c.name() + ".to.bin"), nStops);
      System.err.printf("dumped %s: %d transfers%n", c.name(), to.to.length);
    }
  }

  private static void write(Transfers tx, File file, int nStops) throws IOException {
    try (
      var os = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file.toPath())))
    ) {
      os.writeInt(nStops);
      os.writeInt(tx.to.length);
      for (int n : tx.counts) {
        os.writeInt(n);
      }
      for (int i = 0; i < tx.to.length; ++i) {
        os.writeInt(tx.to[i]);
        os.writeInt(tx.duration[i]);
        os.writeInt(tx.c1[i]);
      }
    }
  }

  private static Transfers load(File file) throws IOException {
    try (
      var is = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())))
    ) {
      int nStops = is.readInt();
      int total = is.readInt();
      var counts = new int[nStops];
      for (int i = 0; i < nStops; ++i) {
        counts[i] = is.readInt();
      }
      var t = new int[total];
      var d = new int[total];
      var c = new int[total];
      for (int i = 0; i < total; ++i) {
        t[i] = is.readInt();
        d[i] = is.readInt();
        c[i] = is.readInt();
      }
      return new Transfers(counts, t, d, c);
    }
  }

  /**
   * Compare the from side of each case, and the to side if the reference has it, with the gate.
   *
   * @return 0 if all cases pass the gate, 1 if not
   */
  private static int compare(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    File ref
  ) throws IOException {
    System.out.println(
      "case\tprofile\treference\tmissing\textra\tout_of_gate_dur\tout_of_gate_c1\tmax_rel_dur\tmax_rel_c1\tp99_rel_dur\tp99_rel_c1\tmean_rel_dur\tmean_rel_c1\tto_missing\tto_extra\tto_out_dur\tto_out_c1\tresult"
    );
    int failed = 0;
    for (var c : cases) {
      var exact = load(new File(ref, c.name() + ".bin"));
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      var est = read(service, nStops, true);
      var r = diff(exact, est, nStops);
      boolean ok = r.missing == 0 && r.extra == 0 && r.outDur == 0 && r.outC1 == 0;
      String to = "-\t-\t-\t-";
      var toFile = new File(ref, c.name() + ".to.bin");
      if (toFile.exists()) {
        var t = diff(load(toFile), read(service, nStops, false), nStops);
        ok &= t.missing == 0 && t.extra == 0 && t.outDur == 0 && t.outC1 == 0;
        to = "%d\t%d\t%d\t%d".formatted(t.missing, t.extra, t.outDur, t.outC1);
      }
      if (!ok) {
        ++failed;
      }
      System.out.printf(
        "%s\t%s\t%d\t%d\t%d\t%d\t%d\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%s\t%s%n",
        c.name(),
        c.profile(),
        exact.to.length,
        r.missing,
        r.extra,
        r.outDur,
        r.outC1,
        r.maxDur,
        r.maxC1,
        r.p99Dur,
        r.p99C1,
        r.meanDur,
        r.meanC1,
        to,
        ok ? "PASS" : "FAIL"
      );
    }
    return failed == 0 ? 0 : 1;
  }

  private record Diff(
    int missing,
    int extra,
    int outDur,
    int outC1,
    double maxDur,
    double maxC1,
    double p99Dur,
    double p99C1,
    double meanDur,
    double meanC1
  ) {}

  private static Diff diff(Transfers exact, Transfers est, int nStops) {
    int missing = 0;
    int extra = 0;
    int outDur = 0;
    int outC1 = 0;
    var relDur = new double[exact.to.length];
    var relC1 = new double[exact.to.length];
    int n = 0;
    int ei = 0;
    int si = 0;
    for (int stop = 0; stop < nStops; ++stop) {
      int eEnd = ei + exact.counts[stop];
      int sEnd = si + est.counts[stop];
      while (ei < eEnd || si < sEnd) {
        if (si == sEnd || (ei < eEnd && exact.to[ei] < est.to[si])) {
          ++missing;
          ++ei;
        } else if (ei == eEnd || est.to[si] < exact.to[ei]) {
          ++extra;
          ++si;
        } else {
          double dd = rel(exact.duration[ei], est.duration[si]);
          double dc = rel(exact.c1[ei], est.c1[si]);
          relDur[n] = dd;
          relC1[n] = dc;
          ++n;
          if (!inGate(exact.duration[ei], est.duration[si], DURATION_UNIT)) {
            ++outDur;
          }
          if (!inGate(exact.c1[ei], est.c1[si], C1_UNIT)) {
            ++outC1;
          }
          ++ei;
          ++si;
        }
      }
    }
    return new Diff(
      missing,
      extra,
      outDur,
      outC1,
      max(relDur, n),
      max(relC1, n),
      p99(relDur, n),
      p99(relC1, n),
      mean(relDur, n),
      mean(relC1, n)
    );
  }

  private static boolean inGate(int exact, int est, int unit) {
    return Math.abs(est - exact) < GATE_RATIO * exact + unit;
  }

  private static double rel(int exact, int est) {
    return exact == 0 ? Math.abs(est) : Math.abs(est - exact) / (double) exact;
  }

  private static double max(double[] v, int n) {
    double m = 0;
    for (int i = 0; i < n; ++i) {
      m = Math.max(m, v[i]);
    }
    return m;
  }

  private static double mean(double[] v, int n) {
    double s = 0;
    for (int i = 0; i < n; ++i) {
      s += v[i];
    }
    return n == 0 ? 0 : s / n;
  }

  private static double p99(double[] v, int n) {
    if (n == 0) {
      return 0;
    }
    var copy = Arrays.copyOf(v, n);
    Arrays.sort(copy);
    return copy[(int) Math.min(n - 1, Math.floor(0.99 * n))];
  }

  // ---------------------------------------------------------------- replay of request reads

  /** The stops one Raptor request read: forward (from stops) and reverse (to stops). */
  private record ReadSet(int[] from, int[] to) {}

  private static List<ReadSet> loadReadSets(File file) throws IOException {
    try (
      var is = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())))
    ) {
      int n = is.readInt();
      var list = new ArrayList<ReadSet>();
      for (int i = 0; i < n; ++i) {
        var from = new int[is.readInt()];
        for (int j = 0; j < from.length; ++j) {
          from[j] = is.readInt();
        }
        var to = new int[is.readInt()];
        for (int j = 0; j < to.length; ++j) {
          to[j] = is.readInt();
        }
        list.add(new ReadSet(from, to));
      }
      return list;
    }
  }

  /** The read set with the median number of stops (from + to). */
  private static ReadSet medianReadSet(List<ReadSet> sets) {
    var sorted = new ArrayList<>(sets);
    sorted.sort(Comparator.comparingInt(r -> r.from.length + r.to.length));
    return sorted.get(sorted.size() / 2);
  }

  /**
   * For each recorded read set a new miss (run with {@code --cache-size=1}, so the cache holds
   * only the latest entry) followed by the forward and the reverse reads of the request, like
   * Raptor does. The case is the next one of the list, so each miss is for new preferences. The
   * first of {@code --rounds} rounds is a JIT warm-up and is not reported. The time and the
   * allocation include the miss and the first reads, the cost a request pays.
   */
  private static void replay(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    Opts opts
  ) throws IOException {
    var sets = loadReadSets(opts.readSets);
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    System.out.println(
      "round\tset\tcase\tprofile\tkind\tmiss_ms\tfwd_ms\trev_ms\ttotal_ms\talloc_MB\tfwd_stops\tfwd_transfers\trev_stops\trev_transfers"
    );
    for (int round = 0; round < opts.rounds; ++round) {
      for (int r = 0; r < sets.size(); ++r) {
        var set = sets.get(r);
        var c = cases.get((round * sets.size() + r) % cases.size());
        long a0 = threads.getThreadAllocatedBytes(tid);
        long t0 = System.nanoTime();
        var service = snapshot.forRequest(c.request()).raptorTransfers();
        long t1 = System.nanoTime();
        long nf = readStops(service, set.from, true);
        long t2 = System.nanoTime();
        long nt = readStops(service, set.to, false);
        long t3 = System.nanoTime();
        long alloc = threads.getThreadAllocatedBytes(tid) - a0;
        if (round == opts.rounds - 1) {
          System.out.printf(
            "%d\t%d\t%s\t%s\t%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d\t%d\t%d%n",
            round,
            r,
            c.name(),
            c.profile(),
            service.getClass().getSimpleName(),
            (t1 - t0) / 1e6,
            (t2 - t1) / 1e6,
            (t3 - t2) / 1e6,
            (t3 - t0) / 1e6,
            alloc / 1e6,
            set.from.length,
            nf,
            set.to.length,
            nt
          );
        }
      }
    }
  }

  private static long readStops(RaptorRegularTransferService service, int[] stops, boolean from) {
    return readAll(service, stops, from);
  }

  /**
   * The heap one cache entry retains, for the median read set: after the miss, and after the
   * forward and the reverse reads of the median read set of the recorded requests. Use a large
   * {@code --cache-size}, entries are added one by one and the heap after a full GC is compared
   * with the heap before the entry, so each line is one entry for the case of the line. The
   * preference independent table is NOT in the number, it is created before (warm up).
   */
  private static void heap(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    Opts opts
  ) throws IOException {
    var set = medianReadSet(loadReadSets(opts.readSets));
    System.out.println(
      "case\tprofile\tkind\tempty_MB\tafter_median_reads_MB\tfwd_stops\trev_stops"
    );
    for (var c : cases) {
      long h0 = usedHeapAfterGc();
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      long h1 = usedHeapAfterGc();
      readAll(service, set.from, true);
      readAll(service, set.to, false);
      long h2 = usedHeapAfterGc();
      System.out.printf(
        "%s\t%s\t%s\t%.2f\t%.2f\t%d\t%d%n",
        c.name(),
        c.profile(),
        service.getClass().getSimpleName(),
        (h1 - h0) / 1e6,
        (h2 - h0) / 1e6,
        set.from.length,
        set.to.length
      );
    }
  }

  /**
   * Check the to side: every to row holds exactly the transposed from rows, and every row, from
   * and to, is sorted by duration.
   *
   * @return 0 if all cases pass
   */
  private static int checkTo(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops
  ) {
    System.out.println(
      "case\ttransfers\tunsorted_from_rows\tunsorted_to_rows\tto_mismatch\tresult"
    );
    int failed = 0;
    for (var c : cases) {
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      var forward = new ArrayList<long[]>();
      var reverse = new ArrayList<long[]>();
      int unsortedFrom = 0;
      int unsortedTo = 0;
      for (int stop = 0; stop < nStops; ++stop) {
        // key: (to stop, from stop), value: (duration, c1)
        unsortedFrom += collect(service.getTransfersFromStop(stop), stop, true, forward) ? 0 : 1;
        unsortedTo += collect(service.getTransfersToStop(stop), stop, false, reverse) ? 0 : 1;
      }
      Comparator<long[]> cmp = (x, y) ->
        x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]);
      forward.sort(cmp);
      reverse.sort(cmp);
      int mismatch = Math.abs(forward.size() - reverse.size());
      for (int i = 0; i < Math.min(forward.size(), reverse.size()); ++i) {
        if (cmp.compare(forward.get(i), reverse.get(i)) != 0) {
          ++mismatch;
        }
      }
      boolean ok = unsortedFrom == 0 && unsortedTo == 0 && mismatch == 0;
      failed += ok ? 0 : 1;
      System.out.printf(
        "%s\t%d\t%d\t%d\t%d\t%s%n",
        c.name(),
        forward.size(),
        unsortedFrom,
        unsortedTo,
        mismatch,
        ok ? "PASS" : "FAIL"
      );
    }
    return failed == 0 ? 0 : 1;
  }

  /** @return true if the row is sorted by duration */
  private static boolean collect(
    Iterator<? extends RaptorTransfer> it,
    int stop,
    boolean from,
    List<long[]> out
  ) {
    int last = Integer.MIN_VALUE;
    boolean sorted = true;
    while (it.hasNext()) {
      var tx = it.next();
      sorted &= tx.durationInSeconds() >= last;
      last = tx.durationInSeconds();
      long key = from ? ((long) tx.stop() << 32) | stop : ((long) stop << 32) | tx.stop();
      out.add(new long[] { key, ((long) tx.durationInSeconds() << 32) | tx.c1() });
    }
    return sorted;
  }

  // ---------------------------------------------------------------- options

  private static final class Opts {

    final String mode;
    final File dir;
    final URI graph;
    final String profile;
    final int repeat;
    final File out;
    final File ref;
    final double fraction;
    final int stops;
    final File readSets;
    final int cacheSize;
    final int rounds;
    final String set;
    final String rt;

    Opts(String[] args) {
      String mode = "bench";
      File dir = null;
      URI graph = null;
      String profile = "all";
      int repeat = 3;
      File out = null;
      File ref = null;
      double fraction = 0.2;
      int stops = 100;
      File readSets = null;
      int cacheSize = CACHE_SIZE;
      int rounds = 2;
      String set = "base";
      String rt = "delete";
      for (var a : args) {
        var kv = a.split("=", 2);
        if (kv.length != 2 || !kv[0].startsWith("--")) {
          throw new IllegalArgumentException("Unknown argument: " + a);
        }
        switch (kv[0]) {
          case "--mode" -> mode = kv[1];
          case "--dir" -> dir = new File(kv[1]);
          case "--graph" -> graph = new File(kv[1]).toURI();
          case "--profile" -> profile = kv[1];
          case "--repeat" -> repeat = Integer.parseInt(kv[1]);
          case "--out" -> out = new File(kv[1]);
          case "--ref" -> ref = new File(kv[1]);
          case "--fraction" -> fraction = Double.parseDouble(kv[1]);
          case "--stops" -> stops = Integer.parseInt(kv[1]);
          case "--readsets" -> readSets = new File(kv[1]);
          case "--cache-size" -> cacheSize = Integer.parseInt(kv[1]);
          case "--rounds" -> rounds = Integer.parseInt(kv[1]);
          case "--set" -> set = kv[1];
          case "--rt" -> rt = kv[1];
          default -> throw new IllegalArgumentException("Unknown argument: " + a);
        }
      }
      if (dir == null) {
        throw new IllegalArgumentException("--dir is required");
      }
      if ((mode.equals("dump") || mode.equals("realtime-dump")) && out == null) {
        throw new IllegalArgumentException("--out is required for dump");
      }
      if ((mode.equals("compare") || mode.equals("realtime-compare")) && ref == null) {
        throw new IllegalArgumentException("--ref is required for compare");
      }
      this.mode = mode;
      this.dir = dir;
      this.graph = graph;
      this.profile = profile;
      this.repeat = repeat;
      this.out = out;
      this.ref = ref;
      this.fraction = fraction;
      this.stops = stops;
      this.readSets = readSets;
      this.cacheSize = cacheSize;
      this.rounds = rounds;
      this.set = set;
      this.rt = rt;
      if ((mode.equals("replay") || mode.equals("heap")) && readSets == null) {
        throw new IllegalArgumentException("--readsets is required for " + mode);
      }
    }
  }
}
