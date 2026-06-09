package org.opentripplanner.core.model.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

public class LocalDateIntervalTest {

  private final LocalDate d0 = LocalDate.of(2020, 1, 1);
  private final LocalDate d1 = LocalDate.of(2020, 1, 7);
  private final LocalDate d2 = LocalDate.of(2020, 1, 15);
  private final LocalDate d3 = LocalDate.of(2020, 2, 1);
  private final LocalDate d4 = LocalDate.of(2020, 2, 7);

  @Test
  void unlimitedFromExclusiveFactory() {
    assertTrue(LocalDateInterval.ofExclusiveEnd(null, null).isUnbounded());
  }

  @Test
  public void ofInclusiveEndFailsIfEndIsBeforeStart() {
    assertThrows(IllegalArgumentException.class, () ->
      LocalDateInterval.ofInclusiveEnd(d1.plusDays(1), d1)
    );
  }

  @Test
  public void ofExclusiveEndFailsIfEndIsBeforeStart() {
    assertThrows(IllegalArgumentException.class, () ->
      LocalDateInterval.ofExclusiveEnd(d1.plusDays(1), d1)
    );
  }

  @Test
  public void ofUnboundedContainsAllDates() {
    LocalDateInterval u = LocalDateInterval.ofUnbounded();
    assertTrue(u.contains(LocalDate.MIN));
    assertTrue(u.contains(LocalDate.MAX.minusDays(1)));
  }

  @Test
  public void isOfUnbounded() {
    assertTrue(LocalDateInterval.ofUnbounded().isUnbounded());
    assertFalse(LocalDateInterval.ofInclusiveEnd(null, d2).isUnbounded());
    assertFalse(LocalDateInterval.ofInclusiveEnd(d2, null).isUnbounded());
  }

  @Test
  public void getStart() {
    assertEquals(d1, LocalDateInterval.ofInclusiveEnd(d1, d2).getInclusiveStart());
    assertEquals(LocalDate.MIN, LocalDateInterval.ofUnbounded().getInclusiveStart());
  }

  @Test
  public void getEnd() {
    assertEquals(d2, LocalDateInterval.ofInclusiveEnd(d1, d2).getEndInclusive());
    assertEquals(LocalDate.MAX, LocalDateInterval.ofUnbounded().getEndInclusive());
  }

  @Test
  public void getEndExclusive() {
    assertEquals(d2.plusDays(1), LocalDateInterval.ofInclusiveEnd(d1, d2).getEndExclusive());
    assertEquals(d2, LocalDateInterval.ofExclusiveEnd(d1, d2).getEndExclusive());
    assertEquals(LocalDate.MAX, LocalDateInterval.ofUnbounded().getEndExclusive());
  }

  @Test
  public void ofInclusiveEndAndOfExclusiveEndAreEquivalent() {
    assertEquals(
      LocalDateInterval.ofInclusiveEnd(d1, d2),
      LocalDateInterval.ofExclusiveEnd(d1, d2.plusDays(1))
    );
  }

  @Test
  public void overlap() {
    LocalDateInterval subject = LocalDateInterval.ofInclusiveEnd(d1, d2);
    LocalDateInterval other;

    // First day overlap
    other = LocalDateInterval.ofInclusiveEnd(d0, d1);
    assertTrue(subject.overlap(other), subject + " should overlap " + other);

    // Last day overlap
    other = LocalDateInterval.ofInclusiveEnd(d2, d3);
    assertTrue(subject.overlap(other), subject + " should overlap " + other);

    // Same periods overlap
    other = LocalDateInterval.ofInclusiveEnd(d1, d2);
    assertTrue(subject.overlap(other), subject + " should overlap " + other);

    // Small period overlap part of large
    other = LocalDateInterval.ofInclusiveEnd(d0, d4);
    assertTrue(subject.overlap(other), subject + " should overlap " + other);

    // Period ending day before, do NOT overlap
    other = LocalDateInterval.ofInclusiveEnd(d0, d1.minusDays(1));
    assertFalse(subject.overlap(other), subject + " should not overlap " + other);

    // Period start day after, do NOT overlap
    other = LocalDateInterval.ofInclusiveEnd(d2.plusDays(1), d3);
    assertFalse(subject.overlap(other), subject + " should not overlap " + other);

    // Period overlap with unlimited
    LocalDateInterval unlimited = LocalDateInterval.ofUnbounded();
    assertTrue(subject.overlap(unlimited), subject + " should overlap unlimited");

    // Unlimited overlap with unlimited
    assertTrue(unlimited.overlap(unlimited), "Unlimited should overlap itself");
  }

