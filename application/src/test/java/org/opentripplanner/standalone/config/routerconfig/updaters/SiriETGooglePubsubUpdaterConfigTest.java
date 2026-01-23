package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import org.junit.jupiter.api.Test;

class SiriETGooglePubsubUpdaterConfigTest {

  private static final String BASE_CONFIG =
    """
    {
      "subscriptionProjectName": "my-project",
      "topicProjectName": "topic-project",
      "topicName": "my-topic"
    }
    """;

  @Test
  void useNewUpdaterImplementationDefaultsToFalse() {
    var config = SiriETGooglePubsubUpdaterConfig.create("test", newNodeAdapterForTest(BASE_CONFIG));
    assertFalse(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeEnabled() {
    var config = SiriETGooglePubsubUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "subscriptionProjectName": "my-project",
          "topicProjectName": "topic-project",
          "topicName": "my-topic",
          "useNewUpdaterImplementation": true
        }
        """
      )
    );
    assertTrue(config.useNewUpdaterImplementation());
  }

  @Test
  void useNewUpdaterImplementationCanBeExplicitlyDisabled() {
    var config = SiriETGooglePubsubUpdaterConfig.create(
      "test",
      newNodeAdapterForTest(
        """
        {
          "subscriptionProjectName": "my-project",
          "topicProjectName": "topic-project",
          "topicName": "my-topic",
          "useNewUpdaterImplementation": false
        }
        """
      )
    );
    assertFalse(config.useNewUpdaterImplementation());
  }
}
