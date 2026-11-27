"""Unit tests of rig.py: python -m unittest discover -s perf-rig/tests (or rig.py unit)."""
import contextlib
import datetime
import io
import json
import math
import random
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import rig  # noqa: E402


def quiet(fn, *args, **kwargs):
    with contextlib.redirect_stdout(io.StringIO()):
        return fn(*args, **kwargs)


class TempDirTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()


class DiffRecordsTest(TempDirTest):
    BLOCKS = {"case-1": ["line a", "line b"], "case-2": ["line c"]}

    def write(self, name, blocks_by_layer=None):
        d = self.tmp / name
        d.mkdir()
        for layer in rig.LAYERS:
            blocks = (blocks_by_layer or {}).get(layer, self.BLOCKS)
            text = "".join(f"### {k}\n" + "".join(l + "\n" for l in v) for k, v in blocks.items())
            (d / f"{layer}.txt").write_text(text, encoding="utf-8")
        return d

    def test_identical(self):
        self.assertTrue(quiet(rig.diff_records, self.write("a"), self.write("b")))

    def test_one_changed_line_in_one_layer(self):
        changed = {"raptor": {"case-1": ["line a", "line B"], "case-2": ["line c"]}}
        b = self.write("b", changed)
        self.assertFalse(quiet(rig.diff_records, self.write("a"), b))
        self.assertEqual(["raptor"], rig.differing_layers(b / "diff-vs-a.txt"))

    def test_reordered_lines_are_a_difference_and_reported_as_such(self):
        reordered = {"routing": {"case-1": ["line b", "line a"], "case-2": ["line c"]}}
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            same = rig.diff_records(self.write("a"), self.write("b", reordered))
        self.assertFalse(same)
        self.assertIn("same lines, other order", out.getvalue())

    def test_missing_result(self):
        missing = {layer: {"case-1": ["line a", "line b"]} for layer in rig.LAYERS}
        self.assertFalse(quiet(rig.diff_records, self.write("a"), self.write("b", missing)))

    def test_subset_of_the_baseline_is_compared_only_where_it_overlaps(self):
        subset = {layer: {"case-1": ["line a", "line b"]} for layer in rig.LAYERS}
        self.assertTrue(quiet(rig.diff_records, self.write("a"), self.write("b", subset), subset=True))

    def test_missing_page_of_a_case_in_the_subset(self):
        paged = {layer: dict(self.BLOCKS, **{"case-1/next1": ["p"]}) for layer in rig.LAYERS}
        subset = {layer: {"case-1": ["line a", "line b"]} for layer in rig.LAYERS}
        self.assertFalse(quiet(rig.diff_records, self.write("a", paged), self.write("b", subset), subset=True))

    def test_extra_result_is_a_difference_also_for_a_subset(self):
        extra = {layer: dict(self.BLOCKS, **{"case-3": ["x"]}) for layer in rig.LAYERS}
        self.assertFalse(quiet(rig.diff_records, self.write("a"), self.write("b", extra), subset=True))


class CompareTestsTest(unittest.TestCase):
    EXPECTED = {
        "app::A#ok": "pass",
        "app::A#broken": "failure",
        "app::A#other": "pass",
        "street::B#ok": "pass",
    }

    def compare(self, actual):
        return quiet(rig.compare_tests, self.EXPECTED, actual)

    def test_same_results_including_the_known_failure(self):
        self.assertTrue(self.compare({"app::A#ok": "pass", "app::A#broken": "failure", "app::A#other": "pass"}))

    def test_newly_failing(self):
        self.assertFalse(self.compare({"app::A#ok": "error", "app::A#broken": "failure", "app::A#other": "pass"}))

    def test_new_test_that_fails(self):
        self.assertFalse(self.compare({"app::A#ok": "pass", "app::A#other": "pass", "app::A#new": "failure"}))

    def test_test_gone_from_a_module_that_ran(self):
        self.assertFalse(self.compare({"app::A#ok": "pass", "app::A#broken": "failure"}))

    def test_module_not_run_is_not_gone(self):
        self.assertTrue(self.compare({"app::A#ok": "pass", "app::A#broken": "pass", "app::A#other": "pass"}))


