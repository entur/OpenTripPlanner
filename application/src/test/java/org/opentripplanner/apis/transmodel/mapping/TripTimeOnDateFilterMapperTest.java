package org.opentripplanner.apis.transmodel.mapping;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.apis.transmodel._support.RequestHelper.list;
import static org.opentripplanner.apis.transmodel._support.RequestHelper.map;
import static org.opentripplanner.transit.model.basic.TransitMode.BUS;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.api.model.transit.DefaultFeedIdMapper;

class TripTimeOnDateFilterMapperTest {

  private static final TripTimeOnDateFilterMapper MAPPER = new TripTimeOnDateFilterMapper(
    new DefaultFeedIdMapper()
  );

  static Stream<Arguments> mapFiltersCases() {
    return Stream.of(
      Arguments.of(
        "selectByLine",
        list(map("select", list(map("lines", list("F:Line:1"))))),
        "[(select: [(routes: [F:Line:1])])]"
      ),
      Arguments.of(
        "selectByAuthority",
        list(map("select", list(map("authorities", list("F:Auth:1"))))),
        "[(select: [(agencies: [F:Auth:1])])]"
      ),
      Arguments.of(
        "selectByMode",
        list(map("select", list(map(entry("transportModes", list(map("transportMode", BUS))))))),
        "[(select: [(transportModes: [BUS])])]"
      ),
      Arguments.of(
        "notByLine",
        list(map("not", list(map("lines", list("F:Line:1"))))),
        "[(not: [(routes: [F:Line:1])])]"
      ),
      Arguments.of(
        "notByAuthority",
        list(map("not", list(map("authorities", list("F:Auth:1"))))),
        "[(not: [(agencies: [F:Auth:1])])]"
      ),
      Arguments.of(
        "selectAndNot",
        list(
          map(
            entry("select", list(map("lines", list("F:Line:1")))),
            entry("not", list(map("authorities", list("F:Auth:1"))))
          )
        ),
        "[(select: [(routes: [F:Line:1])], not: [(agencies: [F:Auth:1])])]"
      ),
      Arguments.of(
        "multipleFilters",
        list(
          map("select", list(map("lines", list("F:Line:1")))),
          map("not", list(map("authorities", list("F:Auth:2"))))
        ),
        "[(select: [(routes: [F:Line:1])]), (not: [(agencies: [F:Auth:2])])]"
      ),
      // Empty list cases
      Arguments.of("emptyFilterList", List.<Map<String, ?>>of(), "[]"),
      Arguments.of("emptySelectArray", list(map("select", list())), "[ALL]"),
      Arguments.of("emptyNotArray", list(map("not", list())), "[ALL]"),
      Arguments.of("emptySelectorInSelect", list(map("select", list(map()))), "[(select: [()])]"),
      Arguments.of("emptySelectorInNot", list(map("not", list(map()))), "[(not: [()])]")
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mapFiltersCases")
  void mapFilters(String description, List<Map<String, ?>> input, String expected) {
    var result = MAPPER.mapFilters(input);
    assertEquals(expected, result.toString());
  }
}
