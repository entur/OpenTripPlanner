package org.opentripplanner.raptor.api.request.via;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestTransfer;

class RaptorViaLocationTest implements RaptorTestConstants {

  private static final Duration MINIMUM_WAIT_TIME = Duration.ofSeconds(23);
  private static final String VIA_LABEL = "Via";
  private static final String PASS_THROUGH_LABEL = "PassThrough";
  private static final int TX_C1 = 3000;
  private static final int TX_DURATION = D30_s;
  private static final TestTransfer TRANSFER = TestTransfer.transfer(STOP_C, TX_DURATION, TX_C1);

  private final RaptorViaLocation subject = RaptorViaLocation.viaVisit(VIA_LABEL, MINIMUM_WAIT_TIME)
    .addTransfer(STOP_B, TRANSFER)
    .addStop(STOP_A)
    .build();

  private final RaptorViaLocation subjectPassThrough = RaptorViaLocation.passThrough(
    PASS_THROUGH_LABEL
  )
    .addStop(STOP_D)
    .build();

  private final RaptorTransferViaConnection transferConnection = subject
    .connections()
    .stream()
    .filter(it -> it instanceof RaptorTransferViaConnection)
    .findFirst()
    .map(it -> (RaptorTransferViaConnection) it)
    .orElseThrow();

  private final RaptorVisitStopViaConnection stopConnection = subject
    .connections()
    .stream()
    .filter(it -> it instanceof RaptorVisitStopViaConnection)
    .findFirst()
    .map(it -> (RaptorVisitStopViaConnection) it)
    .orElseThrow();

  private final RaptorPassThroughViaConnection passThroughStopConnection = subjectPassThrough
    .connections()
    .stream()
    .findFirst()
    .map(it -> (RaptorPassThroughViaConnection) it)
    .orElseThrow();

  @Test
  void connections() {
    var connections = subject.connections();
    assertTrue(connections.contains(transferConnection), connections.toString());
    assertTrue(connections.contains(stopConnection), connections.toString());
  }

  @Test
  void atLeastOneConnectionMustExist() {
    var ex = assertThrows(IllegalArgumentException.class, () ->
      RaptorViaLocation.viaVisit(VIA_LABEL, MINIMUM_WAIT_TIME).build()
    );
    assertEquals("At least on connection must exist!", ex.getMessage());
  }

  @Test
  void at() {
    var subject = RaptorViaLocation.passThrough(VIA_LABEL).addStop(STOP_A, STOP_A).build();
    var ex = assertThrows(IllegalArgumentException.class, () ->
      subject.validateDuplicateConnections()
    );
    assertEquals(
      "All connection need to be pareto-optimal: ((stop 1)) <-> ((stop 1))",
      ex.getMessage()
    );
  }

  @Test
  void testToString() {
    assertEquals(
      "RaptorViaLocation{via-visit Via : [(transfer 2 ~ 3 53s C₁30), (stop 1 23s)]}",
      subject.toString()
    );
    assertEquals(
      "RaptorViaLocation{pass-through PassThrough : [(stop 4)]}",
      subjectPassThrough.toString()
    );

    assertEquals(
      "RaptorViaLocation{via-visit Via : [(transfer B ~ C 53s C₁30), (stop A 23s)]}",
      subject.toString(RaptorTestConstants::stopIndexToName)
    );
  }

  @Test
  void label() {
    assertEquals(VIA_LABEL, subject.label());
    assertEquals(PASS_THROUGH_LABEL, subjectPassThrough.label());
  }

  @Test
  void fromStop() {
    assertEquals(STOP_A, stopConnection.fromStop());
    assertEquals(STOP_B, transferConnection.fromStop());
    assertEquals(STOP_D, passThroughStopConnection.fromStop());
  }

  @Test
  void transfer() {
    assertEquals(TRANSFER, transferConnection.transfer());
  }

  @Test
  void toStop() {
    assertEquals(STOP_C, transferConnection.toStop());
  }

  @Test
  void durationInSeconds() {
    assertEquals(
      MINIMUM_WAIT_TIME.plusSeconds(TRANSFER.durationInSeconds()).toSeconds(),
      transferConnection.durationInSeconds()
    );
  }

  @Test
  void minimumWaitTime() {
    assertEquals(MINIMUM_WAIT_TIME.toSeconds(), stopConnection.minimumWaitTime());
  }

  @Test
  void c1() {
    assertEquals(TX_C1, transferConnection.c1());
  }

  static List<Arguments> isBetterThanViaTransferTestCases() {
    // Subject is: STOP_A, STOP_B, MIN_DURATION, C1
    // Candidate is:
    return List.of(
      Arguments.of(STOP_A, STOP_B, TX_DURATION, TX_C1, true, true, "Same values"),
      Arguments.of(STOP_C, STOP_B, TX_DURATION, TX_C1, false, false, "toStop differ"),
      Arguments.of(STOP_A, STOP_C, TX_DURATION, TX_C1, false, false, "fromStop differ"),
      Arguments.of(STOP_A, STOP_B, TX_DURATION + 1, TX_C1, true, false, "Duration is better"),
      Arguments.of(STOP_A, STOP_B, TX_DURATION - 1, TX_C1, false, false, "Duration is worse"),
      Arguments.of(STOP_A, STOP_B, TX_DURATION, TX_C1 + 1, true, false, "C1 is better"),
      Arguments.of(STOP_A, STOP_B, TX_DURATION, TX_C1 - 1, false, false, "C1 is worse")
    );
  }

