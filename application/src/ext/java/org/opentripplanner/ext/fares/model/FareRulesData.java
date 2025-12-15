package org.opentripplanner.ext.fares.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;

public record FareRulesData(
  List<FareAttribute> fareAttributes,
  List<FareRule> fareRules,
  List<FareLegRule> fareLegRules,
  List<FareTransferRule> fareTransferRules,
  Multimap<FeedScopedId, FeedScopedId> stopAreas,
  SetMultimap<FeedScopedId, LocalDate> serviceIdsToServiceDates
) {
  public FareRulesData() {
    this(
      new ArrayList<>(),
      new ArrayList<>(),
      new ArrayList<>(),
      new ArrayList<>(),
      ArrayListMultimap.create(),
      HashMultimap.create()
    );
  }
}
