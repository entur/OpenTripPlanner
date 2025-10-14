package org.opentripplanner.street.search.request;

public class ElevatorRequest {

  private final double boardCost;
  private final long boardTime;
  private final double hopCost;
  private final int hopTime;

  private ElevatorRequest() {
    this.boardCost = 0;
    this.boardTime = 0;
    this.hopCost = 0;
    this.hopTime = 0;
  }

  private ElevatorRequest(Builder builder) {
    this.boardCost = builder.boardCost;
    this.boardTime = builder.boardTime;
    this.hopCost = builder.hopCost;
    this.hopTime = builder.hopTime;
  }

  public static Builder of() {
    return new Builder();
  }

  public double boardCost() {
    return boardCost;
  }

  public long boardTime() {
    return boardTime;
  }

  public double hopCost() {
    return hopCost;
  }

  public int hopTime() {
    return hopTime;
  }

  public static class Builder {

    private double boardCost = 0;
    private long boardTime = 0;
    private double hopCost = 0;
    private int hopTime = 0;

    public Builder withBoardCost(double boardCost) {
      this.boardCost = boardCost;
      return this;
    }

    public Builder withBoardTime(long boardTime) {
      this.boardTime = boardTime;
      return this;
    }

    public Builder withHopCost(double hopCost) {
      this.hopCost = hopCost;
      return this;
    }

    public Builder withHopTime(int hopTime) {
      this.hopTime = hopTime;
      return this;
    }

    public ElevatorRequest build() {
      return new ElevatorRequest(this);
    }
  }
}
