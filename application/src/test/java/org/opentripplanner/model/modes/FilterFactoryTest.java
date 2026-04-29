package org.opentripplanner.model.modes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.basic.NarrowedTransitMode;
import org.opentripplanner.transit.model.basic.ReplacementRequirement;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;

public class FilterFactoryTest {

  private static final SubMode LOCAL_BUS = SubMode.getOrBuildAndCacheForever("localBus");
  private static final SubMode REGIONAL_BUS = SubMode.getOrBuildAndCacheForever("regionalBus");

  List<NarrowedTransitMode> ONE_MAIN = List.of(
    new NarrowedTransitMode(TransitMode.BUS, null, ReplacementRequirement.IGNORED)
  );
  List<NarrowedTransitMode> MULTIPLE_MAINS = List.of(
    new NarrowedTransitMode(TransitMode.BUS, null, ReplacementRequirement.IGNORED),
    new NarrowedTransitMode(TransitMode.RAIL, null, ReplacementRequirement.IGNORED)
  );
  List<NarrowedTransitMode> ONE_SUB = List.of(
    new NarrowedTransitMode(TransitMode.BUS, LOCAL_BUS, ReplacementRequirement.IGNORED)
  );
  List<NarrowedTransitMode> MULTIPLE_SUBS = List.of(
    new NarrowedTransitMode(TransitMode.BUS, LOCAL_BUS, ReplacementRequirement.IGNORED),
    new NarrowedTransitMode(TransitMode.BUS, REGIONAL_BUS, ReplacementRequirement.IGNORED)
  );
  List<NarrowedTransitMode> ONE_MAIN_COVERED_SUB = List.of(
    new NarrowedTransitMode(TransitMode.BUS, null, ReplacementRequirement.IGNORED),
    new NarrowedTransitMode(TransitMode.BUS, LOCAL_BUS, ReplacementRequirement.IGNORED)
  );
  List<NarrowedTransitMode> ONE_REPLACEMENT = List.of(
    new NarrowedTransitMode(TransitMode.BUS, null, ReplacementRequirement.REQUIRED)
  );
  List<NarrowedTransitMode> MULTIPLE_REPLACEMENTS = List.of(
    new NarrowedTransitMode(TransitMode.BUS, null, ReplacementRequirement.REQUIRED),
    new NarrowedTransitMode(TransitMode.RAIL, null, ReplacementRequirement.FORBIDDEN)
  );
  List<NarrowedTransitMode> ONE_MAIN_COVERED_REPLACEMENT = List.of(
    new NarrowedTransitMode(TransitMode.RAIL, null, ReplacementRequirement.IGNORED),
    new NarrowedTransitMode(TransitMode.RAIL, null, ReplacementRequirement.FORBIDDEN)
  );

  @Test
  void oneMain() {
    var filter = FilterFactory.create(ONE_MAIN);
    assertEquals(AllowMainModeFilter.class, filter.getClass());
    assertEquals("AllowMainModeFilter{mainMode: BUS}", filter.toString());
  }

  @Test
  void multipleMains() {
    var filter = FilterFactory.create(MULTIPLE_MAINS);
    assertEquals("AllowMainModesFilter{mainModes: [RAIL, BUS]}", filter.toString());
  }

  @Test
  void oneSub() {
    var filter = FilterFactory.create(ONE_SUB);
    assertEquals("AllowMainAndSubModeFilter{mainMode: BUS, subMode: localBus}", filter.toString());
  }

  @Test
  void multipleSubs() {
    var filter = FilterFactory.create(MULTIPLE_SUBS);
    assertEquals(
      "AllowMainAndSubModesFilter{mainMode: BUS, subModes: [localBus, regionalBus]}",
      filter.toString()
    );
  }

  @Test
  void dropsCoveredSub() {
    var filter = FilterFactory.create(ONE_MAIN_COVERED_SUB);
    assertEquals("AllowMainModeFilter{mainMode: BUS}", filter.toString());
  }

  @Test
  void oneReplacement() {
    var filter = FilterFactory.create(ONE_REPLACEMENT);
    assertEquals(
      "AllowNarrowedTransitModeFilter{mode: BUS, isReplacement: REQUIRED}",
      filter.toString()
    );
  }

  @Test
  void multipleReplacements() {
    var filter = FilterFactory.create(MULTIPLE_REPLACEMENTS);
    assertEquals(
      "FilterCollection[AllowNarrowedTransitModeFilter{mode: BUS, isReplacement: REQUIRED}, AllowNarrowedTransitModeFilter{mode: RAIL, isReplacement: FORBIDDEN}]",
      filter.toString()
    );
  }

  @Test
  void dropsCoveredReplacement() {
    var filter = FilterFactory.create(ONE_MAIN_COVERED_REPLACEMENT);
    assertEquals("AllowMainModeFilter{mainMode: RAIL}", filter.toString());
  }
}
