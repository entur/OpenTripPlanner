package org.opentripplanner.routing.fares;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.ext.fares.model.FareRulesData;
import org.opentripplanner.model.TransitDataImport;

public interface FareServiceFactory {
  FareService makeFareService();

  void processGtfs(FareRulesData fareRuleService, TransitDataImport transitService);

  void configure(JsonNode config);
}
