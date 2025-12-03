package org.opentripplanner.street.search.request;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import org.opentripplanner.framework.model.Units;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 *  TODO: how long does it /really/ take to  an elevator?
 * <p>
 * THIS CLASS IS IMMUTABLE AND THREAD-SAFE.
 */
public final class ElevatorRequest implements Serializable {

  public static final ElevatorRequest DEFAULT = new ElevatorRequest();

  private final int boardTime;
  private final int hopTime;
  private final double reluctance;

  private ElevatorRequest() {
    this.boardTime = 90;
    this.hopTime = 20;
    this.reluctance = 2.0;
  }

  private ElevatorRequest(Builder builder) {
    this.boardTime = Units.duration(builder.boardTime);
    this.hopTime = Units.duration(builder.hopTime);
    this.reluctance = Units.reluctance(builder.reluctance);
  }

  public static Builder of() {
    return DEFAULT.copyOf();
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  /**
   * How long does it take to board an elevator, on average (actually, it probably should be a bit *more*
   * than average, to prevent optimistic trips)? Setting it to "seems like forever," while accurate,
   * will probably prevent OTP from working correctly.
   */
  public int boardTime() {
    return boardTime;
  }

  /**
   * What is the cost of travelling one floor on an elevator?
   * It is assumed that getting off an elevator is completely free.
   */
  public int hopTime() {
    return hopTime;
  }

  public double reluctance() {
    return reluctance;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ElevatorRequest that = (ElevatorRequest) o;
    return (
      boardTime == that.boardTime && hopTime == that.hopTime && reluctance == that.reluctance
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(boardTime, hopTime, reluctance);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(ElevatorRequest.class)
      .addDurationSec("boardTime", boardTime, DEFAULT.boardTime)
      .addDurationSec("hopTime", hopTime, DEFAULT.hopTime)
      .addNum("reluctance", reluctance, DEFAULT.reluctance)
      .toString();
  }

  public static class Builder {

    private final ElevatorRequest original;
    private int boardTime;
    private int hopTime;
    private double reluctance;

    public Builder(ElevatorRequest original) {
      this.original = original;
      this.boardTime = original.boardTime;
      this.hopTime = original.hopTime;
      this.reluctance = original.reluctance;
    }

    public Builder withBoardTime(int boardTime) {
      this.boardTime = boardTime;
      return this;
    }

    public Builder withHopTime(int hopTime) {
      this.hopTime = hopTime;
      return this;
    }

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public Builder apply(Consumer<Builder> body) {
      body.accept(this);
      return this;
    }

    ElevatorRequest build() {
      var value = new ElevatorRequest(this);
      return original.equals(value) ? original : value;
    }
  }
}
