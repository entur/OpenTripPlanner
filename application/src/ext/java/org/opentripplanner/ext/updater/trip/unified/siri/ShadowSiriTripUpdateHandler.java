package org.opentripplanner.ext.updater.trip.unified.siri;

import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.regression.AdapterOutcome;
import org.opentripplanner.ext.updater.trip.unified.regression.RealTimeTripUpdateComparator;
import org.opentripplanner.ext.updater.trip.unified.regression.RecordingTimetableRepository;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.siri.EntityResolver;
import org.opentripplanner.updater.trip.siri.SiriTripUpdateHandler;
import org.opentripplanner.updater.trip.siri.support.TripReferenceHelper;
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
  private final RecordingTimetableRepository recordingBuffer;

  @Nullable
  private final Path outputDirectory;

  ShadowSiriTripUpdateHandler(
    SiriTripUpdateHandler primaryHandler,
    SiriNewTripUpdateHandler shadowHandler,
    RecordingTimetableRepository recordingBuffer,
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

    // Clear the buffer once, before the first journey, so that the primary and the shadow path both
    // start from the same state. The primary asks to clear again on every per-journey invocation
    // below; the recording buffer ignores those repeats for the rest of this batch.
    recordingBuffer.startBatch();
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
          processOneTrip(
            journey,
            entityResolver,
            incrementality,
            feedId,
            comparator,
            successes,
            errors
          );
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
    UpdateIncrementality incrementality,
    String feedId,
    RealTimeTripUpdateComparator comparator,
    List<UpdateSuccess> successes,
    List<UpdateError> errors
  ) {
    var tripId = Objects.toString(TripReferenceHelper.tripReference(journey), "<unknown trip>");

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    AdapterOutcome shadowOutcome;
    try {
      shadowOutcome = new AdapterOutcome.Published(
        shadowHandler.parseAndExecute(journey).realTimeTripUpdate()
      );
    } catch (UpdateException e) {
      shadowOutcome = new AdapterOutcome.Rejected(e.errorType());
      LOG.warn("Shadow failed for trip {}: {}", tripId, e.errorType());
    } catch (Exception e) {
      shadowOutcome = new AdapterOutcome.Crashed(e.toString());
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary handler per-journey, with the incrementality the
    // caller gave us rather than a substitute, so that the comparison runs against the behaviour
    // production has. The recording buffer captures the RealTimeTripUpdate the primary produces
    // and ignores the repeated per-journey requests to clear the buffer.
    recordingBuffer.clearLastUpdate();
    var singleDelivery = wrapInDelivery(journey);
    var primaryResult = primaryHandler.applyEstimatedTimetable(
      entityResolver,
      feedId,
      incrementality,
      singleDelivery
    );
    var primaryOutcome = AdapterOutcome.ofPrimary(primaryResult, recordingBuffer.lastUpdate());

    // 3. COMPARE
    comparator.compare(primaryOutcome, shadowOutcome, tripId, () -> serializeSiriJourney(journey));

    // Forward the primary result (single journey -> single result)
    if (!primaryResult.errors().isEmpty()) {
      errors.add(primaryResult.errors().getFirst());
    } else if (!primaryResult.successes().isEmpty()) {
      successes.add(primaryResult.successes().getFirst());
    } else {
      // The primary reported neither a success nor an error. Counting that as a success would
      // inflate the success rate for a journey nothing was written for.
      errors.add(new UpdateError(null, UpdateErrorType.UNKNOWN, null, null, tripId));
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
   * {@link TripReferenceHelper#tripReference} if JAXB marshalling fails.
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
        LOG.debug("JAXB marshalling failed, falling back to the trip reference", e);
      }
    }
    return Objects.toString(TripReferenceHelper.tripReference(journey), "<unknown trip>");
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
