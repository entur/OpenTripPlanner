package org.opentripplanner.perfrig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.edge.AreaEdge;
import org.opentripplanner.street.model.edge.AreaGroup;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;

/**
 * Generates a broad, deterministic set of cases from the stops of the graph: many modes, options,
 * times and places, chosen with a seeded random generator.
 */
final class GenCasesCommand {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd'T'HH:mm:ssXXX"
  );

  private final List<LocalDate> dates;
  private final Random random;
  private final ZoneId zone;
  private final TransitService transitService;
  private final List<StopLocation> stops;
  private final List<StopLocation> stations;
  /** A point inside each pedestrian area of the street graph, in a fixed order. */
  private final List<Coordinate> areaPoints;
  /** The number of patterns of the stops up to each index, to pick busy stops; null: any stop. */
  @Nullable
  private final int[] patternsUpTo;
  private final List<RigCase> cases = new ArrayList<>();

  private GenCasesCommand(
    TransitService transitService,
    Graph graph,
    Random random,
    boolean busyStops
  ) {
    this.transitService = transitService;
    this.areaPoints = areaPoints(graph);
    this.random = random;
    this.zone = transitService.getTimeZone();
    this.dates = caseDates(transitService.getTransitServiceStarts().atZone(zone).toLocalDate());
    this.stops = transitService
      .listStopLocations()
      .stream()
      .filter(s -> !transitService.findPatterns(s).isEmpty())
      .sorted(Comparator.comparing(s -> s.getId().toString()))
      .toList();
    var stationIds = new TreeSet<String>();
    var stationList = new ArrayList<StopLocation>();
    for (var s : stops) {
      if (s.getParentStation() != null && stationIds.add(s.getParentStation().getId().toString())) {
        stationList.add(s);
      }
    }
    this.stations = stationList;
    this.patternsUpTo = busyStops ? new int[stops.size()] : null;
    for (int i = 0, sum = 0; busyStops && i < stops.size(); i++) {
      sum += transitService.findPatterns(stops.get(i)).size();
      patternsUpTo[i] = sum;
    }
  }

  static int run(Args args) throws Exception {
    var server = RigServer.load(args.file("dataset"), args.fileOrNull("graph"));
    var generator = new GenCasesCommand(
      server.factory().transitService(),
      server.factory().graph(),
      new Random(args.getInt("seed", 1)),
      args.flag("busy-stops")
    );
    generator.generate();
    RigCase.write(args.file("out"), args.file("dataset").getName(), generator.cases);
    RecordCommand.log(generator.cases.size() + " cases written to " + args.file("out"));
    return 0;
  }

  /** Tue, Wed, Thu, Sat and Sun of the first week that starts a week or more into the service. */
  static List<LocalDate> caseDates(LocalDate serviceStart) {
    var monday = serviceStart.plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    return List.of(
      monday.plusDays(1),
      monday.plusDays(2),
      monday.plusDays(3),
      monday.plusDays(5),
      monday.plusDays(6)
    );
  }

  private void generate() {
    add("transit", 80, () -> trip(coordinate(stop()), coordinate(stop()), false));
    add("transit-local", 40, () -> {
      var from = stop();
      return trip(coordinate(from), coordinate(near(from, 1000, 8000)), false);
    });
    add("transit-arrive-by", 30, () -> trip(coordinate(stop()), coordinate(stop()), true));
    add("transit-station", 25, () -> trip(place(station(), true), place(station(), true), false));
    add("transit-quay", 10, () -> trip(place(stop(), false), place(stop(), false), false));
    add("paging", 20, () -> trip(coordinate(stop()), coordinate(stop()), random.nextBoolean()));
    add("search-window", 12, () -> with(localTrip(), "searchWindow", pick(15, 60, 180)));
    add("num-trip-patterns", 8, () -> with(localTrip(), "numTripPatterns", pick(1, 3, 30)));
    add("walk-speed", 6, () -> with(localTrip(), "walkSpeed", pick(0.7, 1.0, 2.0)));
    add("walk-reluctance", 6, () -> with(localTrip(), "walkReluctance", pick(1.0, 2.0, 10.0)));
    add("wait-reluctance", 4, () -> with(localTrip(), "waitReluctance", pick(0.5, 2.0)));
    add("transfer-slack", 6, () -> with(localTrip(), "transferSlack", pick(0, 300, 900)));
    add("transfer-penalty", 6, () -> with(localTrip(), "transferPenalty", pick(0, 600, 1800)));
    add("max-transfers", 6, () -> with(localTrip(), "maximumTransfers", pick(0, 1, 2)));
    add("max-additional-transfers", 4, () ->
      with(localTrip(), "maximumAdditionalTransfers", pick(0, 1, 3))
    );
    add("relax-priority", 8, () ->
      with(
        localTrip(),
        "relaxTransitGroupPriority",
        pick(map("ratio", 1.0, "constant", "0s"), map("ratio", 1.5, "constant", "20m"))
      )
    );
    add("transit-modes", 21, () -> {
      var modes = pick(
        List.of("metro"),
        List.of("tram"),
        List.of("bus"),
        List.of("water"),
        List.of("rail"),
        List.of("metro", "tram"),
        List.of("bus", "water")
      );
      var transportModes = modes.stream().map(m -> map("transportMode", m)).toList();
      return with(localTrip(), "modes", modes("foot", "foot", "foot", transportModes));
    });
    add("banned-lines", 8, () -> {
      var from = stop();
      var vars = trip(coordinate(from), coordinate(near(from, 2000, 10000)), false);
      return with(vars, "banned", map("lines", lines(from, 2)));
    });
    add("whitelisted-lines", 6, () -> {
      var from = stop();
      var vars = trip(coordinate(from), coordinate(near(from, 2000, 10000)), false);
      return with(vars, "whiteListed", map("lines", lines(from, 3)));
    });
    add("filters", 8, () -> {
      var from = stop();
      var vars = trip(coordinate(from), coordinate(near(from, 2000, 10000)), false);
      var select = random.nextBoolean()
        ? map("not", List.of(map("lines", lines(from, 1))))
        : map("select", List.of(map("transportModes", List.of(map("transportMode", "bus")))));
      return with(vars, "filters", List.of(select));
    });
    add("via-visit", 10, () -> {
      var from = stop();
      var via = near(from, 2000, 6000);
      var vars = trip(coordinate(from), coordinate(near(via, 2000, 6000)), false);
      var visit = new LinkedHashMap<String, Object>();
      visit.put("stopLocationIds", List.of(stationOrStopId(via)));
      visit.put("minimumWaitTime", pick("PT0S", "PT10M"));
      return with(vars, "via", List.of(map("visit", visit)));
    });
    add("via-pass-through", 8, () -> {
      var from = stop();
      var via = near(from, 2000, 6000);
      var vars = trip(coordinate(from), coordinate(near(via, 2000, 6000)), false);
      var passThrough = map("stopLocationIds", List.of(stationOrStopId(via)));
      return with(vars, "via", List.of(map("passThrough", passThrough)));
    });
    add("wheelchair", 15, () -> with(localTrip(), "wheelchairAccessible", true));
    add("bike", 12, () -> with(localTrip(), "modes", modes("bicycle", "bicycle", "bicycle", null)));
    add("bike-park", 8, () -> with(localTrip(), "modes", modes("bike_park", "foot", "foot", null)));
    add("car-park", 12, () -> with(regionalTrip(), "modes", modes("car_park", "foot", "foot", null)));
    add("car-pickup", 8, () ->
      with(
        regionalTrip(),
        "modes",
        random.nextBoolean()
          ? modes("car_pickup", "foot", "foot", null)
          : modes("foot", "car_pickup", "foot", null)
      )
    );
    add("flexible", 5, () -> with(localTrip(), "modes", modes("flexible", "flexible", "flexible", null)));
    add("bike-preferences", 6, () ->
      with(
        with(localTrip(), "modes", modes("bicycle", "bicycle", "bicycle", null)),
        "bikePreferences",
        map("speed", pick(3.0, 8.0), "optimisationMethod", pick("quick", "safe", "flat"))
      )
    );
    add("direct-foot", 12, () -> {
      var from = stop();
      var vars = trip(coordinate(from), coordinate(near(from, 300, 3000)), false);
      return with(vars, "modes", modes(null, null, "foot", List.of()));
    });
    add("direct-bike", 10, () -> {
      var from = stop();
      var vars = trip(coordinate(from), coordinate(near(from, 1000, 15000)), false);
      return with(vars, "modes", modes(null, null, "bicycle", List.of()));
    });
    add("direct-car", 12, () -> with(regionalTrip(), "modes", modes(null, null, "car", List.of())));
    add("max-direct-duration", 4, () ->
      with(
        localTrip(),
        "maxDirectDurationForMode",
        List.of(map("streetMode", "foot", "duration", "PT10M"))
      )
    );
    add("debug-filters", 12, () ->
      with(localTrip(), "itineraryFilters", map("debug", pick("listAll", "limitToSearchWindow")))
    );
    add("timetable-view-off", 5, () -> with(localTrip(), "timetableView", false));
    add("night", 10, () -> {
      var vars = localTrip();
      vars.put("dateTime", dateTime(LocalTime.of(random.nextInt(5), random.nextInt(60))));
      return vars;
    });
    add("short", 10, () -> {
      var from = stop();
      return trip(coordinate(from), coordinate(near(from, 200, 1500)), random.nextBoolean());
    });
    add("same-place", 3, () -> {
      var c = coordinate(stop());
      return trip(c, c, false);
    });
    // From inside a pedestrian area, which is linked to the visibility points of the area. A new
    // category comes last: the random numbers of the cases above stay the same.
    add("area", 12, () -> {
      var inside = areaPoints.get(random.nextInt(areaPoints.size()));
      var from = map("coordinates", map("latitude", round(inside.y), "longitude", round(inside.x)));
      var vars = trip(from, coordinate(nearPoint(inside, 300, 3000)), false);
      return random.nextBoolean() ? with(vars, "modes", modes(null, null, "foot", List.of())) : vars;
    });
  }

  /** An interior point of each area that is 30 m or more from south to north. */
  private static List<Coordinate> areaPoints(Graph graph) {
    var areas = Collections.newSetFromMap(new IdentityHashMap<AreaGroup, Boolean>());
    var points = new TreeSet<Coordinate>();
    for (AreaEdge edge : graph.findEdges(AreaEdge.class)) {
      var geometry = edge.getArea().getGeometry();
      if (areas.add(edge.getArea()) && geometry.getEnvelopeInternal().getHeight() * 111_320 > 30) {
        points.add(geometry.getInteriorPoint().getCoordinate());
      }
    }
    return List.copyOf(points);
  }

  private void add(String category, int count, Supplier<Map<String, Object>> variables) {
    for (int i = 1; i <= count; i++) {
      var id = String.format("%s-%03d", category, i);
      boolean paging = category.equals("paging");
      cases.add(new RigCase(id, category, paging ? 2 : 0, paging ? 1 : 0, variables.get()));
    }
  }

  private Map<String, Object> localTrip() {
    var from = stop();
    return trip(coordinate(from), coordinate(near(from, 1500, 10000)), random.nextInt(4) == 0);
  }

  private Map<String, Object> regionalTrip() {
    var from = stop();
    return trip(coordinate(from), coordinate(near(from, 8000, 40000)), random.nextInt(4) == 0);
  }

  private Map<String, Object> trip(
    Map<String, Object> from,
    Map<String, Object> to,
    boolean arriveBy
  ) {
    var vars = new LinkedHashMap<String, Object>();
    vars.put("from", from);
    vars.put("to", to);
    vars.put("dateTime", dateTime(LocalTime.of(5 + random.nextInt(18), random.nextInt(60))));
    if (arriveBy) {
      vars.put("arriveBy", true);
    }
    return vars;
  }

  private String dateTime(LocalTime time) {
    var date = dates.get(random.nextInt(dates.size()));
    return DATE_TIME.format(ZonedDateTime.of(date, time, zone));
  }

  private static Map<String, Object> with(Map<String, Object> vars, String name, Object value) {
    vars.put(name, value);
    return vars;
  }

  private static Map<String, Object> modes(
    String access,
    String egress,
    String direct,
    List<?> transportModes
  ) {
    var modes = new LinkedHashMap<String, Object>();
    if (access != null) {
      modes.put("accessMode", access);
    }
    if (egress != null) {
      modes.put("egressMode", egress);
    }
    if (direct != null) {
      modes.put("directMode", direct);
    }
    if (transportModes != null) {
      modes.put("transportModes", transportModes);
    }
    return modes;
  }

  /** A point near the stop, where a traveller could start: up to 600 m away. */
  private Map<String, Object> coordinate(StopLocation stop) {
    double distance = 30 + random.nextDouble() * 570;
    double bearing = random.nextDouble() * 2 * Math.PI;
    double lat = stop.getLat() + (distance * Math.cos(bearing)) / 111_320;
    double lon =
      stop.getLon() + (distance * Math.sin(bearing)) / (111_320 * Math.cos(Math.toRadians(stop.getLat())));
    var coordinates = new LinkedHashMap<String, Object>();
    coordinates.put("latitude", round(lat));
    coordinates.put("longitude", round(lon));
    return map("coordinates", coordinates);
  }

  private static double round(double value) {
    return Math.round(value * 1_000_000) / 1_000_000.0;
  }

  private Map<String, Object> place(StopLocation stop, boolean station) {
    var id = station ? stop.getParentStation().getId().getId() : stop.getId().getId();
    return map("place", id);
  }

  private static String stationOrStopId(StopLocation stop) {
    return stop.getParentStation() == null
      ? stop.getId().getId()
      : stop.getParentStation().getId().getId();
  }

  /**
   * A random stop. With busy stops, every second one is picked by its number of patterns: in a
   * national graph most stops have a few departures a day, and most trips go between the others.
   */
  private StopLocation stop() {
    if (patternsUpTo != null && random.nextBoolean()) {
      int pattern = 1 + random.nextInt(patternsUpTo[patternsUpTo.length - 1]);
      int index = Arrays.binarySearch(patternsUpTo, pattern);
      return stops.get(index >= 0 ? index : -index - 1);
    }
    return stops.get(random.nextInt(stops.size()));
  }

  private StopLocation station() {
    return stations.get(random.nextInt(stations.size()));
  }

  /**
   * A stop between min and max meters from the given stop. Random stops are tried first; where
   * few stops are in range, one of them is picked, or one of the stops nearest to the range.
   */
  private StopLocation near(StopLocation from, double min, double max) {
    for (int i = 0; i < 2000; i++) {
      var candidate = stop();
      if (metersOutside(from, candidate, min, max) == 0) {
        return candidate;
      }
    }
    var nearest = stops
      .stream()
      .filter(s -> s != from)
      .sorted(Comparator.comparingDouble(s -> metersOutside(from, s, min, max)))
      .limit(3)
      .toList();
    var inRange = stops.stream().filter(s -> metersOutside(from, s, min, max) == 0).toList();
    var candidates = inRange.isEmpty() ? nearest : inRange;
    return candidates.get(random.nextInt(candidates.size()));
  }

  /** A stop between min and max meters from the point, or else the stop nearest to that range. */
  private StopLocation nearPoint(Coordinate point, double min, double max) {
    var nearestToRange = stops
      .stream()
      .sorted(Comparator.comparingDouble(s -> metersOutside(point.y, point.x, s, min, max)))
      .toList();
    long inRange = stops.stream().filter(s -> metersOutside(point.y, point.x, s, min, max) == 0).count();
    return nearestToRange.get(random.nextInt((int) Math.max(1, inRange)));
  }

  /** How far the distance between the stops is outside the range min to max, 0 if inside. */
  private static double metersOutside(StopLocation a, StopLocation b, double min, double max) {
    return metersOutside(a.getLat(), a.getLon(), b, min, max);
  }

  private static double metersOutside(double lat, double lon, StopLocation b, double min, double max) {
    double d = SphericalDistanceLibrary.distance(lat, lon, b.getLat(), b.getLon());
    return d < min ? min - d : Math.max(0, d - max);
  }

  /** Up to n lines that serve the stop, as API ids. */
  private List<String> lines(StopLocation stop, int n) {
    Set<String> ids = new TreeSet<>();
    for (var pattern : transitService.findPatterns(stop)) {
      ids.add(pattern.getRoute().getId().getId());
    }
    var list = new ArrayList<>(ids);
    var result = new ArrayList<String>();
    while (!list.isEmpty() && result.size() < n) {
      result.add(list.remove(random.nextInt(list.size())));
    }
    return result;
  }

  @SafeVarargs
  private <T> T pick(T... values) {
    return values[random.nextInt(values.length)];
  }

  /** An ordered map, so the case file is the same in every run (Map.of is not ordered). */
  private static Map<String, Object> map(Object... keysAndValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }
}
