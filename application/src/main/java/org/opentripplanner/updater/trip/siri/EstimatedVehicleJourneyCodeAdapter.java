package org.opentripplanner.updater.trip.siri;

/**
 * Adapter for normalizing EstimatedVehicleJourneyCode to proper NeTEx IDs.
 * Ensures Trip uses ServiceJourney prefix and TripOnServiceDate uses DatedServiceJourney prefix.
 */
public class EstimatedVehicleJourneyCodeAdapter {

  private static final String SERVICE_JOURNEY_PREFIX = "ServiceJourney:";
  private static final String DATED_SERVICE_JOURNEY_PREFIX = "DatedServiceJourney:";

  private final String estimatedVehicleJourneyCode;

  public EstimatedVehicleJourneyCodeAdapter(String estimatedVehicleJourneyCode) {
    this.estimatedVehicleJourneyCode = estimatedVehicleJourneyCode;
  }

  /**
   * Get code normalized to ServiceJourney prefix (for Trip entities).
   * If code has DatedServiceJourney: prefix, swaps to ServiceJourney:.
   */
  public String getServiceJourneyId() {
    if (estimatedVehicleJourneyCode.startsWith(DATED_SERVICE_JOURNEY_PREFIX)) {
      return (
        SERVICE_JOURNEY_PREFIX +
        estimatedVehicleJourneyCode.substring(DATED_SERVICE_JOURNEY_PREFIX.length())
      );
    }
    return estimatedVehicleJourneyCode;
  }

  /**
   * Get code normalized to DatedServiceJourney prefix (for TripOnServiceDate entities).
   * If code has ServiceJourney: prefix, swaps to DatedServiceJourney:.
   */
  public String getDatedServiceJourneyId() {
    if (estimatedVehicleJourneyCode.startsWith(SERVICE_JOURNEY_PREFIX)) {
      return (
        DATED_SERVICE_JOURNEY_PREFIX +
        estimatedVehicleJourneyCode.substring(SERVICE_JOURNEY_PREFIX.length())
      );
    }
    return estimatedVehicleJourneyCode;
  }
}
