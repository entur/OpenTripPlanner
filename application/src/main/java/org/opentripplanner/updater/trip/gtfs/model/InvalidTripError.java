package org.opentripplanner.updater.trip.gtfs.model;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.framework.error.OtpError;
import org.opentripplanner.updater.spi.UpdateError.UpdateErrorType;

public class InvalidTripError implements OtpError {

  private final FeedScopedId id;
  private final UpdateErrorType updateError;

  public InvalidTripError(FeedScopedId id, UpdateErrorType updateError) {
    this.id = id;
    this.updateError = updateError;
  }

  @Override
  public String errorCode() {
    return updateError.name();
  }

  @Override
  public String messageTemplate() {
    return updateError.name();
  }

  @Override
  public Object[] messageArguments() {
    return new Object[0];
  }

  public UpdateErrorType code() {
    return updateError;
  }

  @Nullable
  public FeedScopedId tripId() {
    return id;
  }
}
