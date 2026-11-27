package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.ArrivalParetoSetComparatorFactory;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrival;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.TestStopArivalFactory;
import org.opentripplanner.raptor.util.paretoset.ParetoSet;
import org.opentripplanner.raptor.util.paretoset.ParetoSetEventListener;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * The {@link StopArrivalParetoSet} must do exactly what a {@link ParetoSet} with a comparator of
 * the {@link ArrivalParetoSetComparatorFactory} does. This test adds the same random arrivals to
 * both, and compares the elements, the marker and the events after each arrival. The criteria have
 * few distinct values, so the arrivals often dominate each other or are equal.
 */
class StopArrivalParetoSetTest {

  private static final int DEPARTURE_TIME = TimeUtils.time("10:00");
  private static final int FIRST_ARRIVAL_TIME = TimeUtils.time("10:10");
  private static final DominanceFunction C2_DOMINANCE = (left, right) -> left < right;
  private static final RelaxFunction RELAX_C1 = v -> v + 2;
  private static final int N_SEQUENCES = 100;
  private static final int N_ARRIVALS = 60;

  static Stream<Arguments> testCases() {
    return Stream.of(false, true).flatMap(compareArrivedOnBoard ->
      Stream.of(
        Arguments.of("c1", ArrivalParetoSetComparatorFactory.ofCompareC1(), compareArrivedOnBoard),
        Arguments.of(
          "c1 and c2",
          ArrivalParetoSetComparatorFactory.ofCompareC1AndC2(C2_DOMINANCE),
          compareArrivedOnBoard
        ),
        Arguments.of(
          "c1 relaxed on c2 dominance",
          ArrivalParetoSetComparatorFactory.ofCompareC1RelaxedOnC2Dominance(RELAX_C1, C2_DOMINANCE),
          compareArrivedOnBoard
        )
      )
    );
  }

  @ParameterizedTest(name = "{0}, compare arrived on-board: {2}")
  @MethodSource("testCases")
  void doesTheSameAsParetoSet(
    String name,
    ArrivalParetoSetComparatorFactory<McStopArrival<TestTripSchedule>> comparatorFactory,
    boolean compareArrivedOnBoard
  ) {
    for (int seed = 0; seed < N_SEQUENCES; ++seed) {
      var random = new Random(seed);
      var ids = new IdentityHashMap<McStopArrival<TestTripSchedule>, Integer>();
      var expectedEvents = new EventLog(ids);
      var events = new EventLog(ids);

      var expected = ParetoSet.of(
        compareArrivedOnBoard
          ? comparatorFactory.compareArrivalTimeRoundCostAndOnBoardArrival()
          : comparatorFactory.compareArrivalTimeRoundAndCost(),
        expectedEvents
      );
      var subject = new StopArrivalParetoSet<TestTripSchedule>(
        comparatorFactory.compareCriteria(),
        compareArrivedOnBoard,
        events
      );

      for (int i = 0; i < N_ARRIVALS; ++i) {
        var arrival = randomArrival(random);
        ids.put(arrival, i);
        String step = "sequence " + seed + ", arrival " + i + " " + arrival;

        assertEquals(expected.add(arrival), subject.add(arrival), step);
        assertEquals(expectedEvents.events, events.events, step);
        assertEquals(ids(ids, expected.stream()), ids(ids, subject.stream()), step);
        assertEquals(expected.size(), subject.size(), step);
        assertEquals(expected.isEmpty(), subject.isEmpty(), step);
        assertEquals(expected.hasElementsAfterMarker(), subject.hasElementsAfterMarker(), step);
        assertEquals(
          ids(ids, StreamSupport.stream(expected.elementsAfterMarker().spliterator(), false)),
          ids(ids, StreamSupport.stream(subject.elementsAfterMarker().spliterator(), false)),
          step
        );

        if (random.nextInt(8) == 0) {
          expected.markAtEndOfSet();
          subject.markAtEndOfSet();
        }
      }
    }
  }

  private static McStopArrival<TestTripSchedule> randomArrival(Random random) {
    return TestStopArivalFactory.arrivalPath(
      DEPARTURE_TIME,
      FIRST_ARRIVAL_TIME + 60 * random.nextInt(4),
      1 + random.nextInt(3),
      100 + random.nextInt(5),
      1 + random.nextInt(3),
      random.nextBoolean()
    );
  }

  private static List<Integer> ids(
    Map<McStopArrival<TestTripSchedule>, Integer> ids,
    Stream<McStopArrival<TestTripSchedule>> arrivals
  ) {
    return arrivals.map(ids::get).toList();
  }

  /** The events of a set, with each arrival as the number it was added as. */
  private static class EventLog implements ParetoSetEventListener<McStopArrival<TestTripSchedule>> {

    private final Map<McStopArrival<TestTripSchedule>, Integer> ids;
    private final List<String> events = new ArrayList<>();

    EventLog(Map<McStopArrival<TestTripSchedule>, Integer> ids) {
      this.ids = ids;
    }

    @Override
    public void notifyElementAccepted(McStopArrival<TestTripSchedule> newElement) {
      events.add("accepted " + ids.get(newElement));
    }

    @Override
    public void notifyElementDropped(
      McStopArrival<TestTripSchedule> element,
      McStopArrival<TestTripSchedule> droppedByElement
    ) {
      events.add("dropped " + ids.get(element) + " by " + ids.get(droppedByElement));
    }

    @Override
    public void notifyElementRejected(
      McStopArrival<TestTripSchedule> element,
      McStopArrival<TestTripSchedule> rejectedByElement
    ) {
      events.add("rejected " + ids.get(element) + " by " + ids.get(rejectedByElement));
    }
  }
}
