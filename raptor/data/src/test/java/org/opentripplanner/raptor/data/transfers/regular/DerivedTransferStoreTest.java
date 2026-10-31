package org.opentripplanner.raptor.data.transfers.regular;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor.spi.RaptorTransfer;

class DerivedTransferStoreTest {

  private static final int N_STOPS = 40;

  /** payload = [duration at speed 1, c1 per second], the duration is divided by the speed. */
  private static TransferValueFunction function(double speed) {
    return (p, off, len) ->
      TransferValueFunction.pack(
        (int) Math.round(p[off] / speed),
        (int) (p[off] / speed) * (int) p[off + 1]
      );
  }

  private static Map<Integer, Map<Integer, float[]>> randomTransfers(Random random) {
    var result = new HashMap<Integer, Map<Integer, float[]>>();
    for (int from = 0; from < N_STOPS; ++from) {
      var row = new HashMap<Integer, float[]>();
      int n = random.nextInt(8);
      for (int i = 0; i < n; ++i) {
        int to = random.nextInt(N_STOPS);
        if (to != from) {
          row.put(to, new float[] { 1 + random.nextInt(600), 1 + random.nextInt(3) });
        }
      }
      result.put(from, row);
    }
    return result;
  }

  private static DerivedTransferTable table(Map<Integer, Map<Integer, float[]>> transfers) {
    var builder = DerivedTransferTable.of(N_STOPS);
    transfers.forEach((from, row) ->
      row.forEach((to, payload) -> builder.addTransfer(from, to, payload))
    );
    return builder.build();
  }

  private static List<String> rows(RaptorRegularTransferService service, boolean from) {
    var result = new ArrayList<String>();
    for (int stop = 0; stop < N_STOPS; ++stop) {
      var it = from ? service.getTransfersFromStop(stop) : service.getTransfersToStop(stop);
      var row = new StringBuilder(stop + ":");
      int last = -1;
      while (it.hasNext()) {
        RaptorTransfer t = it.next();
        assertThat(t.durationInSeconds()).isAtLeast(last);
        last = t.durationInSeconds();
        row
          .append(" ")
          .append(t.stop())
          .append("/")
          .append(t.durationInSeconds())
          .append("/")
          .append(t.c1());
      }
      result.add(row.toString());
    }
    return result;
  }

  @Test
  void theRowsAreSortedByDurationForEverySpeedAndTheSameAsTheExactStore() {
    var transfers = randomTransfers(new Random(1));
    var table = table(transfers);
    for (double speed : new double[] { 0.5, 1.0, 1.7 }) {
      var function = function(speed);
      var exact = RaptorTransferStore.of(N_STOPS);
      transfers.forEach((from, row) ->
        row.forEach((to, p) -> {
          long v = function.evaluate(p, 0, p.length);
          exact.addTransfer(
            from,
            to,
            TransferValueFunction.durationInSeconds(v),
            TransferValueFunction.c1(v)
          );
        })
      );
      var expected = exact.build();
      for (var mode : DerivedTransferStore.Mode.values()) {
        var store = DerivedTransferStore.of(table, function, mode);
        // The order of equal durations is not defined
        assertThat(sortedRows(store, true)).containsExactlyElementsIn(sortedRows(expected, true));
        assertThat(sortedRows(store, false)).containsExactlyElementsIn(sortedRows(expected, false));
        rows(store, true);
        rows(store, false);
      }
    }
  }

  private static List<String> sortedRows(RaptorRegularTransferService service, boolean from) {
    var result = new ArrayList<String>();
    for (int stop = 0; stop < N_STOPS; ++stop) {
      var it = from ? service.getTransfersFromStop(stop) : service.getTransfersToStop(stop);
      var row = new ArrayList<String>();
      while (it.hasNext()) {
        var t = it.next();
        row.add(t.stop() + "/" + t.durationInSeconds() + "/" + t.c1());
      }
      row.sort(String::compareTo);
      result.add(stop + ":" + row);
    }
    return result;
  }

  @Test
  void aPatchedStoreEqualsAStoreOfTheNewTable() {
    var random = new Random(2);
    for (int round = 0; round < 20; ++round) {
      var transfers = randomTransfers(random);
      var function = function(1.3);
      for (var mode : DerivedTransferStore.Mode.values()) {
        var table = table(transfers);
        var store = DerivedTransferStore.of(table, function, mode);
        // Read some rows, so there is something to invalidate
        rows(store, true);

        var changed = new HashSet<Integer>();
        var patch = table.patch();
        var next = new HashMap<>(transfers);
        for (int i = 0; i < 6; ++i) {
          int from = random.nextInt(N_STOPS);
          changed.add(from);
        }
        var replacement = randomTransfers(random);
        for (int from : changed) {
          patch.replaceTransfersFrom(from);
          next.put(from, replacement.get(from));
          replacement.get(from).forEach((to, p) -> patch.addTransfer(from, to, p));
        }
        var patchedTable = patch.build();
        var patched = store.patch(patchedTable, function, changed);
        var rebuilt = DerivedTransferStore.of(table(next), function, mode);

        assertThat(sortedRows(patched, true)).containsExactlyElementsIn(sortedRows(rebuilt, true));
        assertThat(sortedRows(patched, false)).containsExactlyElementsIn(
          sortedRows(rebuilt, false)
        );
        // The patch does not change the store it was made from
        assertThat(sortedRows(store, true)).containsExactlyElementsIn(
          sortedRows(DerivedTransferStore.of(table(transfers), function, mode), true)
        );
      }
    }
  }

  @Test
  void aReplacedStopWithoutTransfersHasNone() {
    var table = DerivedTransferTable.of(3)
      .addTransfer(0, 1, new float[] { 10, 1 })
      .build();
    var patched = table.patch().replaceTransfersFrom(0).build();
    var store = DerivedTransferStore.of(patched, function(1.0), DerivedTransferStore.Mode.LAZY);
    assertThat(store.getTransfersFromStop(0).hasNext()).isFalse();
    assertThat(store.getTransfersToStop(1).hasNext()).isFalse();
    assertThat(Set.of(patched.size())).containsExactly(0);
  }

  @Test
  void aTransferWithoutValueIsLeftOutOfFromAndToRowsInAllModes() {
    // payload[0] < 0: the transfer does not exist under these preferences
    TransferValueFunction function = (p, off, len) ->
      p[off] < 0 ? TransferValueFunction.NONE : TransferValueFunction.pack((int) p[off], 100);
    var table = DerivedTransferTable.of(N_STOPS)
      .addTransfer(0, 1, new float[] { 30 })
      .addTransfer(0, 2, new float[] { -1 })
      .addTransfer(0, 3, new float[] { 10 })
      .addTransfer(1, 2, new float[] { -1 })
      .build();
    for (var mode : DerivedTransferStore.Mode.values()) {
      var store = DerivedTransferStore.of(table, function, mode);
      assertThat(sortedRows(store, true).subList(0, 2)).containsExactly(
        "0:[1/30/100, 3/10/100]",
        "1:[]"
      );
      assertThat(rows(store, true).get(0)).isEqualTo("0: 3/10/100 1/30/100");
      assertThat(rows(store, false).get(2)).isEqualTo("2:");
      assertThat(rows(store, false).get(3)).isEqualTo("3: 0/10/100");
    }
  }
}
