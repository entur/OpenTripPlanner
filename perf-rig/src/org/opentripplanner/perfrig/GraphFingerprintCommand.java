package org.opentripplanner.perfrig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.routing.graph.SerializedGraphObject;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Writes the content of a graph as sorted text: every vertex and edge with the values of all its
 * public getters (so a change of the internal representation alone does not show), the stops, the
 * patterns and their timetables. The text is written gzipped, with a summary of counts and hashes
 * of chunks of lines, so two graphs are compared fast and the differences found.
 */
final class GraphFingerprintCommand {

  private static final int CHUNK = 20_000;
  private static final Set<String> SKIPPED_METHODS = Set.of(
    "hashCode",
    "toString",
    "getClass",
    "getOutgoing",
    "getIncoming",
    "getFromVertex",
    "getToVertex",
    "getLabel",
    "getLabelString"
  );
  private static final Map<Class<?>, List<Method>> GETTERS = new ConcurrentHashMap<>();

  private GraphFingerprintCommand() {}

  static int run(Args args) throws Exception {
    var outDir = args.file("out");
    outDir.mkdirs();
    long start = System.currentTimeMillis();
    var graphObject = SerializedGraphObject.load(args.file("graph"));
    RecordCommand.log("Graph loaded in " + (System.currentTimeMillis() - start) + " ms");

    var summary = new StringBuilder();
    var street = streetLines(graphObject);
    summary.append(write(new File(outDir, "street.txt.gz"), "street", street));
    var transit = transitLines(graphObject);
    summary.append(write(new File(outDir, "transit.txt.gz"), "transit", transit));
    Files.writeString(new File(outDir, "summary.txt").toPath(), summary, StandardCharsets.UTF_8);
    RecordCommand.log("Fingerprint written to " + outDir);
    return 0;
  }

  /**
   * One block per vertex: the vertex and its outgoing edges, sorted. Split vertices are labeled by
   * their coordinate (see Dump#vertex), and blocks are sorted by their whole text, so the result
   * does not depend on the numbering of the split vertices, which differs between graph builds.
   */
  private static List<String> streetLines(SerializedGraphObject graphObject) {
    var blocks = new ArrayList<List<String>>();
    for (Vertex v : graphObject.graph.getVertices()) {
      var block = new ArrayList<String>();
      block.add("V " + Dump.vertex(v) + " " + v.getClass().getSimpleName() + " " + getters(v));
      var edges = new ArrayList<String>();
      for (Edge e : v.getOutgoing()) {
        edges.add(
          "  E " +
            e.getClass().getSimpleName() +
            " > " +
            Dump.vertex(e.getToVertex()) +
            " " +
            getters(e)
        );
      }
      edges.sort(null);
      block.addAll(edges);
      blocks.add(block);
    }
    blocks.sort(Comparator.comparing(b -> String.join("\n", b)));
    var lines = new ArrayList<String>();
    blocks.forEach(lines::addAll);
    return lines;
  }

  private static List<String> transitLines(SerializedGraphObject graphObject) {
    var repository = graphObject.transitRepository;
    var lines = new ArrayList<String>();
    var stops = new ArrayList<StopLocation>(
      repository.getSiteRepository().listStopLocations()
    );
    stops.sort(Comparator.comparing(s -> s.getId().toString()));
    for (var s : stops) {
      lines.add("S " + s.getId() + " " + s.getClass().getSimpleName() + " " + getters(s));
    }
    var patterns = new ArrayList<>(graphObject.timetableDataImport.getAllTripPatterns());
    patterns.sort(Comparator.comparing(p -> p.getId().toString()));
    for (var p : patterns) {
      lines.add(
        "P " +
          p.getId() +
          " route=" +
          p.getRoute().getId() +
          " mode=" +
          p.getMode() +
          " stops=" +
          p.getStops().stream().map(s -> s.getId().toString()).toList()
      );
      var trips = new ArrayList<String>();
      for (var tt : p.getScheduledTimetable().getTripTimes()) {
        var times = new int[tt.getNumStops() * 2];
        for (int i = 0; i < tt.getNumStops(); i++) {
          times[2 * i] = tt.getArrivalTime(i);
          times[2 * i + 1] = tt.getDepartureTime(i);
        }
        trips.add(
          "  T " +
            tt.getTrip().getId() +
            " service=" +
            tt.getServiceCode() +
            " times=" +
            Dump.hash(Arrays.toString(times))
        );
      }
      trips.sort(null);
      lines.addAll(trips);
    }
    return lines;
  }

  /** Write the lines gzipped, and return the summary: count and a hash per chunk of lines. */
  private static String write(File file, String name, List<String> lines) throws Exception {
    var summary = new StringBuilder();
    var countByType = new TreeMap<String, Integer>();
    for (var line : lines) {
      var w = line.strip().split(" ", 4);
      String key = switch (w[0]) {
        case "V", "S" -> w[0] + " " + (w.length > 2 ? w[2] : "?");
        case "E" -> "E " + w[1];
        default -> w[0];
      };
      countByType.merge(key, 1, Integer::sum);
    }
    summary.append(name).append(" lines ").append(lines.size()).append('\n');
    countByType.forEach((k, v) -> summary.append(name).append(" count ").append(k).append(' ').append(v).append('\n'));
    try (Writer out = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {
      for (int c = 0; c * CHUNK < lines.size(); c++) {
        var chunk = lines.subList(c * CHUNK, Math.min(lines.size(), (c + 1) * CHUNK));
        var text = String.join("\n", chunk);
        summary.append(name).append(" chunk ").append(c).append(' ').append(Dump.hash(text)).append('\n');
        out.write(text);
        out.write('\n');
      }
    }
    return summary.toString();
  }

  /** The values of all public no-argument getters of the object, by name. */
  private static String getters(Object o) {
    var text = new StringBuilder();
    for (Method m : GETTERS.computeIfAbsent(o.getClass(), GraphFingerprintCommand::findGetters)) {
      text.append(m.getName()).append('=');
      try {
        text.append(format(m.invoke(o)));
      } catch (Exception e) {
        text.append('!').append(e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
      }
      text.append(' ');
    }
    return text.toString();
  }

  private static List<Method> findGetters(Class<?> type) {
    var result = new ArrayList<Method>();
    for (Method m : type.getMethods()) {
      if (
        m.getParameterCount() == 0 &&
        !Modifier.isStatic(m.getModifiers()) &&
        m.getReturnType() != void.class &&
        !SKIPPED_METHODS.contains(m.getName()) &&
        (m.getName().startsWith("get") || m.getName().startsWith("is") || m.getName().startsWith("has")) &&
        isValue(m.getReturnType())
      ) {
        m.setAccessible(true);
        result.add(m);
      }
    }
    result.sort(Comparator.comparing(Method::getName));
    return result;
  }

  private static boolean isValue(Class<?> type) {
    return (
      type.isPrimitive() ||
      Number.class.isAssignableFrom(type) ||
      type == Boolean.class ||
      type == String.class ||
      type.isEnum() ||
      I18NString.class.isAssignableFrom(type) ||
      Geometry.class.isAssignableFrom(type) ||
      type.getName().endsWith("FeedScopedId") ||
      type.getName().endsWith("WgsCoordinate") ||
      type.getName().equals("org.locationtech.jts.geom.Coordinate")
    );
  }

  private static String format(Object value) {
    if (value instanceof Geometry g) {
      return Dump.geometry(g);
    }
    return Dump.str(value);
  }
}
