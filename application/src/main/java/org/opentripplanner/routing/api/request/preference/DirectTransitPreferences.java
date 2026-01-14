package org.opentripplanner.routing.api.request.preference;

import java.util.Objects;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;

public class DirectTransitPreferences {

  /* The next constants are package-local to be readable in the unit-test. */
  static final double DEFAULT_FACTOR = 1.0;
  static final CostLinearFunction DEFAULT_COST_RELAX_FUNCTION = CostLinearFunction.of(
    Cost.costOfMinutes(15),
    1.5
  );

  public static final DirectTransitPreferences DEFAULT = new DirectTransitPreferences(
    false,
    DEFAULT_COST_RELAX_FUNCTION,
    DEFAULT_FACTOR,
    false
  );

  private final boolean enabled;
  private final CostLinearFunction costRelaxFunction;
  private final double extraAccessEgressCostFactor;
  // TODO: Find a better name. A Free access/egress, is also an access/egress...
  private final boolean disableAccessEgress;

  private DirectTransitPreferences(
    boolean enabled,
    CostLinearFunction costRelaxFunction,
    double extraAccessEgressCostFactor,
    boolean disableAccessEgress
  ) {
    this.enabled = enabled;
    this.costRelaxFunction = costRelaxFunction;
    this.extraAccessEgressCostFactor = extraAccessEgressCostFactor;
    this.disableAccessEgress = disableAccessEgress;
  }

  public static Builder of() {
    return new Builder(DEFAULT);
  }

  /// Whether to enable direct transit search
  public boolean enabled() {
    return enabled;
  }

  /// This is used to limit the results from the search. Paths are compared with the cheapest path
  /// in the search window and are included in the result if they fall within the limit given by the
  /// costRelaxFunction.
  public CostLinearFunction costRelaxFunction() {
    return costRelaxFunction;
  }

  /// An extra cost that is used to increase the cost of the access/egress legs for this search.
  public double extraAccessEgressCostFactor() {
    return extraAccessEgressCostFactor;
  }

  public boolean addExtraGeneralizedCostToAccessAndEgress() {
    return extraAccessEgressCostFactor != DEFAULT_FACTOR;
  }

  /// If access egress is disabled the search will only include results that require no access or
  /// egress. I.e. a stop-to-stop search.
  public boolean disableAccessEgress() {
    return disableAccessEgress;
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DirectTransitPreferences that = (DirectTransitPreferences) o;
    return (
      enabled == that.enabled &&
      Double.compare(extraAccessEgressCostFactor, that.extraAccessEgressCostFactor) == 0 &&
      disableAccessEgress == that.disableAccessEgress &&
      Objects.equals(costRelaxFunction, that.costRelaxFunction)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      enabled,
      costRelaxFunction,
      extraAccessEgressCostFactor,
      disableAccessEgress
    );
  }

  public static class Builder {

    private boolean enabled;
    private CostLinearFunction costRelaxFunction;
    private double extraAccessEgressCostFactor;
    private boolean disableAccessEgress;
    public DirectTransitPreferences original;

    public Builder(DirectTransitPreferences original) {
      this.original = original;
      this.enabled = original.enabled;
      this.costRelaxFunction = original.costRelaxFunction;
      this.extraAccessEgressCostFactor = original.extraAccessEgressCostFactor;
      this.disableAccessEgress = original.disableAccessEgress;
    }

    public Builder withEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder withCostRelaxFunction(CostLinearFunction costRelaxFunction) {
      this.costRelaxFunction = costRelaxFunction;
      return this;
    }

    public Builder withExtraAccessEgressCostFactor(double extraAccessEgressCostFactor) {
      this.extraAccessEgressCostFactor = extraAccessEgressCostFactor;
      return this;
    }

    public Builder withDisableAccessEgress(boolean disableAccessEgress) {
      this.disableAccessEgress = disableAccessEgress;
      return this;
    }

    public DirectTransitPreferences build() {
      var value = new DirectTransitPreferences(
        enabled,
        costRelaxFunction,
        extraAccessEgressCostFactor,
        disableAccessEgress
      );
      return original.equals(value) ? original : value;
    }
  }
}
