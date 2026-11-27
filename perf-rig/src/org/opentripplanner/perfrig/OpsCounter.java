package org.opentripplanner.perfrig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The counters of the operation-counting agent ({@code perf-rig/agent}): the number of bytecode
 * instructions executed. Zero if the JVM runs without the agent. The agent is found by name, so
 * the rig does not need it to compile or to run.
 */
final class OpsCounter {

  private static final Class<?> OPS = findOps();
  private static final MethodHandle TOTAL = handle("total", long.class);
  private static final MethodHandle SIZE = handle("size", int.class);
  private static final MethodHandle NAME = handle("name", String.class, int.class);
  private static final MethodHandle FIRST_LINE = handle("firstLine", int.class, int.class);
  private static final MethodHandle LAST_LINE = handle("lastLine", int.class, int.class);

  /** A counted method: its source lines (0 if unknown) and the instructions it executed. */
  record Method(String name, int firstLine, int lastLine, long ops) {}

  private OpsCounter() {}

  static boolean active() {
    return OPS != null;
  }

  /** The instructions executed so far, by all threads. */
  static long total() {
    if (OPS == null) {
      return 0;
    }
    try {
      return (long) TOTAL.invokeExact();
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  /** The instructions executed so far by each method; a copy. */
  static long[] byMethod() {
    if (OPS == null) {
      return new long[0];
    }
    try {
      long[] counts = (long[]) OPS.getField("COUNTS").get(null);
      return Arrays.copyOf(counts, (int) SIZE.invokeExact());
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  /** Adds the instructions executed by each method since the earlier call of byMethod. */
  static void addSince(long[] earlier, Map<String, Long> target) {
    long[] now = byMethod();
    try {
      for (int i = 0; i < now.length; i++) {
        long count = now[i] - (i < earlier.length ? earlier[i] : 0);
        if (count != 0) {
          target.merge((String) NAME.invokeExact(i), count, Long::sum);
        }
      }
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  /** All counted methods, with the instructions executed since the earlier call of byMethod. */
  static List<Method> methodsSince(long[] earlier) {
    long[] now = byMethod();
    var result = new ArrayList<Method>(now.length);
    try {
      for (int i = 0; i < now.length; i++) {
        long ops = now[i] - (i < earlier.length ? earlier[i] : 0);
        var name = (String) NAME.invokeExact(i);
        result.add(new Method(name, (int) FIRST_LINE.invokeExact(i), (int) LAST_LINE.invokeExact(i), ops));
      }
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
    return result;
  }

  private static Class<?> findOps() {
    try {
      return Class.forName("rigops.Ops");
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static MethodHandle handle(String name, Class<?> returnType, Class<?>... parameters) {
    if (OPS == null) {
      return null;
    }
    try {
      var type = MethodType.methodType(returnType, parameters);
      return MethodHandles.publicLookup().findStatic(OPS, name, type);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
