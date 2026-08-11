package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.StopSequence;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopResolutionStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.TimeUpdate;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.model.StopTimeUpdate;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Parses the stop time updates of one GTFS-RT TripUpdate into the common model.
 */
final class StopTimeUpdateParser {

  /**
   * How far past the start of its service day a call of a trip that brings its own schedule may
   * lie. GTFS bounds a service day at 48 hours, and a time outside the day the message names is a
   * producer error - typically a timestamp on the wrong day - that would publish the trip on the
   * wrong service day.
   */
  private static final long MAX_ARRIVAL_DEPARTURE_TIME = 48 * 60 * 60;

  private final String feedId;
  private final long startOfService;
  private final boolean reportsOwnSchedule;

  /**
   * @param feedId the feed the stop references are scoped to
   * @param serviceDate the service date the message names. The origin the absolute timestamps are
   *                    measured from is its start of service - noon minus twelve hours and not
   *                    calendar midnight, which differ by the offset shift on a service date
   *                    containing a daylight-saving transition.
   * @param reportsOwnSchedule whether the trip brings its own schedule with it, as NEW, ADDED and
   *                           REPLACEMENT trips do. Such a trip gets a pattern of its own, built
   *                           from the times its calls report, so a call that reports only a
   *                           scheduled time still has to produce one.
   */
  StopTimeUpdateParser(
    String feedId,
    LocalDate serviceDate,
    ZoneId timeZone,
    boolean reportsOwnSchedule
  ) {
    this.feedId = feedId;
    this.startOfService = ServiceDateUtils.asStartOfService(serviceDate, timeZone).toEpochSecond();
    this.reportsOwnSchedule = reportsOwnSchedule;
  }

  public List<ParsedStopTimeUpdate> parse(List<StopTimeUpdate> updates) {
    var result = new ArrayList<ParsedStopTimeUpdate>();

    for (var i = 0; i < updates.size(); i++) {
      result.add(parseStopTimeUpdate(updates.get(i), i));
    }

    return result;
  }

  private ParsedStopTimeUpdate parseStopTimeUpdate(StopTimeUpdate update, int position) {
    var stopId = update.stopId().map(this::createId);
    var assignedStopId = update.assignedStopId().map(this::createId).orElse(null);
    var stopSequence = parseStopSequence(update);

    // Both stop_id and stop_sequence are missing — invalid stop time update
    if (stopId.isEmpty() && stopSequence == null) {
      throw UpdateException.of(UpdateErrorType.INVALID_STOP_REFERENCE);
    }

    // Create StopReference - may have null stopId if only stopSequence is provided
    var stopReference = stopId.isPresent()
      ? StopReference.ofStopId(stopId.get(), assignedStopId)
      : new StopReference(null, assignedStopId, StopResolutionStrategy.DIRECT);

    var builder = ParsedStopTimeUpdate.builder(stopReference);

    if (stopSequence != null) {
      builder.withStopSequence(stopSequence);
    }

    var status = mapStatus(update);
    builder.withStatus(status);

    // An arrival or departure of a trip running to an existing schedule must state a time or a
    // delay - an event stating neither is a producer error, not an unreported call, so the
    // whole entity is rejected rather than letting the interpolator fill the call in. A trip
    // that brings its own schedule is exempt: its calls may state only a scheduled time.
    if (!reportsOwnSchedule && status == ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED) {
      if (!update.isArrivalValid()) {
        throw UpdateException.ofStopPosition(INVALID_ARRIVAL_TIME, position);
      }
      if (!update.isDepartureValid()) {
        throw UpdateException.ofStopPosition(INVALID_DEPARTURE_TIME, position);
      }
    }

    // A trip that brings its own schedule places its calls by the scheduled times it reports,
    // so each of them must lie within the service day the message names - from its start to
    // the 48-hour limit. A time outside it is a producer error, typically a timestamp on the
    // wrong day, that would publish the trip on the wrong service day.
    if (reportsOwnSchedule) {
      if (!isWithinServiceDay(update.scheduledArrivalTimeWithRealTimeFallback())) {
        throw UpdateException.ofStopPosition(INVALID_ARRIVAL_TIME, position);
      }
      if (!isWithinServiceDay(update.scheduledDepartureTimeWithRealTimeFallback())) {
        throw UpdateException.ofStopPosition(INVALID_DEPARTURE_TIME, position);
      }
    }

    parseTimes(update, builder);

    update.stopHeadsign().ifPresent(builder::withStopHeadsign);

    update.pickup().ifPresent(builder::withPickup);
    update.dropoff().ifPresent(builder::withDropoff);

    return builder.build();
  }