class SurefireReportsTest(TempDirTest):
    def test_statuses_and_modules(self):
        reports = self.tmp / "raptor" / "router" / "target" / "surefire-reports"
        reports.mkdir(parents=True)
        (reports / "TEST-x.Foo.xml").write_text(
            """<testsuite>
                 <testcase classname="x.Foo" name="ok"/>
                 <testcase classname="x.Foo" name="fails"><failure message="m"/></testcase>
                 <testcase classname="x.Foo" name="errs"><error message="m"/></testcase>
                 <testcase classname="x.Foo" name="skipped"><skipped/></testcase>
               </testsuite>""",
            encoding="utf-8",
        )
        self.assertEqual(
            {
                "raptor/router::x.Foo#ok": "pass",
                "raptor/router::x.Foo#fails": "failure",
                "raptor/router::x.Foo#errs": "error",
                "raptor/router::x.Foo#skipped": "skipped",
            },
            rig.read_surefire_reports(None, self.tmp),
        )

    def test_reports_older_than_the_run_are_ignored(self):
        reports = self.tmp / "m" / "target" / "surefire-reports"
        reports.mkdir(parents=True)
        (reports / "TEST-a.xml").write_text('<testsuite><testcase classname="a" name="t"/></testsuite>')
        self.assertEqual({}, rig.read_surefire_reports(reports.joinpath("TEST-a.xml").stat().st_mtime + 10, self.tmp))


class ModulesOfTest(TempDirTest):
    def test_nearest_pom_below_the_root(self):
        for d in ["", "street", "raptor", "raptor/router"]:
            (self.tmp / d).mkdir(parents=True, exist_ok=True)
            (self.tmp / d / "pom.xml").write_text("<project/>")
        files = ["street/src/main/A.java", "raptor/router/src/B.java", "perf-rig/rig.py", "README.md"]
        self.assertEqual(["raptor/router", "street"], rig.modules_of(files, self.tmp))


class BenchReportTest(TempDirTest):
    """The report of synthetic benchmarks, with known effects and noise."""

    ITEMS = 60

    def fork(self, side, n, factor, rnd, fingerprint=lambda i: "f", fork_noise=0.0):
        fork_factor = 1 + rnd.uniform(-fork_noise, fork_noise)
        items = []
        for i in range(self.ITEMS):
            base = 1e6 * (1 + i)  # 1 ms .. 60 ms
            wall = [base * factor * fork_factor * (1 + rnd.uniform(-0.02, 0.02)) for _ in range(5)]
            items.append({"id": f"item-{i}", "fingerprint": fingerprint(i), "wall": wall, "cpu": wall,
                          "alloc": [base * factor] * 5})
        meta = {"warmup": 1, "iterations": 5, "maxHeapMb": 1, "raptorThreads": 0, "gcCountMeasure": 0,
                "gcMillisMeasure": 0}
        (self.tmp / f"{side}-{n}.json").write_text(json.dumps({"meta": meta, "levels": {"raptor": items}}))

    def report(self, b_factor, forks=3, fork_noise=0.0, b_fingerprint=lambda i: "f"):
        rnd = random.Random(42)
        for n in range(forks):
            self.fork("A", n, 1.0, rnd, fork_noise=fork_noise)
            self.fork("B", n, b_factor, rnd, b_fingerprint, fork_noise=fork_noise)
        return rig.bench_report(self.tmp, "wall")

    def test_same_code(self):
        self.assertIn("no significant difference", self.report(1.0))

    def test_slower(self):
        self.assertIn("B slower", self.report(1.2))

    def test_faster(self):
        self.assertIn("B faster", self.report(0.8))

    def test_one_fork_gives_no_verdict(self):
        report = self.report(0.8, forks=1)
        self.assertIn("no verdict (1 fork)", report)
        self.assertNotIn("B faster", report)

    def test_other_results_make_the_comparison_invalid(self):
        report = self.report(0.8, b_fingerprint=lambda i: "other" if i == 3 else "f")
        self.assertIn("INVALID", report)
        self.assertNotIn("B faster", report)

    def test_other_processes_that_used_the_cpu_during_a_fork(self):
        rnd = random.Random(1)
        self.fork("A", 0, 1.0, rnd)
        self.fork("B", 0, 1.0, rnd)
        for side in "AB":  # each JVM used 100 CPU seconds
            f = self.tmp / f"{side}-0.json"
            result = json.loads(f.read_text())
            result["meta"]["processCpuSeconds"] = 100
            f.write_text(json.dumps(result))
        machine = {"A0": {"cpuSeconds": 110, "seconds": 100}, "B0": {"cpuSeconds": 400, "seconds": 100}}
        (self.tmp / "meta.json").write_text(json.dumps({"dataset": "d", "A": "a", "B": "b", "machine": machine}))
        report = rig.bench_report(self.tmp, "wall")
        self.assertIn("A0 0.10, B0 3.00", report)
        self.assertIn("WARNING: other processes", report)

    def test_the_interval_is_as_wide_as_the_difference_between_forks(self):
        # Every item of a fork shares the factor of the fork, so the items do not narrow the interval
        a_forks = [{"x": 1.0, "y": 2.0}, {"x": 1.0, "y": 2.0}]
        b_forks = [{"x": 1.06, "y": 2.12}, {"x": 0.94, "y": 1.88}]
        gm, lo, hi = rig.ratio_interval(a_forks, b_forks, ["x", "y"])
        self.assertAlmostEqual(1.0, gm, places=2)
        self.assertLess(lo, 0.85)
        self.assertGreater(hi, 1.15)

    def test_one_slow_fork_of_the_same_code_is_not_a_difference(self):
        # Measured 2026-11-27: of four forks of the same code, one ran the street searches 60 % slower
        rnd = random.Random(7)
        self.fork("A", 0, 1.6, rnd)
        self.fork("A", 1, 1.0, rnd)
        self.fork("B", 0, 1.0, rnd)
        self.fork("B", 1, 1.0, rnd)
        report = rig.bench_report(self.tmp, "wall")
        self.assertIn("no significant difference", report)
        self.assertIn("UNEVEN", report)

    def test_no_interval_from_one_fork_of_each_build(self):
        gm, lo, hi = rig.ratio_interval([{"x": 1.0}], [{"x": 2.0}], ["x"])
        self.assertEqual(2.0, gm)
        self.assertTrue(math.isnan(lo) and math.isnan(hi))


