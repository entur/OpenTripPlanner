package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A filter for {@link TripTimeOnDate} objects using select/not semantics.
 * <p>
 * Select: a TripTimeOnDate must match at least one select criterion (OR between selects).
 * Not: a TripTimeOnDate is excluded if it matches any not criterion.
 * An empty filter (no select, no not) matches everything.
 */
public class TripTimeOnDateFilterRequest {

  private final List<TripTimeOnDateSelectRequest> select;
  private final List<TripTimeOnDateSelectRequest> not;

  private TripTimeOnDateFilterRequest(Builder builder) {
    this.select = List.copyOf(builder.select);
    this.not = List.copyOf(builder.not);
  }

  public static Builder of() {
    return new Builder();
  }

  public List<TripTimeOnDateSelectRequest> select() {
    return select;
  }

  public List<TripTimeOnDateSelectRequest> not() {
    return not;
  }

  /**
   * Returns true if the given TripTimeOnDate matches this filter.
   * <p>
   * A TripTimeOnDate matches if:
   * - It matches at least one select criterion (or select is empty), AND
   * - It does not match any not criterion.
   */
  public boolean matches(TripTimeOnDate tripTime) {
    return (
      (select.isEmpty() || select.stream().anyMatch(s -> s.matches(tripTime))) &&
      not.stream().noneMatch(s -> s.matches(tripTime))
    );
  }

  @Override
  public String toString() {
    if (select.isEmpty() && not.isEmpty()) {
      return "ALL";
    }
    return ToStringBuilder.ofEmbeddedType()
      .addCol("select", select, List.of())
      .addCol("not", not, List.of())
      .toString();
  }

  public static class Builder {

    private final List<TripTimeOnDateSelectRequest> select = new ArrayList<>();
    private final List<TripTimeOnDateSelectRequest> not = new ArrayList<>();

    public Builder addSelect(TripTimeOnDateSelectRequest selectRequest) {
      this.select.add(selectRequest);
      return this;
    }

    public Builder addNot(TripTimeOnDateSelectRequest selectRequest) {
      this.not.add(selectRequest);
      return this;
    }

    public TripTimeOnDateFilterRequest build() {
      return new TripTimeOnDateFilterRequest(this);
    }
  }
}
