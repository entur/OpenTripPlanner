package org.opentripplanner.perfrig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgsAndQueryTest {

  @Test
  void valuesAndFlags() {
    var args = new Args(new String[] { "record", "--out", "dir", "--repeat", "--threads", "8" }, 1);
    assertEquals("dir", args.get("out"));
    assertTrue(args.flag("repeat"));
    assertFalse(args.flag("other"));
    assertEquals(8, args.getInt("threads", 1));
    assertEquals(3, args.getInt("warmup", 3));
    assertNull(args.fileOrNull("graph"));
    assertThrows(IllegalArgumentException.class, () -> args.get("cases"));
  }

  @Test
  void argumentWithoutDashesIsAnError() {
    assertThrows(IllegalArgumentException.class, () -> new Args(new String[] { "x", "y" }, 1));
  }

  /** A misspelled variable would silently use the default of the argument. */
  @Test
  void unknownTripQueryVariableIsAnError() {
    var query = new TripQuery(null);
    assertThrows(IllegalArgumentException.class, () -> query.execute(Map.of("walkspeed", 1.0), null));
  }

  @Test
  void everyVariableIsDeclaredAndUsedInTheQuery() {
    for (String name : TripQuery.VARIABLES) {
      assertTrue(TripQuery.QUERY.contains("$" + name + ":"), name + " is not declared");
      assertTrue(TripQuery.QUERY.contains(name + ": $" + name), name + " is not used");
    }
  }
}
