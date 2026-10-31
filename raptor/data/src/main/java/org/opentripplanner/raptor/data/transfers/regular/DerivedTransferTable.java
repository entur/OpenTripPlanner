package org.opentripplanner.raptor.data.transfers.regular;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * The preference independent data of all transfers of one profile: for each transfer the to stop
 * and a payload of numbers, from which a {@link TransferValueFunction} computes the duration and
 * the c1 cost for any user preferences. The payload is stored once, in the rows of the from stops.
 * The rows of the to stops point to the payload: a (from stop, position) pair per transfer.
 * <p>
 * The table is immutable and thread-safe. A patch replaces the rows of a few from stops and
 * shares all other rows, like {@link RaptorTransferStorePatch}.
 */
public final class DerivedTransferTable {

  private static final FromRow EMPTY_FROM = new FromRow(new int[0], new int[] { 0 }, new float[0]);
  private static final ToRow EMPTY_TO = new ToRow(new int[0], new int[0]);

  private final FromRow[] fromRows;
  private final ToRow[] toRows;

  private DerivedTransferTable(FromRow[] fromRows, ToRow[] toRows) {
    this.fromRows = fromRows;
    this.toRows = toRows;
  }

  public static Builder of(int nStops) {
    return new Builder(nStops);
  }

  public int nStops() {
    return fromRows.length;
  }

  /** The number of transfers. */
  public int size() {
    int n = 0;
    for (var row : fromRows) {
      n += row.to.length;
    }
    return n;
  }

  /** The to stops of the transfers from {@code fromStop}, in the order of the payload. */
  int[] toStops(int fromStop) {
    return fromRows[fromStop].to;
  }

  FromRow fromRow(int fromStop) {
    return fromRows[fromStop];
  }

  ToRow toRow(int toStop) {
    return toRows[toStop];
  }

  public Patch patch() {
    return new Patch(this);
  }

  /** The payloads of all transfers from one stop. The arrays must not be modified. */
  static final class FromRow {

    final int[] to;
    /** Start of the payload of transfer i is {@code off[i]}, its end is {@code off[i + 1]}. */
    final int[] off;
    final float[] vals;

    FromRow(int[] to, int[] off, float[] vals) {
      this.to = to;
      this.off = off;
      this.vals = vals;
    }
  }

  /** The transfers to one stop: the from stop and the position in the row of the from stop. */
  static final class ToRow {

    final int[] from;
    final int[] pos;

    ToRow(int[] from, int[] pos) {
      this.from = from;
      this.pos = pos;
    }
  }

  private static final class Rows {

    final TIntArrayList to = new TIntArrayList(4);
    final TIntArrayList off = new TIntArrayList(5);
    final TFloatArrayList vals = new TFloatArrayList(16);

    Rows() {
      off.add(0);
    }

    void add(int toStop, float[] payload) {
      to.add(toStop);
      vals.add(payload);
      off.add(vals.size());
    }

    FromRow build() {
      return to.isEmpty() ? EMPTY_FROM : new FromRow(to.toArray(), off.toArray(), vals.toArray());
    }
  }

  public static final class Builder {

    private final Rows[] rows;
    private boolean built = false;

    private Builder(int nStops) {
      this.rows = new Rows[nStops];
    }

    public Builder addTransfer(int fromStop, int toStop, float[] payload) {
      if (built) {
        throw new IllegalStateException("The table is built");
      }
      var r = rows[fromStop];
      if (r == null) {
        r = new Rows();
        rows[fromStop] = r;
      }
      r.add(toStop, payload);
      return this;
    }

    public DerivedTransferTable build() {
      built = true;
      int n = rows.length;
      var from = new FromRow[n];
      int[] count = new int[n];
      for (int i = 0; i < n; ++i) {
        from[i] = rows[i] == null ? EMPTY_FROM : rows[i].build();
        rows[i] = null;
        for (int t : from[i].to) {
          ++count[t];
        }
      }
      var tFrom = new int[n][];
      var tPos = new int[n][];
      for (int i = 0; i < n; ++i) {
        if (count[i] > 0) {
          tFrom[i] = new int[count[i]];
          tPos[i] = new int[count[i]];
        }
      }
      int[] fill = new int[n];
      for (int f = 0; f < n; ++f) {
        var to = from[f].to;
        for (int k = 0; k < to.length; ++k) {
          int t = to[k];
          tFrom[t][fill[t]] = f;
          tPos[t][fill[t]] = k;
          ++fill[t];
        }
      }
      var toRows = new ToRow[n];
      for (int i = 0; i < n; ++i) {
        toRows[i] = count[i] == 0 ? EMPTY_TO : new ToRow(tFrom[i], tPos[i]);
      }
      return new DerivedTransferTable(from, toRows);
    }
  }

  /**
   * Replaces all transfers from a few stops. A replaced stop without added transfers has none
   * afterward. The rows of the to stops that are targets of a replaced stop, before or after the
   * patch, are rebuilt, all other rows are shared with the patched table.
   */
  public static final class Patch {

    private final DerivedTransferTable base;
    private final Map<Integer, Rows> replaced = new HashMap<>();

    private Patch(DerivedTransferTable base) {
      this.base = base;
    }

    public Patch replaceTransfersFrom(int fromStop) {
      replaced.computeIfAbsent(fromStop, s -> new Rows());
      return this;
    }

    public Patch addTransfer(int fromStop, int toStop, float[] payload) {
      var r = replaced.get(fromStop);
      if (r == null) {
        throw new IllegalArgumentException("Stop " + fromStop + " is not replaced");
      }
      r.add(toStop, payload);
      return this;
    }

    public DerivedTransferTable build() {
      if (replaced.isEmpty()) {
        return base;
      }
      var from = base.fromRows.clone();
      var affected = new HashSet<Integer>();
      var added = new HashMap<Integer, TIntArrayList>();
      for (var e : replaced.entrySet()) {
        int f = e.getKey();
        for (int t : base.fromRows[f].to) {
          affected.add(t);
        }
        var row = e.getValue().build();
        from[f] = row;
        for (int k = 0; k < row.to.length; ++k) {
          added.computeIfAbsent(row.to[k], s -> new TIntArrayList()).add(new int[] { f, k });
          affected.add(row.to[k]);
        }
      }
      var to = base.toRows.clone();
      for (int t : affected) {
        var old = base.toRows[t];
        var extra = added.get(t);
        int n = 0;
        for (int f : old.from) {
          if (!replaced.containsKey(f)) {
            ++n;
          }
        }
        int add = extra == null ? 0 : extra.size() / 2;
        if (n + add == 0) {
          to[t] = EMPTY_TO;
          continue;
        }
        var nf = new int[n + add];
        var np = new int[n + add];
        int j = 0;
        for (int i = 0; i < old.from.length; ++i) {
          if (!replaced.containsKey(old.from[i])) {
            nf[j] = old.from[i];
            np[j] = old.pos[i];
            ++j;
          }
        }
        for (int i = 0; i < add; ++i) {
          nf[j] = extra.get(2 * i);
          np[j] = extra.get(2 * i + 1);
          ++j;
        }
        to[t] = new ToRow(nf, np);
      }
      return new DerivedTransferTable(from, to);
    }
  }
}
