package org.opentripplanner.raptor.data.transfers.regular;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.opentripplanner.raptor.spi.RaptorTransfer;

/**
 * A transfer store that computes the transfers of a stop the first time they are read, and keeps
 * them. The rows are created by a {@link RowLoader}. A from-row (transfers from a stop) and a
 * to-row (transfers to a stop) are loaded independently.
 * <p>
 * This class is thread-safe without locks. A row is published with a compare-and-set after it is
 * fully built, so a reader sees either no row or a complete, sorted one. If two threads load the
 * same row at the same time, both compute it, the first published row wins and the other is
 * dropped. This is correct as long as the loader is deterministic.
 */
public final class LazyRaptorTransferStore implements RaptorRegularTransferService {

  private static final DefaultRaptorTransfer[] EMPTY_ARRAY = new DefaultRaptorTransfer[0];

  private final AtomicReferenceArray<DefaultRaptorTransfer[]> fromRows;
  private final AtomicReferenceArray<DefaultRaptorTransfer[]> toRows;
  private final RowLoader loader;

  public LazyRaptorTransferStore(int nStops, RowLoader loader) {
    this(new AtomicReferenceArray<>(nStops), new AtomicReferenceArray<>(nStops), loader);
  }

  private LazyRaptorTransferStore(
    AtomicReferenceArray<DefaultRaptorTransfer[]> fromRows,
    AtomicReferenceArray<DefaultRaptorTransfer[]> toRows,
    RowLoader loader
  ) {
    this.fromRows = fromRows;
    this.toRows = toRows;
    this.loader = loader;
  }

  /**
   * Create the store for the next snapshot: all rows are shared, except the dropped ones, which
   * are loaded again, now with {@code newLoader}. This store is not changed.
   */
  public LazyRaptorTransferStore patched(
    RowLoader newLoader,
    Set<Integer> droppedFromRows,
    Set<Integer> droppedToRows
  ) {
    var from = copy(fromRows);
    var to = copy(toRows);
    droppedFromRows.forEach(stop -> from.set(stop, null));
    droppedToRows.forEach(stop -> to.set(stop, null));
    return new LazyRaptorTransferStore(from, to, newLoader);
  }

  /** The loader of this store, to find the rows a patch must drop. */
  public RowLoader loader() {
    return loader;
  }

  /** The number of rows loaded so far, from-rows and to-rows. */
  public int loadedRows() {
    int n = 0;
    for (int i = 0; i < fromRows.length(); ++i) {
      n += (fromRows.getPlain(i) != null ? 1 : 0) + (toRows.getPlain(i) != null ? 1 : 0);
    }
    return n;
  }

  @Override
  public Iterator<? extends RaptorTransfer> getTransfersFromStop(int fromStop) {
    var row = fromRows.getAcquire(fromStop);
    if (row == null) {
      row = publish(fromRows, fromStop, loader.loadFromRow(fromStop));
    }
    return new RowIterator(row);
  }

  @Override
  public Iterator<? extends RaptorTransfer> getTransfersToStop(int toStop) {
    var row = toRows.getAcquire(toStop);
    if (row == null) {
      row = publish(toRows, toStop, loader.loadToRow(toStop, this));
    }
    return new RowIterator(row);
  }

  private static DefaultRaptorTransfer[] publish(
    AtomicReferenceArray<DefaultRaptorTransfer[]> rows,
    int stop,
    int[] triples
  ) {
    var row = toRow(triples);
    var witness = rows.compareAndExchangeRelease(stop, null, row);
    return witness == null ? row : witness;
  }

  private static DefaultRaptorTransfer[] toRow(int[] triples) {
    int size = triples.length / 3;
    if (size == 0) {
      return EMPTY_ARRAY;
    }
    var row = new DefaultRaptorTransfer[size];
    for (int i = 0; i < size; ++i) {
      int j = i * 3;
      row[i] = new DefaultRaptorTransfer(triples[j], triples[j + 1], triples[j + 2]);
    }
    RaptorTransfers.sortByDuration(row);
    return row;
  }

  private static AtomicReferenceArray<DefaultRaptorTransfer[]> copy(
    AtomicReferenceArray<DefaultRaptorTransfer[]> a
  ) {
    var copy = new AtomicReferenceArray<DefaultRaptorTransfer[]>(a.length());
    for (int i = 0; i < a.length(); ++i) {
      var row = a.getAcquire(i);
      if (row != null) {
        copy.setPlain(i, row);
      }
    }
    return copy;
  }

  /** Loads the rows of a stop. Implementations must be deterministic and thread-safe. */
  public interface RowLoader {
    /** The transfers from {@code fromStop}, as triples (toStop, duration, c1). */
    int[] loadFromRow(int fromStop);

    /**
     * The transfers to {@code toStop}, as triples (fromStop, duration, c1).
     *
     * @param fromRows the store itself, to derive the row from the from-rows
     */
    int[] loadToRow(int toStop, RaptorRegularTransferService fromRows);
  }

  private static final class RowIterator implements Iterator<RaptorTransfer> {

    private final DefaultRaptorTransfer[] a;
    private int index;

    RowIterator(DefaultRaptorTransfer[] a) {
      this.a = a;
      this.index = a.length == 0 ? 0 : -1;
    }

    @Override
    public boolean hasNext() {
      index += 1;
      return index < a.length;
    }

    @Override
    public RaptorTransfer next() {
      return a[index];
    }
  }
}
