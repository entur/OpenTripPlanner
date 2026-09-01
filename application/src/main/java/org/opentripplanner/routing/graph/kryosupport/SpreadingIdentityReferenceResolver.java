package org.opentripplanner.routing.graph.kryosupport;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.ReferenceResolver;
import com.esotericsoftware.kryo.util.Util;
import java.util.ArrayList;

/**
 * A {@link ReferenceResolver} that tracks written objects the same way Kryo's default {@code
 * MapReferenceResolver} does (an open-addressed table keyed by {@link System#identityHashCode}),
 * but spreads the identity hash with a Fibonacci multiplicative hash before masking it down to a
 * bucket index.
 * <p>
 * Kryo's own {@code IdentityObjectIntMap} buckets objects with {@code identityHashCode & mask},
 * i.e. it uses the raw identity hash's low bits directly, with no mixing step (unlike {@link
 * java.util.HashMap}, which XORs the high bits into the low bits before masking).
 * <p>
 * This is normally harmless, but graph serialization writes hundreds of thousands of {@link
 * org.opentripplanner.street.model.edge.Edge} instances, and the identity hashes for that many
 * objects, minted in one tight burst just before writing, can end up numerically clustered rather
 * than spread across the full 32-bit range (this is a property of how the JVM's identity hash
 * generator behaves under bursty allocation, not something OTP controls). With unmixed low-bit
 * bucketing, that clustering collapses into a small number of buckets and degrades lookups in the
 * reference table from O(1) to near O(n), which was observed to turn a graph save that should
 * take well under a second into one taking 5+ seconds on a graph with ~300k edges.
 * <p>
 * A single multiplicative mix (the same trick used for e.g. Fibonacci hashing) is enough to break
 * up that clustering, because it spreads any narrow range of input values across the full output
 * range — unlike simple XOR-folding, which does nothing when the differing bits are already
 * concentrated in a narrow range (i.e. the high bits are identical across most inputs).
 */
public class SpreadingIdentityReferenceResolver implements ReferenceResolver {

  /** Fibonacci hashing multiplier: the odd integer nearest to 2^32 / golden ratio. */
  private static final int FIBONACCI_MULTIPLIER = 0x9E3779B9;

  private static final int DEFAULT_CAPACITY = 2048;
  private static final float LOAD_FACTOR = 0.8f;

  private final int maximumCapacity;
  private final ArrayList<Object> readObjects = new ArrayList<>();

  private Object[] keyTable;
  private int[] valueTable;
  private int mask;
  private int threshold;
  private int size;

  public SpreadingIdentityReferenceResolver() {
    this(DEFAULT_CAPACITY);
  }

  public SpreadingIdentityReferenceResolver(int maximumCapacity) {
    this.maximumCapacity = maximumCapacity;
    allocateTable(tableCapacityFor(maximumCapacity));
  }

  @Override
  public void setKryo(Kryo kryo) {}

  @Override
  public int getWrittenId(Object object) {
    for (int i = place(object); ; i = (i + 1) & mask) {
      Object other = keyTable[i];
      if (other == null) {
        return -1;
      }
      if (other == object) {
        return valueTable[i];
      }
    }
  }

  @Override
  public int addWrittenObject(Object object) {
    int id = size;
    int i = place(object);
    while (keyTable[i] != null) {
      i = (i + 1) & mask;
    }
    keyTable[i] = object;
    valueTable[i] = id;
    size++;
    if (size >= threshold) {
      growTable();
    }
    return id;
  }

  @Override
  public int nextReadId(Class type) {
    int id = readObjects.size();
    readObjects.add(null);
    return id;
  }

  @Override
  public void setReadObject(int id, Object object) {
    readObjects.set(id, object);
  }

  @Override
  public Object getReadObject(Class type, int id) {
    return readObjects.get(id);
  }

  @Override
  public void reset() {
    int size = readObjects.size();
    readObjects.clear();
    if (size > maximumCapacity) {
      readObjects.trimToSize();
      readObjects.ensureCapacity(maximumCapacity);
    }
    // The table is reused between (de)serialization calls, so it must be discarded rather than
    // rehashed into a smaller table: rehashing would try to fit however many entries the previous
    // (possibly much larger) run accumulated into the small starting capacity.
    allocateTable(tableCapacityFor(maximumCapacity));
  }

  @Override
  public boolean useReferences(Class type) {
    return !Util.isWrapperClass(type) && !Util.isEnum(type);
  }

  private int place(Object item) {
    int h = System.identityHashCode(item) * FIBONACCI_MULTIPLIER;
    h ^= (h >>> 16);
    return h & mask;
  }

  private static int tableCapacityFor(int expectedEntries) {
    int minCapacity = (int) (expectedEntries / LOAD_FACTOR) + 1;
    int capacity = 4;
    while (capacity < minCapacity) {
      capacity <<= 1;
    }
    return capacity;
  }

  private void allocateTable(int capacity) {
    keyTable = new Object[capacity];
    valueTable = new int[capacity];
    mask = capacity - 1;
    threshold = (int) (capacity * LOAD_FACTOR);
    size = 0;
  }

  private void growTable() {
    Object[] oldKeys = keyTable;
    int[] oldValues = valueTable;
    int oldSize = size;
    allocateTable(keyTable.length << 1);
    for (int i = 0; i < oldKeys.length; i++) {
      Object key = oldKeys[i];
      if (key != null) {
        int j = place(key);
        while (keyTable[j] != null) {
          j = (j + 1) & mask;
        }
        keyTable[j] = key;
        valueTable[j] = oldValues[i];
      }
    }
    size = oldSize;
  }
}
