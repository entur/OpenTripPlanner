package org.opentripplanner.ext.fares.service.gtfs.v2.custom;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.FareTransferRule;
import org.opentripplanner.ext.fares.model.FareTransferRuleBuilder;
import org.opentripplanner.ext.fares.model.TimeLimitType;
import org.opentripplanner.ext.fares.service.gtfs.GtfsFaresService;
import org.opentripplanner.ext.fares.service.gtfs.v1.DefaultFareService;
import org.opentripplanner.ext.fares.service.gtfs.v1.DefaultFareServiceFactory;
import org.opentripplanner.ext.fares.service.gtfs.v2.GtfsFaresV2Service;
import org.opentripplanner.model.fare.FareMedium;
import org.opentripplanner.model.fare.FareProduct;
import org.opentripplanner.model.fare.RiderCategory;
import org.opentripplanner.routing.core.FareType;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.transit.model.basic.Money;

public class OregonHopFareFactory extends DefaultFareServiceFactory {

  static final FeedScopedId TRIMET_ADULT_SINGLE_RIDE = trimetId("TRIMET_ADULT_SINGLE_RIDE");
  static final FeedScopedId TRIMET_HC_SINGLE_RIDE = trimetId("TRIMET_HC_SINGLE_RIDE");
  static final FeedScopedId STREETCAR_ADULT_SINGLE_RIDE = trimetId("STREETCAR_ADULT_SINGLE_RIDE");
  static final FeedScopedId STREETCAR_HC_SINGLE_RIDE = trimetId("STREETCAR_HC_SINGLE_RIDE");

  static final FeedScopedId ADULT_EXPRESS_SINGLE_RIDE = ctranId("ADULT_EXPRESS_SINGLE_RIDE");
  static final FeedScopedId ADULT_REGIONAL_SINGLE_RIDE = ctranId("ADULT_REGIONAL_SINGLE_RIDE");
  static final FeedScopedId HC_REGIONAL_SINGLE_RIDE = ctranId("HC_REGIONAL_SINGLE_RIDE");
  static final FeedScopedId ADULT_LOCAL_SINGLE_RIDE = ctranId("ADULT_LOCAL_SINGLE_RIDE");
  static final FeedScopedId HC_LOCAL_SINGLE_RIDE = ctranId("HC_LOCAL_SINGLE_RIDE");

  // transfer product ids
  static final FeedScopedId TRIMET_TO_CTRAN_ADULT_TRANSFER = ctranId("TRIMET_CTRAN_ADULT_TRANSFER");
  static final FeedScopedId TRIMET_TO_CTRAN_YOUTH_TRANSFER = ctranId("TRIMET_CTRAN_YOUTH_TRANSFER");

  // leg group ids
  static final FeedScopedId LG_TRIMET_TRIMET = trimetId("TRIMET");
  static final FeedScopedId LG_CTRAN_REGIONAL = ctranId("REGIONAL");

  // rider categories
  static final RiderCategory CATEGORY_YOUTH = RiderCategory.of(ctranId("YOUTH"))
    .withName("Youth")
    .build();
  static final RiderCategory CATEGORY_ADULT = RiderCategory.of(ctranId("ADULT"))
    .withName("Adult")
    .build();
  static final RiderCategory CATEGORY_HONOURED_CITIZEN = RiderCategory.of(
    ctranId("HONORED_CITIZEN")
  )
    .withName("Honored Citizen")
    .build();

  // fare media
  static final FareMedium MEDIUM_HOP_FASTPASS = new FareMedium(ctranId("2"), "HOP Fastpass");

