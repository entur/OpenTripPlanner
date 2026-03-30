package org.opentripplanner.model.modes;

import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.opentripplanner.transit.model.basic.NarrowedTransitMode;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.service.ReplacementHelper;
import org.opentripplanner.utils.tostring.ToStringBuilder;

public class AllowNarrowedTransitModeFilter implements AllowTransitModeFilter {

  NarrowedTransitMode mode;

  public AllowNarrowedTransitModeFilter(NarrowedTransitMode mode) {
    this.mode = mode;
  }

  @Override
  public boolean isModeSelective() {
    return true;
  }

  @Override
  public boolean match(
    TransitMode transitMode,
    SubMode netexSubmode,
    @Nullable Integer gtfsExtendedType
  ) {
    if (mode.getMode() != transitMode) {
      return false;
    }
    // Three-valued boolean logic here. Both mode.getSubMode() and mode.isReplacement() can be null,
    // which means "don't care". If we do care, mode.isReplacement() is compared to
    // ReplacementHelper.isReplacement and mode.getSubMode() is compared to netexSubMode.
    // See truth table:
    //    | T  F  x
    //  --+---------
    //  T | T  T  T
    //  F | T  F  F
    //  x | T  F  T
    if (mode.getSubMode() == null && mode.isReplacement() == null) {
      return true;
    }
    return (
      Boolean.valueOf(ReplacementHelper.isReplacement(netexSubmode, gtfsExtendedType)).equals(
        mode.isReplacement()
      ) ||
      netexSubmode.equals(mode.getSubMode())
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode.getMode(), mode.getSubMode(), mode.isReplacement());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AllowNarrowedTransitModeFilter that = (AllowNarrowedTransitModeFilter) o;
    return new EqualsBuilder()
      .append(mode.getMode(), that.mode.getMode())
      .append(mode.getSubMode(), that.mode.getSubMode())
      .append(mode.isReplacement(), that.mode.isReplacement())
      .isEquals();
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(AllowNarrowedTransitModeFilter.class)
      .addEnum("mode", mode.getMode())
      .addObj("subMode", mode.getSubMode())
      .addObj("isReplacement", mode.isReplacement())
      .toString();
  }
}
