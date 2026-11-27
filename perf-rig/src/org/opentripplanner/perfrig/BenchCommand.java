package org.opentripplanner.perfrig;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.routing.algorithm.raptoradapter.router.FilterTransitWhenDirectModeIsEmpty;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressRouter;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.RigStreetSearch;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.routing.linking.mapping.LinkingContextRequestMapper;
import org.opentripplanner.street.linking.TemporaryVerticesContainer;
import org.opentripplanner.street.model.StreetMode;

/**
 * Measures the cases at several levels: the whole API call ({@code api}), the routing service
 * ({@code e2e}), Raptor alone ({@code raptor}), the street searches alone ({@code street}) and the
 * linking of the origin and destination ({@code link}). Each item is measured in each iteration
 * (wall time, CPU time and allocated bytes of the thread), and its result is fingerprinted, so a
 * comparison of two versions can check that they compute the same results.
 * <p>
 * The Raptor level is measured in chunks of cases: the captured transit data of a request is big
 * (~20 MB), so it is captured for a chunk, measured, and released.
 */
final class BenchCommand {

  /** One measured piece of work. */
  interface Item {
    String id();

    /** Prepare the work, not measured; returns the measured work. */
    Runnable prepare();

    /** Run the work once more, and return a hash of the result. */
    String fingerprint();

    /** The hash of the result when the case was first run, {@code null} if there is none. */
    @Nullable
    default String recorded() {
      return null;
    }
  }

  /**
   * What is collected for a level while its items are measured: the instructions executed by each
   * method (with the ops agent), and when the measurements started and ended, in nanoseconds
   * since the epoch, to find the events of a flight recording that belong to the level.
   */
  record Level(Map<String, Long> opsByMethod, List<long[]> windows) {
    Level() {
      this(new HashMap<>(), new ArrayList<>());
    }
  }

  /** The samples of an item: wall, cpu, alloc and ops for each iteration. */
  record Samples(String id, long[][] values) {
    Samples(String id, int iterations) {
      this(id, new long[4][iterations]);
    }
  }

