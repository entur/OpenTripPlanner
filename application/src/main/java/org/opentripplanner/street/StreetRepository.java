package org.opentripplanner.street;

import java.io.Serializable;
import org.opentripplanner.street.model.StreetModelDetails;

/**
 *
 */
public interface StreetRepository extends Serializable {
  StreetModelDetails streetLimitationParameters();

  void setStreetLimitationParameters(StreetModelDetails streetLimitationParameters);
}
