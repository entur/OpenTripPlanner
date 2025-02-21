package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmEntity;

public class ConstantSpeedMapperTest {

  @Test
  public void constantSpeedCarRouting() {
    OsmTagMapper osmTagMapper = new ConstantSpeedFinlandMapper(20f);

    var slowWay = new OsmEntity();
    slowWay.addTag("highway", "residential");
    assertEquals(20f, osmTagMapper.getCarSpeedForWay(slowWay, true));

    var fastWay = new OsmEntity();
    fastWay.addTag("highway", "motorway");
    fastWay.addTag("maxspeed", "120 kmph");
    assertEquals(20f, osmTagMapper.getCarSpeedForWay(fastWay, true));
  }
}
