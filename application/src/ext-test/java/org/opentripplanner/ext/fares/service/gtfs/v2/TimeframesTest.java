package org.opentripplanner.ext.fares.service.gtfs.v2;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import com.google.common.collect.ImmutableMultimap;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.FareModelForTest;
import org.opentripplanner.ext.fares.model.Timeframe;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.PlanTestConstants;
import org.opentripplanner.model.plan.TestTransitLeg;

class TimeframesTest implements PlanTestConstants {

  public static final Timeframe TF_STARTING_AT_NOON = Timeframe.of()
    .withServiceId(id("s1"))
    .withStart(LocalTime.of(12, 0))
    .build();
  private static final GtfsFaresV2Service SERVICE = new GtfsFaresV2Service(
    List.of(
      FareLegRule.of(id("1"), FareModelForTest.FARE_PRODUCT_A)
        .withFromTimeframes(List.of(TF_STARTING_AT_NOON))
        .build()
    ),
    List.of(),
    ImmutableMultimap.of()
  );

  @Test
  void simple() {
    var leg = TestTransitLeg.of().withStartTime("10:00").withEndTime("11:00").build();

    var res = SERVICE.calculateFares(
      Itinerary.ofScheduledTransit(List.of(leg)).withGeneralizedCost(Cost.ZERO).build()
    );
    assertThat(res.itineraryProducts()).isEmpty();
    assertThat(res.legProducts()).isEmpty();
  }
}
