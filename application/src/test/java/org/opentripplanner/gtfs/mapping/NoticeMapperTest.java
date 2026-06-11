package org.opentripplanner.gtfs.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;

class NoticeMapperTest {

  private static final String NOTICE_ID = "N1";
  private static final String DISPLAY_TEXT = "Platform change";

  private static final org.onebusaway.gtfs.model.Notice GTFS_NOTICE;

  static {
    GTFS_NOTICE = new org.onebusaway.gtfs.model.Notice();
    GTFS_NOTICE.setId(new AgencyAndId(TimetableRepositoryForTest.FEED_ID, NOTICE_ID));
    GTFS_NOTICE.setDisplayText(DISPLAY_TEXT);
  }

  private final NoticeMapper subject = new NoticeMapper(
    new IdFactory(TimetableRepositoryForTest.FEED_ID)
  );

  @Test
  void testMap() {
    var result = subject.map(GTFS_NOTICE);

    assertEquals(TimetableRepositoryForTest.FEED_ID, result.getId().getFeedId());
    assertEquals(NOTICE_ID, result.getId().getId());
    assertEquals(DISPLAY_TEXT, result.text());
  }

  @Test
  void testPublicCodeIsNull() {
    var result = subject.map(GTFS_NOTICE);
    assertEquals(null, result.publicCode());
  }

  @Test
  void testMapCacheReturnsSameInstance() {
    var result1 = subject.map(GTFS_NOTICE);
    var result2 = subject.map(GTFS_NOTICE);
    assertSame(result1, result2);
  }
}
