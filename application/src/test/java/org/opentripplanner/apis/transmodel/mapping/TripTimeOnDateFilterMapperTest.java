package org.opentripplanner.apis.transmodel.mapping;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.apis.transmodel._support.RequestHelper.list;
import static org.opentripplanner.apis.transmodel._support.RequestHelper.map;
import static org.opentripplanner.transit.model.basic.TransitMode.BUS;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.api.model.transit.DefaultFeedIdMapper;

class TripTimeOnDateFilterMapperTest {

  private static final TripTimeOnDateFilterMapper MAPPER = new TripTimeOnDateFilterMapper(
    new DefaultFeedIdMapper()
  );

  @Test
  void mapEmptyFilter() {
    var result = MAPPER.mapFilters(List.of());
    assertTrue(result.isEmpty());
  }

  @Test
  void mapSelectByLine() {
    var result = MAPPER.mapFilters(list(map("select", list(map("lines", list("F:Line:1"))))));
    assertEquals("[(select: [(routes: [F:Line:1])])]", result.toString());
  }

  @Test
  void mapSelectByAuthority() {
    var result = MAPPER.mapFilters(list(map("select", list(map("authorities", list("F:Auth:1"))))));
    assertEquals("[(select: [(agencies: [F:Auth:1])])]", result.toString());
  }

  @Test
  void mapSelectByMode() {
    var result = MAPPER.mapFilters(
      list(map("select", list(map(entry("transportModes", list(map("transportMode", BUS)))))))
    );
    assertEquals(
      "[(select: [(transportModes: AllowMainModeFilter{mainMode: BUS})])]",
      result.toString()
    );
  }

  @Test
  void mapNotByLine() {
    var result = MAPPER.mapFilters(list(map("not", list(map("lines", list("F:Line:1"))))));
    assertEquals("[(not: [(routes: [F:Line:1])])]", result.toString());
  }

  @Test
  void mapNotByAuthority() {
    var result = MAPPER.mapFilters(list(map("not", list(map("authorities", list("F:Auth:1"))))));
    assertEquals("[(not: [(agencies: [F:Auth:1])])]", result.toString());
  }

  @Test
  void mapSelectAndNot() {
    var result = MAPPER.mapFilters(
      list(
        map(
          entry("select", list(map("lines", list("F:Line:1")))),
          entry("not", list(map("authorities", list("F:Auth:1"))))
        )
      )
    );
    assertEquals(
      "[(select: [(routes: [F:Line:1])], not: [(agencies: [F:Auth:1])])]",
      result.toString()
    );
  }

  @Test
  void mapMultipleFilters() {
    var result = MAPPER.mapFilters(
      list(
        map("select", list(map("lines", list("F:Line:1")))),
        map("not", list(map("authorities", list("F:Auth:2"))))
      )
    );
    assertEquals(2, result.size());
    assertEquals(
      "[(select: [(routes: [F:Line:1])]), (not: [(agencies: [F:Auth:2])])]",
      result.toString()
    );
  }
}