class OpsReportTest(TempDirTest):
    def fork(self, side, ops, by_method):
        items = [{"id": k, "fingerprint": "f", "ops": v} for k, v in ops.items()]
        result = {"meta": {"ops": True}, "levels": {"raptor": items}, "opsByMethod": {"raptor": by_method}}
        (self.tmp / f"{side}-0.json").write_text(json.dumps(result))

    def test_less_work_in_one_method(self):
        self.fork("A", {"x": [1000, 1000], "y": [500, 500]}, {"org/o/Foo.bar(I)V": 1200, "org/o/Foo.baz()V": 300})
        self.fork("B", {"x": [900, 900], "y": [500, 500]}, {"org/o/Foo.bar(I)V": 1100, "org/o/Foo.baz()V": 300})
        report = rig.ops_report(self.tmp)
        row = next(line for line in report.splitlines() if line.startswith("raptor ")).split()
        self.assertEqual(["1500", "1400", "0.93333"], row[2:5])
        self.assertEqual(["1", "0"], row[6:8], "items with less and with more ops")
        self.assertIn("-6.667%  org.o.Foo.bar(int)", report)
        self.assertNotIn("Foo.baz()\n  ", report.split("changed most")[1])

    def test_a_benchmark_is_logged_in_the_history(self):
        self.fork("A", {"x": [1000, 1000]}, {})
        self.fork("B", {"x": [900, 900]}, {})
        bench = self.tmp / "bench-1"
        bench.mkdir()
        for f in self.tmp.glob("*.json"):
            f.rename(bench / f.name)
        (bench / "meta.json").write_text(json.dumps({"dataset": "oslo", "A": "base: a", "B": "wt: b"}))
        rig.ops_report(bench)
        rig.append_history(bench)
        rig.append_history(bench)
        header, first, second = (self.tmp / "history.tsv").read_text().splitlines()
        self.assertTrue(header.startswith("benchmark\tdataset\tkind\tlevel\titems\tratio\t"))
        self.assertEqual("bench-1\toslo\tops\traptor\t1\t0.90000\t\t\t0.00000\t0\tbase: a\twt: b", first)
        self.assertEqual(first, second)

    def test_counts_that_differ_less_than_the_tolerance_are_not_a_change(self):
        self.fork("A", {"x": [100000, 100002]}, {"org/o/Foo.bar(I)V": 100001})
        self.fork("B", {"x": [100005, 100004]}, {"org/o/Foo.bar(I)V": 100004})
        report = rig.ops_report(self.tmp)
        row = next(line for line in report.splitlines() if line.startswith("raptor ")).split()
        self.assertEqual(["0", "0"], row[6:8])
        self.assertNotIn("changed most", report)

    def test_method_name(self):
        self.assertEqual("org.x.Foo.bar(Baz, int, long[])", rig.method_name("org/x/Foo.bar(Lorg/x/Baz;I[J)V"))
        self.assertEqual("org.x.Foo.<init>()", rig.method_name("org/x/Foo.<init>()V"))


