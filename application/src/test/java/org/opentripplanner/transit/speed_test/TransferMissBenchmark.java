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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;
import org.opentripplanner.core.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.core.model.transaction.RepositoryHandle;
import org.opentripplanner.core.model.transaction.RepositoryRegistry;
import org.opentripplanner.raptor.data.transfers.regular.LazyRaptorTransferStore;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferencesBuilder;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.transfer.configure.StreetTransferFactory;
import org.opentripplanner.transfer.streetadapter.RegularTransferPreferencesMapper;
import org.opentripplanner.transit.transfer.regular.RegularTransferRepository;
import org.opentripplanner.transit.transfer.regular.RegularTransferRepositorySnapshot;

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
 *   <li>{@code compare}: compare the transfers of each case with the reference in {@code --ref}.
 *       Exit code 1 if a transfer is outside the gate.</li>
 * </ul>
 * Arguments: {@code --dir=<dir with graph.obj, otp-config.json>}, {@code --graph=<file>}
 * (default {@code <dir>/graph.obj}), {@code --profile=walk|bicycle|car|all} (default all),
 * {@code --repeat=<n>} (bench, default 3).
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

    var cases = cases(opts.profile);
    switch (opts.mode) {
      case "bench" -> bench(snapshot, cases, nStops, opts.repeat);
      case "dump" -> dump(snapshot, cases, nStops, opts.out);
      case "compare" -> System.exit(compare(snapshot, cases, nStops, opts.ref));
      case "replay" -> replay(snapshot, cases, nStops, opts);
      case "threads" -> threads(snapshot, cases, nStops, opts);
      case "heap" -> heap(snapshot, cases, opts);
      case "realtime" -> realtime(registry, handle, cases, nStops, opts);
      case "checkto" -> System.exit(checkTo(snapshot, cases, nStops));
      default -> throw new IllegalArgumentException("Unknown mode: " + opts.mode);
    }
  }

  // ---------------------------------------------------------------- cases

  /**
   * Values in different steps of {@code Speed} and {@code Reluctance}, so each case is a distinct
   * cache entry (close values share one, see the issue). None is the value of a profile.
   */
  private static List<Case> cases(String profile) {
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
        list.add(
          new Case(
            "bike-speed-" + v,
            "bicycle",
            request(StreetMode.BIKE, p -> p.withBike(b -> b.withSpeed(v)))
          )
        );
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
    var random = new Random(42);
    return random.ints(0, nStops).distinct().limit(Math.min(SAMPLE_STOPS, nStops)).toArray();
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

  private static Transfers read(RaptorRegularTransferService service, int nStops) {
    var counts = new int[nStops];
    int total = 0;
    var rows = new ArrayList<int[]>();
    for (int stop = 0; stop < nStops; ++stop) {
      var row = new ArrayList<int[]>();
      var it = service.getTransfersFromStop(stop);
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
      var tx = read(snapshot.forRequest(c.request()).raptorTransfers(), nStops);
      try (
        var os = new DataOutputStream(
          new BufferedOutputStream(Files.newOutputStream(new File(out, c.name() + ".bin").toPath()))
        )
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
      System.err.printf("dumped %s: %d transfers%n", c.name(), tx.to.length);
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

  /** @return 0 if all cases pass the gate, 1 if not */
  private static int compare(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    File ref
  ) throws IOException {
    System.out.println(
      "case\tprofile\treference\tmissing\textra\tout_of_gate_dur\tout_of_gate_c1\tmax_rel_dur\tmax_rel_c1\tp99_rel_dur\tp99_rel_c1\tmean_rel_dur\tmean_rel_c1\tresult"
    );
    int failed = 0;
    for (var c : cases) {
      var exact = load(new File(ref, c.name() + ".bin"));
      var est = read(snapshot.forRequest(c.request()).raptorTransfers(), nStops);
      var r = diff(exact, est, nStops);
      boolean ok = r.missing == 0 && r.extra == 0 && r.outDur == 0 && r.outC1 == 0;
      if (!ok) {
        ++failed;
      }
      System.out.printf(
        "%s\t%s\t%d\t%d\t%d\t%d\t%d\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%s%n",
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

  /** The stops one Raptor request read: forward (from-stops) and reverse (to-stops). */
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

  private static long readFrom(RaptorRegularTransferService service, int[] stops) {
    long count = 0;
    long sink = 0;
    for (int stop : stops) {
      var it = service.getTransfersFromStop(stop);
      while (it.hasNext()) {
        var tx = it.next();
        sink += tx.stop() + tx.durationInSeconds() + tx.c1();
        ++count;
      }
    }
    return sink == 42 ? -count : count;
  }

  private static long readTo(RaptorRegularTransferService service, int[] stops) {
    long count = 0;
    long sink = 0;
    for (int stop : stops) {
      var it = service.getTransfersToStop(stop);
      while (it.hasNext()) {
        var tx = it.next();
        sink += tx.stop() + tx.durationInSeconds() + tx.c1();
        ++count;
      }
    }
    return sink == 42 ? -count : count;
  }

  /**
   * For each recorded read set a new miss (the cache holds only the latest entry, run with
   * {@code --cache-size=1}) followed by the forward and the reverse reads of the request. The
   * reads are then repeated on the cached entry (warm). The first of {@code --rounds} rounds is
   * a JIT warm-up and is not reported.
   */
  private static void replay(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    Opts opts
  ) throws IOException {
    var sets = loadReadSets(opts.readSets);
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    System.out.println(
      "round\tset\tcase\tprofile\tmiss_ms\tfwd_ms\trev_ms\ttotal_fwd_ms\ttotal_both_ms\talloc_MB\tfwd_warm_ms\trev_warm_ms\tfwd_stops\tfwd_transfers\trev_stops\trev_transfers\theap_MB\tloaded_rows"
    );
    long heapBefore = opts.retained > 0 ? usedHeapAfterGc() : 0;
    int measuredHeap = 0;
    for (int round = 0; round < opts.rounds; ++round) {
      for (int r = 0; r < sets.size(); ++r) {
        var set = sets.get(r);
        var c = cases.get((round * sets.size() + r) % cases.size());
        long a0 = threads.getThreadAllocatedBytes(tid);
        long t0 = System.nanoTime();
        var service = snapshot.forRequest(c.request()).raptorTransfers();
        long t1 = System.nanoTime();
        long nf = readFrom(service, set.from);
        long t2 = System.nanoTime();
        long nt = readTo(service, set.to);
        long t3 = System.nanoTime();
        long alloc = threads.getThreadAllocatedBytes(tid) - a0;
        readFrom(service, set.from);
        long t4 = System.nanoTime();
        readTo(service, set.to);
        long t5 = System.nanoTime();
        String heap = "";
        if (round == opts.rounds - 1 && measuredHeap < opts.retained) {
          heap = String.format("%.1f", (usedHeapAfterGc() - heapBefore) / 1e6);
          ++measuredHeap;
        }
        if (round == opts.rounds - 1 || opts.rounds == 1) {
          System.out.printf(
            "%d\t%d\t%s\t%s\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.2f\t%.2f\t%d\t%d\t%d\t%d\t%s\t%d%n",
            round,
            r,
            c.name(),
            c.profile(),
            (t1 - t0) / 1e6,
            (t2 - t1) / 1e6,
            (t3 - t2) / 1e6,
            (t2 - t0) / 1e6,
            (t3 - t0) / 1e6,
            alloc / 1e6,
            (t4 - t3) / 1e6,
            (t5 - t4) / 1e6,
            set.from.length,
            nf,
            set.to.length,
            nt,
            heap,
            service instanceof LazyRaptorTransferStore lazy ? lazy.loadedRows() : -1
          );
        }
      }
    }
  }

  /**
   * Retained heap of a cache entry in steps: empty entry, after the first reverse read (builds the
   * reverse index of a lazy store), after the forward reads of read set {@code --rounds}, and after
   * the reverse reads too. Use {@code --cache-size=1}.
   */
  private static void heap(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    Opts opts
  ) throws IOException {
    var sets = loadReadSets(opts.readSets);
    System.out.println(
      "case\tset\tempty_MB\tafter_first_reverse_MB\tafter_forward_MB\tafter_both_MB\tindex_MB"
    );
    // The previous entry is evicted when the next one is added, so its size is added back to the
    // heap difference. The reverse index of a lazy store is built once, at the first reverse read.
    double previous = 0;
    for (int i = 0; i < 7; ++i) {
      var c = cases.get(i % cases.size());
      var set = sets.get((i * 7) % sets.size());
      long h0 = usedHeapAfterGc();
      var service = snapshot.forRequest(c.request()).raptorTransfers();
      long h1 = usedHeapAfterGc();
      readTo(service, new int[] { set.to[0] });
      long h2 = usedHeapAfterGc();
      readFrom(service, set.from);
      long h3 = usedHeapAfterGc();
      readTo(service, set.to);
      long h4 = usedHeapAfterGc();
      double index = i == 0 ? (h2 - h1) / 1e6 : 0;
      double empty = (h1 - h0) / 1e6 + previous;
      double both = (h4 - h0) / 1e6 + previous - index;
      System.out.printf(
        "%s\t%d\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f%n",
        c.name(),
        (i * 7) % sets.size(),
        empty,
        (h2 - h0) / 1e6 + previous - index,
        (h3 - h0) / 1e6 + previous - index,
        both,
        index
      );
      previous = both;
    }
  }

  /**
   * Real-time scenario: warm the entry of the first case, then replace the transfers from K stops
   * (each loses its first target) in a transaction, and read the entry of the new snapshot. The
   * time and allocation are those of the patch plus the reads of read set 0. The fingerprints of
   * the from-rows and to-rows are printed, they must be equal for all variants.
   */
  private static <P> void realtime(
    RepositoryRegistry registry,
    RepositoryHandle<
      RegularTransferRepositorySnapshot<P, RouteRequest>,
      RegularTransferRepository<P>
    > handle,
    List<Case> cases,
    int nStops,
    Opts opts
  ) throws Exception {
    var sets = loadReadSets(opts.readSets);
    var set = sets.get(0);
    var c = cases.get(0);
    var profile = new RegularTransferPreferencesMapper().toUserPreferences(
      request(StreetMode.WALK, p -> {})
    );
    var updateManager =
      org.opentripplanner.core.framework.transaction.internal.TransactionFactory.createUpdateManagerWithAtomicCommits(
        "rt",
        registry,
        Thread.ofPlatform().factory()
      );
    var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long tid = Thread.currentThread().threadId();
    System.out.println(
      "k_stops\tpatch_ms\tpatch_alloc_MB\treads_ms\treads_alloc_MB\tfingerprint_from\tfingerprint_to"
    );
    for (int k : new int[] { 0, 1, 20, 200, 6000 }) {
      var before = handle.repositorySnapshot(registry.scope());
      var service = before.forRequest(c.request()).raptorTransfers();
      readFrom(service, set.from);
      readTo(service, set.to);
      var stops = new ArrayList<Integer>();
      for (int stop = 0; stop < nStops; ++stop) {
        if (service.getTransfersFromStop(stop).hasNext()) {
          stops.add(stop);
        }
      }
      int step = Math.max(1, stops.size() / Math.max(1, k));
      var chosen = new ArrayList<Integer>();
      for (int i = k == 0 ? stops.size() : 0; i < stops.size() && chosen.size() < k; i += step) {
        chosen.add(stops.get(i));
      }
      var requestView = before.forRequest(c.request());
      var changes = new HashMap<Integer, Map<Integer, P>>();
      for (int stop : chosen) {
        var targets = new ArrayList<Integer>();
        var it = service.getTransfersFromStop(stop);
        while (it.hasNext()) {
          targets.add(it.next().stop());
        }
        targets.sort(null);
        var map = new HashMap<Integer, P>();
        for (int i = targets.size() > 1 ? 1 : 0; i < targets.size(); ++i) {
          map.put(targets.get(i), requestView.findPath(stop, targets.get(i)));
        }
        changes.put(stop, map);
      }
      updateManager
        .submit(ctx -> {
          var repository = ctx.repository(handle);
          changes.forEach((stop, map) -> repository.replaceTransfersFrom(profile, stop, map));
        })
        .get();
      var after = handle.repositorySnapshot(registry.scope());
      long a0 = threads.getThreadAllocatedBytes(tid);
      long t0 = System.nanoTime();
      var patched = after.forRequest(c.request()).raptorTransfers();
      long t1 = System.nanoTime();
      long a1 = threads.getThreadAllocatedBytes(tid);
      readFrom(patched, set.from);
      readTo(patched, set.to);
      long t2 = System.nanoTime();
      long a2 = threads.getThreadAllocatedBytes(tid);
      long fpFrom = 0;
      long fpTo = 0;
      for (int stop = 0; stop < nStops; ++stop) {
        var it = patched.getTransfersFromStop(stop);
        while (it.hasNext()) {
          var tx = it.next();
          fpFrom += mix(stop, tx.stop(), tx.durationInSeconds(), tx.c1());
        }
        it = patched.getTransfersToStop(stop);
        while (it.hasNext()) {
          var tx = it.next();
          fpTo += mix(tx.stop(), stop, tx.durationInSeconds(), tx.c1());
        }
      }
      System.out.printf(
        "%d\t%.2f\t%.1f\t%.1f\t%.1f\t%x\t%x%n",
        chosen.size(),
        (t1 - t0) / 1e6,
        (a1 - a0) / 1e6,
        (t2 - t1) / 1e6,
        (a2 - a1) / 1e6,
        fpFrom,
        fpTo
      );
    }
    updateManager.shutdown();
  }

  private static long mix(int from, int to, int duration, int c1) {
    long h = from * 0x9E3779B97F4A7C15L + to;
    h = h * 0xC2B2AE3D27D4EB4FL + duration;
    h = h * 0x165667B19E3779F9L + c1;
    return h ^ (h >>> 29);
  }

  /**
   * {@code --threads} threads replay different read sets against ONE cache entry (one miss, the
   * others wait for it or, for a lazy store, fill rows concurrently). After the replay the whole
   * store is read and compared with the reference of the case, to prove that concurrent filling
   * gave exact transfers. Needs {@code --ref}.
   */
  private static void threads(
    RegularTransferRepositorySnapshot<?, RouteRequest> snapshot,
    List<Case> cases,
    int nStops,
    Opts opts
  ) throws Exception {
    var sets = loadReadSets(opts.readSets);
    System.out.println(
      "round\tcase\twall_ms\tmean_thread_ms\tmax_thread_ms\tout_of_gate_dur\tout_of_gate_c1\tmissing\textra"
    );
    for (int round = 0; round < opts.rounds; ++round) {
      var c = cases.get(round % cases.size());
      var latency = new long[opts.threads];
      var start = new CyclicBarrier(opts.threads + 1);
      var workers = new ArrayList<Thread>();
      for (int i = 0; i < opts.threads; ++i) {
        int idx = i;
        var set = sets.get((round * opts.threads + i) % sets.size());
        var worker = new Thread(() -> {
          try {
            start.await();
            long t0 = System.nanoTime();
            var service = snapshot.forRequest(c.request()).raptorTransfers();
            readFrom(service, set.from);
            readTo(service, set.to);
            latency[idx] = System.nanoTime() - t0;
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
        workers.add(worker);
        worker.start();
      }
      start.await();
      long t0 = System.nanoTime();
      for (var w : workers) {
        w.join();
      }
      long wall = System.nanoTime() - t0;
      var exact = load(new File(opts.ref, c.name() + ".bin"));
      var est = read(snapshot.forRequest(c.request()).raptorTransfers(), nStops);
      var d = diff(exact, est, nStops);
      System.out.printf(
        "%d\t%s\t%.1f\t%.1f\t%.1f\t%d\t%d\t%d\t%d%n",
        round,
        c.name(),
        wall / 1e6,
        Arrays.stream(latency).average().orElse(0) / 1e6,
        Arrays.stream(latency).max().orElse(0) / 1e6,
        d.outDur,
        d.outC1,
        d.missing,
        d.extra
      );
    }
  }

  /**
   * Check the reverse side: every to-row holds exactly the transposed from-rows, and every row,
   * from and to, is sorted by duration. Returns 0 if all cases pass.
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
      var fwd = new ArrayList<long[]>();
      int unsortedFrom = 0;
      int unsortedTo = 0;
      for (int stop = 0; stop < nStops; ++stop) {
        int last = Integer.MIN_VALUE;
        boolean sorted = true;
        var it = service.getTransfersFromStop(stop);
        while (it.hasNext()) {
          var tx = it.next();
          sorted &= tx.durationInSeconds() >= last;
          last = tx.durationInSeconds();
          // key: (toStop, fromStop), value: (duration, c1)
          fwd.add(new long[] {
            ((long) tx.stop() << 32) | stop,
            ((long) tx.durationInSeconds() << 32) | tx.c1(),
          });
        }
        unsortedFrom += sorted ? 0 : 1;
      }
      var rev = new ArrayList<long[]>();
      for (int stop = 0; stop < nStops; ++stop) {
        int last = Integer.MIN_VALUE;
        boolean sorted = true;
        var it = service.getTransfersToStop(stop);
        while (it.hasNext()) {
          var tx = it.next();
          sorted &= tx.durationInSeconds() >= last;
          last = tx.durationInSeconds();
          rev.add(new long[] {
            ((long) stop << 32) | tx.stop(),
            ((long) tx.durationInSeconds() << 32) | tx.c1(),
          });
        }
        unsortedTo += sorted ? 0 : 1;
      }
      Comparator<long[]> cmp = (x, y) ->
        x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]);
      fwd.sort(cmp);
      rev.sort(cmp);
      int mismatch = Math.abs(fwd.size() - rev.size());
      for (int i = 0; i < Math.min(fwd.size(), rev.size()); ++i) {
        if (cmp.compare(fwd.get(i), rev.get(i)) != 0) {
          ++mismatch;
        }
      }
      boolean ok = unsortedFrom == 0 && unsortedTo == 0 && mismatch == 0;
      failed += ok ? 0 : 1;
      System.out.printf(
        "%s\t%d\t%d\t%d\t%d\t%s%n",
        c.name(),
        fwd.size(),
        unsortedFrom,
        unsortedTo,
        mismatch,
        ok ? "PASS" : "FAIL"
      );
    }
    return failed == 0 ? 0 : 1;
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
    final File readSets;
    final int cacheSize;
    final int threads;
    final int rounds;
    final int retained;

    Opts(String[] args) {
      String mode = "bench";
      File dir = null;
      URI graph = null;
      String profile = "all";
      int repeat = 3;
      File out = null;
      File ref = null;
      File readSets = null;
      int cacheSize = CACHE_SIZE;
      int threads = 8;
      int rounds = 2;
      int retained = 0;
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
          case "--readsets" -> readSets = new File(kv[1]);
          case "--cache-size" -> cacheSize = Integer.parseInt(kv[1]);
          case "--threads" -> threads = Integer.parseInt(kv[1]);
          case "--rounds" -> rounds = Integer.parseInt(kv[1]);
          case "--retained" -> retained = Integer.parseInt(kv[1]);
          default -> throw new IllegalArgumentException("Unknown argument: " + a);
        }
      }
      if (dir == null) {
        throw new IllegalArgumentException("--dir is required");
      }
      if (mode.equals("dump") && out == null) {
        throw new IllegalArgumentException("--out is required for dump");
      }
      if (mode.equals("compare") && ref == null) {
        throw new IllegalArgumentException("--ref is required for compare");
      }
      this.mode = mode;
      this.dir = dir;
      this.graph = graph;
      this.profile = profile;
      this.repeat = repeat;
      this.out = out;
      this.ref = ref;
      this.readSets = readSets;
      this.cacheSize = cacheSize;
      this.threads = threads;
      this.rounds = rounds;
      this.retained = retained;
    }
  }
}
