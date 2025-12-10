package org.opentripplanner.updater.trip.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EstimatedVehicleJourneyCodeAdapterTest {

  @ParameterizedTest
  @CsvSource(
    {
      "1234, 1234",
      "ServiceJourney:1234, ServiceJourney:1234",
      "DatedServiceJourney:1234, ServiceJourney:1234",
    }
  )
  void getServiceJourneyId(String code, String expected) {
    var adapter = new EstimatedVehicleJourneyCodeAdapter(code);
    assertEquals(expected, adapter.getServiceJourneyId());
  }

  @ParameterizedTest
  @CsvSource(
    {
      "1234, 1234",
      "ServiceJourney:1234, DatedServiceJourney:1234",
      "DatedServiceJourney:1234, DatedServiceJourney:1234",
    }
  )
  void getDatedServiceJourneyId(String code, String expected) {
    var adapter = new EstimatedVehicleJourneyCodeAdapter(code);
    assertEquals(expected, adapter.getDatedServiceJourneyId());
  }
}