  private static final com.sun.management.ThreadMXBean THREADS =
    (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static final com.sun.management.OperatingSystemMXBean OS =
    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

  private BenchCommand() {}

  static int run(Args args) throws Exception {
    var server = RigServer.load(args.file("dataset"), args.fileOrNull("graph"));
    var cases = RecordCommand.selectCases(args);
    var levels = List.of(args.get("levels", "e2e,raptor,street").split(","));
    int warmup = args.getInt("warmup", 3);
    int iterations = args.getInt("iterations", 5);
    int raptorChunk = args.getInt("raptor-chunk", 25);
    log("Graph loaded in " + server.loadMillis() + " ms. Preparing " + cases.size() + " cases.");

    var runner = new CaseRunner(server);
    var results = new ArrayList<CaseRunner.Result>();
    for (var c : cases) {
      results.addAll(runner.run(c, false));
    }
    var items = new LinkedHashMap<String, List<Item>>();
    for (var level : levels) {
      if (!level.equals("raptor")) {
        items.put(level, items(server, level, results));
        log(level + ": " + items.get(level).size() + " items");
      }
    }
    forceGc();
    long heapAfterPrepare = usedHeap();

    var gcBefore = gcTotals();
    for (int w = 1; w <= warmup; w++) {
      log("Warmup " + w + "/" + warmup);
      items.values().forEach(list -> list.forEach(item -> item.prepare().run()));
    }
    var out = new LinkedHashMap<String, List<Map<String, Object>>>();
    var collected = new LinkedHashMap<String, Level>();
    // A flight recording of the measurements, without the graph loading and the warmup
    Recording recording = args.get("jfr", null) == null ? null : startFlightRecording();
    var gcStart = gcTotals();
    for (var entry : items.entrySet()) {
      log("Measuring " + entry.getKey());
      var level = collected.computeIfAbsent(entry.getKey(), name -> new Level());
      out.put(entry.getKey(), measure(entry.getValue(), iterations, level));
    }
    if (levels.contains("raptor")) {
      var level = collected.computeIfAbsent("raptor", name -> new Level());
      out.put("raptor", measureRaptor(server, runner, results, warmup, iterations, raptorChunk, level));
    }
    var gcEnd = gcTotals();
    if (recording != null) {
      recording.dump(args.file("jfr").toPath());
      recording.close();
    }

    var meta = new LinkedHashMap<String, Object>();
    meta.put("label", args.get("label", ""));
    meta.put("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
    meta.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments());
    meta.put("processors", Runtime.getRuntime().availableProcessors());
    meta.put("maxHeapMb", Runtime.getRuntime().maxMemory() >> 20);
    meta.put("heapAfterPrepareMb", heapAfterPrepare >> 20);
    meta.put("loadMillis", server.loadMillis());
    meta.put("raptorThreads", server.routerConfig().transitTuningConfig().searchThreadPoolSize());
    meta.put("warmup", warmup);
    meta.put("iterations", iterations);
    meta.put("cases", cases.size());
    meta.put("gcCountWarmup", gcStart[0] - gcBefore[0]);
    meta.put("gcCountMeasure", gcEnd[0] - gcStart[0]);
    meta.put("gcMillisMeasure", gcEnd[1] - gcStart[1]);

    if (args.flag("thread-id")) {
      // The items were measured on this thread: to find it in a trace of the system
      meta.put("threadId", osThreadId());
    }

    var root = new LinkedHashMap<String, Object>();
    meta.put("ops", OpsCounter.active());
    // With the CPU time of the whole machine, this gives what other processes used meanwhile
    meta.put("processCpuSeconds", OS.getProcessCpuTime() / 1e9);
    root.put("meta", meta);
    root.put("levels", out);
    var windows = new LinkedHashMap<String, List<long[]>>();
    collected.forEach((name, level) -> windows.put(name, level.windows()));
    root.put("windows", windows);
    if (OpsCounter.active()) {
      var opsByMethod = new LinkedHashMap<String, Map<String, Long>>();
      collected.forEach((name, level) -> opsByMethod.put(name, level.opsByMethod()));
      root.put("opsByMethod", opsByMethod);
    }
    Json.write(args.file("out"), root);
    log("Done");
    return 0;
  }

  /** Measure all items, in each iteration, then fingerprint them. */
  private static List<Map<String, Object>> measure(List<Item> items, int iterations, Level level) {
    var samples = new ArrayList<Samples>();
    items.forEach(item -> samples.add(new Samples(item.id(), iterations)));
    long[] opsBefore = OpsCounter.byMethod();
    long start = epochNanos();
    for (int i = 0; i < iterations; i++) {
      forceGc();
      for (int k = 0; k < items.size(); k++) {
        measureOnce(items.get(k), samples.get(k), i);
      }
    }
    level.windows().add(new long[] { start, epochNanos() });
    OpsCounter.addSince(opsBefore, level.opsByMethod());
    var result = new ArrayList<Map<String, Object>>();
    for (int k = 0; k < items.size(); k++) {
      result.add(output(samples.get(k), items.get(k)));
    }
    return result;
  }

  private static void measureOnce(Item item, Samples samples, int iteration) {
    var work = item.prepare();
    long o0 = OpsCounter.total();
    long a0 = THREADS.getCurrentThreadAllocatedBytes();
    long c0 = THREADS.getCurrentThreadCpuTime();
    long w0 = System.nanoTime();
    work.run();
    long w1 = System.nanoTime();
    long c1 = THREADS.getCurrentThreadCpuTime();
    long a1 = THREADS.getCurrentThreadAllocatedBytes();
    long o1 = OpsCounter.total();
    samples.values()[0][iteration] = w1 - w0;
    samples.values()[1][iteration] = c1 - c0;
    samples.values()[2][iteration] = a1 - a0;
    samples.values()[3][iteration] = o1 - o0;
  }

  private static Map<String, Object> output(Samples samples, Item item) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", samples.id());
    m.put("fingerprint", item.fingerprint());
    if (item.recorded() != null) {
      m.put("recorded", item.recorded());
    }
    m.put("wall", samples.values()[0]);
    m.put("cpu", samples.values()[1]);
    m.put("alloc", samples.values()[2]);
    if (OpsCounter.active()) {
      m.put("ops", samples.values()[3]);
    }
    return m;
  }

  /**
   * Capture the Raptor calls of a chunk of results, run each call once, measure the iterations,
   * fingerprint, release, and go on with the next chunk. The first chunk is run warmup times more
   * before anything is measured.
   */
  private static List<Map<String, Object>> measureRaptor(
    RigServer server,
    CaseRunner runner,
    List<CaseRunner.Result> results,
    int warmup,
    int iterations,
    int chunkSize,
    Level level
  ) {
    var raptorData = server.factory().transitService().getRaptorTransitData();
    IntFunction<String> stopId = i -> {
      var stop = raptorData.getStopByIndex(i);
      return stop == null ? "#" + i : stop.getId().toString();
    };
    var out = new ArrayList<Map<String, Object>>();
    for (int start = 0; start < results.size(); start += chunkSize) {
      var items = new ArrayList<Item>();
      for (var result : results.subList(start, Math.min(results.size(), start + chunkSize))) {
        var calls = runner.runOne(result.id(), result.variables(), true).raptorCalls();
        for (int i = 0; i < calls.size(); i++) {
          items.add(raptorItem(server, result.id() + "#" + i, calls.get(i), stopId));
        }
      }
      int runsBefore = start == 0 ? warmup : 1;
      log("Measuring raptor, results " + start + "-" + (start + chunkSize) + ": " + items.size() + " items");
      for (int w = 0; w < runsBefore; w++) {
        items.forEach(item -> item.prepare().run());
      }
      out.addAll(measure(items, iterations, level));
    }
    return out;
  }

