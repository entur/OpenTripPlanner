package org.opentripplanner.raptor.data.transfers.regular;

/**
 * Evaluates the duration and the c1 cost of a transfer from its payload, the numbers stored per
 * transfer in a {@link DerivedTransferTable}, under the user preferences of the function. The
 * result is packed in a {@code long} so an evaluation does not allocate. A function is immutable
 * and thread-safe.
 */
@FunctionalInterface
public interface TransferValueFunction {
  /**
   * The result for a transfer that does not exist under the user preferences of the function, for
   * example a path that can not be traversed. The store leaves it out of the rows.
   */
  long NONE = pack(Integer.MIN_VALUE, 0);

  /**
   * @return the duration in seconds and the c1 cost of the transfer, see {@link #pack}, or
   *         {@link #NONE}
   */
  long evaluate(float[] payload, int offset, int length);

  static long pack(int durationInSeconds, int c1) {
    return ((long) c1 << 32) | (durationInSeconds & 0xFFFFFFFFL);
  }

  static int durationInSeconds(long packed) {
    return (int) packed;
  }

  static int c1(long packed) {
    return (int) (packed >>> 32);
  }
}
