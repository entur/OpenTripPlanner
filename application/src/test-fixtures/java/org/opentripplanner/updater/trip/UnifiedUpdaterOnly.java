package org.opentripplanner.updater.trip;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Marks a test that asserts behaviour only the unified trip update implementation has, so that it
 * is skipped when the suite runs against the legacy implementation
 * ({@link TripUpdateAdapterUnderTest#LEGACY}, the default).
 * <p>
 * Every use is a documented difference between the two implementations. State in {@link #value()}
 * what the legacy implementation does instead and why the difference is intended - if it is not
 * intended it is a parity defect in the unified implementation, and the fix belongs there rather
 * than here. The set of annotated tests is the visible record of where the two paths diverge, and
 * it should shrink as the migration completes; once production runs the unified implementation the
 * annotation can be deleted along with the legacy adapters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@EnabledIfSystemProperty(
  named = TripUpdateAdapterUnderTest.PROPERTY,
  matches = "unified",
  disabledReason = "Asserts behaviour that only the unified trip update implementation has."
)
public @interface UnifiedUpdaterOnly {
  /** Why the legacy implementation cannot satisfy this test. */
  String value();
}
