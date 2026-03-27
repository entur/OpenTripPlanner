package org.opentripplanner.ext.carpooling.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.arrivalIsAfterDepartureTime;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.journeyWithDifferentCapacitiesPerCall;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.journeyWithOnboardCounts;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.journeyWithTotalCapacity;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.lessThanTwoStops;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.minimalCompleteJourney;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.minimalCompleteJourneyWithPolygon;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.stopTimesAreOutOfOrder;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.tripHasAimedTimesOnly;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.tripHasExpectedTimesOnly;
import static org.opentripplanner.ext.carpooling.updater.CarpoolSiriMapper.DEFAULT_ONBOARD_COUNT;
import static org.opentripplanner.ext.carpooling.updater.CarpoolSiriMapper.DEFAULT_TOTAL_CAPACITY;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;

public class CarpoolSiriMapperTest {

  private final CarpoolSiriMapper mapper = new CarpoolSiriMapper();

  @Test
  void mapSiriToCarpoolTrip_arrivalIsAfterDepartureTime_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(arrivalIsAfterDepartureTime())
    );
  }

  @Test
  void mapSiriToCarpoolTrip_lessThanTwoStops_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(lessThanTwoStops())
    );
  }

  @Test
  void mapSiriToCarpoolTrip_minimalData_mapsOk() {
    var journey = minimalCompleteJourney();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    var expectedStartTime = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .stream()
      .findFirst()
      .map(EstimatedCall::getAimedDepartureTime)
      .orElseThrow();
    var expectedEndTime = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .stream()
      .reduce((a, b) -> b)
      .map(EstimatedCall::getAimedArrivalTime)
      .orElseThrow();
    assertEquals(expectedStartTime, mapped.startTime());
    assertEquals(expectedEndTime, mapped.endTime());

    var startName = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .getFirst()
      .getStopPointNames()
      .getFirst()
      .getValue();
    var endName = journey
      .getEstimatedCalls()
      .getEstimatedCalls()
      .getLast()
      .getStopPointNames()
      .getFirst()
      .getValue();
    assertEquals("First stop", startName);
    assertEquals("Last stop", endName);
  }

  @Test
  void mapSiriToCarpoolTrip_minimalDataUsingPolygonStops_mapsOk() {
    var journey = minimalCompleteJourneyWithPolygon();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    var firstStop = mapped.stops().getFirst();
    var lastStop = mapped.stops().getLast();

    assertNotNull(firstStop.getCoordinate());
    assertNotNull(lastStop.getCoordinate());
  }

  @Test
  void mapSiriToCarpoolTrip_tripHasOnlyAimedTimes_mapsOk() {
    var journey = tripHasAimedTimesOnly();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNotNull(mapped.startTime());
    assertNotNull(mapped.endTime());
  }

  @Test
  void mapSiriToCarpoolTrip_tripHasOnlyExpectedTimes_mapsOk() {
    var journey = tripHasExpectedTimesOnly();
    var mapped = mapper.mapSiriToCarpoolTrip(journey);

    assertNotNull(mapped.startTime());
    assertNotNull(mapped.endTime());
  }

  @Test
  void mapSiriToCarpoolTrip_stopTimesAreOutOfOrder_trowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
      mapper.mapSiriToCarpoolTrip(stopTimesAreOutOfOrder())
    );
  }

  // -- extractTotalCapacity tests --

  @Test
  void mapSiriToCarpoolTrip_noCapacityData_returnsDefaultCapacity() {
    var mapped = mapper.mapSiriToCarpoolTrip(minimalCompleteJourney());
    assertEquals(DEFAULT_TOTAL_CAPACITY, mapped.totalCapacity());
  }

  @Test
  void mapSiriToCarpoolTrip_withCapacityData_usesProvidedCapacity() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithTotalCapacity(3));
    assertEquals(3, mapped.totalCapacity());
  }

  @Test
  void mapSiriToCarpoolTrip_zeroCapacity_returnsDefaultCapacity() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithTotalCapacity(0));
    assertEquals(DEFAULT_TOTAL_CAPACITY, mapped.totalCapacity());
  }

  @Test
  void mapSiriToCarpoolTrip_negativeCapacity_returnsDefaultCapacity() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithTotalCapacity(-1));
    assertEquals(DEFAULT_TOTAL_CAPACITY, mapped.totalCapacity());
  }

  @Test
  void mapSiriToCarpoolTrip_differentCapacitiesPerCall_usesFirstValue() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithDifferentCapacitiesPerCall(3, 7));
    assertEquals(3, mapped.totalCapacity());
  }

  @Test
  void mapSiriToCarpoolTrip_consistentCapacitiesPerCall_usesValue() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithDifferentCapacitiesPerCall(4, 4));
    assertEquals(4, mapped.totalCapacity());
  }

  // -- extractOnboardCount tests --

  @Test
  void mapSiriToCarpoolTrip_noOccupancyData_returnsDefaultOnboardCount() {
    var mapped = mapper.mapSiriToCarpoolTrip(minimalCompleteJourney());
    for (var stop : mapped.stops()) {
      assertEquals(DEFAULT_ONBOARD_COUNT, stop.getOnboardCount());
    }
  }

  @Test
  void mapSiriToCarpoolTrip_withOccupancyData_usesProvidedOnboardCount() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithOnboardCounts(2, 3));
    assertEquals(2, mapped.stops().getFirst().getOnboardCount());
    assertEquals(3, mapped.stops().getLast().getOnboardCount());
  }

  @Test
  void mapSiriToCarpoolTrip_zeroOnboardCount_returnsDefaultOnboardCount() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithOnboardCounts(0, 0));
    for (var stop : mapped.stops()) {
      assertEquals(DEFAULT_ONBOARD_COUNT, stop.getOnboardCount());
    }
  }

  @Test
  void mapSiriToCarpoolTrip_negativeOnboardCount_returnsDefaultOnboardCount() {
    var mapped = mapper.mapSiriToCarpoolTrip(journeyWithOnboardCounts(-1, -1));
    for (var stop : mapped.stops()) {
      assertEquals(DEFAULT_ONBOARD_COUNT, stop.getOnboardCount());
    }
  }
}
