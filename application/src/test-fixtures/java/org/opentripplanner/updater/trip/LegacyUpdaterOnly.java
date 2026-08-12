package org.opentripplanner.updater.trip;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Marks a test that asserts behaviour only the legacy trip update implementation has, so that it
 * is skipped when the suite runs against the unified implementation
 * ({@link TripUpdateAdapterUnderTest#UNIFIED}).
 * <p>
 * Every use is a documented difference between the two implementations. State in {@link #value()}
 * what the unified implementation does instead and why the difference is intended. The annotated
 * test should have a companion test, in the same class or named in {@link #value()}, pinning the
 * unified behaviour. Once production runs the unified implementation, each annotated test is a
 * candidate for deletion together with the legacy behaviour it pins.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@DisabledIfSystemProperty(
  named = TripUpdateAdapterUnderTest.PROPERTY,
  matches = "unified",
  disabledReason = "Asserts behaviour that only the legacy trip update implementation has."
)
public @interface LegacyUpdaterOnly {
  /** Why the unified implementation cannot satisfy this test. */
  String value();
}
