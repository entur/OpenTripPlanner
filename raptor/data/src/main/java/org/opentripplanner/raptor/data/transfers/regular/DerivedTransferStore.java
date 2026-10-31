package org.opentripplanner.raptor.data.transfers.regular;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import org.opentripplanner.raptor.spi.RaptorTransfer;

/**
 * The Raptor transfers for one set of user preferences, derived from a shared
 * {@link DerivedTransferTable} with a {@link TransferValueFunction}. Creating the store costs
 * nothing, unless the rows are created at once, see {@link Mode}. A row is the transfers of one
 * stop sorted by duration; Raptor stops at the first transfer that is too long, so the rows are
 * sorted for every user preference.
 * <p>
 * The store is thread-safe. Two threads may create the same row at the same time, the rows are
 * equal and either is used.
 */
public final class DerivedTransferStore implements RaptorRegularTransferService {

  /** When the rows of the stops are created. */
  public enum Mode {
    /** All rows are created with the store. */
    EAGER,
    /** A row is created when it is read for the first time, and then kept. */
    LAZY,
    /** A row is created on every read, nothing is kept. */
    ON_READ,
  }

  /** Measurement only: skip the sort, to measure its cost. The rows are NOT valid without it. */
  private static final boolean NO_SORT = Boolean.getBoolean("otp.transfers.nosort");

  private static final DefaultRaptorTransfer[] EMPTY = new DefaultRaptorTransfer[0];
  private static final VarHandle ROW = MethodHandles.arrayElementVarHandle(
    DefaultRaptorTransfer[][].class
  );

  private final DerivedTransferTable table;
  private final TransferValueFunction function;
  private final Mode mode;
  private final DefaultRaptorTransfer[][] fromRows;
  private final DefaultRaptorTransfer[][] toRows;

  private DerivedTransferStore(
    DerivedTransferTable table,
    TransferValueFunction function,
    Mode mode,
    DefaultRaptorTransfer[][] fromRows,
    DefaultRaptorTransfer[][] toRows
  ) {
    this.table = table;
    this.function = function;
    this.mode = mode;
    this.fromRows = fromRows;
    this.toRows = toRows;
  }

  public static DerivedTransferStore of(
    DerivedTransferTable table,
    TransferValueFunction function,
    Mode mode
  ) {
    int n = table.nStops();
    var store = new DerivedTransferStore(
      table,
      function,
      mode,
      new DefaultRaptorTransfer[n][],
      new DefaultRaptorTransfer[n][]
    );
    if (mode == Mode.EAGER) {
      store.fill();
    }
    return store;
  }

  /**
   * The store of the same user preferences for {@code newTable}, the table this store's table was
   * patched to by replacing the transfers from {@code changedFromStops}. The rows that did not
   * change are shared with this store. {@code newFunction} is the function for the new table: it
   * evaluates the same user preferences, but may need data of the new table.
   */
  public DerivedTransferStore patch(
    DerivedTransferTable newTable,
    TransferValueFunction newFunction,
    Set<Integer> changedFromStops
  ) {
    if (changedFromStops.isEmpty() && newTable == table && newFunction == function) {
      return this;
    }
    var from = fromRows.clone();
    var to = toRows.clone();
    for (int f : changedFromStops) {
      from[f] = null;
      for (int t : table.toStops(f)) {
        to[t] = null;
      }
      for (int t : newTable.toStops(f)) {
        to[t] = null;
      }
    }
    var patched = new DerivedTransferStore(newTable, newFunction, mode, from, to);
    if (mode == Mode.EAGER) {
      patched.fill();
    }
    return patched;
  }

  public DerivedTransferTable table() {
    return table;
  }

  private void fill() {
    for (int i = 0; i < fromRows.length; ++i) {
      if (fromRows[i] == null) {
        fromRows[i] = computeFrom(i);
      }
      if (toRows[i] == null) {
        toRows[i] = computeTo(i);
      }
    }
  }

  @Override
  public Iterator<? extends RaptorTransfer> getTransfersFromStop(int fromStop) {
    return new RowIterator(rowFrom(fromStop));
  }

  @Override
  public Iterator<? extends RaptorTransfer> getTransfersToStop(int toStop) {
    return new RowIterator(rowTo(toStop));
  }

  private DefaultRaptorTransfer[] rowFrom(int stop) {
    if (mode == Mode.ON_READ) {
      return computeFrom(stop);
    }
    var row = (DefaultRaptorTransfer[]) ROW.getAcquire(fromRows, stop);
    if (row == null) {
      row = computeFrom(stop);
      ROW.setRelease(fromRows, stop, row);
    }
    return row;
  }

  private DefaultRaptorTransfer[] rowTo(int stop) {
    if (mode == Mode.ON_READ) {
      return computeTo(stop);
    }
    var row = (DefaultRaptorTransfer[]) ROW.getAcquire(toRows, stop);
    if (row == null) {
      row = computeTo(stop);
      ROW.setRelease(toRows, stop, row);
    }
    return row;
  }

  private DefaultRaptorTransfer[] computeFrom(int stop) {
    var r = table.fromRow(stop);
    int n = r.to.length;
    if (n == 0) {
      return EMPTY;
    }
    long[] values = new long[n];
    long[] keys = new long[n];
    int m = 0;
    for (int k = 0; k < n; ++k) {
      long v = function.evaluate(r.vals, r.off[k], r.off[k + 1] - r.off[k]);
      values[k] = v;
      if (v != TransferValueFunction.NONE) {
        keys[m++] = ((long) TransferValueFunction.durationInSeconds(v) << 32) | k;
      }
    }
    return sorted(m == n ? keys : Arrays.copyOf(keys, m), values, r.to);
  }

  private DefaultRaptorTransfer[] computeTo(int stop) {
    var r = table.toRow(stop);
    int n = r.from.length;
    if (n == 0) {
      return EMPTY;
    }
    long[] values = new long[n];
    long[] keys = new long[n];
    int[] stops = r.from;
    int m = 0;
    for (int k = 0; k < n; ++k) {
      var fr = table.fromRow(stops[k]);
      int p = r.pos[k];
      long v = function.evaluate(fr.vals, fr.off[p], fr.off[p + 1] - fr.off[p]);
      values[k] = v;
      if (v != TransferValueFunction.NONE) {
        keys[m++] = ((long) TransferValueFunction.durationInSeconds(v) << 32) | k;
      }
    }
    return sorted(m == n ? keys : Arrays.copyOf(keys, m), values, stops);
  }

  private static DefaultRaptorTransfer[] sorted(long[] keys, long[] values, int[] stops) {
    if (keys.length > 1 && !NO_SORT) {
      Arrays.sort(keys);
    }
    var row = new DefaultRaptorTransfer[keys.length];
    for (int i = 0; i < row.length; ++i) {
      int k = (int) keys[i];
      long v = values[k];
      row[i] = new DefaultRaptorTransfer(
        stops[k],
        TransferValueFunction.durationInSeconds(v),
        TransferValueFunction.c1(v)
      );
    }
    return row;
  }

  private static final class RowIterator implements Iterator<RaptorTransfer> {

    private final DefaultRaptorTransfer[] row;
    private int index = 0;

    RowIterator(DefaultRaptorTransfer[] row) {
      this.row = row;
    }

    @Override
    public boolean hasNext() {
      return index < row.length;
    }

    @Override
    public RaptorTransfer next() {
      if (index >= row.length) {
        throw new NoSuchElementException();
      }
      return row[index++];
    }
  }
}
