package org.opentripplanner.gtfs.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Timeframe;

class TimeframeMapperTest {

  private static final IdFactory A = new IdFactory("A");
  public static final LocalTime START = LocalTime.NOON;
  public static final LocalTime END = START.plusHours(1);

  @Test
  void map() {
    var tf = new Timeframe();
    tf.setTimeframeGroupId(new AgencyAndId("a", "1"));
    tf.setId(new AgencyAndId("a", "1"));
    tf.setStartTime(START);
    tf.setEndTime(END);
    tf.setServiceId("s1");

    var mapper = new TimeframeMapper(A);
    var mapped = mapper.map(tf);
    assertEquals(START, mapped.startTime());
    assertEquals(END, mapped.endTime());
  }
}
