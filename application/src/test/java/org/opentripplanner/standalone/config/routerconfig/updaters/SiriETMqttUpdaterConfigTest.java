package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class SiriETMqttUpdaterConfigTest {

  private static final String BASE_CONFIG = """
    {
      "feedId": "test-feed",
      "host": "mqtt.example.com",
      "port": 1883,
      "topic": "siri/et",
      "qos": 1,
      "fuzzyTripMatching": false
    }
    """;

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = SiriETMqttUpdaterConfig.create("test", newNodeAdapterForTest(BASE_CONFIG));
    assertFalse(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = SiriETMqttUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "host": "mqtt.example.com",
          "port": 1883,
          "topic": "siri/et",
          "qos": 1,
          "fuzzyTripMatching": false,
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = SiriETMqttUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "feedId": "test-feed",
          "host": "mqtt.example.com",
          "port": 1883,
          "topic": "siri/et",
          "qos": 1,
          "fuzzyTripMatching": false,
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
  }
}