  public FareService makeFareService() {
    DefaultFareService fareService = new DefaultFareService();
    fareService.addFareRules(FareType.regular, regularFareRules.values());

    final Money ADULT_TRIMET = findFareProduct(TRIMET_ADULT_SINGLE_RIDE).price();
    final Money REDUCED_TRIMET = findFareProduct(TRIMET_HC_SINGLE_RIDE).price();
    final Money ADULT_STREETCAR = findFareProduct(STREETCAR_ADULT_SINGLE_RIDE).price();
    final Money REDUCED_STREETCAR = findFareProduct(STREETCAR_HC_SINGLE_RIDE).price();

    final Money CTRAN_EXP = findFareProduct(ADULT_EXPRESS_SINGLE_RIDE).price();

    final Money ADULT_CTRAN_REGIONAL = findFareProduct(ADULT_REGIONAL_SINGLE_RIDE).price();
    final Money REDUCED_CTRAN_REGIONAL = findFareProduct(HC_REGIONAL_SINGLE_RIDE).price();

    final Money ADULT_CTRAN_LOCAL = findFareProduct(ADULT_LOCAL_SINGLE_RIDE).price();
    final Money REDUCED_CTRAN_LOCAL = findFareProduct(HC_LOCAL_SINGLE_RIDE).price();

    // TODO: Senior discounted express upcharge during the mornings
    // Handle senior off-peak fares
    // FareProduct seniorOffPeakExpress = findFareProduct(new FeedScopedId("CTRAN", "HC_EXPRESS_SINGLE_RIDE_MIDDAY"));
    // Money CTRAN_EXP_SENIOR_OFFPEAK = seniorOffPeakExpress.price();

    // TriMet to C-TRAN
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("trimet-to-ctran-regional"))
        .withFromLegGroup(LG_TRIMET_TRIMET)
        .withToLegGroup(LG_CTRAN_REGIONAL)
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_REGIONAL,
            ADULT_TRIMET,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_TRIMET,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_TRIMET
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("trimet-to-ctran-local"))
        .withFromLegGroup(LG_TRIMET_TRIMET)
        .withToLegGroup(ctranId("LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_LOCAL,
            ADULT_TRIMET,
            REDUCED_CTRAN_LOCAL,
            REDUCED_TRIMET,
            Money.ZERO_USD,
            REDUCED_TRIMET
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("trimet-to-ctran-flex"))
        .withFromLegGroup(LG_TRIMET_TRIMET)
        .withToLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_LOCAL,
            ADULT_TRIMET,
            REDUCED_CTRAN_LOCAL,
            REDUCED_TRIMET,
            Money.ZERO_USD,
            REDUCED_TRIMET
          )
        )
        .build()
    );

    this.fareTransferRules.add(
      FareTransferRule.of()
        .withId(ctranId("trimet-to-ctran-express"))
        .withFromLegGroup(LG_TRIMET_TRIMET)
        .withToLegGroup(ctranId("EXPRESS"))
        .withTransferCount(FareTransferRule.UNLIMITED_TRANSFERS)
        .withTimeLimit(TimeLimitType.DEPARTURE_TO_DEPARTURE, Duration.ofMinutes(180))
        .withFareProducts(
          generateHopFareProducts(
            CTRAN_EXP,
            ADULT_TRIMET,
            CTRAN_EXP,
            REDUCED_TRIMET,
            CTRAN_EXP,
            REDUCED_TRIMET
          )
        )
        .build()
    );

    // PSC to C-TRAN
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("psc-to-ctran-regional"))
        .withFromLegGroup(trimetId("PSC"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_REGIONAL,
            ADULT_STREETCAR,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_STREETCAR,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_STREETCAR
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("psc-to-ctran-local"))
        .withFromLegGroup(trimetId("PSC"))
        .withToLegGroup(ctranId("LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_LOCAL,
            ADULT_STREETCAR,
            REDUCED_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            Money.ZERO_USD,
            REDUCED_STREETCAR
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("psc-to-ctran-flex"))
        .withFromLegGroup(trimetId("PSC"))
        .withToLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_LOCAL,
            ADULT_STREETCAR,
            REDUCED_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            Money.ZERO_USD,
            REDUCED_STREETCAR
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("psc-to-ctran-express"))
        .withFromLegGroup(LG_TRIMET_TRIMET)
        .withToLegGroup(ctranId("EXPRESS"))
        .withFareProducts(
          generateHopFareProducts(
            CTRAN_EXP,
            ADULT_STREETCAR,
            CTRAN_EXP,
            REDUCED_STREETCAR,
            CTRAN_EXP,
            REDUCED_STREETCAR
          )
        )
        .build()
    );

    // C-TRAN to TriMet
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-regional-to-trimet"))
        .withToLegGroup(LG_TRIMET_TRIMET)
        .withFromLegGroup(LG_CTRAN_REGIONAL)
        .withFareProducts(
          generateHopFareProducts(
            ADULT_TRIMET,
            ADULT_CTRAN_REGIONAL,
            REDUCED_TRIMET,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_TRIMET,
            REDUCED_CTRAN_REGIONAL
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-local-to-trimet"))
        .withToLegGroup(LG_TRIMET_TRIMET)
        .withFromLegGroup(ctranId("LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_TRIMET,
            ADULT_CTRAN_LOCAL,
            REDUCED_TRIMET,
            REDUCED_CTRAN_LOCAL,
            REDUCED_TRIMET,
            Money.ZERO_USD
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-flex-to-trimet"))
        .withToLegGroup(LG_TRIMET_TRIMET)
        .withFromLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_TRIMET,
            ADULT_CTRAN_LOCAL,
            REDUCED_TRIMET,
            REDUCED_CTRAN_LOCAL,
            REDUCED_TRIMET,
            Money.ZERO_USD
          )
        )
        .build()
    );

    this.fareTransferRules.add(
      FareTransferRule.of()
        .withId(trimetId("ctran-express-to-trimet"))
        .withToLegGroup(LG_TRIMET_TRIMET)
        .withFromLegGroup(ctranId("EXPRESS"))
        .build()
    );

    // C-TRAN to PSC
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-regional-to-psc"))
        .withToLegGroup(trimetId("PSC"))
        .withFromLegGroup(LG_CTRAN_REGIONAL)
        .withFareProducts(
          generateHopFareProducts(
            ADULT_STREETCAR,
            ADULT_CTRAN_REGIONAL,
            REDUCED_STREETCAR,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_STREETCAR,
            REDUCED_CTRAN_REGIONAL
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-local-to-psc"))
        .withToLegGroup(trimetId("PSC"))
        .withFromLegGroup(ctranId("LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_STREETCAR,
            ADULT_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            REDUCED_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            Money.ZERO_USD
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-flex-to-psc"))
        .withToLegGroup(trimetId("PSC"))
        .withFromLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withFareProducts(
          generateHopFareProducts(
            ADULT_STREETCAR,
            ADULT_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            REDUCED_CTRAN_LOCAL,
            REDUCED_STREETCAR,
            Money.ZERO_USD
          )
        )
        .build()
    );

    this.fareTransferRules.add(
      defaultTransfer()
        .withId(trimetId("ctran-express-to-psc"))
        .withToLegGroup(trimetId("PSC"))
        .withFromLegGroup(ctranId("EXPRESS"))
        .build()
    );

    // The C-TRAN Data fails to include unlimited free transfers. This corrects the data. TODO: is this correct?
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("EXP_TO_LOCAL_UNLIMITED"))
        .withFromLegGroup(ctranId("EXPRESS"))
        .withToLegGroup(ctranId("LOCAL"))
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("EXP_TO_REGIONAL_UNLIMITED"))
        .withFromLegGroup(ctranId("EXPRESS"))
        .withToLegGroup(LG_CTRAN_REGIONAL)
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("REGIONAL_TO_LOCAL_UNLIMITED"))
        .withFromLegGroup(LG_CTRAN_REGIONAL)
        .withToLegGroup(ctranId("LOCAL"))
        .build()
    );

    // CTRAN to CTRAN Flex transfers
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("LOCAL_TO_FLEX"))
        .withFromLegGroup(ctranId("LOCAL"))
        .withToLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("REGIONAL_TO_FLEX"))
        .withFromLegGroup(LG_CTRAN_REGIONAL)
        .withToLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("EXPRESS_TO_FLEX"))
        .withFromLegGroup(ctranId("EXPRESS"))
        .withToLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .build()
    );

    // CTRAN Flex to CTRAN transfers
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("FLEX_TO_LOCAL"))
        .withFromLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withToLegGroup(ctranId("LOCAL"))
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("FLEX_TO_REGIONAL"))
        .withFromLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withToLegGroup(LG_CTRAN_REGIONAL)
        .withFareProducts(
          generateHopFareProducts(
            ADULT_CTRAN_REGIONAL,
            ADULT_CTRAN_LOCAL,
            REDUCED_CTRAN_REGIONAL,
            REDUCED_CTRAN_LOCAL,
            REDUCED_CTRAN_REGIONAL,
            Money.ZERO_USD
          )
        )
        .build()
    );
    this.fareTransferRules.add(
      defaultTransfer()
        .withId(ctranId("FLEX_TO_EXPRESS"))
        .withFromLegGroup(FeedScopedId.parse("CTRAN_FLEX:LOCAL"))
        .withToLegGroup(ctranId("EXPRESS"))
        .withFareProducts(
          generateHopFareProducts(
            CTRAN_EXP,
            ADULT_CTRAN_LOCAL,
            CTRAN_EXP,
            REDUCED_CTRAN_LOCAL,
            CTRAN_EXP,
            Money.ZERO_USD
          )
        )
        .build()
    );

    var faresV2Service = GtfsFaresV2Service.of()
      .withLegRules(this.fareLegRules)
      .withTransferRules(this.fareTransferRules)
      .withStopAreas(this.stopAreas)
      .withServiceIds(this.serviceDates)
      .build();

    return new GtfsFaresService(fareService, faresV2Service);
  }

  /**
   * Pulls out a desired fare product by ID from the fare leg rule table. Otherwise, generates a $0 fare as a null.
   * @param fareProductId The fare product to find
   * @return              The fare product from the GTFS, or a generated $0 fare product.
   */
  private FareProduct findFareProduct(FeedScopedId fareProductId) {
    Optional<FareLegRule> potentialRuleMatch = this.fareLegRules.stream()
      .filter(f ->
        f
          .fareProducts()
          .stream()
          .anyMatch(fp -> fp.id().equals(fareProductId))
      )
      .findFirst();

    return potentialRuleMatch
      .flatMap(flr ->
        flr
          .fareProducts()
          .stream()
          .filter(fp -> fp.id().equals(fareProductId))
          .findFirst()
      )
      .orElse(FareProduct.of(fareProductId, "Could not find fare in data", Money.ZERO_USD).build());
  }

  /**
   * Generates fare products based on C-TRAN/TriMet data. Relies on 2 fares per rider category.
   * To calculate the effective fare, the second fare is subtracted from the first.
   */
  private Collection<FareProduct> generateHopFareProducts(
    Money AdultLarger,
    Money AdultSmaller,
    Money SeniorLarger,
    Money SeniorSmaller,
    Money YouthLarger,
    Money YouthSmaller
  ) {
    final Collection<FareProduct> hopFareProducts = new HashSet<>();

    // Adult
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_ADULT_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, AdultLarger.minus(AdultSmaller))
      )
        .withCategory(CATEGORY_ADULT)
        .withMedium(MEDIUM_HOP_FASTPASS)
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_ADULT_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, AdultLarger.minus(AdultSmaller))
      )
        .withCategory(CATEGORY_ADULT)
        .withMedium(new FareMedium(ctranId("3"), "Open Payment"))
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_ADULT_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, AdultLarger.minus(AdultSmaller))
      )
        .withCategory(CATEGORY_ADULT)
        .withMedium(new FareMedium(ctranId("4"), "Virtual HOP Fastpass"))
        .build()
    );

    // Senior
    hopFareProducts.add(
      FareProduct.of(
        ctranId("TRIMET_CTRAN_HC_TRANSFER"),
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, (SeniorLarger.minus(SeniorSmaller)))
      )
        .withCategory(CATEGORY_HONOURED_CITIZEN)
        .withMedium(MEDIUM_HOP_FASTPASS)
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        ctranId("TRIMET_CTRAN_HONORED_CITIZEN_TRANSFER"),
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, SeniorLarger.minus(SeniorSmaller))
      )
        .withCategory(CATEGORY_HONOURED_CITIZEN)
        .withMedium(new FareMedium(ctranId("3"), "Open Payment"))
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        ctranId("TRIMET_CTRAN_HONORED_CITIZEN_TRANSFER"),
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, SeniorLarger.minus(SeniorSmaller))
      )
        .withCategory(CATEGORY_HONOURED_CITIZEN)
        .withMedium(new FareMedium(ctranId("4"), "Virtual HOP Fastpass"))
        .build()
    );

    // Youth
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_YOUTH_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, YouthLarger.minus(YouthSmaller))
      )
        .withCategory(CATEGORY_YOUTH)
        .withMedium(MEDIUM_HOP_FASTPASS)
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_YOUTH_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, YouthLarger.minus(YouthSmaller))
      )
        .withCategory(CATEGORY_YOUTH)
        .withMedium(new FareMedium(ctranId("3"), "Open Payment"))
        .build()
    );
    hopFareProducts.add(
      FareProduct.of(
        TRIMET_TO_CTRAN_YOUTH_TRANSFER,
        "TriMet to C-TRAN",
        Money.max(Money.ZERO_USD, YouthLarger.minus(YouthSmaller))
      )
        .withCategory(CATEGORY_YOUTH)
        .withMedium(new FareMedium(ctranId("4"), "Virtual HOP Fastpass"))
        .build()
    );

    return hopFareProducts;
  }

  private static FareTransferRuleBuilder defaultTransfer() {
    return FareTransferRule.of()
      .withTransferCount(FareTransferRule.UNLIMITED_TRANSFERS)
      .withTimeLimit(TimeLimitType.DEPARTURE_TO_DEPARTURE, Duration.ofMinutes(180));
  }

  private static FeedScopedId trimetId(String id) {
    return new FeedScopedId("TRIMET", id);
  }

  private static FeedScopedId ctranId(String id) {
    return new FeedScopedId("CTRAN", id);
  }
}
