package org.opentripplanner.perfrig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.street.model.vertex.OsmVertex;
import org.opentripplanner.street.model.vertex.SplitterVertex;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;

class DumpTest {

  private static final GeometryFactory GF = new GeometryFactory();

  public static class NoToString {

    public int getCount() {
      return 2;
    }

    public String getName() {
      return "n";
    }
  }

  @Test
  void simpleValues() {
    assertEquals("-", Dump.str(null));
    assertEquals("Optional[x]", Dump.str(Optional.of("x")));
    assertEquals("Optional.empty", Dump.str(Optional.empty()));
    assertEquals("[1, -]", Dump.str(java.util.Arrays.asList(1, null)));
    assertEquals("1.0000000000000002", Dump.str(1.0000000000000002));
  }

  @Test
  void objectWithoutToStringIsWrittenWithItsGetters() {
    assertEquals("NoToString{getCount=2 getName=n}", Dump.str(new NoToString()));
    assertEquals("[NoToString{getCount=2 getName=n}]", Dump.str(List.of(new NoToString())));
  }

  @Test
  void identityHashesAreRemoved() {
    var value = new Object() {
      @Override
      public String toString() {
        return "Leg{use=StairsUse@6d8fd852, n=1}";
      }
    };
    assertEquals("Leg{use=StairsUse@?, n=1}", Dump.str(value));
  }

  @Test
  void exceptionIsPartOfTheValue() {
    assertEquals(
      "!IllegalStateException",
      Dump.v(() -> {
        throw new IllegalStateException();
      })
    );
  }

  @Test
  void geometryHashChangesWithTheSmallestChangeOfACoordinate() {
    var a = line(10.0, 59.0, 10.1, 59.1);
    assertEquals(Dump.geometry(a), Dump.geometry(line(10.0, 59.0, 10.1, 59.1)));
    assertNotEquals(Dump.geometry(a), Dump.geometry(line(10.0, 59.0, 10.1, Math.nextUp(59.1))));
    assertTrue(Dump.geometry(a).startsWith("2pts#"));
    assertEquals("-", Dump.geometry(null));
  }

  @Test
  void hashIsStable() {
    assertEquals(Dump.hash("abc"), Dump.hash("abc"));
    assertNotEquals(Dump.hash("abc"), Dump.hash("abd"));
  }

  @Test
  void permanentVerticesKeepTheirLabel() {
    assertEquals("osm:node:123", Dump.vertex(new OsmVertex(10.0, 59.0, 123)));
  }

  @Test
  void splitAndTemporaryVerticesAreLabeledByCoordinate() {
    var name = NonLocalizedString.ofNullable("n");
    var split7 = new SplitterVertex("split_7", 10.0, 59.0, name);
    var split8 = new SplitterVertex("split_8", 10.0, 59.0, name);
    var elsewhere = new SplitterVertex("split_7", 10.0, Math.nextUp(59.0), name);
    assertEquals(Dump.vertex(split7), Dump.vertex(split8));
    assertNotEquals(Dump.vertex(split7), Dump.vertex(elsewhere));

    var temp1 = new TemporaryStreetLocation(new Coordinate(10.0, 59.0), name);
    var temp2 = new TemporaryStreetLocation(new Coordinate(10.0, 59.0), name);
    assertNotEquals(temp1.getLabelString(), temp2.getLabelString());
    assertEquals(Dump.vertex(temp1), Dump.vertex(temp2));
  }

  @Test
  void dumpIndents() {
    var out = new Dump().line("a").indent().line("b").outdent().line("c");
    assertEquals("a\n  b\nc\n", out.toString());
  }

  private static LineString line(double x1, double y1, double x2, double y2) {
    return GF.createLineString(new Coordinate[] { new Coordinate(x1, y1), new Coordinate(x2, y2) });
  }
}
