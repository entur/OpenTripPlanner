package org.opentripplanner.raptor.rangeraptor.multicriteria;

import java.util.Arrays;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.McStopArrivals;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrival;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrivalFactory;
import org.opentripplanner.raptor.rangeraptor.multicriteria.heuristic.HeuristicsProvider;
import org.opentripplanner.raptor.rangeraptor.multicriteria.ride.AbstractPatternRide;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * A list of candidates for new stop arrivals, in the order they are added: either arrivals by
 * transit or arrivals by transfer. {@link #commit()} adds them to the stop arrivals.
 * <p>
 * Most candidates are rejected, so the arrival is not created when it is added: the list keeps
 * what the arrival is created from, and the values it is compared by. The values are those of the
 * arrival {@link #createArrival(int)} creates. The arrival is created when it is accepted, or when
 * a listener or the debugger needs a rejected arrival.
 * <p>
 * An arrival that exists already can be added as well; it keeps its place in the order.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
final class StopArrivalCandidates<T extends RaptorTripSchedule> {

  /** The values kept for each candidate: stop, arrival-time, round, c1 and c2. */
  private static final int N_VALUES = 5;

  /** In place of the round of a candidate: the candidate is an arrival that exists. */
  private static final int ARRIVAL_EXISTS = -1;

  private static final int INITIAL_CAPACITY = 256;

  private final McStopArrivalFactory<T> stopArrivalFactory;
  private final HeuristicsProvider<T> heuristics;
  private final McStopArrivals<T> arrivals;

  /** Creates the arrival of the candidate that is being committed. */
  private final Supplier<McStopArrival<T>> createCurrent = this::createCurrent;
  private int current;

  /**
   * What the arrival of a candidate is created from: the ride of an arrival by transit, or the
   * arrival that an arrival by transfer continues from. For an arrival that exists: the arrival.
   */
  private Object[] sources = new Object[INITIAL_CAPACITY];

  /** The transfer of each candidate, {@code null} if this is a list of arrivals by transit. */
  @Nullable
  private RaptorTransfer[] transfers;

  private int[] values = new int[INITIAL_CAPACITY * N_VALUES];
  private int size = 0;

  private StopArrivalCandidates(
    McStopArrivalFactory<T> stopArrivalFactory,
    HeuristicsProvider<T> heuristics,
    McStopArrivals<T> arrivals,
    boolean byTransfer
  ) {
    this.stopArrivalFactory = stopArrivalFactory;
    this.heuristics = heuristics;
    this.arrivals = arrivals;
    this.transfers = byTransfer ? new RaptorTransfer[INITIAL_CAPACITY] : null;
  }

  /**
   * A list of arrivals by transit, added with {@link #addTransitArrival}.
   *
   * @param heuristics rejects the candidates that can not reach the destination.
   * @param arrivals   the stop arrivals the candidates are committed to.
   */
  static <T extends RaptorTripSchedule> StopArrivalCandidates<T> ofTransitArrivals(
    McStopArrivalFactory<T> stopArrivalFactory,
    HeuristicsProvider<T> heuristics,
    McStopArrivals<T> arrivals
  ) {
    return new StopArrivalCandidates<>(stopArrivalFactory, heuristics, arrivals, false);
  }

  /**
   * A list of arrivals by transfer, added with {@link #addTransferArrival}.
   *
   * @param heuristics rejects the candidates that can not reach the destination.
   * @param arrivals   the stop arrivals the candidates are committed to.
   */
  static <T extends RaptorTripSchedule> StopArrivalCandidates<T> ofTransferArrivals(
    McStopArrivalFactory<T> stopArrivalFactory,
    HeuristicsProvider<T> heuristics,
    McStopArrivals<T> arrivals
  ) {
    return new StopArrivalCandidates<>(stopArrivalFactory, heuristics, arrivals, true);
  }

  /**
   * Add the arrival of the given ride at a stop. The arguments are those of
   * {@link McStopArrivalFactory#createTransitStopArrival}.
   */
  void addTransitArrival(AbstractPatternRide<T> ride, int alightStop, int stopArrivalTime, int c1) {
    final int index = nextIndex();
    final int[] v = values;
    final int i = index * N_VALUES;
    sources[index] = ride;
    v[i] = alightStop;
    v[i + 1] = stopArrivalTime;
    v[i + 2] = ride.prevArrival().round() + 1;
    v[i + 3] = c1;
    v[i + 4] = stopArrivalFactory.transitStopArrivalC2(ride);
  }

  /**
   * Add the arrival by the given transfer from an arrival. The arguments are those of
   * {@link McStopArrivalFactory#createTransferStopArrival}.
   */
  void addTransferArrival(McStopArrival<T> previous, RaptorTransfer transfer, int arrivalTime) {
    final int index = nextIndex();
    final int[] v = values;
    final int i = index * N_VALUES;
    sources[index] = previous;
    transfers[index] = transfer;
    v[i] = transfer.stop();
    v[i + 1] = arrivalTime;
    v[i + 2] = previous.round();
    v[i + 3] = previous.c1() + transfer.c1();
    v[i + 4] = previous.c2();
  }

  /** Add an arrival that exists. Its values are not kept, the arrival has them. */
  void addArrival(McStopArrival<T> arrival) {
    int index = nextIndex();
    sources[index] = arrival;
    values[index * N_VALUES + 2] = ARRIVAL_EXISTS;
  }

  int size() {
    return size;
  }

  /**
   * Add the candidates to the stop arrivals, in the order they were added to this list, and clear
   * the list. A candidate is added as an arrival is: unless the heuristics reject it, the pareto
   * set of its stop accepts or rejects it.
   */
  @SuppressWarnings("unchecked")
  void commit() {
    final boolean arrivedOnBoard = arrivedOnBoard();

    for (int index = 0; index < size; ++index) {
      final int[] v = values;
      final int i = index * N_VALUES;
      final int round = v[i + 2];

      if (round == ARRIVAL_EXISTS) {
        var arrival = (McStopArrival<T>) sources[index];
        if (!heuristics.rejectDestinationArrivalBasedOnHeuristic(arrival)) {
          arrivals.addStopArrival(arrival);
        }
        continue;
      }
      final int stop = v[i];
      final int arrivalTime = v[i + 1];
      final int c1 = v[i + 3];
      final int c2 = v[i + 4];
      current = index;

      if (
        !heuristics.rejectDestinationArrivalBasedOnHeuristic(
          stop,
          arrivalTime,
          c1,
          c2,
          createCurrent
        )
      ) {
        arrivals.addStopArrival(stop, arrivalTime, round, c1, c2, arrivedOnBoard, createCurrent);
      }
    }
    clear();
  }

  void clear() {
    Arrays.fill(sources, 0, size, null);
    if (transfers != null) {
      Arrays.fill(transfers, 0, size, null);
    }
    size = 0;
  }

  /**
   * The arrival at the given index if it was added as an arrival, {@code null} if it is not
   * created yet.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  McStopArrival<T> arrival(int index) {
    return round(index) == ARRIVAL_EXISTS ? (McStopArrival<T>) sources[index] : null;
  }

  int stop(int index) {
    return values[index * N_VALUES];
  }

  int arrivalTime(int index) {
    return values[index * N_VALUES + 1];
  }

  int round(int index) {
    return values[index * N_VALUES + 2];
  }

  int c1(int index) {
    return values[index * N_VALUES + 3];
  }

  int c2(int index) {
    return values[index * N_VALUES + 4];
  }

  /** An arrival by transit arrives on-board, an arrival by transfer does not. */
  boolean arrivedOnBoard() {
    return transfers == null;
  }

  /**
   * Create the arrival by transit or by transfer at the given index.
   */
  @SuppressWarnings("unchecked")
  McStopArrival<T> createArrival(int index) {
    if (transfers == null) {
      return stopArrivalFactory.createTransitStopArrival(
        (AbstractPatternRide<T>) sources[index],
        stop(index),
        arrivalTime(index),
        c1(index)
      );
    }
    return stopArrivalFactory.createTransferStopArrival(
      (McStopArrival<T>) sources[index],
      transfers[index],
      arrivalTime(index)
    );
  }

  private McStopArrival<T> createCurrent() {
    return createArrival(current);
  }

  private int nextIndex() {
    if (size == sources.length) {
      sources = Arrays.copyOf(sources, size * 2);
      values = Arrays.copyOf(values, values.length * 2);
      if (transfers != null) {
        transfers = Arrays.copyOf(transfers, size * 2);
      }
    }
    return size++;
  }
}