  @Test
  public void intersection() {
    LocalDateInterval subject = LocalDateInterval.ofInclusiveEnd(d1, d2);

    assertEquals(subject, subject.intersection(subject));

    // First day in common
    assertEquals(
      LocalDateInterval.ofInclusiveEnd(d1, d1),
      subject.intersection(LocalDateInterval.ofInclusiveEnd(d0, d1))
    );

    // Last day in common
    assertEquals(
      LocalDateInterval.ofInclusiveEnd(d2, d2),
      subject.intersection(LocalDateInterval.ofInclusiveEnd(d2, d3))
    );

    // The intersection of subject and unlimited -> subject
    assertEquals(subject, subject.intersection(LocalDateInterval.ofUnbounded()));
  }

  @Test
  public void intersectionFailsIfIntervalsDoNotOverlap() {
    assertThrows(IllegalArgumentException.class, () ->
      LocalDateInterval.ofInclusiveEnd(d0, d1).intersection(
        LocalDateInterval.ofInclusiveEnd(d1.plusDays(1), d2)
      )
    );
  }

  @Test
  public void contains() {
    LocalDateInterval subject = LocalDateInterval.ofInclusiveEnd(d1, d3);
    assertFalse(subject.contains(d0));
    assertTrue(subject.contains(d1));
    assertTrue(subject.contains(d2));
    assertTrue(subject.contains(d3));
    assertFalse(subject.contains(d4));
  }

  @Test
  public void containsWithExclusiveEnd() {
    LocalDateInterval subject = LocalDateInterval.ofExclusiveEnd(d1, d3);
    assertFalse(subject.contains(d0));
    assertTrue(subject.contains(d1));
    assertTrue(subject.contains(d2));
    assertFalse(subject.contains(d3));
  }

  @Test
  public void testHashCodeAndEquals() {
    LocalDateInterval subject = LocalDateInterval.ofInclusiveEnd(d1, d3);
    LocalDateInterval same = LocalDateInterval.ofInclusiveEnd(d1, d3);
    LocalDateInterval i1 = LocalDateInterval.ofInclusiveEnd(d1, d2);
    LocalDateInterval i2 = LocalDateInterval.ofInclusiveEnd(d2, d3);

    assertEquals(subject, same);
    assertNotEquals(subject, i1);
    assertNotEquals(subject, i2);
    assertEquals(LocalDateInterval.ofInclusiveEnd(null, null), LocalDateInterval.ofUnbounded());

    assertEquals(subject.hashCode(), same.hashCode());
    assertNotEquals(subject.hashCode(), i1.hashCode());
    assertNotEquals(subject.hashCode(), i2.hashCode());
    assertEquals(
      LocalDateInterval.ofInclusiveEnd(null, null).hashCode(),
      LocalDateInterval.ofUnbounded().hashCode()
    );
  }

  @Test
  public void testToStringInclusiveFormat() {
    assertEquals("[2020-01-07, 2020-01-15]", LocalDateInterval.ofInclusiveEnd(d1, d2).toString());
    assertEquals("[MIN, 2020-01-15]", LocalDateInterval.ofInclusiveEnd(null, d2).toString());
    assertEquals("[2020-01-07, MAX]", LocalDateInterval.ofInclusiveEnd(d1, null).toString());
    assertEquals("[MIN, MAX]", LocalDateInterval.ofInclusiveEnd(null, null).toString());
    assertEquals("[MIN, MAX]", LocalDateInterval.ofUnbounded().toString());
  }

  @Test
  public void testToStringExclusiveFormat() {
    assertEquals(
      "[2020-01-07, 2020-01-16)",
      LocalDateInterval.ofExclusiveEnd(d1, d2.plusDays(1)).toString()
    );
    assertEquals("[MIN, 2020-01-15)", LocalDateInterval.ofExclusiveEnd(null, d2).toString());
    assertEquals("[2020-01-07, MAX)", LocalDateInterval.ofExclusiveEnd(d1, null).toString());
    assertEquals("[MIN, MAX)", LocalDateInterval.ofExclusiveEnd(null, null).toString());
  }

  @Test
  public void daysInPeriod() {
    assertEquals(7, LocalDateInterval.ofInclusiveEnd(d0, d1).daysInPeriod());
  }
}
