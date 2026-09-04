package org.opentripplanner.netex;

import static com.google.common.truth.Truth.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.model.TransitDataImport;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Loads a NeTEx Part 5 "New Modes" data set through the real NeTEx import and asserts on the
 * result. The data set is a structured carpool trip whose stops are {@code VehicleMeetingPoint}s -
 * named coordinates with no StopPlace, no Quay and no entry in the national stop register.
 * <p>
 * The assertions below are the properties the GTFS carpool feed produces for the same kind of
 * trip: the mode is CARPOOL, the stops are bare coordinates without a parent station, and the
 * operator comes from the feed rather than from a national register. Keeping the two delivery
 * tracks equivalent on these points is the design claim this PoC rests on, so they are asserted
 * here rather than left to manual inspection.
 * <p>
 * Note what this test does not do: it does not load the GTFS feed and diff the two. The ids can
 * never match across formats, so any comparison would be a curated projection of selected fields -
 * which is what the assertions below already are, only written down explicitly. The consequence is
 * that a change to the GTFS feed will not be noticed here.
 */
class NetexNewModesBundleSmokeTest {

  private static final String EVJEVEGEN = "NBG:VehicleMeetingPoint:evjevegen";
  private static final String STRANDVEIEN = "NBG:VehicleMeetingPoint:strandveien";

  @Test
  void smokeTestOfNetexNewModesLoadData() {
    OTPFeature.NetexNewModes.testOn(() -> {
      var netexBundle = ConstantsForTests.createMinimalNetexNewModesBundle();

      // Run the check to make sure it does not throw an exception
      netexBundle.checkInputs();

      var transitBuilder = netexBundle.loadBundle(new Deduplicator(), DataImportIssueStore.NOOP);
      var otpModel = transitBuilder.build();

      assertMeetingPointsBecameStopsWithoutStations(otpModel);
      assertModeIsCarpool(otpModel);
      assertOperatorComesFromTheFeed(otpModel);
      assertServiceLinkGeometryIsUsed(otpModel);
    });
  }

  /**
   * With the feature off, the same document must still be readable - the Part 5 frames simply fall
   * through as unmapped, like any other frame OTP does not read. Nothing is imported from them:
   * without the meeting points the scheduled stop points resolve to nothing, and the trips are
   * dropped rather than imported half-built.
   * <p>
   * This is the other half of the toggle. It also keeps the assertions in
   * {@link #smokeTestOfNetexNewModesLoadData()} honest: they only hold because the feature is on.
   */
  @Test
  void partFiveIsNotImportedWhenTheFeatureIsOff() {
    OTPFeature.NetexNewModes.testOff(() -> {
      var netexBundle = ConstantsForTests.createMinimalNetexNewModesBundle();

      netexBundle.checkInputs();

      var transitBuilder = netexBundle.loadBundle(new Deduplicator(), DataImportIssueStore.NOOP);
      var otpModel = transitBuilder.build();

      assertThat(otpModel.siteRepository().listRegularStops()).isEmpty();
      assertThat(otpModel.getAllTrips()).isEmpty();
    });
  }

  /**
   * The meeting points become plain stops with no parent station, and the data set brings no
   * stations at all - the whole point of modelling them as Part 5 meeting points.
   */
  private void assertMeetingPointsBecameStopsWithoutStations(TransitDataImport otpModel) {
    List<RegularStop> stops = otpModel
      .siteRepository()
      .listRegularStops()
      .stream()
      .sorted(Comparator.comparing(it -> it.getId().getId()))
      .toList();

    assertThat(stops).hasSize(2);
    assertThat(
      stops
        .stream()
        .map(it -> it.getId().getId())
        .toList()
    )
      .containsExactly(EVJEVEGEN, STRANDVEIEN)
      .inOrder();

    var evjevegen = stops.get(0);
    assertThat(evjevegen.getName().toString()).isEqualTo(
      "Evjevegen – innfartsparkering ved Mellomriksvegen"
    );
    assertThat(evjevegen.getLat()).isWithin(1e-6).of(63.467782);
    assertThat(evjevegen.getLon()).isWithin(1e-6).of(10.942183);

    assertThat(stops.stream().map(RegularStop::getParentStation).toList()).containsExactly(
      null,
      null
    );
    assertThat(otpModel.siteRepository().listStations()).isEmpty();
  }

  /**
   * The line declares {@code taxi}, and the carpool classification arrives from the Part 5
   * ModeOfOperation on the Network. Getting this wrong is not hypothetical - an earlier revision
   * reported these trips as taxi.
   */
  private void assertModeIsCarpool(TransitDataImport otpModel) {
    var trips = otpModel.getAllTrips();

    assertThat(trips).hasSize(2);
    assertThat(trips.stream().map(Trip::getMode).distinct().toList()).containsExactly(
      TransitMode.CARPOOL
    );
  }

  private void assertOperatorComesFromTheFeed(TransitDataImport otpModel) {
    var operators = otpModel.getAllOperators();

    assertThat(operators).hasSize(1);
    assertThat(operators.iterator().next().getName()).isEqualTo("AtB Nabogo");
  }

  /**
   * One of the two journey patterns carries a ServiceLink whose endpoints are scheduled stop
   * points projected onto meeting points. Its hop geometry must therefore follow the driven route,
   * while the pattern without a ServiceLink falls back to a straight line between the two stops.
   * This is the path that broke the first time a meeting point was used as a service link
   * endpoint.
   */
  private void assertServiceLinkGeometryIsUsed(TransitDataImport otpModel) {
    var patterns = otpModel.getTripPatterns();

    assertThat(patterns).hasSize(2);
    assertThat(patterns.stream().map(this::firstHopPointCount).sorted().toList())
      .containsExactly(2, 8)
      .inOrder();
  }

  private int firstHopPointCount(TripPattern pattern) {
    var geometry = pattern.getHopGeometry(0);
    return geometry == null ? 0 : geometry.getNumPoints();
  }
}
