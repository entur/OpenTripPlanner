package org.opentripplanner.updater.trip.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.regression.RealTimeTripUpdateComparator;
import org.opentripplanner.updater.trip.regression.RecordingTimetableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;

/**
 * Update-scoped task produced by {@link ShadowSiriTripUpdateAdapter#forUpdate}. Per-trip
 * interleaving guarantees that both the primary and the shadow path see identical buffer state:
 * <ol>
 *   <li>Shadow runs first (reads buffer, produces record, does NOT write)</li>
 *   <li>Primary runs second (reads same buffer, produces record, writes to buffer)</li>
 *   <li>Compare the two records</li>
 * </ol>
 */
class ShadowSiriTripUpdateHandler implements SiriTripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ShadowSiriTripUpdateHandler.class);

  @Nullable
  private static final JAXBContext JAXB_CONTEXT = initJaxbContext();

  private final SiriTripUpdateHandler primaryHandler;
  private final SiriNewTripUpdateHandler shadowHandler;
  private final RecordingTimetableSnapshot recordingBuffer;

  @Nullable
  private final Path outputDirectory;

  ShadowSiriTripUpdateHandler(
    SiriTripUpdateHandler primaryHandler,
    SiriNewTripUpdateHandler shadowHandler,
    RecordingTimetableSnapshot recordingBuffer,
    @Nullable Path outputDirectory
  ) {
    this.primaryHandler = primaryHandler;
    this.shadowHandler = shadowHandler;
    this.recordingBuffer = recordingBuffer;
    this.outputDirectory = outputDirectory;
  }

  @Override
  public UpdateResult applyEstimatedTimetable(
    EntityResolver entityResolver,
    String feedId,
    UpdateIncrementality incrementality,
    List<EstimatedTimetableDeliveryStructure> updates
  ) {
    if (updates == null) {
      LOG.warn("updates is null");
      return UpdateResult.empty();
    }

    // Handle FULL_DATASET buffer clear once before the loop
    if (incrementality == FULL_DATASET) {
      recordingBuffer.clear(feedId);
    }

    var comparator = new RealTimeTripUpdateComparator(outputDirectory);
    List<UpdateSuccess> successes = new ArrayList<>();
    List<UpdateError> errors = new ArrayList<>();

    for (var etDelivery : updates) {
      for (var versionFrame : etDelivery.getEstimatedJourneyVersionFrames()) {
        var journeys = versionFrame.getEstimatedVehicleJourneies();
        LOG.debug("Shadow: handling {} EstimatedVehicleJourneys.", journeys.size());
        for (EstimatedVehicleJourney journey : journeys) {
          processOneTrip(journey, entityResolver, feedId, comparator, successes, errors);
        }
      }
    }

    comparator.logSummary();

    LOG.debug("Shadow: message contains {} trip updates", successes.size() + errors.size());
    return UpdateResult.of(successes, errors);
  }

  private void processOneTrip(
    EstimatedVehicleJourney journey,
    EntityResolver entityResolver,
    String feedId,
    RealTimeTripUpdateComparator comparator,
    List<UpdateSuccess> successes,
    List<UpdateError> errors
  ) {
    var tripId = DebugString.of(journey);

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    RealTimeTripUpdate shadowRecord = null;
    String shadowFailureReason = null;
    try {
      shadowRecord = shadowHandler.parseAndDispatch(journey).realTimeTripUpdate();
    } catch (UpdateException e) {
      shadowFailureReason = "failed: " + e.errorType();
      LOG.warn("Shadow failed for trip {}: {}", tripId, e.errorType());
    } catch (Exception e) {
      shadowFailureReason = "exception: " + e.getMessage();
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary handler per-trip. The recording buffer
    // captures the RealTimeTripUpdate the primary produces.
    recordingBuffer.clearLastUpdate();
    var singleDelivery = wrapInDelivery(journey);
    var primaryResult = primaryHandler.applyEstimatedTimetable(
      entityResolver,
      feedId,
      DIFFERENTIAL,
      singleDelivery
    );
    var primaryRecord = recordingBuffer.lastUpdate();

    // 3. COMPARE
    String primaryFailureReason = null;
    if (primaryRecord == null && primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()) {
      primaryFailureReason = primaryResult.errors().getFirst().toString();
    }
    comparator.compare(
      primaryRecord,
      shadowRecord,
      tripId,
      () -> serializeSiriJourney(journey),
      primaryFailureReason,
      shadowFailureReason
    );

    // Return the primary result (single trip -> single result)
    if (primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()) {
      errors.add(primaryResult.errors().getFirst());
    } else if (!primaryResult.successes().isEmpty()) {
      successes.add(primaryResult.successes().getFirst());
    } else {
      successes.add(UpdateSuccess.noWarnings());
    }
  }

  /**
   * Wrap a single {@link EstimatedVehicleJourney} in the delivery structure expected by the
   * primary handler.
   */
  private static List<EstimatedTimetableDeliveryStructure> wrapInDelivery(
    EstimatedVehicleJourney journey
  ) {
    var versionFrame = new EstimatedVersionFrameStructure();
    versionFrame.getEstimatedVehicleJourneies().add(journey);

    var delivery = new EstimatedTimetableDeliveryStructure();
    delivery.getEstimatedJourneyVersionFrames().add(versionFrame);
    return List.of(delivery);
  }

  /**
   * Serialize an {@link EstimatedVehicleJourney} to XML using JAXB. Falls back to
   * {@link DebugString#of} if JAXB marshalling fails.
   */
  static String serializeSiriJourney(EstimatedVehicleJourney journey) {
    if (JAXB_CONTEXT != null) {
      try {
        var marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        var writer = new StringWriter();
        marshaller.marshal(journey, writer);
        return writer.toString();
      } catch (JAXBException e) {
        LOG.debug("JAXB marshalling failed, falling back to DebugString", e);
      }
    }
    return DebugString.of(journey);
  }

  @Nullable
  private static JAXBContext initJaxbContext() {
    try {
      return JAXBContext.newInstance(EstimatedVehicleJourney.class);
    } catch (JAXBException e) {
      LOG.warn("Failed to create JAXBContext for SIRI serialization, will use fallback", e);
      return null;
    }
  }
}
