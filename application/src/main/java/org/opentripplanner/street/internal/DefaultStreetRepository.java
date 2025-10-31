package org.opentripplanner.street.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.model.StreetLimitationParameters;

@Singleton
public class DefaultStreetRepository implements StreetRepository {

  private StreetLimitationParameters streetLimitationParameters =
    StreetLimitationParameters.DEFAULT;

  @Inject
  public DefaultStreetRepository() {}

  @Override
  public StreetLimitationParameters streetLimitationParameters() {
    return streetLimitationParameters;
  }

  @Override
  public void setStreetLimitationParameters(StreetLimitationParameters streetLimitationParameters) {
    this.streetLimitationParameters = streetLimitationParameters;
  }
}
