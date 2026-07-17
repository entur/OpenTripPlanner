package org.opentripplanner.ext.flexbooking;

import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.addOnboardCount;
import static org.opentripplanner.ext.carpooling.CarpoolEstimatedVehicleJourneyData.forPoint;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_EAST;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import uk.org.siri.siri21.DataFrameRefStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.OperatorRefStructure;

/**
 * Programmatic SIRI-ET fixtures for the flex booking mapper and updater tests, following the
 * feed contract documented in the sandbox doc: a journey per flex service journey, matched via
 * {@code FramedVehicleJourneyRef}, with flexible-area calls for the vehicle anchor and the
 * booked passenger stops.
 */
public class FlexBookingEstimatedVehicleJourneyData {

  /**
   * A journey for the given service journey ref and date with a vehicle anchor call and two
   * booked passenger calls: a pickup 10 minutes and a dropoff 45 minutes after {@code start}.
   * The pickup carries a 15-minute and the dropoff a 10-minute deviation budget via
   * {@code LatestExpectedArrivalTime}.
   */
  public static EstimatedVehicleJourney completeJourney(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = new EstimatedVehicleJourney();
    journey.setEstimatedVehicleJourneyCode(serviceJourneyRef + ":" + serviceDate);
    var operator = new OperatorRefStructure();
    operator.setValue("ENT");
    journey.setOperatorRef(operator);
    journey.setFramedVehicleJourneyRef(framedRef(serviceJourneyRef, serviceDate.toString()));

    var anchor = forPoint(OSLO_CENTER);
    anchor.setAimedDepartureTime(start);

    var pickup = forPoint(OSLO_EAST);
    pickup.setAimedDepartureTime(start.plusMinutes(11));
    pickup.setAimedArrivalTime(start.plusMinutes(10));
    pickup.setExpectedArrivalTime(start.plusMinutes(10));
    pickup.setLatestExpectedArrivalTime(start.plusMinutes(25));
    addOnboardCount(pickup, 2);

    var dropoff = forPoint(OSLO_NORTH);
    dropoff.setAimedDepartureTime(null);
    dropoff.setAimedArrivalTime(start.plusMinutes(45));
    dropoff.setExpectedArrivalTime(start.plusMinutes(45));
    dropoff.setLatestExpectedArrivalTime(start.plusMinutes(55));

    journey.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());
    journey.getEstimatedCalls().getEstimatedCalls().add(anchor);
    journey.getEstimatedCalls().getEstimatedCalls().add(pickup);
    journey.getEstimatedCalls().getEstimatedCalls().add(dropoff);

    return journey;
  }

  public static EstimatedVehicleJourney journeyWithoutFramedRef(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = completeJourney(serviceJourneyRef, serviceDate, start);
    journey.setFramedVehicleJourneyRef(null);
    return journey;
  }

  public static EstimatedVehicleJourney journeyWithBadDataFrame(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = completeJourney(serviceJourneyRef, serviceDate, start);
    journey.setFramedVehicleJourneyRef(framedRef(serviceJourneyRef, "not-a-date"));
    return journey;
  }

  public static EstimatedVehicleJourney cancelledJourney(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = completeJourney(serviceJourneyRef, serviceDate, start);
    journey.setCancellation(Boolean.TRUE);
    return journey;
  }

  /**
   * A journey whose only call is the vehicle anchor — a vehicle with no booked passengers.
   */
  public static EstimatedVehicleJourney journeyWithSingleCall(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = completeJourney(serviceJourneyRef, serviceDate, start);
    var calls = journey.getEstimatedCalls().getEstimatedCalls();
    calls.subList(1, calls.size()).clear();
    return journey;
  }

  /**
   * A journey violating the feed contract in a way the mapper rejects with an exception:
   * the booked calls have no flexible-area stop assignment.
   */
  public static EstimatedVehicleJourney journeyWithoutFlexibleAreas(
    String serviceJourneyRef,
    LocalDate serviceDate,
    ZonedDateTime start
  ) {
    var journey = completeJourney(serviceJourneyRef, serviceDate, start);
    for (var call : journey.getEstimatedCalls().getEstimatedCalls()) {
      call.getDepartureStopAssignments().clear();
      call.getArrivalStopAssignments().clear();
    }
    return journey;
  }

  private static FramedVehicleJourneyRefStructure framedRef(
    String serviceJourneyRef,
    String dataFrame
  ) {
    var framedRef = new FramedVehicleJourneyRefStructure();
    var dataFrameRef = new DataFrameRefStructure();
    dataFrameRef.setValue(dataFrame);
    framedRef.setDataFrameRef(dataFrameRef);
    framedRef.setDatedVehicleJourneyRef(serviceJourneyRef);
    return framedRef;
  }
}
