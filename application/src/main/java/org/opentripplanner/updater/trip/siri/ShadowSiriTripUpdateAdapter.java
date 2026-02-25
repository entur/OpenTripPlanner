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
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.spi.UpdateResult;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.DefaultTripUpdateApplier;
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.LastStopArrivalTimeMatcher;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.UpdateIncrementality;
import org.opentripplanner.updater.trip.handlers.SiriRouteCreationStrategy;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;
import org.opentripplanner.updater.trip.regression.RealTimeTripUpdateComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;

/**
 * Shadow adapter that runs both the primary (legacy) and the new (unified) SIRI-ET adapters on
 * every trip, comparing the {@link RealTimeTripUpdate} records they produce. Only the primary
 * adapter writes to the snapshot buffer; the shadow adapter is read-only.
 * <p>
 * Per-trip interleaving guarantees that both adapters see identical buffer state:
 * <ol>
 *   <li>Shadow runs first (reads buffer, produces record, does NOT write)</li>
 *   <li>Primary runs second (reads same buffer, produces record, writes to buffer)</li>
 *   <li>Compare the two records</li>
 * </ol>
 */
public class ShadowSiriTripUpdateAdapter implements SiriTripUpdateAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShadowSiriTripUpdateAdapter.class);

  @Nullable
  private static final JAXBContext JAXB_CONTEXT = initJaxbContext();

  private final SiriRealTimeTripUpdateAdapter primaryAdapter;
  private final TimetableSnapshotManager snapshotManager;
  private final SiriTripUpdateParser parser;
  private final DefaultTripUpdateApplier applier;
  private final String feedId;

  @Nullable
  private final Path outputDirectory;

  public ShadowSiriTripUpdateAdapter(
    SiriRealTimeTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    TimetableSnapshotManager snapshotManager,
    boolean fuzzyTripMatching,
    String feedId
  ) {
    this(
      primaryAdapter,
      timetableRepository,
      deduplicator,
      snapshotManager,
      fuzzyTripMatching,
      feedId,
      null
    );
  }

  public ShadowSiriTripUpdateAdapter(
    SiriRealTimeTripUpdateAdapter primaryAdapter,
    TimetableRepository timetableRepository,
    DeduplicatorService deduplicator,
    TimetableSnapshotManager snapshotManager,
    boolean fuzzyTripMatching,
    String feedId,
    @Nullable Path outputDirectory
  ) {
    this.primaryAdapter = primaryAdapter;
    this.snapshotManager = snapshotManager;
    this.feedId = feedId;
    this.outputDirectory = outputDirectory;

    TransitEditorService transitEditorService = new DefaultTransitService(
      timetableRepository,
      snapshotManager.getTimetableSnapshotBuffer()
    );

    var tripPatternCache = new TripPatternCache(
      new TripPatternIdGenerator(),
      transitEditorService::findPattern
    );
    this.parser = new SiriTripUpdateParser(feedId, transitEditorService.getTimeZone());

    FuzzyTripMatcher fuzzyMatcher = fuzzyTripMatching
      ? new LastStopArrivalTimeMatcher(
          transitEditorService,
          new StopResolver(transitEditorService),
          transitEditorService.getTimeZone()
        )
      : null;

    this.applier = new DefaultTripUpdateApplier(
      feedId,
      transitEditorService.getTimeZone(),
      transitEditorService,
      deduplicator,
      snapshotManager,
      tripPatternCache,
      fuzzyMatcher,
      new SiriRouteCreationStrategy(feedId)
    );
  }

  @Override
  public UpdateResult applyEstimatedTimetable(
    @Nullable SiriFuzzyTripMatcher fuzzyTripMatcher,
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
      snapshotManager.clearBuffer(feedId);
    }

    var comparator = new RealTimeTripUpdateComparator(outputDirectory);
    List<Result<UpdateSuccess, UpdateError>> results = new ArrayList<>();

    for (var etDelivery : updates) {
      for (var versionFrame : etDelivery.getEstimatedJourneyVersionFrames()) {
        var journeys = versionFrame.getEstimatedVehicleJourneies();
        LOG.debug("Shadow: handling {} EstimatedVehicleJourneys.", journeys.size());
        for (EstimatedVehicleJourney journey : journeys) {
          results.add(
            processOneTrip(journey, fuzzyTripMatcher, entityResolver, feedId, comparator)
          );
        }
      }
    }

    comparator.logSummary();

    LOG.debug("Shadow: message contains {} trip updates", results.size());
    return UpdateResult.ofResults(results);
  }

  private Result<UpdateSuccess, UpdateError> processOneTrip(
    EstimatedVehicleJourney journey,
    @Nullable SiriFuzzyTripMatcher fuzzyTripMatcher,
    EntityResolver entityResolver,
    String feedId,
    RealTimeTripUpdateComparator comparator
  ) {
    var tripId = DebugString.of(journey);

    // 1. SHADOW FIRST: parse + apply but do NOT write to buffer
    RealTimeTripUpdate shadowRecord = null;
    String shadowFailureReason = null;
    try {
      var parseResult = parser.parse(journey);
      if (parseResult.isSuccess()) {
        var applyResult = applier.apply(parseResult.successValue());
        if (applyResult.isSuccess()) {
          shadowRecord = applyResult.successValue().realTimeTripUpdate();
        } else {
          shadowFailureReason = "apply failed: " + applyResult.failureValue();
          LOG.warn("Shadow apply failed for trip {}: {}", tripId, applyResult.failureValue());
        }
      } else {
        shadowFailureReason = "parse failed: " + parseResult.failureValue();
        LOG.warn("Shadow parse failed for trip {}: {}", tripId, parseResult.failureValue());
      }
    } catch (Exception e) {
      shadowFailureReason = "exception: " + e.getMessage();
      LOG.warn("Shadow adapter error for trip {}", tripId, e);
    }

    // 2. PRIMARY SECOND: call through the primary adapter per-trip
    // Install listener to capture the RealTimeTripUpdate the primary produces
    RealTimeTripUpdate[] primaryRecord = { null };
    snapshotManager.setUpdateBufferListener(update -> primaryRecord[0] = update);
    try {
      var singleDelivery = wrapInDelivery(journey);
      var primaryResult = primaryAdapter.applyEstimatedTimetable(
        fuzzyTripMatcher,
        entityResolver,
        feedId,
        DIFFERENTIAL,
        singleDelivery
      );

      // 3. COMPARE
      String primaryFailureReason = null;
      if (
        primaryRecord[0] == null && primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()
      ) {
        primaryFailureReason = primaryResult.errors().getFirst().toString();
      }
      comparator.compare(
        primaryRecord[0],
        shadowRecord,
        tripId,
        () -> serializeSiriJourney(journey),
        primaryFailureReason,
        shadowFailureReason
      );

      // Return the primary result (single trip → single result)
      if (primaryResult.failed() > 0 && !primaryResult.errors().isEmpty()) {
        return Result.failure(primaryResult.errors().getFirst());
      }
      if (!primaryResult.successes().isEmpty()) {
        return Result.success(primaryResult.successes().getFirst());
      }
      return Result.success(UpdateSuccess.noWarnings());
    } finally {
      snapshotManager.setUpdateBufferListener(null);
    }
  }

  /**
   * Wrap a single {@link EstimatedVehicleJourney} in the delivery structure expected by the
   * primary adapter.
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
