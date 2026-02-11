package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.List;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.model.RaptorConstants;
import org.opentripplanner.raptor.api.model.RaptorCostConverter;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.utils.time.TimeUtils;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.astar.model.GraphPath;


public class CarpoolAccessEgress implements RoutingAccessEgress {

  private final int startOfTrip;
  private final int endOfTrip;
  private final int stop;
  private final int durationInSeconds;
  private final int cost;
  private final Duration extraTimeForStop;
  private final static int COST_PER_SECOND_OF_WAITING_FOR_PASSENGERS = 2; // NOT SURE WHAT THIS SHOULD BE, TEMPORARY VALUE
  private final List<GraphPath<State, Edge, Vertex>> segments;
  private final TimeAndCost penalty;
  private final double totalWeight;

  public CarpoolAccessEgress(int stop, Duration duration, Duration extraTimeForStop, int startOfTrip, int endOfTrip, List<GraphPath<State, Edge, Vertex>> segments, TimeAndCost penalty) {
    this.startOfTrip = startOfTrip;
    this.endOfTrip = endOfTrip;
    this.stop = stop;
    this.durationInSeconds = (int) duration.getSeconds();
    var combinedWeight = segments.stream().map(
      segment -> segment.states.getLast().getWeight()).reduce(0.0,  Double::sum);
    var costForExtraTimeForStops = extraTimeForStop.getSeconds() * COST_PER_SECOND_OF_WAITING_FOR_PASSENGERS;
    var totalWeight = combinedWeight + costForExtraTimeForStops;
    this.cost = RaptorCostConverter.toRaptorCost(totalWeight);
    this.totalWeight = totalWeight;
    this.segments = segments;
    this.penalty = penalty;
    this.extraTimeForStop = extraTimeForStop;
  }

  @Override
  public int stop() {
    return this.stop;
  }

  @Override
  public int c1() {
    return this.cost;
  }

  @Override
  public int durationInSeconds() {
    return this.durationInSeconds;
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    if(requestedDepartureTime > startOfTrip){
      return RaptorConstants.TIME_NOT_SET;
    }
    return startOfTrip;
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    if(requestedArrivalTime < endOfTrip){
      return RaptorConstants.TIME_NOT_SET;
    }
    return endOfTrip;
  }

  @Override
  public boolean hasOpeningHours() {
    return true;
  }


  public int getStartOfTrip() {return startOfTrip;}
  public int getEndOfTrip() {return endOfTrip;}

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    return new CarpoolAccessEgress(
      this.stop, Duration.ofSeconds(this.durationInSeconds), this.extraTimeForStop,
      this.startOfTrip, this.endOfTrip,this.segments, penalty
    );
  }

  @Override
  public State getLastState() {
    throw new UnsupportedOperationException("Not sure how to support this. We would need to somehow merge the states of the segments. Might be possible.");
  }

  @Override
  public boolean isWalkOnly() {
    return false;
  }

  @Override
  public TimeAndCost penalty() {
    return this.penalty;
  }

  public List<GraphPath<State, Edge, Vertex>> getSegments() {return this.segments;}

  public double getTotalWeight() {return this.totalWeight;}

}

