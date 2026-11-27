package org.opentripplanner.perfrig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs all cases and writes every layer of the results to text files, one block per result.
 * Optionally runs the cases again in the same JVM, and concurrently, and checks that the results
 * are the same as the first time.
 */
final class RecordCommand {

  private RecordCommand() {}

  static int run(Args args) throws Exception {
    var server = RigServer.load(args.file("dataset"), args.fileOrNull("graph"));
    var cases = selectCases(args);
    var outDir = args.file("out");
    outDir.mkdirs();
    var runner = new CaseRunner(server);
    log("Graph loaded in " + server.loadMillis() + " ms. Running " + cases.size() + " cases.");
    long edgesBefore = edgesOfGraph(server);

    long start = System.currentTimeMillis();
    var first = runSequential(runner, cases);
    long millis = System.currentTimeMillis() - start;
    writeLayers(outDir, first);
    writeTimings(outDir, first, cases);

    var summary = new LinkedHashMap<String, Object>();
    summary.put("cases", cases.size());
    summary.put("results", first.size());
    summary.put("loadMillis", server.loadMillis());
    summary.put("runMillis", millis);
    summary.put("graphqlErrors", countGraphqlErrors(first));
    int problems = 0;

    if (args.flag("repeat")) {
      log("Running the cases again in the same JVM");
      var diffs = compare(first, runSequential(runner, cases), false);
      summary.put("repeatDiffs", diffs);
      problems += diffs.size();
    }
    int threads = args.getInt("threads", 0);
    if (threads > 1) {
      log("Running the cases on " + threads + " threads");
      var diffs = compare(first, runConcurrent(runner, cases, threads), true);
      summary.put("concurrentThreads", threads);
      summary.put("concurrentDiffs", diffs);
      problems += diffs.size();
    }
    // A request links temporary vertices and edges to the graph, and must remove all of them
    long edgesLeft = edgesOfGraph(server) - edgesBefore;
    summary.put("edgesLeftInGraph", edgesLeft);
    if (edgesLeft != 0) {
      log("DIFF the requests changed the graph: " + edgesLeft + " edges more than before");
      problems++;
    }
    Json.write(new File(outDir, "summary.json"), summary);
    log("Done in " + (System.currentTimeMillis() - start) / 1000 + " s, problems: " + problems);
    return problems == 0 ? 0 : 3;
  }

  /** The incoming and outgoing edges of all vertices of the graph. */
  private static long edgesOfGraph(RigServer server) {
    long edges = 0;
    for (var vertex : server.factory().graph().getVertices()) {
      edges += vertex.getDegreeIn() + vertex.getDegreeOut();
    }
    return edges;
  }

  /** The cases selected by --only (regex on id or category) and --every (Nth of each category). */
  static List<RigCase> selectCases(Args args) {
    var all = RigCase.read(args.file("cases"));
    String only = args.get("only", null);
    int every = args.getInt("every", 1);
    var indexInCategory = new LinkedHashMap<String, Integer>();
    var result = new ArrayList<RigCase>();
    for (var c : all) {
      int index = indexInCategory.merge(c.category(), 1, Integer::sum) - 1;
      if (index % every != 0) {
        continue;
      }
      if (only != null && !c.id().matches(only) && !c.category().matches(only)) {
        continue;
      }
      result.add(c);
    }
    return result;
  }

  static List<CaseRunner.Result> runSequential(CaseRunner runner, List<RigCase> cases) {
    var results = new ArrayList<CaseRunner.Result>();
    for (int i = 0; i < cases.size(); i++) {
      results.addAll(runner.run(cases.get(i), false));
      if ((i + 1) % 50 == 0) {
        log((i + 1) + "/" + cases.size());
      }
    }
    return results;
  }

