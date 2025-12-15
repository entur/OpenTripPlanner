package org.opentripplanner.routing.api.request.preference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.routing.api.request.preference.ImmutablePreferencesAsserts.assertEqualsAndHashCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ElevatorPreferencesTest {

  public static final int BOARD_COST = 100;
  public static final Duration BOARD_TIME = Duration.ofSeconds(60);
  public static final Duration HOP_TIME = Duration.ofSeconds(120);
  public static final double RELUCTANCE = 2.5;
  private final ElevatorPreferences subject = ElevatorPreferences.of()
    .withBoardCost(BOARD_COST)
    .withBoardTime(BOARD_TIME)
    .withHopTime(HOP_TIME)
    .withReluctance(RELUCTANCE)
    .build();

  @Test
  void boardCost() {
    assertEquals(BOARD_COST, subject.boardCost());
  }

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
    assertSame(ElevatorPreferences.DEFAULT, ElevatorPreferences.of().build());
    assertSame(subject, subject.copyOf().build());

    // Create a copy, make a change and set it back again to force creating a new object
    var other = subject.copyOf().withBoardTime(Duration.ofSeconds(123)).build();
    var same = other.copyOf().withBoardTime(BOARD_TIME).build();
    assertEqualsAndHashCode(subject, other, same);
  }

  @Test
  void testToString() {
    assertEquals("ElevatorPreferences{}", ElevatorPreferences.DEFAULT.toString());
    assertEquals(
      "ElevatorPreferences{boardCost: $100, boardTime: 1m, hopTime: 2m, reluctance: 2.5}",
      subject.toString()
    );
  }
}
