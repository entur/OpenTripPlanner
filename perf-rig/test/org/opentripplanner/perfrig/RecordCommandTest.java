package org.opentripplanner.perfrig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;

class RecordCommandTest {

  @TempDir
  Path tmp;

  @Test
  void sameResultsHaveNoDifferences() {
    var results = List.of(result("a", "api", "routing", "raptor"));
    assertEquals(List.of(), RecordCommand.compare(results, results, false));
  }

  @Test
  void aDifferenceInAnyLayerIsFound() {
    var expected = List.of(result("a", "api", "routing", "raptor"));
    assertEquals(1, RecordCommand.compare(expected, List.of(result("a", "API", "routing", "raptor")), false).size());
    assertEquals(1, RecordCommand.compare(expected, List.of(result("a", "api", "ROUTING", "raptor")), false).size());
    assertEquals(1, RecordCommand.compare(expected, List.of(result("a", "api", "routing", "RAPTOR")), false).size());
  }

  @Test
  void missingAndUnexpectedResultsAreFound() {
    var diffs = RecordCommand.compare(
      List.of(result("a", "x", "x", "x")),
      List.of(result("b", "x", "x", "x")),
      false
    );
    assertEquals(List.of("a: missing", "b: unexpected"), diffs);
  }

  /** Only a concurrent run may get access/egress paths with the same cost in another order. */
  @Test
  void orderOfAccessEgressIsIgnoredOnlyForAConcurrentRun() {
    var a = access(1, 60);
    var b = access(2, 60);
    var expected = List.of(result("c", "api", "routing", raptorText(List.of(a, b))));
    var reordered = List.of(result("c", "api", "routing", raptorText(List.of(b, a))));

    assertEquals(1, RecordCommand.compare(expected, reordered, false).size());
    assertEquals(List.of(), RecordCommand.compare(expected, reordered, true));

    var otherPath = List.of(result("c", "api", "routing", raptorText(List.of(a, access(3, 60)))));
    assertEquals(1, RecordCommand.compare(expected, otherPath, true).size());
  }

  @Test
  void selectEveryNthCaseOfEachCategory() throws Exception {
    var cases = new ArrayList<RigCase>();
    for (var category : List.of("x", "y")) {
      for (int i = 0; i < 5; i++) {
        cases.add(new RigCase(category + "-" + i, category, 0, 0, Map.of()));
      }
    }
    File file = tmp.resolve("cases.json").toFile();
    RigCase.write(file, "test", cases);

    var every2 = RecordCommand.selectCases(new Args(new String[] { "--cases", file.getPath(), "--every", "2" }, 0));
    assertEquals(List.of("x-0", "x-2", "x-4", "y-0", "y-2", "y-4"), ids(every2));

    var onlyY = RecordCommand.selectCases(new Args(new String[] { "--cases", file.getPath(), "--only", "y" }, 0));
    assertEquals(List.of("y-0", "y-1", "y-2", "y-3", "y-4"), ids(onlyY));
  }

  @Test
  void casesRoundTrip() throws Exception {
    var variables = new LinkedHashMap<String, Object>();
    variables.put("from", Map.of("place", "NSR:StopPlace:1"));
    variables.put("walkSpeed", 1.3);
    var cases = List.of(new RigCase("p-1", "paging", 2, 1, variables));
    File file = tmp.resolve("cases.json").toFile();
    RigCase.write(file, "test", cases);
    assertEquals(cases, RigCase.read(file));
    assertTrue(Files.readString(file.toPath()).contains("\"next\" : 2"));
  }

  @Test
  void caseDatesAreInTheSecondWeekOfTheService() {
    var december = List.of(1, 2, 3, 5, 6).stream().map(d -> LocalDate.of(2026, 12, d)).toList();
    assertEquals(december, GenCasesCommand.caseDates(LocalDate.of(2026, 11, 23)));
    assertEquals(december, GenCasesCommand.caseDates(LocalDate.of(2026, 11, 22)));
  }

  @Test
  void handWrittenCasesAreReadAfterTheGeneratedOnes() throws Exception {
    File file = tmp.resolve("cases.json").toFile();
    RigCase.write(file, "test", List.of(new RigCase("x-1", "x", 0, 0, Map.of())));
    RigCase.write(tmp.resolve("cases.extra.json").toFile(), "test", List.of(new RigCase("own-1", "own", 0, 0, Map.of())));
    assertEquals(List.of("x-1", "own-1"), ids(RigCase.read(file)));
  }

  private static List<String> ids(List<RigCase> cases) {
    return cases.stream().map(RigCase::id).toList();
  }

  private static CaseRunner.Result result(String id, String api, String routing, String raptor) {
    return new CaseRunner.Result(id, Map.of(), api, routing, raptor, List.of(), List.of(), 0, null, null);
  }

  private static String raptorText(List<RaptorAccessEgress> accesses) {
    var out = new Dump();
    RaptorCapture.accessEgress(out, "access", accesses, i -> "stop" + i);
    return out.toString();
  }

  private static RaptorAccessEgress access(int stop, int duration) {
    return new RaptorAccessEgress() {
      public int stop() {
        return stop;
      }

      public int c1() {
        return duration * 100;
      }

      public int durationInSeconds() {
        return duration;
      }

      public int earliestDepartureTime(int requestedDepartureTime) {
        return requestedDepartureTime;
      }

      public int latestArrivalTime(int requestedArrivalTime) {
        return requestedArrivalTime;
      }

      public boolean hasOpeningHours() {
        return false;
      }
    };
  }
}
