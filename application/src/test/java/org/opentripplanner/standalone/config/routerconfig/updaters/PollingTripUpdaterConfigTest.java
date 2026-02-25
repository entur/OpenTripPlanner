package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class PollingTripUpdaterConfigTest {

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = PollingTripUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/gtfs-rt"
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
    assertEquals("test-feed", config.feedId());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = PollingTripUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/gtfs-rt",
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = PollingTripUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/gtfs-rt",
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
  }
}
