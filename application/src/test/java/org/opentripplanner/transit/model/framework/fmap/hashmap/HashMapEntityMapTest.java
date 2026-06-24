package org.opentripplanner.transit.model.framework.fmap.hashmap;

import org.opentripplanner.transit.model.framework.fmap.FmapTestEntity;
import org.opentripplanner.transit.model.framework.fmap.MutableEntityMapContractTest;

class HashMapEntityMapTest extends MutableEntityMapContractTest<HashMapEntityMap<FmapTestEntity>> {

  @Override
  protected HashMapEntityMap<FmapTestEntity> newMap() {
    return new HashMapEntityMap<>();
  }
}
