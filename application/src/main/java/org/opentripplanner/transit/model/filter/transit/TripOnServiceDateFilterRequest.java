package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.filter.selector.SelectorBasedFilterRequest;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A filter for {@link org.opentripplanner.transit.model.timetable.TripOnServiceDate} objects
 * using select/not semantics.
 * <p>
 * Select: a TripOnServiceDate must match at least one select criterion (OR between selects).
 * Not: a TripOnServiceDate is excluded if it matches any not criterion.
 * A filter with no select and no not matches everything.
 */
public class TripOnServiceDateFilterRequest
  implements SelectorBasedFilterRequest<TripOnServiceDateSelectRequest> {

  @Nullable
  private final List<TripOnServiceDateSelectRequest> select;

  @Nullable
  private final List<TripOnServiceDateSelectRequest> not;

  private TripOnServiceDateFilterRequest(Builder builder) {
    this.select = builder.select.isEmpty() ? null : List.copyOf(builder.select);
    this.not = builder.not.isEmpty() ? null : List.copyOf(builder.not);
  }

  public static Builder of() {
    return new Builder();
  }

  @Override
  public List<TripOnServiceDateSelectRequest> select() {
    return select;
  }

  @Override
  public List<TripOnServiceDateSelectRequest> not() {
    return not;
  }

  @Override
  public String toString() {
    if (select == null && not == null) {
      return "ALL";
    }
    return ToStringBuilder.ofEmbeddedType().addCol("select", select).addCol("not", not).toString();
  }

  public static class Builder {

    private final List<TripOnServiceDateSelectRequest> select = new ArrayList<>();
    private final List<TripOnServiceDateSelectRequest> not = new ArrayList<>();

    public Builder addSelect(TripOnServiceDateSelectRequest selectRequest) {
      this.select.add(selectRequest);
      return this;
    }

    public Builder addNot(TripOnServiceDateSelectRequest selectRequest) {
      this.not.add(selectRequest);
      return this;
    }

    public TripOnServiceDateFilterRequest build() {
      return new TripOnServiceDateFilterRequest(this);
    }
  }
}
