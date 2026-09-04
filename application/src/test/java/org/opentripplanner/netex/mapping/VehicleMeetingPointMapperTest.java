package org.opentripplanner.netex.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.entur.netex2.model.LocationStructure;
import org.entur.netex2.model.MultilingualString;
import org.entur.netex2.model.VehicleMeetingPoint;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.transit.service.SiteRepository;

/**
 * The happy path is covered end to end by {@code NetexNewModesBundleSmokeTest}. What is tested here
 * is what happens to input that is not as promised - a meeting point is created by a service
 * provider rather than by a stop register, so it is the kind of data that arrives incomplete.
 */
class VehicleMeetingPointMapperTest {

  private static final String ID = "NBG:VehicleMeetingPoint:1";
  private static final String NAME = "Strandveien - parkeringen ved Nyhavna";
  private static final double LATITUDE = 63.437207;
  private static final double LONGITUDE = 10.419224;

  private static final String NO_COORDINATES_ISSUE = "VehicleMeetingPointWithoutCoordinates";

  private final DefaultDataImportIssueStore issueStore = new DefaultDataImportIssueStore();
  private final VehicleMeetingPointMapper mapper = new VehicleMeetingPointMapper(
    MappingSupport.ID_FACTORY,
    SiteRepository.of(),
    issueStore
  );

  @Test
  void mapsNameAndCoordinate() {
    var stop = mapper.map(meetingPoint(NAME, LATITUDE, LONGITUDE));

    assertThat(stop).isNotNull();
    assertThat(stop.getId().getId()).isEqualTo(ID);
    assertThat(stop.getName().toString()).isEqualTo(NAME);
    assertThat(stop.getLat()).isWithin(1e-6).of(LATITUDE);
    assertThat(stop.getLon()).isWithin(1e-6).of(LONGITUDE);
    assertThat(issueTypes()).isEmpty();
  }

  /**
   * A meeting point is deliberately modelled without a StopPlace, so there is nothing for it to be
   * a child of.
   */
  @Test
  void hasNoParentStation() {
    var stop = mapper.map(meetingPoint(NAME, LATITUDE, LONGITUDE));

    assertThat(stop.getParentStation()).isNull();
  }

  /**
   * A meeting point carries its whole identity in the name, so a missing one is worth noticing in
   * the output. Falling back to the id keeps the stop usable rather than nameless.
   */
  @Test
  void fallsBackToTheIdWhenNameIsMissing() {
    var stop = mapper.map(meetingPoint(null, LATITUDE, LONGITUDE));

    assertThat(stop.getName().toString()).isEqualTo(ID);
  }

  @Test
  void dropsPointWithoutLocation() {
    var point = new VehicleMeetingPoint().withId(ID);

    assertThat(mapper.map(point)).isNull();
    assertThat(issueTypes()).containsExactly(NO_COORDINATES_ISSUE);
  }

  @Test
  void dropsPointWithLongitudeButNoLatitude() {
    var point = new VehicleMeetingPoint()
      .withId(ID)
      .withLocation(new LocationStructure().withLongitude(BigDecimal.valueOf(LONGITUDE)));

    assertThat(mapper.map(point)).isNull();
    assertThat(issueTypes()).containsExactly(NO_COORDINATES_ISSUE);
  }

  @Test
  void dropsPointWithLatitudeButNoLongitude() {
    var point = new VehicleMeetingPoint()
      .withId(ID)
      .withLocation(new LocationStructure().withLatitude(BigDecimal.valueOf(LATITUDE)));

    assertThat(mapper.map(point)).isNull();
    assertThat(issueTypes()).containsExactly(NO_COORDINATES_ISSUE);
  }

  /**
   * Two scheduled stop points may be projected onto the same meeting point, so the mapper is
   * called more than once for the same id and must not build a second stop.
   */
  @Test
  void returnsTheSameStopWhenMappedTwice() {
    var first = mapper.map(meetingPoint(NAME, LATITUDE, LONGITUDE));
    var second = mapper.map(meetingPoint(NAME, LATITUDE, LONGITUDE));

    assertThat(first).isSameInstanceAs(second);
  }

  private static VehicleMeetingPoint meetingPoint(
    @Nullable String name,
    double latitude,
    double longitude
  ) {
    var point = new VehicleMeetingPoint()
      .withId(ID)
      .withLocation(
        new LocationStructure()
          .withLatitude(BigDecimal.valueOf(latitude))
          .withLongitude(BigDecimal.valueOf(longitude))
      );
    return name == null ? point : point.withName(new MultilingualString().withContent(name));
  }

  private List<String> issueTypes() {
    return issueStore.listIssues().stream().map(DataImportIssue::getType).toList();
  }
}