  /** Run the cases in random order on many threads, as a server under load does. */
  private static List<CaseRunner.Result> runConcurrent(
    CaseRunner runner,
    List<RigCase> cases,
    int threads
  ) throws Exception {
    var shuffled = new ArrayList<>(cases);
    Collections.shuffle(shuffled, new Random(threads));
    Map<String, List<CaseRunner.Result>> byCase = new ConcurrentHashMap<>();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      var futures = new ArrayList<Future<?>>();
      for (var c : shuffled) {
        futures.add(pool.submit(() -> byCase.put(c.id(), runner.run(c, false))));
      }
      for (var f : futures) {
        f.get();
      }
    } finally {
      pool.shutdown();
    }
    var results = new ArrayList<CaseRunner.Result>();
    for (var c : cases) {
      results.addAll(byCase.get(c.id()));
    }
    return results;
  }

  /**
   * The results that differ: case id, layer and the first line that differs.
   *
   * @param concurrent ignore the order of access/egress paths with the same cost. Concurrent
   *                   requests link their temporary edges to the same street vertices, which
   *                   changes the order of the edges of the vertices, and so the order in which
   *                   the street search finds stops at the same cost. The paths themselves must
   *                   be the same.
   */
  static List<String> compare(
    List<CaseRunner.Result> expected,
    List<CaseRunner.Result> actual,
    boolean concurrent
  ) {
    var diffs = new ArrayList<String>();
    Map<String, CaseRunner.Result> actualById = new LinkedHashMap<>();
    actual.forEach(r -> actualById.put(r.id(), r));
    for (var e : expected) {
      var a = actualById.remove(e.id());
      if (a == null) {
        diffs.add(e.id() + ": missing");
        continue;
      }
      for (String layer : CaseRunner.LAYERS) {
        var d = concurrent
          ? firstDifference(withoutOrder(e.layer(layer)), withoutOrder(a.layer(layer)))
          : firstDifference(e.layer(layer), a.layer(layer));
        if (d != null) {
          diffs.add(e.id() + " " + layer + ": " + d);
        }
      }
    }
    actualById.keySet().forEach(id -> diffs.add(id + ": unexpected"));
    diffs.forEach(d -> log("DIFF " + d));
    return diffs;
  }

  private static String withoutOrder(String text) {
    return text.replaceAll(RaptorCapture.ORDER + "[0-9a-f]+", RaptorCapture.ORDER);
  }

  private static String firstDifference(String expected, String actual) {
    if (expected.equals(actual)) {
      return null;
    }
    var e = expected.split("\n", -1);
    var a = actual.split("\n", -1);
    for (int i = 0; i < Math.min(e.length, a.length); i++) {
      if (!e[i].equals(a[i])) {
        return "line " + (i + 1) + ": '" + e[i].strip() + "' != '" + a[i].strip() + "'";
      }
    }
    return "length " + e.length + " != " + a.length;
  }

  private static void writeLayers(File outDir, List<CaseRunner.Result> results)
    throws IOException {
    for (String layer : CaseRunner.LAYERS) {
      var text = new StringBuilder();
      for (var r : results) {
        text.append("### ").append(r.id()).append('\n');
        var block = r.layer(layer);
        text.append(block);
        if (!block.endsWith("\n")) {
          text.append('\n');
        }
      }
      Files.writeString(new File(outDir, layer + ".txt").toPath(), text, StandardCharsets.UTF_8);
    }
  }

  private static void writeTimings(File outDir, List<CaseRunner.Result> results, List<RigCase> cases)
    throws IOException {
    var text = new StringBuilder("id\tmillis\n");
    for (var r : results) {
      text.append(r.id()).append('\t').append(r.nanos() / 1_000_000).append('\n');
    }
    Files.writeString(new File(outDir, "timings.tsv").toPath(), text, StandardCharsets.UTF_8);
  }

  private static int countGraphqlErrors(List<CaseRunner.Result> results) {
    int count = 0;
    for (var r : results) {
      if (r.api().contains("\"errors\"")) {
        count++;
      }
    }
    return count;
  }

  static void log(String message) {
    System.err.println("[rig] " + message);
  }
}