  private FeedScopedId createId(String entityId) {
    return new FeedScopedId(feedId, entityId);
  }

  /**
   * The {@code stop_sequence} the call reports, or {@code null} when it reports none. A negative
   * value - the protobuf uint32 read overflowing the Java int - has already been rejected as
   * INVALID_STOP_SEQUENCE by the wrapper validation {@link GtfsRtTripUpdateParser#parse} runs
   * first, so the value object's own check is an invariant here, not a rejection path.
   */
  @Nullable
  private StopSequence parseStopSequence(StopTimeUpdate update) {
    var stopSequence = update.stopSequence();
    return stopSequence.isPresent() ? StopSequence.of(stopSequence.getAsInt()) : null;
  }

  /**
   * Whether the scheduled time the call reports lies within its service day - from the start of
   * service to the 48-hour limit. A call that reports no scheduled time has nothing to hold
   * against the day.
   */
  private boolean isWithinServiceDay(OptionalLong scheduledTime) {
    if (scheduledTime.isEmpty()) {
      return true;
    }
    long secondsPastMidnight = scheduledTime.getAsLong() - startOfService;
    return 0 <= secondsPastMidnight && secondsPastMidnight <= MAX_ARRIVAL_DEPARTURE_TIME;
  }

  private ParsedStopTimeUpdate.StopUpdateStatus mapStatus(StopTimeUpdate update) {
    if (update.isSkipped()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED;
    }
    if (update.isNoData()) {
      return ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA;
    }
    return ParsedStopTimeUpdate.StopUpdateStatus.SCHEDULED;
  }

  private void parseTimes(StopTimeUpdate update, ParsedStopTimeUpdate.Builder builder) {
    var arrival = parseTimeUpdate(
      update.arrivalTime(),
      update.arrivalDelay(),
      update.scheduledArrivalTimeWithRealTimeFallback()
    );
    if (arrival != null) {
      builder.withArrivalUpdate(arrival);
    }

    var departure = parseTimeUpdate(
      update.departureTime(),
      update.departureDelay(),
      update.scheduledDepartureTimeWithRealTimeFallback()
    );
    if (departure != null) {
      builder.withDepartureUpdate(departure);
    }
  }

  /**
   * The update for one end of a call - its arrival or its departure - or {@code null} if the
   * message states nothing about it.
   * <p>
   * A predicted time is taken as it is given, and the scheduled time the message reports alongside
   * it is carried along as the aimed time. A call of a trip that reports its own schedule may state
   * only that scheduled time, and then the call runs to the schedule it reported, offset by the
   * delay if it stated one. Otherwise the only thing left to go by is the delay, which is
   * meaningful just for a trip that already has a scheduled timetable to apply it to.
   *
   * @param time      the predicted time, as an absolute timestamp
   * @param delay     the delay against the scheduled time
   * @param aimedTime the scheduled time as reported by the message, as an absolute timestamp -
   *                  derived from {@code time - delay} where the message states no scheduled time
   */
  @Nullable
  private TimeUpdate parseTimeUpdate(OptionalLong time, OptionalInt delay, OptionalLong aimedTime) {
    ServiceTime aimed = aimedTime.isPresent()
      ? ServiceTime.ofSecondsPastMidnight((int) (aimedTime.getAsLong() - startOfService))
      : null;

    if (time.isPresent()) {
      return TimeUpdate.ofAbsolute(
        ServiceTime.ofSecondsPastMidnight((int) (time.getAsLong() - startOfService)),
        aimed
      );
    }
    if (reportsOwnSchedule && aimed != null) {
      return TimeUpdate.ofAbsolute(aimed.plusSeconds(delay.orElse(0)), aimed);
    }
    if (delay.isPresent()) {
      return TimeUpdate.ofDelay(delay.getAsInt());
    }
    return null;
  }
}
