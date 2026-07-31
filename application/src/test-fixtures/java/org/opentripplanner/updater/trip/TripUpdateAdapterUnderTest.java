package org.opentripplanner.updater.trip;

/**
 * Selects which trip update implementation the shared test helpers drive.
 * <p>
 * The default is {@link #LEGACY}, the implementation production runs, so a plain test run is the
 * regression suite for the production path. The unified implementation is covered by a second,
 * narrow Surefire execution that sets {@value #PROPERTY} to {@code unified} and runs only the
 * GTFS-RT and SIRI-ET module tests. That keeps both paths under test without running the whole
 * suite twice.
 * <p>
 * When production switches to the unified implementation, flip the default here and let the
 * second execution cover the legacy path for as long as it is still shipped.
 *
 * @see org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper
 * @see org.opentripplanner.updater.trip.siri.SiriTestHelper
 */
public enum TripUpdateAdapterUnderTest {
  /** The implementation production runs today. */
  LEGACY("legacy"),

  /** The format-independent implementation the unified updater introduces. */
  UNIFIED("unified");

  /** System property naming the implementation under test. Unset means {@link #LEGACY}. */
  public static final String PROPERTY = "otp.test.tripUpdateAdapter";

  private final String propertyValue;

  TripUpdateAdapterUnderTest(String propertyValue) {
    this.propertyValue = propertyValue;
  }

  /**
   * The implementation the current test run drives, as named by {@value #PROPERTY}.
   *
   * @throws IllegalArgumentException if the property is set to an unknown value, so that a typo
   *                                  fails the run instead of silently selecting the default.
   */
  public static TripUpdateAdapterUnderTest current() {
    var value = System.getProperty(PROPERTY);
    if (value == null || value.isBlank()) {
      return LEGACY;
    }
    for (var candidate : values()) {
      if (candidate.propertyValue.equals(value)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
      "Unknown value '%s' for system property %s, expected one of: %s".formatted(
        value,
        PROPERTY,
        String.join(", ", LEGACY.propertyValue, UNIFIED.propertyValue)
      )
    );
  }
}