class CoverageTest(unittest.TestCase):
    METHODS = [
        ("org/opentripplanner/street/Foo.used(I)V", 10, 20, 500),
        ("org/opentripplanner/street/Foo.unused()V", 30, 40, 0),
        ("org/opentripplanner/street/Foo$Inner.unused()V", 50, 60, 0),
    ]
    FOO = "street/src/main/java/org/opentripplanner/street/Foo.java"

    def test_changed_lines_of_a_diff(self):
        diff = "\n".join([
            "diff --git a/x/A.java b/x/A.java", "--- a/x/A.java", "+++ b/x/A.java",
            "@@ -5 +5,3 @@ class A {", "+a", "@@ -20,2 +22,0 @@", "-b",
            "diff --git a/x/Gone.java b/x/Gone.java", "--- a/x/Gone.java", "+++ /dev/null", "@@ -1,9 +0,0 @@",
        ])
        self.assertEqual({"x/A.java": [(5, 7), (22, 22)]}, rig.diff_line_ranges(diff))

    def test_a_changed_method_that_no_case_executes(self):
        self.assertEqual([f"{self.FOO}:30: org.opentripplanner.street.Foo.unused()"],
                         rig.not_executed(self.METHODS, {self.FOO: [(12, 12), (35, 36)]}))

    def test_an_inner_class_belongs_to_the_source_file(self):
        self.assertEqual([f"{self.FOO}:50: org.opentripplanner.street.Foo$Inner.unused()"],
                         rig.not_executed(self.METHODS, {self.FOO: [(55, 55)]}))

    def test_a_changed_class_that_is_not_loaded_and_a_test_that_is_not_counted(self):
        changes = {"street/src/main/java/org/opentripplanner/street/Other.java": [(1, 5)],
                   "street/src/test/java/org/opentripplanner/street/FooTest.java": [(1, 5)]}
        self.assertEqual(1, len(rig.not_executed(self.METHODS, changes)))

    def test_share_of_a_package_that_is_executed(self):
        classes = {"org/opentripplanner/street/Foo", "org/opentripplanner/street/Foo$Inner",
                   "org/opentripplanner/street/NeverLoaded", "org/opentripplanner/street/Unused"}
        row = next(line for line in rig.coverage_by_package(self.METHODS, classes).splitlines()
                   if line.startswith("org.opentripplanner.street "))
        self.assertEqual(["4", "25%", "3", "33%"], row.split()[1:])


class MutateTest(unittest.TestCase):
    def test_places_for_a_bug_in_the_executed_lines(self):
        lines = [
            "  int a(int x) {",                       # 1
            "    if (x < 3 && ok) {  // x < 3",       # 2
            '      log("a < b");',                    # 3 a string
            "    for (int i = 0; i < x; i++) {",      # 4 a loop
            "    return x + 1;",                      # 5
            "  }",                                    # 6
            "  int notExecuted(int x) {",             # 7
            "    return x - 1;",                      # 8
        ]
        self.assertEqual([(2, " < ", " <= "), (2, " && ", " || "), (5, " + ", " - ")],
                         rig.mutation_sites(lines, [(1, 6)]))

    def test_lines_of_the_executed_methods_of_a_source_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            coverage = Path(tmp) / "wt.tsv"
            coverage.write_text("method\tfirstLine\tlastLine\tops\n"
                                "org/opentripplanner/a/Foo.used()V\t10\t20\t5\n"
                                "org/opentripplanner/a/Foo$In.used()V\t30\t40\t5\n"
                                "org/opentripplanner/a/Foo.unused()V\t50\t60\t0\n"
                                "org/opentripplanner/a/Bar.used()V\t70\t80\t5\n")
            self.assertEqual([(10, 20), (30, 40)],
                             rig.executed_lines(coverage, "street/src/main/java/org/opentripplanner/a/Foo.java"))


