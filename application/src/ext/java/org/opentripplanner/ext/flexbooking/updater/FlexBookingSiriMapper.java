package org.opentripplanner.ext.flexbooking.updater;

import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.updater.CarpoolSiriMapper;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.ext.flexbooking.FlexBookingRepository;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedVehicleJourney;

/**
 * Maps SIRI {@link EstimatedVehicleJourney} messages to booked flex vehicle tours.
 * <p>
 * The journey is matched to a flex trip via its {@code FramedVehicleJourneyRef} (service journey
 * id + data frame service date) and validated against the static transit model: the referenced
 * trip must exist, be an {@link UnscheduledTrip}, and run on the referenced date. The tour itself
 * — stop coordinates from flexible areas, expected times, per-stop deviation budgets, onboard
 * counts and capacity — is mapped by the reused {@link CarpoolSiriMapper}, producing the tour
 * shape documented on {@link FlexBookingRepository}.
 */
public class FlexBookingSiriMapper {

  private static final Logger LOG = LoggerFactory.getLogger(FlexBookingSiriMapper.class);

  /**
   * Upper bound on a tour's span and straight-line drive time, replacing the carpool default of
   * {@link CarpoolTrip#MAX_TRIP_DURATION}: a flex vehicle's booked tour may legitimately span a
   * whole service day.
   */
  public static final Duration MAX_TOUR_DURATION = Duration.ofHours(24);

  private final String feedId;
  private final TimetableRepository timetableRepository;
  private final CarpoolSiriMapper carpoolMapper;

  public FlexBookingSiriMapper(String feedId, TimetableRepository timetableRepository) {
    this.feedId = feedId;
    this.timetableRepository = timetableRepository;
    this.carpoolMapper = new CarpoolSiriMapper(feedId, MAX_TOUR_DURATION);
  }

  /**
   * Resolves the repository key for the given journey from its {@code FramedVehicleJourneyRef}.
   * Returns empty (after logging a warning) when the reference or its data frame date is missing
   * or unparseable — the journey cannot be matched to a flex trip and must be skipped.
   */
  public Optional<TripIdAndServiceDate> resolveTourKey(EstimatedVehicleJourney journey) {
    var framedRef = journey.getFramedVehicleJourneyRef();
    if (framedRef == null) {
      LOG.warn("EstimatedVehicleJourney without FramedVehicleJourneyRef, skipping");
      return Optional.empty();
    }
    var journeyRef = framedRef.getDatedVehicleJourneyRef();
    if (journeyRef == null || journeyRef.isBlank()) {
      LOG.warn("FramedVehicleJourneyRef without DatedVehicleJourneyRef, skipping");
      return Optional.empty();
    }
    if (framedRef.getDataFrameRef() == null || framedRef.getDataFrameRef().getValue() == null) {
      LOG.warn("Journey {}: missing DataFrameRef, skipping", journeyRef);
      return Optional.empty();
    }
    var dataFrame = framedRef.getDataFrameRef().getValue();
    var serviceDate = ServiceDateUtils.parseStringToOptional(dataFrame);
    if (serviceDate.isEmpty()) {
      LOG.warn("Journey {}: unparseable DataFrameRef '{}', skipping", journeyRef, dataFrame);
      return Optional.empty();
    }
    return Optional.of(
      new TripIdAndServiceDate(new FeedScopedId(feedId, journeyRef), serviceDate.get())
    );
  }

  /**
   * Validates the key against the static transit model. Returns false (after logging a warning)
   * when the referenced trip is unknown, is not an {@link UnscheduledTrip}, or does not run on
   * the referenced service date.
   */
  public boolean isActiveUnscheduledTrip(TripIdAndServiceDate key) {
    var flexTrip = timetableRepository.getFlexTrip(key.tripId());
    if (flexTrip == null) {
      LOG.warn("Journey {} does not reference a known flex trip, skipping", key);
      return false;
    }
    if (!(flexTrip instanceof UnscheduledTrip)) {
      LOG.warn("Flex trip {} is not an unscheduled trip, skipping", key);
      return false;
    }
    var serviceId = flexTrip.getTrip().getServiceId();
    if (
      !timetableRepository.getTripCalendar().listServiceDates(serviceId).contains(key.serviceDate())
    ) {
      LOG.warn("Flex trip {} does not run on the referenced service date, skipping", key);
      return false;
    }
    return true;
  }

  /**
   * Maps the journey to a booked tour. Returns {@code null} when the journey has fewer than two
   * calls — a vehicle with no (or a single) booked call has no commitments to protect, so the
   * caller removes any stored tour and routing falls back to the static flex behavior.
   *
   * @throws IllegalArgumentException when the journey violates the feed contract (missing
   *         {@code EstimatedVehicleJourneyCode} or {@code OperatorRef}, or any of the
   *         {@link CarpoolSiriMapper} validations: out-of-order calls, missing flexible areas,
   *         missing first-departure/last-arrival times, span or beeline drive time over
   *         {@link #MAX_TOUR_DURATION})
   */
  @Nullable
  public CarpoolTrip mapToTour(EstimatedVehicleJourney journey) {
    var estimatedCalls = journey.getEstimatedCalls();
    var calls = estimatedCalls == null ? null : estimatedCalls.getEstimatedCalls();
    if (calls == null || calls.size() < 2) {
      return null;
    }
    if (journey.getEstimatedVehicleJourneyCode() == null) {
      throw new IllegalArgumentException("Journey without EstimatedVehicleJourneyCode");
    }
    if (journey.getOperatorRef() == null) {
      throw new IllegalArgumentException("Journey without OperatorRef");
    }
    return carpoolMapper.mapSiriToCarpoolTrip(journey);
  }
}
