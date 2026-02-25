package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class SiriETLiteUpdaterConfigTest {

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = SiriETLiteUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et-lite"
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
    assertEquals("test-feed", config.feedId());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = SiriETLiteUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et-lite",
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = SiriETLiteUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et-lite",
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
  }
}
