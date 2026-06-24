package org.opentripplanner.transit.model.framework.fmap.mvcc;

import org.opentripplanner.transit.model.framework.fmap.FmapTestEntity;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMapContractTest;

class VersionedEntityMapTest
  extends MutableEntityMapContractTest<VersionedEntityMap<FmapTestEntity>> {

  @Override
  protected VersionedEntityMap<FmapTestEntity> newMap() {
    return new VersionedEntityMap<>();
  }
}