class PlantedBugTest(TempDirTest):
    """A bug that selftest or mutate plants is removed, also by the next command if they were killed."""

    def setUp(self):
        super().setUp()
        self.saved = rig.DATA, rig.REPO
        rig.DATA = rig.REPO = self.tmp
        (self.tmp / "A.java").write_bytes(b"int a = 1 + 1;\n")

    def tearDown(self):
        rig.DATA, rig.REPO = self.saved
        super().tearDown()

    def test_new_content_of_a_file(self):
        rig.plant("A.java", content=b"int a = 1 - 1;\n")
        self.assertEqual(b"int a = 1 - 1;\n", (self.tmp / "A.java").read_bytes())
        self.assertEqual("A.java", rig.unplant())
        self.assertEqual(b"int a = 1 + 1;\n", (self.tmp / "A.java").read_bytes())
        self.assertIsNone(rig.unplant())

    def test_a_patch(self):
        subprocess.run(["git", "init", "-q"], cwd=self.tmp, check=True)
        patch = self.tmp / "bug.patch"
        patch.write_bytes(b"--- a/A.java\n+++ b/A.java\n@@ -1 +1 @@\n-int a = 1 + 1;\n+int a = 1 - 1;\n")
        rig.plant(patch=patch)
        self.assertEqual(b"int a = 1 - 1;\n", (self.tmp / "A.java").read_bytes())
        self.assertEqual(str(patch), rig.unplant())
        self.assertEqual(b"int a = 1 + 1;\n", (self.tmp / "A.java").read_bytes())


class LockTest(TempDirTest):
    def setUp(self):
        super().setUp()
        self.data, rig.DATA = rig.DATA, self.tmp

    def tearDown(self):
        rig.DATA = self.data
        super().tearDown()

    def test_a_second_command_stops_while_the_first_runs(self):
        lock = rig.acquire_lock("bench", wait=False)
        with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()) as message:
            rig.acquire_lock("check", wait=False)
        self.assertIn("bench since", message.getvalue())
        lock.unlink()
        rig.acquire_lock("check", wait=False).unlink()

    def test_a_command_that_waits_runs_when_the_first_is_done(self):
        first = rig.acquire_lock("bench", wait=False)
        threading.Timer(0.5, first.unlink).start()
        second = rig.acquire_lock("check", wait=True)
        self.assertEqual("check", json.loads(second.read_text())["command"])

    def test_the_lock_of_a_command_that_was_killed_is_taken_over(self):
        killed = subprocess.Popen([sys.executable, "-c", "pass"])
        killed.wait()
        (self.tmp / "rig.lock").write_text(json.dumps({"pid": killed.pid, "command": "bench", "since": "x"}))
        lock = rig.acquire_lock("check", wait=False)
        self.assertEqual("check", json.loads(lock.read_text())["command"])


class ProfileTest(unittest.TestCase):
    def test_events_of_the_main_thread_of_a_flight_recording(self):
        printed = "\n".join([
            "jdk.ExecutionSample {", "  startTime = 01:33:55.866 (2026-11-27)",
            '  sampledThread = "main" (javaThreadId = 3)', '  state = "STATE_RUNNABLE"', "  stackTrace = [",
            "    java.util.BitSet.get(int) line: 650", "    org.o.Foo.bar(String, int) line: 173", "    ...", "  ]", "}",
            "",
            "jdk.ExecutionSample {", "  startTime = 01:33:55.900 (2026-11-27)",
            '  sampledThread = "G1 Conc#0" (javaThreadId = 9)', "  stackTrace = [", "    a.B.c() line: 1", "  ]", "}",
            "",
            "jdk.ObjectAllocationSample {", "  startTime = 01:33:56.010 (2026-11-27)",
            "  objectClass = int[] (classLoader = bootstrap)", "  weight = 504.2 kB",
            '  eventThread = "main" (javaThreadId = 3)', "  stackTrace = [", "    org.o.Foo.<init>(int) line: 77", "  ]",
            "}", ""])
        samples, allocations = rig.parse_flight_events(printed)
        moment = datetime.datetime(2026, 11, 27, 1, 33, 55).timestamp()
        self.assertEqual([(int(moment) * 10 ** 9 + 866_000_000, "java.util.BitSet.get", "org.o.Foo.bar", 1)], samples)
        self.assertEqual([("org.o.Foo.<init>", "", 504200.0)], [a[1:] for a in allocations])

    def test_share_of_each_method_with_its_main_caller(self):
        events = [(0, "a.B.hot", "a.C.first", 1)] * 6 + [(0, "a.B.hot", "a.C.second", 1)] * 2 \
            + [(0, "a.B.cold", "", 1)] * 2
        self.assertEqual(["raptor: time (share of 10)",
                          "  80.00%  a.B.hot   <- 75% from a.C.first",
                          "  20.00%  a.B.cold",
                          ""], rig.top_methods("raptor: time", events, 5))


