package org.opentripplanner.standalone.config.routerconfig.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.standalone.config.framework.json.JsonSupport.newNodeAdapterForTest;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.application.OtpAppException;

class TripUpdateAdapterSelectionConfigTest {

  @Test
  void defaultsToTheLegacyImplementation() {
    var selection = TripUpdateAdapterSelectionConfig.create(newNodeAdapterForTest("{}"));
    assertFalse(selection.useNewUpdaterImplementation());
    assertFalse(selection.shadowComparison());
    assertNull(selection.shadowComparisonReportDirectory());
  }

  @Test
  void selectsTheUnifiedImplementation() {
    var selection = TripUpdateAdapterSelectionConfig.create(
      newNodeAdapterForTest(
        """
        { "useNewUpdaterImplementation": true }
        """
      )
    );
    assertTrue(selection.useNewUpdaterImplementation());
    assertFalse(selection.shadowComparison());
  }

  @Test
  void selectsShadowComparisonWithAReportDirectory() {
    var selection = TripUpdateAdapterSelectionConfig.create(
      newNodeAdapterForTest(
        """
        {
          "shadowComparison": true,
          "shadowComparisonReportDirectory": "/var/otp/shadow-reports"
        }
        """
      )
    );
    assertTrue(selection.shadowComparison());
    assertFalse(selection.useNewUpdaterImplementation());
    assertEquals(Path.of("/var/otp/shadow-reports"), selection.shadowComparisonReportDirectory());
  }

  /**
   * Shadow comparison always serves the legacy implementation, so it cannot honor an explicit
   * request to serve the unified one. Silently picking one reading is worse than rejecting the
   * configuration: an operator flipping to the unified implementation while the shadow flag is
   * still set would keep serving legacy without any sign of it.
   */
  @Test
  void rejectsShadowComparisonCombinedWithTheUnifiedImplementation() {
    var ex = assertThrows(OtpAppException.class, () ->
      TripUpdateAdapterSelectionConfig.create(
        newNodeAdapterForTest(
          """
          {
            "useNewUpdaterImplementation": true,
            "shadowComparison": true
          }
          """
        )
      )
    );
    assertTrue(ex.getMessage().contains("mutually exclusive"), ex.getMessage());
  }

  /** The same rejection through a real updater config, the way router-config parsing hits it. */
  @Test
  void pollingTripUpdaterRejectsTheCombination() {
    assertThrows(OtpAppException.class, () ->
      PollingTripUpdaterConfig.create(
        "test",
        newNodeAdapterForTest(
          """
          {
            "feedId": "test-feed",
            "url": "https://example.com/gtfs-rt",
            "useNewUpdaterImplementation": true,
            "shadowComparison": true
          }
          """
        )
      )
    );
  }
}
