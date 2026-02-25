package org.opentripplanner.standalone.config.routerconfig.updaters.azure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class SiriAzureETUpdaterConfigTest {

  private static final String BASE_CONFIG = """
    {
      "servicebus-url": "Endpoint=sb://example.servicebus.windows.net/",
      "topic": "my-topic",
      "feedId": "test-feed"
    }
    """;

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = SiriAzureETUpdaterConfig.create("test", newNodeAdapterForTest(BASE_CONFIG));
    assertFalse(config.isUseNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = SiriAzureETUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "servicebus-url": "Endpoint=sb://example.servicebus.windows.net/",
          "topic": "my-topic",
          "feedId": "test-feed",
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.isUseNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = SiriAzureETUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "servicebus-url": "Endpoint=sb://example.servicebus.windows.net/",
          "topic": "my-topic",
          "feedId": "test-feed",
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.isUseNewUpdaterImplementation());
  }
}
