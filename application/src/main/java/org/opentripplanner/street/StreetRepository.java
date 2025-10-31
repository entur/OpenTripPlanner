package org.opentripplanner.street;

import java.io.Serializable;
import org.opentripplanner.street.model.StreetLimitationParameters;

/**
 *
 */
public interface StreetRepository extends Serializable {
  StreetLimitationParameters streetLimitationParameters();

  void setStreetLimitationParameters(StreetLimitationParameters streetLimitationParameters);
}
