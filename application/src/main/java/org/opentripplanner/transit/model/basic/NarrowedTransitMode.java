package org.opentripplanner.transit.model.basic;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * This is used as a data container for when we want more accurate mode matching than is available
 * with just MainAndSubMode. Currently only the additional filtering of replacement is implemented,
 * but this is designed to be capable of holding whatever NeTEx submode/GTFS extended type derived
 * features we implement.
 *
 * @see org.opentripplanner.model.modes.AllowNarrowedTransitModeFilter
 */
public class NarrowedTransitMode {

  TransitMode mode;

  /**
   * null here means that we don't care about what SubMode the trip has
   */
  @Nullable
  SubMode subMode;

  /**
   * null here means that we don't care about whether the trip is a replacement
   */
  @Nullable
  Boolean replacement;

  private static final List<NarrowedTransitMode> ALL = Stream.of(TransitMode.values())
    .map(mode -> new NarrowedTransitMode(mode, null, null))
    .toList();

  public NarrowedTransitMode(
    TransitMode mode,
    @Nullable SubMode subMode,
    @Nullable Boolean replacement
  ) {
    this.mode = mode;
    this.subMode = subMode;
    this.replacement = replacement;
  }

  public static NarrowedTransitMode of(MainAndSubMode mode) {
    return new NarrowedTransitMode(mode.mainMode(), mode.subMode(), null);
  }

  public static List<NarrowedTransitMode> all() {
    return ALL;
  }

  public boolean isMainModeOnly() {
    return (this.subMode == null && this.replacement == null);
  }

  public MainAndSubMode toMainAndSubMode() {
    if (this.replacement != null) {
      throw new IllegalArgumentException("Not convertible to MainAndSubMode");
    }
    return new MainAndSubMode(this.mode, this.subMode);
  }

  public TransitMode getMode() {
    return mode;
  }

  @Nullable
  public SubMode getSubMode() {
    return subMode;
  }

  @Nullable
  public Boolean isReplacement() {
    return replacement;
  }

  public String toString() {
    if (replacement != null) {
      return mode.name() + "::" + (subMode == null ? "" : subMode.name()) + "::" + replacement;
    }
    if (subMode == null) {
      return mode.name();
    }
    return mode.name() + "::" + subMode;
  }

  /**
   * Make sure the String serialization is deterministic by sorting the elements in
   * alphabetic order.
   *
   * @see MainAndSubMode::toString(Collection)
   */
  public static String toString(Collection<NarrowedTransitMode> modes) {
    return modes != null
      ? modes.stream().map(NarrowedTransitMode::toString).sorted().toList().toString()
      : null;
  }
}
