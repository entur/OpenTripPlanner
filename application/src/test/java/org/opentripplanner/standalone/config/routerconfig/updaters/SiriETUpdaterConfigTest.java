package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class SiriETUpdaterConfigTest {

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = SiriETUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et"
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
    assertEquals("test-feed", config.feedId());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = SiriETUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et",
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = SiriETUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "url": "https://example.com/siri-et",
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
  }
}
