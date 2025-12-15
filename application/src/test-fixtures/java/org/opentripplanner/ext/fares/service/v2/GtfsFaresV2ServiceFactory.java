package org.opentripplanner.ext.fares.service.v2;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.FareTransferRule;
import org.opentripplanner.ext.fares.service.gtfs.v2.GtfsFaresV2Service;

public class GtfsFaresV2ServiceFactory {

  public static GtfsFaresV2Service build(
    List<FareLegRule> legRules,
    List<FareTransferRule> transferRules,
    Multimap<FeedScopedId, FeedScopedId> stopAreas
  ) {
    return new GtfsFaresV2Service(legRules, transferRules, stopAreas, ImmutableMultimap.of());
  }

  public static GtfsFaresV2Service build(
    List<FareLegRule> legRules,
    List<FareTransferRule> transferRules
  ) {
    return build(legRules, transferRules, ImmutableMultimap.of());
  }
}