  private static List<Item> items(RigServer server, String level, List<CaseRunner.Result> results) {
    var query = new TripQuery(server.transmodelSchema());
    var items = new ArrayList<Item>();
    for (var result : results) {
      if (level.equals("api")) {
        items.add(apiItem(server, query, result));
        continue;
      }
      for (int i = 0; i < result.routings().size(); i++) {
        var routing = result.routings().get(i);
        var request = routing.request();
        var id = result.id() + (i == 0 ? "" : "#" + i);
        boolean firstPage = request.pageCursor() == null;
        switch (level) {
          case "e2e" -> items.add(e2eItem(server, id, routing));
          case "street" -> {
            if (firstPage) {
              items.addAll(streetItems(server, id, request));
            }
          }
          case "link" -> {
            if (firstPage) {
              items.add(linkItem(server, id, request));
            }
          }
          default -> throw new IllegalArgumentException("Unknown level: " + level);
        }
      }
    }
    return items;
  }

  private static Item apiItem(RigServer server, TripQuery query, CaseRunner.Result result) {
    return new Item() {
      public String id() {
        return result.id();
      }

      public Runnable prepare() {
        var scope = server.newRequestScope();
        return () -> query.execute(result.variables(), scope.transmodelContext());
      }

      public String fingerprint() {
        var scope = server.newRequestScope();
        return Dump.hash(Json.pretty(query.execute(result.variables(), scope.transmodelContext())));
      }

      public String recorded() {
        return Dump.hash(result.api());
      }
    };
  }

  private static Item e2eItem(RigServer server, String id, CaseRunner.Routing routing) {
    var request = routing.request();
    return new Item() {
      public String id() {
        return id;
      }

      public Runnable prepare() {
        var scope = server.newRequestScope();
        return () -> scope.routingService().route(request);
      }

      public String fingerprint() {
        var response = server.newRequestScope().routingService().route(request);
        return Dump.hash(ResponseDump.dump(request, response));
      }

      public String recorded() {
        return routing.fingerprint();
      }
    };
  }

  private static Item raptorItem(
    RigServer server,
    String id,
    RaptorCapture.RouteCall call,
    IntFunction<String> stopId
  ) {
    var factory = server.raptorServiceFactory();
    return new Item() {
      public String id() {
        return id;
      }

      public Runnable prepare() {
        var service = factory.createPlainService(call.extraMcSearch());
        return () -> service.route(call.request(), call.data());
      }

      public String fingerprint() {
        var response = factory
          .createPlainService(call.extraMcSearch())
          .route(call.request(), call.data());
        return RaptorCapture.fingerprint(response.paths(), stopId);
      }

      public String recorded() {
        return call.fingerprint();
      }
    };
  }

  /** The direct, access and egress street searches of a request, as RoutingWorker does them. */
  private static List<Item> streetItems(RigServer server, String id, RouteRequest request) {
    var list = new ArrayList<Item>();
    var directMode = new FilterTransitWhenDirectModeIsEmpty(
      request.journey().direct().mode(),
      false
    ).resolveDirectMode();
    if (directMode != StreetMode.NOT_SET && !request.isViaSearch()) {
      var direct = request
        .copyOf()
        .withJourney(jb ->
          jb.withDirect(new StreetRequest(directMode, request.journey().direct().rentalDuration()))
        )
        .buildRequest();
      list.add(streetItem(server, id + ":direct-" + directMode, direct, null));
    }
    if (request.journey().transit().enabled()) {
      var access = request.journey().access().mode();
      var egress = request.journey().egress().mode();
      list.add(streetItem(server, id + ":access-" + access, accessRequest(request), AccessEgressType.ACCESS));
      list.add(streetItem(server, id + ":egress-" + egress, request, AccessEgressType.EGRESS));
    }
    return list;
  }

  /** As AccessEgressFetcher does for the access. */
  private static RouteRequest accessRequest(RouteRequest request) {
    return request
      .copyOf()
      .withPreferences(p -> {
        p.withBike(b -> b.withRental(r -> r.withAllowArrivingInRentedVehicleAtDestination(false)));
        p.withCar(c -> c.withRental(r -> r.withAllowArrivingInRentedVehicleAtDestination(false)));
        p.withScooter(s -> s.withRental(r -> r.withAllowArrivingInRentedVehicleAtDestination(false)));
      })
      .buildRequest();
  }

