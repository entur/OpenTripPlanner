package org.opentripplanner.perfrig;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.street.model.vertex.TemporaryVertex;
import org.opentripplanner.street.model.vertex.Vertex;

/** Helpers to write deterministic, line-based text. */
final class Dump {

  /** The identity hash of Object.toString, different in each JVM. */
  private static final Pattern IDENTITY_HASH = Pattern.compile("@[0-9a-f]{5,8}\\b");
  private static final Set<String> SKIPPED_GETTERS = Set.of(
    "hashCode",
    "toString",
    "getClass",
    "getOutgoing",
    "getIncoming",
    "getFromVertex",
    "getToVertex",
    "getLabel"
  );
  private static final Map<Class<?>, List<Method>> GETTERS = new ConcurrentHashMap<>();

  private final StringBuilder buf = new StringBuilder();
  private String indent = "";

  Dump line(String text) {
    buf.append(indent).append(text).append('\n');
    return this;
  }

  Dump indent() {
    indent = indent + "  ";
    return this;
  }

  Dump outdent() {
    indent = indent.substring(2);
    return this;
  }

  @Override
  public String toString() {
    return buf.toString();
  }

  /**
   * The value as text. Exceptions are part of the result, so the dump never stops at a field that
   * is not supported by a type of leg.
   */
  static String v(Supplier<?> value) {
    try {
      return str(value.get());
    } catch (Exception e) {
      return "!" + e.getClass().getSimpleName();
    }
  }

  /**
   * The value as text. A value without its own toString is written with its getters, and identity
   * hashes in toString results are removed, so the text is the same in every JVM.
   */
  static String str(Object value) {
    if (value == null) {
      return "-";
    }
    if (value instanceof Optional<?> o) {
      return o.isPresent() ? "Optional[" + str(o.get()) + "]" : "Optional.empty";
    }
    if (value instanceof Collection<?> c) {
      return c.stream().map(Dump::str).collect(Collectors.joining(", ", "[", "]"));
    }
    if (!hasOwnToString(value.getClass())) {
      return value.getClass().getSimpleName() + "{" + getters(value).strip() + "}";
    }
    return IDENTITY_HASH.matcher(value.toString()).replaceAll("@?");
  }

  private static boolean hasOwnToString(Class<?> type) {
    try {
      return type.getMethod("toString").getDeclaringClass() != Object.class;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /** The values of all public no-argument getters of the object, by name. */
  static String getters(Object o) {
    var text = new StringBuilder();
    for (Method m : GETTERS.computeIfAbsent(o.getClass(), Dump::findGetters)) {
      text.append(m.getName()).append('=');
      try {
        var value = m.invoke(o);
        text.append(value instanceof Geometry g ? geometry(g) : simple(value));
      } catch (Exception e) {
        var cause = e.getCause() == null ? e : e.getCause();
        text.append('!').append(cause.getClass().getSimpleName());
      }
      text.append(' ');
    }
    return text.toString();
  }

  /** A value of a getter: no recursion into objects without their own toString. */
  private static String simple(Object value) {
    if (value == null) {
      return "-";
    }
    var text = hasOwnToString(value.getClass())
      ? value.toString()
      : value.getClass().getSimpleName();
    return IDENTITY_HASH.matcher(text).replaceAll("@?");
  }

  private static List<Method> findGetters(Class<?> type) {
    var result = new ArrayList<Method>();
    for (Method m : type.getMethods()) {
      if (
        m.getParameterCount() == 0 &&
        !Modifier.isStatic(m.getModifiers()) &&
        m.getReturnType() != void.class &&
        !SKIPPED_GETTERS.contains(m.getName()) &&
        (m.getName().startsWith("get") || m.getName().startsWith("is") || m.getName().startsWith("has"))
      ) {
        try {
          m.setAccessible(true);
          result.add(m);
        } catch (RuntimeException ignore) {
          // Not accessible
        }
      }
    }
    result.sort(Comparator.comparing(Method::getName));
    return result;
  }

  /**
   * The label of a vertex. A temporary or split vertex is labeled with its exact coordinate: its
   * label comes from a counter of the graph (Graph.nextSplitNumber), shared by all requests, so it
   * is different when other requests run at the same time.
   */
  static String vertex(Vertex v) {
    var label = v.getLabelString();
    if (v instanceof TemporaryVertex || label.startsWith("split_")) {
      return "at:" + Double.doubleToLongBits(v.getLat()) + "," + Double.doubleToLongBits(v.getLon());
    }
    return label;
  }

  /** All coordinates of a geometry, exactly, as a short hash, with the number of points. */
  static String geometry(Geometry geometry) {
    if (geometry == null) {
      return "-";
    }
    var coordinates = geometry.getCoordinates();
    var text = new StringBuilder();
    for (Coordinate c : coordinates) {
      text.append(Double.doubleToLongBits(c.x)).append(',');
      text.append(Double.doubleToLongBits(c.y)).append(',');
      text.append(Double.doubleToLongBits(c.getZ())).append(';');
    }
    var first = coordinates.length == 0 ? "" : " " + coordinates[0].y + "," + coordinates[0].x;
    var last = coordinates.length < 2
      ? ""
      : " .. " + coordinates[coordinates.length - 1].y + "," + coordinates[coordinates.length - 1].x;
    return coordinates.length + "pts#" + hash(text.toString()) + first + last;
  }

  static String hash(String text) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(
        text.getBytes(StandardCharsets.UTF_8)
      );
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
