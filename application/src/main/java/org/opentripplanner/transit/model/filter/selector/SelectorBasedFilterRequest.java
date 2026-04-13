package org.opentripplanner.transit.model.filter.selector;

import java.util.List;
import javax.annotation.Nullable;

public interface SelectorBasedFilterRequest<TSelectRequest> {
  @Nullable
  List<TSelectRequest> select();

  @Nullable
  List<TSelectRequest> not();
}
