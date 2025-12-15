package org.opentripplanner.ext.fares.service.gtfs.v2;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.model.plan.TestTransitLegBuilder.DEFAULT_DATE;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import com.google.common.collect.ImmutableMultimap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.FareTestConstants;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.model.plan.PlanTestConstants;
import org.opentripplanner.model.plan.TestItinerary;
import org.opentripplanner.model.plan.TestTransitLeg;

class TimeframeTest implements PlanTestConstants, FareTestConstants {

  private static final GtfsFaresV2Service SERVICE = GtfsFaresV2Service.of()
    .withLegRules(
      List.of(
        FareLegRule.of(id("r1"), FARE_PRODUCT_A)
          .withFromTimeframes(List.of(TIMEFRAME_TWELVE_TO_TWO))
          .build(),
        FareLegRule.of(id("r2"), FARE_PRODUCT_B)
          .withFromTimeframes(List.of(TIMEFRAME_THREE_TO_FIVE))
          .build()
      )
    )
    .withServiceIds(
      ImmutableMultimap.of(
        TIMEFRAME_THREE_TO_FIVE.serviceId(),
        DEFAULT_DATE,
        TIMEFRAME_TWELVE_TO_TWO.serviceId(),
        DEFAULT_DATE
      )
    )
    .build();

  @Test
  void oneLeg() {
    var leg = TestTransitLeg.of()
      .withStartTime("12:00")
      .withEndTime("12:10")
      .withServiceId(TIMEFRAME_TWELVE_TO_TWO.serviceId())
      .build();
    var it = TestItinerary.of(leg).build();
    var result = SERVICE.calculateFares(it);

    assertThat(result.itineraryProducts()).isEmpty();
    assertThat(result.offersForLeg(leg)).contains(FareOffer.of(leg.startTime(), FARE_PRODUCT_A));
  }

  @Test
  void twoLegs() {
    var leg1 = TestTransitLeg.of()
      .withStartTime("12:00")
      .withEndTime("12:10")
      .withServiceId(TIMEFRAME_TWELVE_TO_TWO.serviceId())
      .build();
    var leg2 = TestTransitLeg.of()
      .withStartTime("15:20")
      .withEndTime("15:30")
      .withServiceId(TIMEFRAME_THREE_TO_FIVE.serviceId())
      .build();
    var it = TestItinerary.of(leg1, leg2).build();
    var result = SERVICE.calculateFares(it);

    assertThat(result.itineraryProducts()).isEmpty();
    assertThat(result.offersForLeg(leg1)).contains(FareOffer.of(leg1.startTime(), FARE_PRODUCT_A));
    assertThat(result.offersForLeg(leg2)).contains(FareOffer.of(leg2.startTime(), FARE_PRODUCT_B));
  }
}