  /**
   * A street search, measured without the linking.
   *
   * @param type {@code null} for the direct search
   */
  private static Item streetItem(
    RigServer server,
    String id,
    RouteRequest request,
    AccessEgressType type
  ) {
    var factory = server.factory();
    var services = server.newRequestScope().factory();
    return new Item() {
      public String id() {
        return id;
      }

      public Runnable prepare() {
        var container = new TemporaryVerticesContainer();
        LinkingContext linkingContext;
        try {
          linkingContext = factory
            .linkingContextFactory()
            .create(container, LinkingContextRequestMapper.map(request));
        } catch (RuntimeException e) {
          container.close();
          return () -> {};
        }
        return () -> {
          try {
            search(linkingContext);
          } catch (RuntimeException ignore) {
            // No path or no stops in range; the fingerprint has the exception
          } finally {
            container.close();
          }
        };
      }

      private String search(LinkingContext linkingContext) {
        if (type == null) {
          var paths = RigStreetSearch.direct(
            request,
            linkingContext,
            services.streetLimitationParametersService(),
            services.vehicleRentalService()
          );
          return paths
            .stream()
            .map(p ->
              p.weight() + " " + p.duration() + " " + p.distanceMeters() + " " + Dump.geometry(p.geometry())
            )
            .collect(Collectors.joining("\n"));
        }
        var mode = type.isAccess() ? request.journey().access().mode() : request.journey().egress().mode();
        var preferences = request.preferences().street().accessEgress();
        var stops = AccessEgressRouter.findAccessEgresses(
          request,
          mode,
          List.of(),
          type,
          preferences.maxDuration().valueOf(mode),
          preferences.maxStopCountLimit().limitForMode(mode),
          linkingContext
        );
        return stops
          .stream()
          .map(s ->
            s.stopId() + " " + s.distance() + " " + s.state().getWeight() + " " + s.state().getElapsedTimeSeconds()
          )
          .collect(Collectors.joining("\n"));
      }

      public String fingerprint() {
        try (var container = new TemporaryVerticesContainer()) {
          var linkingContext = factory
            .linkingContextFactory()
            .create(container, LinkingContextRequestMapper.map(request));
          return Dump.hash(search(linkingContext));
        } catch (RuntimeException e) {
          return "!" + e.getClass().getSimpleName();
        }
      }
    };
  }

  /** Linking the origin and destination to the street graph, and removing them again. */
  private static Item linkItem(RigServer server, String id, RouteRequest request) {
    var factory = server.factory();
    return new Item() {
      public String id() {
        return id;
      }

      public Runnable prepare() {
        return () -> {
          try (var container = new TemporaryVerticesContainer()) {
            factory.linkingContextFactory().create(container, LinkingContextRequestMapper.map(request));
          } catch (RuntimeException ignore) {
            // Not linked; the fingerprint has the exception
          }
        };
      }

      public String fingerprint() {
        try (var container = new TemporaryVerticesContainer()) {
          var ctx = factory
            .linkingContextFactory()
            .create(container, LinkingContextRequestMapper.map(request));
          var text = new StringBuilder();
          for (var v : ctx.findVertices(request.from())) {
            text.append(Dump.vertex(v)).append(' ').append(v.getCoordinate()).append('\n');
          }
          for (var v : ctx.findVertices(request.to())) {
            text.append(Dump.vertex(v)).append(' ').append(v.getCoordinate()).append('\n');
          }
          return Dump.hash(text.toString());
        } catch (RuntimeException e) {
          return "!" + e.getClass().getSimpleName();
        }
      }
    };
  }

  private static Recording startFlightRecording() throws Exception {
    var recording = new Recording(Configuration.getConfiguration("profile"));
    recording.start();
    return recording;
  }

  /** The id that Windows has for the current thread. */
  private static long osThreadId() {
    try {
      var getCurrentThreadId = Linker.nativeLinker().downcallHandle(
        SymbolLookup.libraryLookup("kernel32", Arena.global()).find("GetCurrentThreadId").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT)
      );
      return Integer.toUnsignedLong((int) getCurrentThreadId.invokeExact());
    } catch (Throwable e) {
      throw new IllegalStateException("Can not read the id of the thread", e);
    }
  }

  private static long epochNanos() {
    var now = Instant.now();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  static void forceGc() {
    var ref = new WeakReference<>(new Object());
    while (ref.get() != null) {
      System.gc();
    }
    System.gc();
  }

  private static long usedHeap() {
    var rt = Runtime.getRuntime();
    return rt.totalMemory() - rt.freeMemory();
  }

  /** Total count and milliseconds of all garbage collections so far. */
  private static long[] gcTotals() {
    long count = 0;
    long millis = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      count += Math.max(0, gc.getCollectionCount());
      millis += Math.max(0, gc.getCollectionTime());
    }
    return new long[] { count, millis };
  }

  private static void log(String message) {
    RecordCommand.log(message);
  }
}
