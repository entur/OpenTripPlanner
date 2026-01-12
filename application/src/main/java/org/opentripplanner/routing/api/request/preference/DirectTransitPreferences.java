package org.opentripplanner.routing.api.request.preference;

import java.util.Objects;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;

public class DirectTransitPreferences {

  /* The next constants are package-local to we readable in the unit-test. */
  static final double NOT_SET = -999.999;
  static final double DEFAULT_FACTOR = 1.0;
  static final CostLinearFunction DEFAULT_COST_RELAX_FUNCTION =
    CostLinearFunction.of(Cost.costOfMinutes(15), 1.5);

  public static final DirectTransitPreferences OFF =
    new DirectTransitPreferences(CostLinearFunction.ZERO, NOT_SET, false);

  public static final DirectTransitPreferences DEFAULT = new DirectTransitPreferences(
    DEFAULT_COST_RELAX_FUNCTION,
    DEFAULT_FACTOR,
    false);

  private final CostLinearFunction costRelaxFunction;
  private final double extraAccessEgressCostFactor;
  // TODO: Find a better name. A Free access/egress, is also an access/egress...
  private final boolean disableAccessEgress;

  public DirectTransitPreferences(CostLinearFunction costRelaxFunction, double extraAccessEgressCostFactor, boolean disableAccessEgress) {
    this.costRelaxFunction = costRelaxFunction;
    this.extraAccessEgressCostFactor = extraAccessEgressCostFactor;
    this.disableAccessEgress = disableAccessEgress;
  }

  public static Builder of() {
    return new Builder(OFF);
  }

  /// Whether to enable direct transit search
  public boolean enabled() {
    return !OFF.equals(this);
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
      Double.compare(extraAccessEgressCostFactor, that.extraAccessEgressCostFactor) == 0 &&
      disableAccessEgress == that.disableAccessEgress &&
      Objects.equals(costRelaxFunction, that.costRelaxFunction)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      costRelaxFunction,
      extraAccessEgressCostFactor,
      disableAccessEgress
    );
  }

  private static <T> T valueOrDefault(T value, T notSet, T defaultValue) {
    return value == notSet ? defaultValue : value;
  }

  public static class Builder {

    private CostLinearFunction costRelaxFunction;
    private double extraAccessEgressCostFactor;
    private boolean disableAccessEgress;
    public DirectTransitPreferences original;

    public Builder(DirectTransitPreferences original) {
      this.original = original;
      this.costRelaxFunction = original.costRelaxFunction;
      this.extraAccessEgressCostFactor = original.extraAccessEgressCostFactor;
      this.disableAccessEgress = original.disableAccessEgress;
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
        valueOrDefault(costRelaxFunction, OFF.costRelaxFunction, DEFAULT.costRelaxFunction),
        valueOrDefault(extraAccessEgressCostFactor, OFF.extraAccessEgressCostFactor, DEFAULT.extraAccessEgressCostFactor),
        valueOrDefault(disableAccessEgress, OFF.disableAccessEgress, DEFAULT.disableAccessEgress)
      );
      return original.equals(value) ? original : value;
    }
  }
}