class SourceStateTest(TempDirTest):
    def git(self, *args):
        subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=self.tmp, check=True,
                       capture_output=True)

    def test_the_state_is_the_content_of_the_otp_sources(self):
        self.git("init", "-q")
        (self.tmp / "A.java").write_text("a")
        (self.tmp / "perf-rig").mkdir()
        (self.tmp / "perf-rig" / "rig.py").write_text("x")
        self.git("add", ".")
        self.git("commit", "-qm", "first")
        committed = rig.source_state(self.tmp)

        (self.tmp / "A.java").write_text("b")
        edited = rig.source_state(self.tmp)
        self.assertNotEqual(committed, edited)
        self.git("add", ".")
        self.assertEqual(edited, rig.source_state(self.tmp), "staged")
        self.git("commit", "-qm", "second")
        self.assertEqual(edited, rig.source_state(self.tmp), "committed")

        (self.tmp / "perf-rig" / "rig.py").write_text("y")
        self.assertEqual(edited, rig.source_state(self.tmp), "the rig is not a part of the build")
        (self.tmp / "New.java").write_text("n")
        self.assertNotEqual(edited, rig.source_state(self.tmp), "untracked file")
        (self.tmp / "New.java").unlink()
        (self.tmp / "A.java").unlink()
        self.assertNotEqual(edited, rig.source_state(self.tmp), "deleted file")


class BySearchTest(unittest.TestCase):
    def test_total_of_each_street_search_and_mode(self):
        a = {"c1:access-WALK": 10, "c2:access-WALK": 30, "c1:direct-BIKE": 5}
        b = {"c1:access-WALK": 10, "c2:access-WALK": 10, "c1:direct-BIKE": 10}
        self.assertEqual(["street: B/A of the total by search and mode: access-WALK 0.500 (2 items), "
                          "direct-BIKE 2.000 (1 items)"], rig.by_search("street", a, b, list(a)))
        self.assertEqual([], rig.by_search("raptor", {"c1#0": 1}, {"c1#0": 1}, ["c1#0"]))


class CpusTest(unittest.TestCase):
    def test_fast_and_slow_cores_and_a_list(self):
        classes = {0: 1, 1: 1, 2: 0, 3: 0}
        self.assertEqual([0, 1], rig.parse_cpus("fast", classes))
        self.assertEqual([2, 3], rig.parse_cpus("slow", classes))
        self.assertEqual([4, 5, 6, 9], rig.parse_cpus("4-6,9", {}))


class FingerprintMismatchTest(unittest.TestCase):
    def test_an_item_with_two_fingerprints(self):
        def fork(prints):
            return {"levels": {"e2e": [{"id": k, "fingerprint": v} for k, v in prints.items()]}}

        forks = {"A": [fork({"a": "1", "b": "2"}), fork({"a": "1", "b": "2"})], "B": [fork({"a": "1", "b": "3"})]}
        self.assertEqual(["b"], rig.fingerprint_mismatches(forks, "e2e"))

    def test_a_replay_that_differs_from_the_recorded_result(self):
        def fork():
            return {"levels": {"raptor": [{"id": "a", "fingerprint": "1", "recorded": "1"},
                                          {"id": "b", "fingerprint": "2", "recorded": "3"}]}}

        self.assertEqual(["b"], rig.fingerprint_mismatches({"A": [fork()], "B": [fork()]}, "raptor"))

    def test_an_item_missing_in_one_fork(self):
        def fork(ids):
            return {"levels": {"raptor": [{"id": k, "fingerprint": "1"} for k in ids]}}

        forks = {"A": [fork(["a", "b#0", "b#1"])], "B": [fork(["a", "b#0"])]}
        self.assertEqual(["b#1"], rig.fingerprint_mismatches(forks, "raptor"))


class JarDirectoryTest(TempDirTest):
    def test_all_files_with_posix_names(self):
        src = self.tmp / "classes"
        (src / "org" / "x").mkdir(parents=True)
        (src / "org" / "x" / "A.class").write_bytes(b"\xca\xfe")
        (src / "logback.xml").write_text("<configuration/>")
        jar = self.tmp / "a.jar"
        rig.jar_directory(src, jar)
        with zipfile.ZipFile(jar) as z:
            self.assertEqual(["logback.xml", "org/x/A.class"], sorted(z.namelist()))
            self.assertEqual(b"\xca\xfe", z.read("org/x/A.class"))


class MedianTest(unittest.TestCase):
    def test_odd_and_even(self):
        self.assertEqual(2, rig.median([3, 1, 2]))
        self.assertEqual(2.5, rig.median([4, 1, 2, 3]))


if __name__ == "__main__":
    unittest.main()
