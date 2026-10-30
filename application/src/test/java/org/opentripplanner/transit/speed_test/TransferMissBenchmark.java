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
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import org.opentripplanner.core.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.raptor.data.transfers.regular.RaptorRegularTransferService;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferencesBuilder;
import org.opentripplanner.standalone.config.ConfigModel;
import org.opentripplanner.standalone.config.OtpConfigLoader;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.transfer.configure.StreetTransferFactory;
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
      CACHE_SIZE,
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

  // ---------------------------------------------------------------- options

  private static final class Opts {

    final String mode;
    final File dir;
    final URI graph;
    final String profile;
    final int repeat;
    final File out;
    final File ref;

    Opts(String[] args) {
      String mode = "bench";
      File dir = null;
      URI graph = null;
      String profile = "all";
      int repeat = 3;
      File out = null;
      File ref = null;
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
    }
  }
}
