package org.opentripplanner.ext.fares.service.gtfs.v2.custom;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.ext.fares.service.gtfs.v2.custom.OregonHopFareFactory.LG_CTRAN_REGIONAL;
import static org.opentripplanner.ext.fares.service.gtfs.v2.custom.OregonHopFareFactory.LG_TRIMET_TRIMET;
import static org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory.id;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.FareRulesData;
import org.opentripplanner.ext.fares.model.FareTestConstants;
import org.opentripplanner.model.fare.FareProduct;
import org.opentripplanner.model.plan.TestItinerary;
import org.opentripplanner.model.plan.TestTransitLeg;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.transit.model.basic.Money;

class OregonHopFareFactoryTest implements FareTestConstants {

  private static final FeedScopedId NETWORK_TRIMET = id("network-trimet");
  private static final FeedScopedId NETWORK_CTRAN = id("network-ctran");
  private static final FareProduct FP_TRIMET_REGULAR = FareProduct.of(
    id("ctran-regular"),
    "regular",
    Money.usDollars(10)
  ).build();

  @Test
  void trimetToCtranIsFree() {
    var service = hopService();

    var trimetLeg = TestTransitLeg.of().withNetwork(NETWORK_TRIMET).build();
    var ctranLeg = TestTransitLeg.of().withNetwork(NETWORK_CTRAN).build();

    var itin = TestItinerary.of(trimetLeg, ctranLeg).build();
    var results = service.calculateFares(itin);

    assertThat(results.getItineraryProducts()).containsExactly(FP_TRIMET_REGULAR);
  }

  private static FareService hopService() {
    var factory = new OregonHopFareFactory();

    var data = new FareRulesData();
    data
      .fareLegRules()
      .addAll(
        List.of(
          FareLegRule.of(id("trimet-local"), FP_TRIMET_REGULAR)
            .withLegGroupId(LG_TRIMET_TRIMET)
            .withNetworkId(NETWORK_TRIMET)
            .build(),
          FareLegRule.of(id("ctran-regional"), FARE_PRODUCT_B)
            .withLegGroupId(LG_CTRAN_REGIONAL)
            .withNetworkId(NETWORK_CTRAN)
            .build()
        )
      );

    factory.processGtfs(data);
    return factory.makeFareService();
  }
}
