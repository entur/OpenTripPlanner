package rigops;

/**
 * perf-rig: the counters of the operation-counting agent. On the boot class path, so that
 * instrumented JDK classes can reach it. It calls nothing: instrumented code of java.util uses it.
 */
public final class Ops {

  /**
   * The number of bytecode instructions executed, by method index. Updated without
   * synchronization: exact for the code of one thread while the other threads are idle.
   */
  public static final long[] COUNTS = new long[1 << 20];

  private static final String[] NAMES = new String[COUNTS.length];
  private static final int[] FIRST_LINES = new int[COUNTS.length];
  private static final int[] LAST_LINES = new int[COUNTS.length];
  private static int size;

  private Ops() {}

  /**
   * The index of the counter of a new method, or -1 if there is no room for more methods.
   *
   * @param firstLine the first source line of the method, 0 if unknown
   */
  public static synchronized int register(String method, int firstLine, int lastLine) {
    if (size == NAMES.length) {
      return -1;
    }
    NAMES[size] = method;
    FIRST_LINES[size] = firstLine;
    LAST_LINES[size] = lastLine;
    return size++;
  }

  /** The number of methods registered. */
  public static int size() {
    return size;
  }

  public static String name(int index) {
    return NAMES[index];
  }

  public static int firstLine(int index) {
    return FIRST_LINES[index];
  }

  public static int lastLine(int index) {
    return LAST_LINES[index];
  }

  /** The number of instructions executed so far, in all methods. */
  public static long total() {
    long sum = 0;
    for (int i = 0, n = size; i < n; i++) {
      sum += COUNTS[i];
    }
    return sum;
  }
}
