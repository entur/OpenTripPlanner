package org.opentripplanner.street.geometry;

import java.io.ByteArrayOutputStream;

/**
 * Variable-length integer encoding. This optimize integer storage when most of the values are
 * small, but few of them can be quite large (as in a geometry). Adapted Dlugosz scheme to support
 * signed int whose average are around 0.
 * <p>
 * See Dlugosz' variable-length integer encoding (http://www.dlugosz.com/ZIP2/VLI.html).
 *
 * @author laurent
 */
public class DlugoszVarLenIntPacker {

  public static byte[] pack(int[] arr) {
    if (arr == null) {
      return null;
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream(arr.length);
    for (int i : arr) {
      if (i >= -64 && i <= 63) {
        // 0xxx xxxx -> 7 bits value
        // i+64 between 0 and 127, 7 bits
        int ui = i + 64;
        baos.write(ui);
      } else if (i >= -8192 && i <= 8191) {
        // 10xx xxxx + 8 -> 14 bits value
        // i+8192 between 0 and 16383
        int ui = i + 8192;
        // 6b MSB
        baos.write(0x80 | (ui >> 8));
        // 8b LSB
        baos.write(ui & 0xFF);
      } else if (i >= -1048576 && i <= 1048575) {
        // 110 xxxx + 2x8 -> 21 bits value
        // i + 1048576 between 0 and 2097151
        int ui = i + 1048576;
        // 5b MSB
        baos.write(0xC0 | (ui >> 16));
        // 8b
        baos.write((ui >> 8) & 0xFF);
        // 8b
        baos.write(ui & 0xFF);
      } else if (i >= -67108864 && i <= 67108863) {
        // 1110 0xxx + 3x8 -> 27 bits value
        // i + 67108864 between 0 and 134217727
        int ui = i + 67108864;
        // 3b MSB
        baos.write(0xE0 | (ui >> 24));
        // 8b
        baos.write((ui >> 16) & 0xFF);
        // 8b
        baos.write((ui >> 8) & 0xFF);
        // 8b
        baos.write(ui & 0xFF);
      } else {
        // int can't have more than 32 bits
        // 1110 1xxx + 4x8 -> 35 bits value
        // i + 0x80000000 fits in 35 bits for sure
        long ui = (long) i + 2147483648L;
        // 3b MSB
        baos.write((int) (0xE8 | (ui >> 32)));
        // 8b
        baos.write((int) ((ui >> 24) & 0xFF));
        // 8b
        baos.write((int) ((ui >> 16) & 0xFF));
        // 8b
        baos.write((int) ((ui >> 8) & 0xFF));
        // 8b
        baos.write((int) (ui & 0xFF));
      }
    }
    return baos.toByteArray();
  }

  public static int[] unpack(byte[] arr) {
    if (arr == null) {
      return null;
    }
    int[] out = new int[countValues(arr)];
    int pos = 0;
    int idx = 0;
    while (pos < arr.length) {
      var decoded = decodeAt(arr, pos);
      out[idx++] = decoded.value();
      pos = decoded.nextPos();
    }
    return out;
  }

  /**
   * Number of ints encoded in {@code arr} without decoding their values. Walks the leading tag of
   * each varint to skip its body. Used by allocation-free decoders to size their target buffers.
   */
  public static int countValues(byte[] arr) {
    if (arr == null) {
      return 0;
    }
    int n = 0;
    int pos = 0;
    while (pos < arr.length) {
      int v1 = arr[pos] & 0xFF;
      if ((v1 & 0x80) == 0x00) {
        pos += 1;
      } else if ((v1 & 0xC0) == 0x80) {
        pos += 2;
      } else if ((v1 & 0xE0) == 0xC0) {
        pos += 3;
      } else if ((v1 & 0xF8) == 0xE0) {
        pos += 4;
      } else {
        pos += 5;
      }
      n++;
    }
    return n;
  }

  /**
   * Decoded varint together with the next read position in the packed array. The record is sized
   * (two ints) and trivially constructed so that escape analysis can scalar-replace the allocation
   * inside hot decode loops — verified empirically with a JIT-allocation microbenchmark to produce
   * zero heap traffic in steady state.
   */
  record DecodedVarint(int value, int nextPos) {}

  /**
   * Decode the varint at {@code pos} in {@code arr}, returning the decoded value and the next read
   * position. Callers should destructure the result into local ints immediately so that escape
   * analysis can eliminate the {@link DecodedVarint} allocation.
   */
  static DecodedVarint decodeAt(byte[] arr, int pos) {
    int v1 = arr[pos] & 0xFF;
    int sv;
    int nextPos;
    if ((v1 & 0x80) == 0x00) {
      // 0xxx xxxx -> 7 bits value
      sv = (v1 & 0x7F) - 64;
      nextPos = pos + 1;
    } else if ((v1 & 0xC0) == 0x80) {
      // 10xx xxxx + 8 -> 14 bits value
      sv = ((v1 & 0x3F) << 8) + (arr[pos + 1] & 0xFF) - 8192;
      nextPos = pos + 2;
    } else if ((v1 & 0xE0) == 0xC0) {
      // 110 xxxx + 2x8 -> 21 bits value
      sv = ((v1 & 0x1F) << 16) + ((arr[pos + 1] & 0xFF) << 8) + (arr[pos + 2] & 0xFF) - 1048576;
      nextPos = pos + 3;
    } else if ((v1 & 0xF8) == 0xE0) {
      // 1110 0xxx + 3x8 -> 27 bits value
      sv =
        ((v1 & 0x1F) << 24) +
        ((arr[pos + 1] & 0xFF) << 16) +
        ((arr[pos + 2] & 0xFF) << 8) +
        (arr[pos + 3] & 0xFF) -
        67108864;
      nextPos = pos + 4;
    } else {
      // 1110 1xxx + 4x8 -> 35 bits value
      long lsv =
        (((long) v1 & 0x1F) << 32) +
        ((arr[pos + 1] & 0xFF) << 24) +
        ((arr[pos + 2] & 0xFF) << 16) +
        ((arr[pos + 3] & 0xFF) << 8) +
        (arr[pos + 4] & 0xFF) -
        2147483648L;
      sv = (int) lsv;
      nextPos = pos + 5;
    }
    return new DecodedVarint(sv, nextPos);
  }
}