  @ParameterizedTest
  @MethodSource("isBetterThanViaTransferTestCases")
  void isBetterThanViaTransfer(
    int fromStop,
    int toStop,
    int minWaitTime,
    int c1,
    boolean expectedIsBetter,
    boolean expectedEquals,
    String description
  ) {
    var subject = RaptorViaLocation.viaVisit("Subject")
      .addTransfer(STOP_A, new TestTransfer(STOP_B, TX_DURATION, TX_C1))
      .build()
      .connections()
      .getFirst();

    var candidate = RaptorViaLocation.viaVisit("Candidate")
      .addTransfer(fromStop, new TestTransfer(toStop, minWaitTime, c1))
      .build()
      .connections()
      .getFirst();

    assertEquals(subject.isBetterOrEqual(candidate), expectedIsBetter, description);
    assertEquals(subject.equals(candidate), expectedEquals);

    if (expectedEquals) {
      assertEquals(subject.hashCode(), candidate.hashCode());
    } else {
      assertNotEquals(subject.hashCode(), candidate.hashCode());
    }
  }

  static List<Arguments> isBetterThanViaStopVisitTestCases() {
    // Subject is: STOP_A, STOP_B, MIN_DURATION, C1
    // Candidate is:
    return List.of(
      Arguments.of(STOP_A, MINIMUM_WAIT_TIME, true, true, "Same values"),
      Arguments.of(STOP_C, MINIMUM_WAIT_TIME, false, false, "fromStop differ"),
      Arguments.of(STOP_A, MINIMUM_WAIT_TIME.plusSeconds(1), true, false, "Duration is better"),
      Arguments.of(STOP_A, MINIMUM_WAIT_TIME.minusSeconds(1), false, false, "Duration is worse")
    );
  }

  @ParameterizedTest
  @MethodSource("isBetterThanViaStopVisitTestCases")
  void isBetterThanViaStopVisit(
    int fromStop,
    Duration minWaitTime,
    boolean expectedIsBetter,
    boolean expectedEquals,
    String description
  ) {
    var subject = RaptorViaLocation.viaVisit("Subject", MINIMUM_WAIT_TIME)
      .addStop(STOP_A)
      .build()
      .connections()
      .getFirst();

    var candidate = RaptorViaLocation.viaVisit("Candidate", minWaitTime)
      .addStop(fromStop)
      .build()
      .connections()
      .getFirst();

    assertEquals(subject.isBetterOrEqual(candidate), expectedIsBetter, description);
    assertEquals(subject.equals(candidate), expectedEquals);

    if (expectedEquals) {
      assertEquals(subject.hashCode(), candidate.hashCode());
    } else {
      assertNotEquals(subject.hashCode(), candidate.hashCode());
    }
  }

  @Test
  void passThroughIsBetterEqualsAndHashCode() {
    var subject = RaptorViaLocation.passThrough("Subject")
      .addStop(STOP_A)
      .build()
      .connections()
      .getFirst();

    var same = RaptorViaLocation.passThrough("Same")
      .addStop(STOP_A)
      .build()
      .connections()
      .getFirst();

    var diffrent = RaptorViaLocation.viaVisit("Diffrent")
      .addStop(STOP_B)
      .build()
      .connections()
      .getFirst();

    assertTrue(subject.isBetterOrEqual(subject));
    assertTrue(subject.isBetterOrEqual(same));
    assertFalse(subject.isBetterOrEqual(diffrent));

    assertTrue(subject.equals(subject));
    assertTrue(subject.equals(same));
    assertFalse(subject.equals(diffrent));

    assertEquals(subject.hashCode(), same.hashCode());
    assertNotEquals(subject.hashCode(), diffrent.hashCode());
  }

  @Test
  void asBitSet() {
    var subject = RaptorViaLocation.passThrough(VIA_LABEL)
      .addStop(2)
      .addStop(7)
      .addStop(13)
      .build();

    var bitSet = subject.asBitSet();

    // Sample some all set values as well as some not set values
    assertFalse(bitSet.get(0));
    assertTrue(bitSet.get(2));
    assertFalse(bitSet.get(3));
    assertFalse(bitSet.get(6));
    assertTrue(bitSet.get(7));
    assertTrue(bitSet.get(13));
    assertFalse(bitSet.get(15000000));
  }

  @Test
  void testEqualsAndHAshCode() {
    var viaTxConnection = RaptorViaLocation.viaVisit("SameAsVia", MINIMUM_WAIT_TIME)
      .addTransfer(STOP_B, TRANSFER)
      .build();
    var viaStopConnections = RaptorViaLocation.viaVisit("SameAsVia", MINIMUM_WAIT_TIME)
      .addStop(STOP_A)
      .build();

    var sameTransferConnection = viaTxConnection.connections().get(0);
    var sameStopConnection = viaStopConnections.connections().get(0);

    // Equals
    assertEquals(sameTransferConnection, transferConnection);
    assertEquals(sameStopConnection, stopConnection);
    assertNotEquals(sameStopConnection, transferConnection);

    // Hash code
    assertEquals(sameTransferConnection.hashCode(), transferConnection.hashCode());
    assertEquals(sameStopConnection.hashCode(), stopConnection.hashCode());
    assertNotEquals(sameStopConnection.hashCode(), transferConnection.hashCode());
  }
}
