package org.opentripplanner.ext.ojp.mapping;

import de.vdv.ojp20.siri.StopPointRefStructure;
import javax.annotation.Nullable;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.transit.model.site.StopLocation;

class StopPointRefMapper {

  private final FeedScopedIdMapper idMapper;

  StopPointRefMapper(FeedScopedIdMapper idMapper) {
    this.idMapper = idMapper;
  }

  @Nullable
  StopPointRefStructure stopPointRef(@Nullable StopLocation stop) {
    if (stop == null) return null;
    return new StopPointRefStructure().withValue(idMapper.mapToApi(stop.getId()));
  }
}
