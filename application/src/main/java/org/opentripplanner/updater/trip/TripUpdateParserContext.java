package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * Context information needed by parsers when converting format-specific real-time messages
 * to the common model.
 */
public final class TripUpdateParserContext {

  private final String feedId;
  private final ZoneId timeZone;
  private final Supplier<LocalDate> localDateNow;

  /**
   * @param feedId The feed ID to use when creating FeedScopedIds
   * @param timeZone The time zone for interpreting times in the feed
   * @param localDateNow Supplier for the current date (allows injection for testing)
   */
  public TripUpdateParserContext(String feedId, ZoneId timeZone, Supplier<LocalDate> localDateNow) {
    this.feedId = Objects.requireNonNull(feedId, "feedId must not be null");
    this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
    this.localDateNow = Objects.requireNonNull(localDateNow, "localDateNow must not be null");
  }

  public String feedId() {
    return feedId;
  }

  public ZoneId timeZone() {
    return timeZone;
  }

  public Supplier<LocalDate> localDateNow() {
    return localDateNow;
  }

  /**
   * Create a FeedScopedId using this context's feed ID.
   */
  public FeedScopedId createId(String entityId) {
    return new FeedScopedId(feedId, entityId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TripUpdateParserContext that = (TripUpdateParserContext) o;
    return (
      Objects.equals(feedId, that.feedId) &&
      Objects.equals(timeZone, that.timeZone) &&
      Objects.equals(localDateNow, that.localDateNow)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(feedId, timeZone, localDateNow);
  }

  @Override
  public String toString() {
    return (
      "TripUpdateParserContext{" +
      "feedId='" +
      feedId +
      '\'' +
      ", timeZone=" +
      timeZone +
      ", localDateNow=" +
      localDateNow +
      '}'
    );
  }
}
