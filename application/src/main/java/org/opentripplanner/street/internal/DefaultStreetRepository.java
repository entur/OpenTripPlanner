package org.opentripplanner.street.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.opentripplanner.street.StreetRepository;
import org.opentripplanner.street.model.StreetModelDetails;

@Singleton
public class DefaultStreetRepository implements StreetRepository {

  private StreetModelDetails streetLimitationParameters = StreetModelDetails.DEFAULT;

  @Inject
  public DefaultStreetRepository() {}

  @Override
  public StreetModelDetails streetLimitationParameters() {
    return streetLimitationParameters;
  }

  @Override
  public void setStreetLimitationParameters(StreetModelDetails streetLimitationParameters) {
    this.streetLimitationParameters = streetLimitationParameters;
  }
}
