package org.opentripplanner.routing.api.request.preference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.routing.api.request.preference.DirectTransitPreferences.DEFAULT;

import org.junit.jupiter.api.Test;
import org.opentripplanner._support.asserts.AssertEqualsAndHashCode;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;

class DirectTransitPreferencesTest {

  private static final CostLinearFunction COST_RELAX_FUNCTION = CostLinearFunction.of(
    Cost.ONE_HOUR_WITH_TRANSIT,
    3.0
  );
  private static final double EXTRA_ACCESS_EGRESS_COST_FACTOR = 5.0;
  private static final boolean DISABLE_ACCESS_EGRESS = true;

  private DirectTransitPreferences subject = DirectTransitPreferences.of()
    .withEnabled(true)
    .withCostRelaxFunction(COST_RELAX_FUNCTION)
    .withExtraAccessEgressCostFactor(EXTRA_ACCESS_EGRESS_COST_FACTOR)
    .withDisableAccessEgress(DISABLE_ACCESS_EGRESS)
    .build();

  @Test
  void enabled() {
    assertFalse(DEFAULT.enabled());
    assertTrue(subject.enabled());
  }

  @Test
  void costRelaxFunction() {
    assertEquals(DirectTransitPreferences.DEFAULT_COST_RELAX_FUNCTION, DEFAULT.costRelaxFunction());
    assertEquals(COST_RELAX_FUNCTION, subject.costRelaxFunction());
  }

  @Test
  void extraAccessEgressCostFactor() {
    assertEquals(DirectTransitPreferences.DEFAULT_FACTOR, DEFAULT.extraAccessEgressCostFactor());
    assertEquals(EXTRA_ACCESS_EGRESS_COST_FACTOR, subject.extraAccessEgressCostFactor());
  }

  @Test
  void disableAccessEgress() {
    // Skip OFF - we do not care
    assertFalse(DEFAULT.disableAccessEgress());
    assertTrue(subject.disableAccessEgress());
  }

  @Test
  void copyOf() {}

  @Test
  void testEqualsAndHashCode() {
    var sameAs = DirectTransitPreferences.of()
      .withEnabled(true)
      .withCostRelaxFunction(COST_RELAX_FUNCTION)
      .withExtraAccessEgressCostFactor(EXTRA_ACCESS_EGRESS_COST_FACTOR)
      .withDisableAccessEgress(DISABLE_ACCESS_EGRESS)
      .build();

    AssertEqualsAndHashCode.verify(subject).differentFrom(DEFAULT).sameAs(sameAs);
  }
}
