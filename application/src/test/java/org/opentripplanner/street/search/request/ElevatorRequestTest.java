package org.opentripplanner.street.search.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.street.search.request.ImmutableRequestAsserts.assertEqualsAndHashCode;

import org.junit.jupiter.api.Test;

class ElevatorRequestTest {

  public static final int BOARD_TIME = 60;
  public static final int HOP_TIME = 120;
  public static final double RELUCTANCE = 2.5;
  private final ElevatorRequest subject = ElevatorRequest.of()
    .withBoardTime(BOARD_TIME)
    .withHopTime(HOP_TIME)
    .withReluctance(RELUCTANCE)
    .build();

  @Test
  void boardTime() {
    assertEquals(BOARD_TIME, subject.boardTime());
  }

  @Test
  void hopTime() {
    assertEquals(HOP_TIME, subject.hopTime());
  }

  @Test
  void reluctance() {
    assertEquals(RELUCTANCE, subject.reluctance());
  }

  @Test
  void testEqualsAndHashCode() {
    // Return same object if no value is set
    assertSame(ElevatorRequest.DEFAULT, ElevatorRequest.of().build());
    assertSame(subject, subject.copyOf().build());

    // Create a copy, make a change and set it back again to force creating a new object
    var other = subject.copyOf().withBoardTime(123).build();
    var same = other.copyOf().withBoardTime(BOARD_TIME).build();
    assertEqualsAndHashCode(subject, other, same);
  }

  @Test
  void testToString() {
    assertEquals("ElevatorRequest{}", ElevatorRequest.DEFAULT.toString());
    assertEquals(
      "ElevatorRequest{boardTime: 1m, hopTime: 2m, reluctance: 2.5}",
      subject.toString()
    );
  }
}
