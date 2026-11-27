#!/usr/bin/env python
"""perf-rig: correctness and performance rig for OpenTripPlanner. See perf-rig/README.md."""
import argparse
import datetime
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA = Path(os.environ.get("PERF_RIG_DATA", REPO.parent / "perf-rig-data"))
RIG_SRC = REPO / "perf-rig" / "src"
AGENT_SRC = REPO / "perf-rig" / "agent"
# What the ops agent counts: OTP and the libraries that do the work of a request
OPS_PACKAGES = ("org/opentripplanner/,java/util/,java/time/,com/carrotsearch/hppc/,org/locationtech/jts/,"
                "com/google/common/,graphql/")
# Counts of the same work differ a little between JVM runs; a larger difference is a change
OPS_TOLERANCE = 0.001
# A time benchmark compiles in the foreground: the JIT compiler then compiles the same way in each
# run. With background compilation on the few pinned cores, the Raptor forks of the same code
# differed by 2.1 % (standard deviation) instead of 0.8 %, and were 10 % slower.
TIME_JVM = ["-Xbatch"]
# A time benchmark is not reliable if other processes use more than this many cores meanwhile,
# or if the slowest fork takes this much longer than the fastest
OTHER_CPU_LIMIT = 1.0
UNEVEN_FORKS = 1.15
CASES_DIR = REPO / "perf-rig" / "cases"
MAIN = "org.opentripplanner.perfrig.RigMain"
WORKTREE_BUILD = "wt"


# ---------------------------------------------------------------------------------------------
# Small helpers


def log(msg):
    print(f"[rig.py] {msg}", flush=True)


def die(msg, code=2):
    print(f"[rig.py] ERROR: {msg}", file=sys.stderr, flush=True)
    sys.exit(code)


def now():
    return datetime.datetime.now().strftime("%Y%m%d-%H%M%S")


def git(*args, cwd=REPO, env=None):
    return subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True, encoding="utf-8", errors="replace",
                          check=True).stdout.strip()


def win(path):
    """A path for Java on Windows: forward slashes, drive letter."""
    return Path(path).resolve().as_posix()


def run_logged(cmd, logfile, cwd=REPO, echo=None, high_priority=False):
    """Run a command with its output in logfile; echo the lines that match the regex echo."""
    logfile.parent.mkdir(parents=True, exist_ok=True)
    pattern = re.compile(echo) if echo else None
    # High priority (Windows): other processes do not take the processor from a benchmark
    flags = subprocess.HIGH_PRIORITY_CLASS if high_priority and os.name == "nt" else 0
    with open(logfile, "w", encoding="utf-8", errors="replace", buffering=1) as out:
        out.write(" ".join(str(c) for c in cmd) + "\n")
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace", creationflags=flags)
        for line in proc.stdout:
            out.write(line)
            if pattern and pattern.search(line):
                print(line.rstrip(), flush=True)
        return proc.wait()


# The commands that only read results. All other commands build or run OTP, one at a time: two
# would share the build directories and disturb each other's measurements.
READ_ONLY = {"builds", "diff", "report", "graph-diff"}


def acquire_lock(command, wait):
    """Create the lock file of the data directory, or stop (or wait) if another command holds it."""
    import time
    lock = DATA / "rig.lock"
    DATA.mkdir(parents=True, exist_ok=True)
    while True:
        try:
            with open(lock, "x", encoding="utf-8") as f:
                json.dump({"pid": os.getpid(), "command": command, "since": now()}, f)
            return lock
        except FileExistsError:
            pass
        try:
            holder = json.loads(lock.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            holder = None  # being written or removed right now
        if holder and not process_alive(holder["pid"]):
            lock.unlink(missing_ok=True)  # left by a command that was killed
        elif holder and not wait:
            die(f"Another rig.py command is running: {holder['command']} since {holder['since']} "
                f"(pid {holder['pid']}, {lock}). Wait for it, or start this one with --wait.")
        else:
            time.sleep(2)


def process_alive(pid):
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    import ctypes
    kernel32 = ctypes.windll.kernel32
    kernel32.OpenProcess.restype = ctypes.c_void_p
    handle = kernel32.OpenProcess(0x1000, False, pid)  # the right to query the process
    if not handle:
        return False
    code = ctypes.c_ulong()
    kernel32.GetExitCodeProcess(ctypes.c_void_p(handle), ctypes.byref(code))
    kernel32.CloseHandle(ctypes.c_void_p(handle))
    return code.value == 259  # still active


def retry(action, seconds=30):
    """
    Do a file operation again while Windows denies it: a virus scanner, or a process that has just
    ended, can hold files for a moment.
    """
    import time
    deadline = time.time() + seconds
    while True:
        try:
            return action()
        except PermissionError:
            if time.time() > deadline:
                raise
            time.sleep(0.5)


def tail(path, n=40):
    lines = Path(path).read_text(encoding="utf-8", errors="replace").splitlines()
    return "\n".join(lines[-n:])


# ---------------------------------------------------------------------------------------------
# Builds: a build is a directory with otp-classpath.txt (the OTP classes and dependencies, one
# entry per line) and meta.json. "wt" is the live build of this working tree; other builds are
# frozen copies. The rig itself is compiled into each build from the CURRENT rig sources, so two
# builds that are compared always run the same rig code.


def build_dir(name):
    return DATA / "builds" / name


def build_meta(name):
    """What a build was made from (meta.json), {} if there is no such build."""
    f = build_dir(name) / "meta.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}


def read_classpath(name):
    """The classpath of a build: the rig classes, then the OTP classes and dependencies."""
    f = build_dir(name) / "otp-classpath.txt"
    if not f.exists():
        die(f"No build '{name}'. Run: rig.py build" + ("" if name == WORKTREE_BUILD else f" and rig.py freeze {name}"))
    otp = [line for line in f.read_text(encoding="utf-8").splitlines() if line]
    rig = build_dir(name) / "rig-classes"
    marker = build_dir(name) / "rig-sources.sha"
    current = rig_sources_hash()
    if not marker.exists() or marker.read_text() != current:
        log(f"Compiling the rig for build '{name}'")
        error = compile_rig(otp, rig)
        if error is None:
            marker.write_text(current)
        elif name != WORKTREE_BUILD and rig.exists():
            # The working tree changed code the rig uses, and the rig was changed with it
            log(f"WARNING: the rig does not compile against build '{name}' any more. Using the rig classes "
                f"compiled for it earlier, which may not do the same as the current rig.\n{error[:1000]}")
        else:
            print(error)
            die("Compiling the rig failed")
    return [win(rig)] + otp


def rig_sources_hash():
    h = hashlib.sha256()
    for p in sorted(RIG_SRC.rglob("*.java")):
        h.update(p.relative_to(RIG_SRC).as_posix().encode())
        h.update(p.read_bytes())
    return h.hexdigest()


def maven_compile(fast):
    cmd = [shutil.which("mvn") or "mvn", "-o", "-B", "-pl", "application", "-am", "compile",
           "dependency:build-classpath", "-Dmdep.outputFile=target/rig-classpath.txt",
           "-Dmdep.includeScope=runtime", "-Dps", "-Dcs", "-Dplugin.frontend.skip=true"]
    if fast:
        # Compile only the changed files, do not recompile a module because a dependency changed
        cmd.append("-Dmaven.compiler.useIncrementalCompilation=false")
    logfile = DATA / "logs" / f"maven-{now()}.log"
    log(f"Maven compile{' (fast)' if fast else ''}, log: {logfile}")
    started = datetime.datetime.now()
    code = run_logged(cmd, logfile, echo=r"\[ERROR\]|Compiling \d+ source|BUILD")
    if code != 0:
        print(tail(logfile))
        die("Maven compile failed")
    log(f"Maven compile done in {(datetime.datetime.now() - started).seconds} s")


def compile_rig(classpath, out_dir):
    """
    Compile the rig sources against the given OTP classpath. Returns the compiler output if it
    fails; the classes compiled earlier are kept then.
    """
    new_dir = out_dir.with_name(out_dir.name + ".new")
    if new_dir.exists():
        shutil.rmtree(new_dir)
    new_dir.mkdir(parents=True)
    sources = sorted(str(p) for p in RIG_SRC.rglob("*.java"))
    argfile = out_dir.parent / "javac-args.txt"
    argfile.write_text(
        "-cp \"" + ";".join(classpath) + "\"\n" + "\n".join("\"" + win(s) + "\"" for s in sources) + "\n",
        encoding="utf-8")
    cmd = ["javac", "--release", "25", "-proc:none", "-nowarn", "-encoding", "UTF-8",
           "-d", win(new_dir), f"@{win(argfile)}"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        shutil.rmtree(new_dir)
        return result.stdout + result.stderr
    if out_dir.exists():
        retry(lambda: shutil.rmtree(out_dir))
    retry(lambda: new_dir.rename(out_dir))
    return None


def cmd_build(args):
    sources = source_state()  # before compiling: a file edited meanwhile makes the build out of date
    maven_compile(args.fast)
    otp = [win(REPO / "application" / "target" / "classes")]
    otp += (REPO / "application" / "target" / "rig-classpath.txt").read_text(encoding="utf-8").strip().split(";")
    otp = [win(p) for p in otp if p]
    target = build_dir(WORKTREE_BUILD)
    target.mkdir(parents=True, exist_ok=True)
    (target / "otp-classpath.txt").write_text("\n".join(otp) + "\n", encoding="utf-8")
    (target / "rig-sources.sha").unlink(missing_ok=True)
    write_build_meta(target, sources, args.fast)
    read_classpath(WORKTREE_BUILD)
    log(f"Build '{WORKTREE_BUILD}' ready ({describe_build(WORKTREE_BUILD)})")


def write_build_meta(target, sources, fast):
    meta = dict(git_state(), time=now(), sources=sources, fast=fast, serializationVersion=serialization_version())
    (target / "meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")


def git_state():
    """The commit of the working tree and whether it has uncommitted changes: the label of a build."""
    status = git("status", "--porcelain", "--untracked-files=no")
    diff_hash = hashlib.sha256(git("diff", "HEAD").encode()).hexdigest()[:12] if status else ""
    return {"head": git("rev-parse", "HEAD"), "dirty": bool(status), "diffHash": diff_hash,
            "branch": git("rev-parse", "--abbrev-ref", "HEAD")}


def source_state(repo=REPO):
    """
    A hash of the content of all OTP source files of the working tree (everything but perf-rig,
    staged or not, and untracked files): what build 'wt' is compiled from.
    """
    files = ["-c", "core.quotepath=false", "ls-files"]
    paths = ["--", ".", ":!perf-rig"]
    blobs = {}
    for line in git(*files, "--stage", *paths, cwd=repo).splitlines():
        entry, path = line.split("\t")
        blobs[path] = entry.split()[1]
    # The files that differ from the index, and the untracked ones, are hashed as they are now
    changed = git(*files, "--modified", "--others", "--exclude-standard", *paths, cwd=repo).splitlines()
    present = [f for f in changed if (repo / f).is_file()]
    hashed = subprocess.run(["git", "hash-object", "--stdin-paths"], cwd=repo, input="\n".join(present),
                            capture_output=True, encoding="utf-8", check=True).stdout.split()
    blobs.update({f: "deleted" for f in changed})
    blobs.update(zip(present, hashed))
    return hashlib.sha256("\n".join(f"{f} {blob}" for f, blob in sorted(blobs.items())).encode()).hexdigest()[:16]


def require_current(build):
    """
    Stop if build 'wt' is used after the OTP sources changed: its classes are not that code. Or
    after its classes were removed: the checkout of another commit clears them (a git hook).
    """
    if build != WORKTREE_BUILD:
        return
    meta = build_meta(build)
    if meta and meta.get("sources") != source_state():
        die("The OTP sources changed after build 'wt' was compiled. Run: rig.py build [--fast]")
    classpath = build_dir(build) / "otp-classpath.txt"
    entries = [Path(e) for e in classpath.read_text(encoding="utf-8").split() if e.startswith(win(REPO))]
    if any(not e.exists() or (e.is_dir() and not any(e.iterdir())) for e in entries):
        die("Classes of build 'wt' are gone (the checkout of another commit removes them). Run: rig.py build")


def serialization_version():
    pom = (REPO / "pom.xml").read_text(encoding="utf-8")
    m = re.search(r"<otp.serialization.version.id>(\d+)<", pom)
    return m.group(1) if m else "?"


def describe_build(name):
    m = build_meta(name)
    if not m:
        return "?"
    return (f"{m['branch']} {m['head'][:10]}{' +dirty ' + m['diffHash'] if m['dirty'] else ''}, built {m['time']}"
            + (" with --fast" if m.get("fast") else ""))


def cmd_freeze(args):
    """Copy the classes of the working-tree build, so it can be compared with later builds."""
    name = args.name
    if name == WORKTREE_BUILD:
        die("Pick another name")
    source = build_dir(WORKTREE_BUILD)
    f = source / "otp-classpath.txt"
    if not f.exists():
        die("No build 'wt'. Run: rig.py build")
    require_current(WORKTREE_BUILD)
    entries = [line for line in f.read_text(encoding="utf-8").splitlines() if line]
    target = build_dir(name)
    if target.exists():
        if not args.force:
            die(f"Build '{name}' exists; use --force to replace it")
        shutil.rmtree(target)
    (target / "classes").mkdir(parents=True)
    frozen = []
    repo = win(REPO)
    for i, entry in enumerate(entries):
        if entry.startswith(repo):
            # One jar per class directory: creating thousands of small files is slow on Windows
            module = Path(entry).parent.parent.name if entry.endswith("/classes") else Path(entry).name
            dest = target / "classes" / f"{i:02d}-{module}.jar"
            jar_directory(Path(entry), dest)
            frozen.append(win(dest))
        else:
            frozen.append(entry)
    (target / "otp-classpath.txt").write_text("\n".join(frozen) + "\n", encoding="utf-8")
    # The sources are still those of the build (require_current), so it gets the label of the working
    # tree as it is now: changes that were committed after the build are in that commit
    meta = dict(build_meta(WORKTREE_BUILD), **git_state())
    (target / "meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    log(f"Froze the working-tree build as '{name}' ({describe_build(name)})")
    if build_meta(name)["dirty"]:
        log("WARNING: built from uncommitted changes, so no commit has the code of this build. "
            "'test --scope changed' counts everything changed since its HEAD commit.")
    if build_meta(name).get("fast"):
        log("WARNING: compiled with --fast, which can leave classes out of date. Freeze a full build.")


def jar_directory(directory, jar, manifest=None):
    import zipfile
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_STORED) as z:
        if manifest:
            z.writestr("META-INF/MANIFEST.MF", manifest)
        for path in sorted(directory.rglob("*")):
            if path.is_file():
                z.write(path, path.relative_to(directory).as_posix())


def ops_agent():
    """The jar of the agent that counts bytecode instructions (perf-rig/agent), built when it changes."""
    out = DATA / "builds" / "ops-agent"
    sources = sorted(AGENT_SRC.rglob("*.java"))
    current = hashlib.sha256(b"".join(p.read_bytes() for p in sources)).hexdigest()
    marker = out / "sources.sha"
    if not marker.exists() or marker.read_text() != current:
        log("Compiling the ops agent")
        if out.exists():
            shutil.rmtree(out)
        # The counters are a jar of their own: the agent puts it on the boot class path
        boot = [str(s) for s in sources if s.parent.name == "rigops"]
        agent = [str(s) for s in sources if s.parent.name != "rigops"]
        for directory, files in [(out / "boot", boot), (out / "agent", agent)]:
            directory.mkdir(parents=True)
            subprocess.run(["javac", "--release", "25", "-nowarn", "-encoding", "UTF-8", "-cp", win(out / "boot"),
                            "-d", win(directory), *files], check=True)
        jar_directory(out / "boot", out / "ops-boot.jar")
        jar_directory(out / "agent", out / "ops-agent.jar", manifest=(
            "Manifest-Version: 1.0\n"
            "Premain-Class: org.opentripplanner.perfrig.agent.OpsAgent\n"
            "Boot-Class-Path: ops-boot.jar\n"
            "Can-Retransform-Classes: true\n"))
        marker.write_text(current)
    return out / "ops-agent.jar"


def ops_jvm():
    """The JVM arguments that run a rig command with the ops agent."""
    return [f"-javaagent:{win(ops_agent())}={OPS_PACKAGES}"]


def cmd_builds(args):
    root = DATA / "builds"
    for d in sorted(root.iterdir()) if root.exists() else []:
        if (d / "otp-classpath.txt").exists():
            print(f"{d.name:20} {describe_build(d.name)}")


# ---------------------------------------------------------------------------------------------
# Running the Java side


def dataset_dir(name):
    d = DATA / "datasets" / name
    if not d.exists():
        die(f"No dataset {d}")
    return d


def dataset_settings(name):
    """
    The settings of a dataset, in datasets/<name>/rig.json: heap (default 6g), graphHeap (10g),
    graphInTwoSteps (street graph first, then transit: needs less memory), raptorChunk (25),
    busyStops (gen-cases picks every second stop by its number of patterns).
    """
    f = DATA / "datasets" / name / "rig.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}


def graph_file(dataset, build):
    """The graph built by this build, or else the default graph of the dataset."""
    for candidate in [DATA / "graphs" / dataset / build / "graph.obj", DATA / "graphs" / dataset / "default" / "graph.obj"]:
        if candidate.exists():
            return candidate
    die(f"No graph for dataset {dataset}. Run: rig.py graph")


def java_cmd(build, heap, extra_jvm=(), fixed_heap=True):
    require_current(build)
    entries = read_classpath(build)
    argfile = build_dir(build) / "java-cp.txt"
    argfile.write_text("-cp \"" + ";".join(entries) + "\"\n", encoding="utf-8")
    heap_args = [f"-Xms{heap}", f"-Xmx{heap}"] if fixed_heap else [f"-Xmx{heap}"]
    extra_jvm = [*extra_jvm, *os.environ.get("RIG_JAVA_OPTS", "").split()]
    return ["java", *heap_args, "-XX:+UseG1GC", *extra_jvm, f"@{win(argfile)}"]


def run_rig(build, rig_args, logfile, heap, extra_jvm=(), high_priority=False):
    cmd = java_cmd(build, heap, extra_jvm) + [MAIN] + [str(a) for a in rig_args]
    return run_logged(cmd, logfile, echo=r"^\[rig\]|Exception|Error:", high_priority=high_priority)


def cases_file(dataset):
    return CASES_DIR / f"{dataset}.json"


def cases_sha(dataset):
    """A hash of the generated cases and the hand-written ones (<dataset>.extra.json, if any)."""
    extra = cases_file(dataset).with_suffix(".extra.json")
    content = cases_file(dataset).read_bytes() + (extra.read_bytes() if extra.exists() else b"")
    return hashlib.sha256(content).hexdigest()[:12]


def selection_args(args):
    result = []
    if getattr(args, "quick", False):
        result += ["--every", "4"]
    if getattr(args, "every", None):
        result += ["--every", args.every]
    if getattr(args, "only", None):
        result += ["--only", args.only]
    return result


def cmd_graph(args):
    """Build the graph of a dataset with a build, as OTPMain --build --save does."""
    build = args.build
    ds = dataset_dir(args.dataset)
    out = DATA / "graphs" / args.dataset / (args.name or ("default" if build == WORKTREE_BUILD else build))
    out.mkdir(parents=True, exist_ok=True)
    settings = dataset_settings(args.dataset)
    heap = args.graph_heap or settings.get("graphHeap", "10g")
    steps = [["--buildStreet"], ["--loadStreet", "--save"]] if settings.get("graphInTwoSteps") else [["--build", "--save"]]
    started = datetime.datetime.now()
    for step in steps:
        logfile = DATA / "logs" / f"graph-{args.dataset}-{now()}.log"
        log(f"Building the {args.dataset} graph with build '{build}' ({' '.join(step)}), log: {logfile}")
        cmd = java_cmd(build, heap, fixed_heap=False) + ["org.opentripplanner.standalone.OTPMain", *step, win(ds)]
        code = run_logged(cmd, logfile, echo=r"ERROR|Exception|Graph building took|Graph written")
        if code != 0:
            print(tail(logfile))
            die("Graph build failed")
    (ds / "streetGraph.obj").unlink(missing_ok=True)
    built = ds / "graph.obj"
    shutil.move(str(built), str(out / "graph.obj"))
    (out / "meta.json").write_text(json.dumps({"build": build, "buildInfo": describe_build(build), "time": now()}, indent=2))
    log(f"Graph {out / 'graph.obj'} built in {(datetime.datetime.now() - started).seconds} s")


def cmd_graph_fingerprint(args):
    graph = Path(args.graph) if args.graph else graph_file(args.dataset, args.build)
    out = DATA / "fingerprints" / args.dataset / (args.name or args.build)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    log(f"Fingerprint of {graph} (read with build '{args.build}') to {out}")
    code = run_rig(args.build, ["graph-fingerprint", "--graph", win(graph), "--out", win(out)],
                   out / "run.log", args.heap)
    if code != 0:
        print(tail(out / "run.log"))
        die("graph-fingerprint failed")


def read_gz_blocks(path):
    """Yield (key, lines) for each top-level line and the indented lines after it."""
    import gzip
    key, lines = None, []
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith(" "):
                lines.append(line)
                continue
            if key is not None:
                yield key, lines
            parts = line.split(" ", 2)
            key, lines = " ".join(parts[:2]), [line]
    if key is not None:
        yield key, lines


def cmd_graph_diff(args):
    import difflib
    a_dir = DATA / "fingerprints" / args.dataset / args.a
    b_dir = DATA / "fingerprints" / args.dataset / args.b
    a_sum = (a_dir / "summary.txt").read_text(encoding="utf-8").splitlines()
    b_sum = (b_dir / "summary.txt").read_text(encoding="utf-8").splitlines()
    if a_sum == b_sum:
        print("RESULT: IDENTICAL graphs")
        sys.exit(0)
    for line in difflib.unified_diff([l for l in a_sum if " chunk " not in l],
                                     [l for l in b_sum if " chunk " not in l], lineterm="", n=0):
        print(line)
    shown = 0
    for part in ["street", "transit"]:
        a_blocks = read_gz_blocks(a_dir / f"{part}.txt.gz")
        b_blocks = read_gz_blocks(b_dir / f"{part}.txt.gz")
        a_item, b_item = next(a_blocks, None), next(b_blocks, None)
        changed = only_a = only_b = 0
        while a_item or b_item:
            if b_item is None or (a_item and a_item[0] < b_item[0]):
                only_a += 1
                if shown < args.max_diffs:
                    print(f"- only in {args.a}: {a_item[1][0][:300]}")
                    shown += 1
                a_item = next(a_blocks, None)
            elif a_item is None or b_item[0] < a_item[0]:
                only_b += 1
                if shown < args.max_diffs:
                    print(f"+ only in {args.b}: {b_item[1][0][:300]}")
                    shown += 1
                b_item = next(b_blocks, None)
            else:
                if a_item[1] != b_item[1]:
                    changed += 1
                    if shown < args.max_diffs:
                        print(f"~ {a_item[0]}")
                        for d in list(difflib.unified_diff(a_item[1], b_item[1], lineterm="", n=0))[2:12]:
                            print("    " + d[:300])
                        shown += 1
                a_item, b_item = next(a_blocks, None), next(b_blocks, None)
        print(f"{part}: {changed} changed, {only_a} only in {args.a}, {only_b} only in {args.b}")
    print("RESULT: DIFFERENT graphs")
    sys.exit(1)


def cmd_gen_cases(args):
    out = cases_file(args.dataset)
    out.parent.mkdir(parents=True, exist_ok=True)
    logfile = DATA / "logs" / f"gen-cases-{now()}.log"
    busy_stops = ["--busy-stops"] if dataset_settings(args.dataset).get("busyStops") else []
    code = run_rig(args.build, ["gen-cases", "--dataset", win(dataset_dir(args.dataset)),
                                "--graph", win(graph_file(args.dataset, args.build)), "--out", win(out),
                                "--seed", args.seed] + busy_stops, logfile, args.heap)
    if code != 0:
        print(tail(logfile))
        die("gen-cases failed")


def record(build, dataset, out_dir, args):
    require_current(build)
    if out_dir.exists():
        retry(lambda: shutil.rmtree(out_dir))
    out_dir.mkdir(parents=True)
    graph = graph_file(dataset, build)
    rig_args = ["record", "--dataset", win(dataset_dir(dataset)), "--graph", win(graph),
                "--cases", win(cases_file(dataset)), "--out", win(out_dir)] + selection_args(args)
    if args.repeat:
        rig_args.append("--repeat")
    if args.threads:
        rig_args += ["--threads", args.threads]
    meta = {"build": build, "buildInfo": describe_build(build), "dataset": dataset, "graph": str(graph),
            "graphStamp": f"{graph.stat().st_size} bytes, written {int(graph.stat().st_mtime)}",
            "casesSha": cases_sha(dataset), "selection": selection_args(args), "time": now()}
    (out_dir / "meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    log(f"Recording build '{build}' ({meta['buildInfo']}) to {out_dir}")
    code = run_rig(build, rig_args, out_dir / "run.log", args.heap)
    if code not in (0, 3):
        print(tail(out_dir / "run.log"))
        die("Record failed")
    if code == 3:
        log("WARNING: the repeated or concurrent runs gave other results than the first run, or the requests "
            "left edges in the graph, see the DIFF lines and summary.json")
    return code


def record_dir(dataset, name):
    return DATA / "records" / dataset / name


def cmd_record(args):
    code = record(args.build, args.dataset, record_dir(args.dataset, args.name), args)
    sys.exit(1 if code == 3 else 0)


def cmd_check(args):
    """Record a build (default 'wt') and compare it with a recorded baseline."""
    base = record_dir(args.dataset, args.baseline)
    if not (base / "meta.json").exists():
        die(f"No record '{args.baseline}'. Record it first: rig.py record {args.baseline} --build <build>")
    base_meta = json.loads((base / "meta.json").read_text(encoding="utf-8"))
    if base_meta["casesSha"] != cases_sha(args.dataset):
        die(f"The cases changed after the record '{args.baseline}' was made; record a new baseline")
    # A baseline of all cases can be compared with any selection of them
    subset = base_meta["selection"] != selection_args(args)
    if subset and base_meta["selection"]:
        die(f"The baseline was recorded with the selection {base_meta['selection']}; record it with all "
            f"cases, or check with the same selection")
    out = record_dir(args.dataset, args.name or f"check-{args.build}")
    code = record(args.build, args.dataset, out, args)
    same = diff_records(base, out, args.max_diffs, subset)
    stamp = json.loads((out / "meta.json").read_text(encoding="utf-8"))["graphStamp"]
    if base_meta.get("graphStamp", stamp) != stamp:
        log(f"NOTE: the graph is not the file the baseline was recorded with ({base_meta['graphStamp']}); "
            f"a graph built again or by another build can explain differences")
    if code == 3:
        log("FAILED: the repeated or concurrent runs differ from the first run, or the requests left edges in "
            "the graph (see the DIFF lines and summary.json)")
    tests_ok = True
    tests = args.tests or ("none" if args.quick else "changed")
    if tests != "none":
        if args.build != WORKTREE_BUILD:
            log(f"Not running the OTP tests: they run on the working tree, not on build '{args.build}'")
        else:
            tests_ok = run_tests(tests, args.baseline, f"check-{args.build}")
    ok = same and code == 0 and tests_ok
    log("CHECK PASSED" if ok else "CHECK FAILED")
    if ok and build_meta(args.build).get("fast"):
        log(f"Build '{args.build}' was compiled with --fast, which can leave classes out of date: "
            f"before the change counts as correct, run a full build and check again")
    sys.exit(0 if ok else 1)


# ---------------------------------------------------------------------------------------------
# The OTP tests. Some fail on Windows on every build (e.g. the env-var config tests), so a run
# is compared with the test record of the baseline: a test that passed there and fails or is
# gone now is a regression.


def tests_dir():
    return DATA / "tests"


def changed_modules(baseline):
    """The Maven modules with files changed since the commit of the baseline build."""
    head = build_meta(baseline).get("head")
    if not head:
        die(f"No build '{baseline}' to find the changed modules from; use --tests all")
    files = git("diff", "--name-only", head).splitlines()
    files += git("ls-files", "--others", "--exclude-standard").splitlines()
    return modules_of(files, REPO)


def modules_of(files, root):
    """The Maven modules (directories with a pom.xml, below the root) the files belong to."""
    modules = set()
    for f in files:
        d = (root / f).parent
        while d != root and not (d / "pom.xml").exists():
            d = d.parent
        if d != root:
            modules.add(d.relative_to(root).as_posix())
    return sorted(modules)


def run_tests(scope, baseline, name, parse_only=False):
    """Run the OTP tests ('changed' modules or 'all'), record them, compare with the baseline."""
    started = datetime.datetime.now().timestamp()
    modules = []
    if not parse_only:
        cmd = [shutil.which("mvn") or "mvn", "-o", "-B", "test", "-Dps", "-Dcs",
               "-Dplugin.frontend.skip=true", "-Dmaven.test.failure.ignore=true",
               "-Dsurefire.failIfNoSpecifiedTests=false"]
        if scope == "changed":
            modules = changed_modules(baseline)
            if not modules:
                log("No OTP module changed since the baseline; no tests to run")
                return True
            cmd += ["-pl", ",".join(modules), "-am"]
        logfile = DATA / "logs" / f"maven-test-{now()}.log"
        log(f"Running the OTP tests ({'modules ' + ', '.join(modules) + ' and their dependencies' if modules else 'all'}), log: {logfile}")
        modified_before = set(git("diff", "--name-only").splitlines())
        code = run_logged(cmd, logfile, echo=r"\[ERROR\] (Failures|Tests run:.*FAIL)|BUILD|Reactor Summary")
        # A test that writes a source file (a generated schema) would make build 'wt' out of date
        rewritten = sorted(set(git("diff", "--name-only").splitlines()) - modified_before)
        if rewritten:
            log("The tests changed files of the working tree, which are restored: " + ", ".join(rewritten))
            git("checkout", "--", *rewritten)
        if code != 0:
            print(tail(logfile))
            log("FAILED: the Maven test build failed (compile error?)")
            return False
    results = read_surefire_reports(None if parse_only else started)
    counts = {}
    for status in results.values():
        counts[status] = counts.get(status, 0) + 1
    log(f"Tests: {len(results)} run, {counts}")
    tests_dir().mkdir(parents=True, exist_ok=True)
    record_file = tests_dir() / f"{name}.json"
    record_file.write_text(json.dumps({"time": now(), "scope": scope, "modules": modules,
                                       "build": describe_build(WORKTREE_BUILD), "results": results},
                                      indent=1, sort_keys=True), encoding="utf-8")
    baseline_file = tests_dir() / f"{baseline}.json"
    if not baseline_file.exists():
        failing = sorted(k for k, v in results.items() if v in ("failure", "error"))
        log(f"No test record '{baseline}' to compare with; {len(failing)} failing: " + " ".join(failing[:20]))
        return not failing
    expected = json.loads(baseline_file.read_text(encoding="utf-8"))["results"]
    return compare_tests(expected, results)


def compare_tests(expected, actual):
    run_modules = {k.split("::")[0] for k in actual}
    newly_failing = sorted(k for k, v in actual.items()
                           if v in ("failure", "error") and expected.get(k) not in ("failure", "error"))
    gone = sorted(k for k, v in expected.items()
                  if k.split("::")[0] in run_modules and k not in actual and v == "pass")
    fixed = sorted(k for k, v in actual.items() if v == "pass" and expected.get(k) in ("failure", "error"))
    for k in newly_failing[:30]:
        log(f"  NEWLY FAILING: {k}")
    for k in gone[:30]:
        log(f"  GONE (passed in the baseline, not run now): {k}")
    for k in fixed[:10]:
        log(f"  now passing: {k}")
    ok = not newly_failing and not gone
    log(f"Tests compared with the baseline: {len(newly_failing)} newly failing, {len(gone)} gone, "
        f"{len(fixed)} fixed -> {'OK' if ok else 'REGRESSION'}")
    return ok


def read_surefire_reports(since, root=REPO):
    """module::class#method -> pass/failure/error/skipped, from the reports written after since."""
    import xml.etree.ElementTree as ET
    results = {}
    for report in root.glob("**/target/surefire-reports/TEST-*.xml"):
        if since is not None and report.stat().st_mtime < since:
            continue
        module = report.parent.parent.parent.relative_to(root).as_posix()
        for case in ET.parse(report).getroot().iter("testcase"):
            status = "pass"
            for child in case:
                if child.tag in ("failure", "error", "skipped"):
                    status = child.tag
            results[f"{module}::{case.get('classname')}#{case.get('name')}"] = status
    return results


def cmd_test(args):
    sys.exit(0 if run_tests(args.scope, args.baseline, args.name, args.parse_only) else 1)


# ---------------------------------------------------------------------------------------------
# Unit tests of the rig itself: perf-rig/tests (Python) and perf-rig/test (Java, JUnit 5)

JUNIT_JARS = [
    "org/junit/jupiter/junit-jupiter-api/{v}/junit-jupiter-api-{v}.jar",
    "org/junit/jupiter/junit-jupiter-engine/{v}/junit-jupiter-engine-{v}.jar",
    "org/junit/platform/junit-platform-launcher/{v}/junit-platform-launcher-{v}.jar",
    "org/junit/platform/junit-platform-engine/{v}/junit-platform-engine-{v}.jar",
    "org/junit/platform/junit-platform-commons/{v}/junit-platform-commons-{v}.jar",
    "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
    "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
]


def junit_classpath():
    pom = (REPO / "pom.xml").read_text(encoding="utf-8")
    version = re.search(r"<junit.version>([^<]+)<", pom).group(1)
    m2 = Path.home() / ".m2" / "repository"
    jars = [m2 / j.format(v=version) for j in JUNIT_JARS]
    missing = [str(j) for j in jars if not j.exists()]
    if missing:
        die("Missing JUnit jars (run a Maven test build once): " + ", ".join(missing))
    return [win(j) for j in jars]


def cmd_unit(args):
    import unittest
    log("Python unit tests")
    suite = unittest.defaultTestLoader.discover(str(REPO / "perf-rig" / "tests"))
    python_ok = unittest.TextTestRunner(verbosity=1).run(suite).wasSuccessful()

    log("Java unit tests")
    classpath = read_classpath(args.build) + junit_classpath()
    out = build_dir(args.build) / "rig-test-classes"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    sources = sorted(str(p) for p in (REPO / "perf-rig" / "test").rglob("*.java"))
    argfile = build_dir(args.build) / "javac-test-args.txt"
    argfile.write_text("-cp \"" + ";".join(classpath) + "\"\n"
                       + "\n".join("\"" + win(s) + "\"" for s in sources) + "\n", encoding="utf-8")
    result = subprocess.run(["javac", "--release", "25", "-proc:none", "-nowarn", "-encoding", "UTF-8",
                             "-d", win(out), f"@{win(argfile)}"], capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout + result.stderr)
        die("Compiling the Java unit tests failed")
    cp_file = build_dir(args.build) / "java-test-cp.txt"
    cp_file.write_text("-cp \"" + ";".join([win(out)] + classpath) + "\"\n", encoding="utf-8")
    java_ok = subprocess.run(["java", f"@{win(cp_file)}", "org.opentripplanner.perfrig.RigTestMain",
                              win(out)]).returncode == 0
    log(f"Python: {'OK' if python_ok else 'FAILED'}, Java: {'OK' if java_ok else 'FAILED'}")
    sys.exit(0 if python_ok and java_ok else 1)


def cmd_diff(args):
    same = diff_records(record_dir(args.dataset, args.a), record_dir(args.dataset, args.b), args.max_diffs)
    sys.exit(0 if same else 1)


def cmd_selftest(args):
    """
    Apply each patch of perf-rig/mutations, build, check against the baseline record and revert.
    A *.differs.patch is a planted bug and must be found; a *.same.patch changes the code without
    changing any result, and must not be.
    """
    patches = sorted((REPO / "perf-rig" / "mutations").glob(args.pattern))
    if git("status", "--porcelain", "--untracked-files=no"):
        die("The working tree has uncommitted changes; commit them first")
    build = ["build"] + (["--fast"] if args.fast else [])
    results = []
    try:
        for patch in patches:
            expect_diff = ".differs." in patch.name
            log(f"=== {patch.name}: expecting {'differences' if expect_diff else 'identical results'}")
            plant(patch=patch)
            try:
                if run_self(args, build) != 0:
                    results.append(f"{patch.name}: BUILD FAILED")
                    continue
                name = "selftest-" + patch.name.split(".")[0]
                # Without the OTP tests: the selftest is about what the comparison of results finds
                code = run_self(args, ["check", "--baseline", args.baseline, "--name", name, "--tests", "none"]
                                + (["--quick"] if args.quick else []))
                if code not in (0, 1):
                    results.append(f"{patch.name}: CHECK DID NOT RUN")
                    continue
                layers = differing_layers(record_dir(args.dataset, name) / f"diff-vs-{args.baseline}.txt")
                found = "differences in " + ", ".join(layers) if layers else "differences" if code else "identical"
                results.append(f"{patch.name}: {'OK' if (code == 1) == expect_diff else 'FAIL'} ({found})")
            finally:
                unplant()
    finally:
        log("Restoring the build of the working tree")
        run_self(args, build)
    print("\n".join(["selftest results:"] + results))
    sys.exit(0 if all(": OK" in r for r in results) else 1)


def plant(path=None, content=None, patch=None):
    """
    Plant a bug: new content for a source file, or a patch. It is noted in the data directory, so
    that the next command removes the bug if this one is killed before it does.
    """
    planted = {"patch": str(patch)} if patch else {"file": path, "backup": str(DATA / "planted.backup")}
    if not patch:
        shutil.copyfile(REPO / path, planted["backup"])
    (DATA / "planted.json").write_text(json.dumps(planted), encoding="utf-8")
    if patch:
        subprocess.run(["git", "apply", str(patch)], cwd=REPO, check=True)
    else:
        (REPO / path).write_bytes(content)


def unplant():
    """Remove the planted bug, if there is one; returns the file or patch it was in."""
    note = DATA / "planted.json"
    if not note.exists():
        return None
    planted = json.loads(note.read_text(encoding="utf-8"))
    if "file" in planted:
        shutil.copyfile(planted["backup"], REPO / planted["file"])
    elif subprocess.run(["git", "apply", "-R", "--check", planted["patch"]], cwd=REPO).returncode == 0:
        subprocess.run(["git", "apply", "-R", planted["patch"]], cwd=REPO, check=True)
    note.unlink()
    return planted.get("file") or planted["patch"]


# The small bugs that mutate plants: an operator and what replaces it
MUTATIONS = [(" < ", " <= "), (" <= ", " < "), (" > ", " >= "), (" >= ", " > "), (" == ", " != "), (" != ", " == "),
             (" && ", " || "), (" || ", " && "), (" + ", " - "), (" - ", " + ")]


def cmd_mutate(args):
    """
    How much the rig notices in the given source files: plant a small bug (an operator replaced
    by another) at random lines of the methods the cases execute, one at a time, and check each
    against the baseline. A bug that is not found shows what a mistake there would get away with.
    """
    coverage = DATA / "coverage" / args.dataset / f"{WORKTREE_BUILD}.tsv"
    made_from = coverage.with_suffix(".json")
    made_from = json.loads(made_from.read_text(encoding="utf-8")) if made_from.exists() else {}
    if made_from.get("sources") != source_state():
        die("Run 'rig.py coverage [--quick]' first: the bugs are planted in the methods its cases execute, "
            "and are checked with the same cases")
    sites = []
    for path in args.files:
        lines = (REPO / path).read_text(encoding="utf-8").splitlines()
        sites += [(path,) + site for site in mutation_sites(lines, executed_lines(coverage, path))]
    import random
    random.Random(args.seed).shuffle(sites)
    log(f"{len(sites)} places for a bug in the executed methods of {len(args.files)} files, trying {args.count}")
    results = []
    try:
        for path, number, old, new in sites[:args.count]:
            lines = (REPO / path).read_bytes().decode("utf-8").split("\n")
            lines[number - 1] = lines[number - 1].replace(old, new, 1)
            name = f"{path.split('/')[-1]}:{number} '{old.strip()}' to '{new.strip()}'"
            log(f"=== {name}: {lines[number - 1].strip()[:120]}")
            plant(path, content="\n".join(lines).encode("utf-8"))
            try:
                if run_self(args, ["build", "--fast"]) != 0:
                    results.append(f"not built         {name}")
                    continue
                code = run_self(args, ["check", "--tests", "none", "--name", "mutate"] + made_from["selection"],
                                args.timeout)
                layers = differing_layers(record_dir(args.dataset, "mutate") / f"diff-vs-{args.baseline}.txt")
                found = {0: "NOT FOUND", 1: "found in " + ", ".join(layers)}.get(code, "found: the run fails")
                results.append(f"{found:17} {name}")
            finally:
                unplant()
    finally:
        log("Restoring the build of the working tree")
        run_self(args, ["build", "--fast"])
    print("\n".join(["mutate results:"] + sorted(results)))
    missed = sum(r.startswith("NOT FOUND") for r in results)
    found = sum(r.startswith("found") for r in results)
    print(f"{found} bugs found, {missed} not found, {len(results) - found - missed} did not compile")
    sys.exit(1 if missed else 0)


def executed_lines(coverage, path):
    """The line ranges of the methods of a source file that the cases execute, from a coverage file."""
    source = re.search(r"/src/(?:main|ext)/java/(.+)\.java$", path)
    if not source:
        die(f"{path} is not a Java source file of OTP")
    return [(first, last) for name, first, last, ops in read_coverage(coverage)
            if ops > 0 and first > 0 and class_of(name).split("$")[0] == source.group(1)]


def mutation_sites(lines, ranges):
    """
    The (line number, operator, replacement) of each bug that can be planted in the given line
    ranges: lines of code without strings, where string concatenation and messages would be hit.
    """
    sites = []
    for number, line in enumerate(lines, 1):
        code = line.split("//")[0]
        if not any(first <= number <= last for first, last in ranges) or '"' in code or "'" in code:
            continue
        # A loop that never ends, or runs once too often, says little about the checks
        if code.strip().startswith(("*", "/*", "@", "for ", "for(", "while ", "while(")):
            continue
        sites += [(number, old, new) for old, new in MUTATIONS if old in code]
    return sites


def run_self(args, command, timeout=None):
    """
    Run another command of this script, with the same dataset and heap; returns its exit code,
    or 124 if it is stopped after the timeout (seconds) with the processes it started.
    """
    me = [sys.executable, str(Path(__file__).resolve()), "--dataset", args.dataset, "--heap", args.heap]
    process = subprocess.Popen(me + command, cwd=REPO)
    try:
        return process.wait(timeout)
    except subprocess.TimeoutExpired:
        log(f"Stopping 'rig.py {command[0]}' after {timeout} s")
        if os.name == "nt":
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(process.pid)], capture_output=True)
        else:
            process.kill()
        process.wait()
        return 124


def run_steps(args, steps):
    """Run the commands one after the other, and stop at the first that fails."""
    for step in steps:
        log("=== rig.py " + " ".join(step))
        if run_self(args, step) != 0:
            die(f"'{step[0]}' failed; the steps after it were not run", 1)
    log("=== all steps passed")


def cmd_try(args):
    """
    One round of an experiment: compile the changed files, check every 4th case against the
    baseline, and compare the work done with another build.
    """
    run_steps(args, [["build", "--fast"], ["check", "--quick"],
                     ["bench", "--ops", "--quick", "--levels", args.levels, "--baseline", args.against]])


def cmd_accept(args):
    """
    What a change must pass before it is committed: a full build, every changed method executed
    by a case, and all cases, also repeated and concurrent, with all OTP tests: a change of one
    module can break a test of a module that uses it.
    """
    run_steps(args, [["build"], ["coverage"], ["check", "--repeat", "--threads", "8", "--tests", "all"]])


# ---------------------------------------------------------------------------------------------
# Journal: branch 'perf-rig' has the whole journal of the experiments, whatever branch they are on

JOURNAL = "perf-rig/JOURNAL.md"
RIG_BRANCH = "perf-rig"


def cmd_journal(args):
    commit_journal(args.message)
    log(f"The journal is committed, also on branch '{RIG_BRANCH}'")


def commit_journal(message, repo=REPO):
    """
    Commit the journal file on the branch worked on and, if that is another branch, on branch
    'perf-rig' too, without checking it out. Stops if the file lacks an entry of the journal of
    'perf-rig': an entry another branch added would be lost.
    """
    branch = git("rev-parse", "--abbrev-ref", "HEAD", cwd=repo)
    missing = journal_entries(git("show", f"{RIG_BRANCH}:{JOURNAL}", cwd=repo)) \
        - journal_entries((repo / JOURNAL).read_text(encoding="utf-8"))
    if missing:
        die(f"{JOURNAL} lacks entries of the journal of branch '{RIG_BRANCH}':\n  " + "\n  ".join(sorted(missing))
            + f"\nGet them, then add yours again: git checkout {RIG_BRANCH} -- {JOURNAL}")
    worktrees = git("worktree", "list", "--porcelain", cwd=repo).splitlines()
    if branch != RIG_BRANCH and f"branch refs/heads/{RIG_BRANCH}" in worktrees:
        die(f"Branch '{RIG_BRANCH}' is checked out in another worktree; it can not be committed to from here")
    if git("status", "--porcelain", "--", JOURNAL, cwd=repo):
        git("commit", "-q", "-m", message, "--", JOURNAL, cwd=repo)
    blob = git("rev-parse", f"HEAD:{JOURNAL}", cwd=repo)
    if blob == git("rev-parse", f"{RIG_BRANCH}:{JOURNAL}", cwd=repo):
        return
    # The tree of 'perf-rig' with the journal replaced, made in an index of its own
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / "index"))
        git("read-tree", RIG_BRANCH, cwd=repo, env=env)
        git("update-index", "--cacheinfo", f"100644,{blob},{JOURNAL}", cwd=repo, env=env)
        tree = git("write-tree", cwd=repo, env=env)
    commit = git("commit-tree", tree, "-p", RIG_BRANCH, "-m", message, cwd=repo)
    git("update-ref", f"refs/heads/{RIG_BRANCH}", commit, cwd=repo)


def journal_entries(text):
    """The headings of the entries of a journal."""
    return set(re.findall(r"^### .*$", text, re.M))


# ---------------------------------------------------------------------------------------------
# Coverage: the OTP code the cases execute, from the counts of the ops agent. A check that passes
# says nothing about code that no case executes.


def cmd_coverage(args):
    """
    Run the cases with the ops agent and report the methods changed since the commit of the
    baseline build that no case executes, and the share of each package that is executed.
    """
    out = DATA / "coverage" / args.dataset / f"{args.build}.tsv"
    out.parent.mkdir(parents=True, exist_ok=True)
    rig_args = ["coverage", "--dataset", win(dataset_dir(args.dataset)),
                "--graph", win(graph_file(args.dataset, args.build)), "--cases", win(cases_file(args.dataset)),
                "--out", win(out)] + selection_args(args)
    log(f"Running the cases of {args.dataset} with the ops agent on build '{args.build}'")
    if run_rig(args.build, rig_args, out.with_suffix(".log"), args.heap, ops_jvm()) != 0:
        print(tail(out.with_suffix(".log")))
        die("Coverage failed")
    made_from = {"sources": source_state(), "selection": selection_args(args)}
    out.with_suffix(".json").write_text(json.dumps(made_from), encoding="utf-8")
    methods = read_coverage(out)
    packages = coverage_by_package(methods, otp_classes(args.build))
    out.with_suffix(".packages.txt").write_text(packages, encoding="utf-8")
    print(packages)
    head = build_meta(args.baseline)["head"]
    changes = diff_line_ranges(git("diff", "-U0", head, "--", "*.java"))
    changes.update({f: [(1, 10 ** 9)] for f in git("ls-files", "--others", "--exclude-standard", "--", "*.java").splitlines()})
    untested = not_executed(methods, changes)
    if untested:
        print(f"Changed since build '{args.baseline}' and NOT EXECUTED by any case, so not checked:")
        print("\n".join("  " + line for line in untested))
    else:
        print(f"Every method changed since build '{args.baseline}' is executed by a case.")
    sys.exit(1 if untested else 0)


def read_coverage(file):
    """The OTP methods of a coverage file: (name, first line, last line, ops)."""
    methods = []
    for line in file.read_text(encoding="utf-8").splitlines()[1:]:
        name, first, last, ops = line.split("\t")
        if name.startswith("org/opentripplanner/"):
            methods.append((name, int(first), int(last), int(ops)))
    return methods


def class_of(method):
    """org/x/Foo$Bar.baz(I)V -> org/x/Foo$Bar"""
    return method.split("(")[0].rsplit(".", 1)[0]


def otp_classes(build):
    """The names of all OTP classes of a build, e.g. org/opentripplanner/street/Foo$Bar."""
    import zipfile
    names = set()
    for entry in (build_dir(build) / "otp-classpath.txt").read_text(encoding="utf-8").splitlines():
        path = Path(entry)
        if path.is_dir():
            names |= {f.relative_to(path).as_posix()[:-6] for f in path.rglob("*.class")}
        elif build_dir(build) in path.parents:
            names |= {n[:-6] for n in zipfile.ZipFile(path).namelist() if n.endswith(".class")}
    return {n for n in names if n.startswith("org/opentripplanner/")}


def coverage_by_package(methods, classes):
    """A table: for each package (two levels below org.opentripplanner) the share that is executed."""
    def package(cls):
        return ".".join(cls.split("/")[:-1][:4])

    stats = {}
    for cls in classes:
        stats.setdefault(package(cls), [set(), set(), 0, 0])[0].add(cls)
    for name, _, _, ops in methods:
        row = stats.setdefault(package(class_of(name)), [set(), set(), 0, 0])
        row[0].add(class_of(name))
        row[2] += 1
        if ops > 0:
            row[1].add(class_of(name))
            row[3] += 1
    lines = [f"{'package':58} {'classes':>7} {'executed':>8} {'methods':>7} {'executed':>8}"]
    total = [0, 0, 0, 0]
    for name in sorted(stats):
        all_classes, executed_classes, method_count, executed_methods = stats[name]
        row = [len(all_classes), len(executed_classes), method_count, executed_methods]
        total = [t + r for t, r in zip(total, row)]
        lines.append(coverage_row(name, row))
    lines.append(coverage_row("total", total))
    lines.append("classes: all classes of the build; methods: of the classes that were loaded. A method is executed "
                 "if a case runs any of it, so a branch of it may still be untested.")
    return "\n".join(lines) + "\n"


def coverage_row(name, row):
    classes, executed_classes, methods, executed_methods = row
    return (f"{name:58} {classes:7} {executed_classes / max(1, classes):8.0%} {methods:7} "
            f"{executed_methods / max(1, methods):8.0%}")


def diff_line_ranges(diff):
    """The line ranges a unified diff (-U0) changes in the new version of each file."""
    changes, current = {}, None
    for line in diff.splitlines():
        if line.startswith("diff --git"):
            current = None
        elif line.startswith("+++ b/"):
            current = line[6:]
        elif line.startswith("@@") and current:
            start, count = re.match(r"@@ -\S+ \+(\d+)(?:,(\d+))?", line).groups()
            # Deleted lines (count 0) change the code around the line they were at
            changes.setdefault(current, []).append((int(start), int(start) + max(int(count or 1), 1) - 1))
    return changes


def not_executed(methods, changes):
    """The methods with changed lines that no case executes; changes: source file -> line ranges."""
    by_source = {}
    for method in methods:
        by_source.setdefault(class_of(method[0]).split("$")[0], []).append(method)
    lines = []
    for path, ranges in sorted(changes.items()):
        source = re.search(r"/src/(?:main|ext)/java/(.+)\.java$", path)
        if not source:
            continue
        if source.group(1) not in by_source:
            lines.append(f"{path}: no code of the class runs (it is not loaded, or has no code)")
        for name, first, last, ops in by_source.get(source.group(1), []):
            if ops == 0 and first > 0 and any(first <= end and last >= start for start, end in ranges):
                lines.append(f"{path}:{first}: {method_name(name)}")
    return lines


# ---------------------------------------------------------------------------------------------
# Comparing records


LAYERS = ["api", "routing", "raptor"]


def read_blocks(path):
    blocks = {}
    current = None
    lines = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("### "):
                if current is not None:
                    blocks[current] = lines
                current = line[4:].rstrip("\n")
                lines = []
            else:
                lines.append(line.rstrip("\n"))
    if current is not None:
        blocks[current] = lines
    return blocks


def diff_records(a_dir, b_dir, max_diffs=10, subset=False):
    """Compare the layers of two records; with subset, B may have only some of the results of A."""
    import difflib
    report = []
    total = 0
    for layer in LAYERS:
        a = read_blocks(a_dir / f"{layer}.txt")
        b = read_blocks(b_dir / f"{layer}.txt")
        if subset:
            # Every result of the cases B ran: a page of a case is "case/next1"
            cases = {k.split("/")[0] for k in b}
            a = {k: v for k, v in a.items() if k.split("/")[0] in cases}
        missing = [k for k in a if k not in b]
        extra = [k for k in b if k not in a]
        changed = [k for k in a if k in b and a[k] != b[k]]
        reordered = [k for k in changed if sorted(a[k]) == sorted(b[k])]
        total += len(missing) + len(extra) + len(changed)
        status = "identical" if not (missing or extra or changed) else \
            f"{len(changed)} changed ({len(reordered)} only reordered), {len(missing)} missing, {len(extra)} extra"
        report.append(f"{layer:8} {len(a)} results: {status}")
        for k in missing[:max_diffs]:
            report.append(f"  missing: {k}")
        for k in extra[:max_diffs]:
            report.append(f"  extra: {k}")
        for k in changed[:max_diffs]:
            report.append(f"  --- {k}" + (" (same lines, other order)" if k in reordered else ""))
            udiff = list(difflib.unified_diff(a[k], b[k], lineterm="", n=1))[2:]
            for line in udiff[:24]:
                report.append("    " + (line if len(line) < 400 else line[:400] + " ..."))
            if len(udiff) > 24:
                report.append(f"    ... {len(udiff) - 24} more diff lines")
        if len(changed) > max_diffs:
            report.append(f"  ... and {len(changed) - max_diffs} more changed: " + " ".join(changed[max_diffs:max_diffs + 50]))
    text = f"Comparing {a_dir.name} (A) with {b_dir.name} (B)\n" + "\n".join(report) + "\n"
    text += "RESULT: IDENTICAL\n" if total == 0 else f"RESULT: {total} DIFFERENCES\n"
    print(text, end="")
    (b_dir / f"diff-vs-{a_dir.name}.txt").write_text(text, encoding="utf-8")
    return total == 0


def differing_layers(report_file):
    """The layers that a report of diff_records has differences in."""
    text = report_file.read_text(encoding="utf-8") if report_file.exists() else ""
    return [layer for layer in LAYERS if re.search(rf"^{layer} .* results: (?!identical)", text, re.M)]


# ---------------------------------------------------------------------------------------------
# Benchmarks


def efficiency_classes():
    """Windows: the efficiency class of each logical processor; the highest class is the fastest cores."""
    import ctypes
    import struct
    size = ctypes.c_ulong(0)
    info = ctypes.windll.kernel32.GetLogicalProcessorInformationEx
    info(0, None, ctypes.byref(size))  # 0: one record for each processor core
    buffer = ctypes.create_string_buffer(size.value)
    if not info(0, buffer, ctypes.byref(size)):
        die("Can not read the processor cores")
    classes, offset = {}, 0
    while offset < size.value:
        # Record: relationship, size, flags, efficiency class, 20 reserved bytes, group count, mask
        record_size = struct.unpack_from("I", buffer, offset + 4)[0]
        efficiency = buffer.raw[offset + 9]
        mask = struct.unpack_from("Q", buffer, offset + 32)[0]
        classes.update({cpu: efficiency for cpu in range(64) if mask >> cpu & 1})
        offset += record_size
    return classes


def machine_cpu_seconds():
    """The CPU time all processes of the machine have used so far; 0 where it is not known."""
    if os.name != "nt":
        return 0.0
    import ctypes
    idle, kernel, user = ctypes.c_ulonglong(), ctypes.c_ulonglong(), ctypes.c_ulonglong()
    ctypes.windll.kernel32.GetSystemTimes(ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user))
    # In units of 100 ns; the kernel time includes the idle time
    return (kernel.value + user.value - idle.value) / 1e7


def other_cpu_use(out, forks):
    """
    Report lines on the CPU that other processes used during each fork, in cores. A build or a
    browser that runs during some of the forks makes their timings slower.
    """
    f = out / "meta.json"
    machine = json.loads(f.read_text(encoding="utf-8")).get("machine", {}) if f.exists() else {}
    cores = {}
    for side in "AB":
        for i, r in enumerate(forks[side]):
            m = machine.get(f"{side}{i}")
            if m and m["cpuSeconds"] and "processCpuSeconds" in r["meta"]:
                cores[f"{side}{i}"] = max(0.0, m["cpuSeconds"] - r["meta"]["processCpuSeconds"]) / m["seconds"]
    if not cores:
        return []
    lines = ["CPU used by other processes during each fork, in cores: "
             + ", ".join(f"{k} {v:.2f}" for k, v in cores.items())]
    if max(cores.values()) > OTHER_CPU_LIMIT:
        lines.append(f"WARNING: other processes used more than {OTHER_CPU_LIMIT} cores during a fork; "
                     "the timings are not reliable, run the benchmark again on a quiet machine")
    return lines


def parse_cpus(spec, classes):
    """
    The logical processors of bench --cpus: 'fast' (the cores of the highest efficiency class, the
    P-cores of a hybrid CPU), 'slow' (all other cores), or a list like 4-11 or 0,2,4.
    """
    if spec in ("fast", "slow"):
        fastest = max(classes.values())
        return sorted(cpu for cpu, efficiency in classes.items() if (efficiency == fastest) == (spec == "fast"))
    cpus = []
    for part in spec.split(","):
        first, _, last = part.partition("-")
        cpus += range(int(first), int(last or first) + 1)
    return cpus


def pin_to_cpus(spec):
    """
    Let this process and the JVMs it starts run on the given logical processors only. Returns
    them, or None if nothing is pinned: 'all', or 'fast'/'slow' where the cores are not known.
    """
    by_class = spec in ("fast", "slow")
    if spec == "all" or (by_class and os.name != "nt"):
        return None
    if os.name != "nt":
        cpus = parse_cpus(spec, {})
        os.sched_setaffinity(0, cpus)
        return cpus
    import ctypes
    cpus = parse_cpus(spec, efficiency_classes() if by_class else {})
    kernel32 = ctypes.windll.kernel32
    kernel32.GetCurrentProcess.restype = ctypes.c_void_p
    kernel32.SetProcessAffinityMask.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
    if not cpus or not kernel32.SetProcessAffinityMask(kernel32.GetCurrentProcess(), sum(1 << cpu for cpu in cpus)):
        die(f"Can not run on the processors {spec}")
    return cpus


def cmd_bench(args):
    a, b = args.baseline, args.candidate
    for build in (a, b):
        require_current(build)
        read_classpath(build)  # compiles the rig if it changed, so that is not part of a fork
    out = DATA / "bench" / f"{now()}-{a}-vs-{b}{'-ops' if args.ops else ''}"
    out.mkdir(parents=True)
    levels = args.levels
    # Counted instructions do not depend on the JIT compiler or the machine: one short fork is enough.
    # Time differs more between forks than between the iterations of a fork: more forks, each short.
    forks = args.forks or (1 if args.ops else 3)
    warmup = args.warmup or ("1" if args.ops else "2")
    iterations = args.iterations or ("2" if args.ops else "3")
    jvm = ops_jvm() if args.ops else TIME_JVM
    common = ["bench", "--dataset", win(dataset_dir(args.dataset)), "--cases", win(cases_file(args.dataset)),
              "--levels", levels, "--warmup", warmup, "--iterations", iterations,
              "--raptor-chunk", dataset_settings(args.dataset).get("raptorChunk", 25)] + selection_args(args)
    log(f"Benchmark {a} (A) vs {b} (B), levels {levels}, {forks} forks each, results in {out}")
    builds = {"A": f"{a}: {describe_build(a)}", "B": f"{b}: {describe_build(b)}", "dataset": args.dataset}
    cpus = None if args.ops else pin_to_cpus(args.cpus)
    if cpus:
        builds["cpus"] = cpus
        log(f"Running on the logical processors {cpus}")
    (out / "meta.json").write_text(json.dumps(builds, indent=2), encoding="utf-8")
    log(f"A: {builds['A']}")
    log(f"B: {builds['B']}")
    # ABBA order cancels a drift of the machine (thermal, other load) over the run
    order = []
    for f in range(forks):
        order += [(a, "A", f), (b, "B", f)] if f % 2 == 0 else [(b, "B", f), (a, "A", f)]
    builds["machine"] = {}
    for build, side, f in order:
        result = out / f"{side}-{f}.json"
        known = known_ops(build, args.dataset, common) if args.ops else None
        if known and known.exists():
            log(f"Fork {side}{f} ({build}): its counts are known from an earlier benchmark")
            shutil.copyfile(known, result)
            continue
        log(f"Fork {side}{f} ({build})")
        started, cpu_before = datetime.datetime.now(), machine_cpu_seconds()
        code = run_rig(build, common + ["--out", win(result), "--graph", win(graph_file(args.dataset, build)),
                                        "--label", f"{side}{f}:{build}"],
                       out / f"{side}-{f}.log", args.heap, jvm, high_priority=True)
        if code != 0:
            print(tail(out / f"{side}-{f}.log"))
            die(f"Fork {side}{f} failed")
        builds["machine"][f"{side}{f}"] = {"cpuSeconds": machine_cpu_seconds() - cpu_before,
                                           "seconds": (datetime.datetime.now() - started).total_seconds()}
        (out / "meta.json").write_text(json.dumps(builds, indent=2), encoding="utf-8")
        if known:
            known.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(result, known)
    print(ops_report(out) if args.ops else bench_report(out, args.metric))
    append_history(out)


def known_ops(build, dataset, bench_args):
    """
    The file with the counts of a frozen build for a benchmark with these arguments, there or not.
    The counts are the same in every run, so 'try' counts the baseline once, not in every round.
    """
    if build == WORKTREE_BUILD:
        return None
    graph = graph_file(dataset, build)
    made_from = [build_meta(build), rig_sources_hash(), ops_jvm(), (ops_agent().parent / "sources.sha").read_text(),
                 cases_sha(dataset), str(graph), graph.stat().st_size, int(graph.stat().st_mtime),
                 [str(a) for a in bench_args]]
    key = hashlib.sha256(json.dumps(made_from).encode()).hexdigest()[:16]
    return DATA / "bench" / "known-ops" / f"{build}-{key}.json"


def append_history(out):
    """A line for each level of a benchmark in bench/history.tsv: the log of a series of experiments."""
    summary = json.loads((out / "summary.json").read_text(encoding="utf-8"))
    meta = json.loads((out / "meta.json").read_text(encoding="utf-8"))
    columns = ["benchmark", "dataset", "kind", "level", "items", "ratio", "low", "high", "noise", "mismatches", "A", "B"]
    history = out.parent / "history.tsv"
    lines = [] if history.exists() else ["\t".join(columns)]
    for level, row in summary["levels"].items():
        values = dict(row, benchmark=out.name, dataset=meta["dataset"], kind=summary["kind"], level=level,
                      A=meta["A"], B=meta["B"])
        cells = [values.get(c) for c in columns]
        lines.append("\t".join("" if v is None else f"{v:.5f}" if isinstance(v, float) else str(v) for v in cells))
    with open(history, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def read_forks(out):
    """The results of the forks of a benchmark directory: A-0.json, B-0.json, ..."""
    forks = {"A": [], "B": []}
    for f in sorted(out.glob("[AB]-*.json")):
        forks[f.name[0]].append(json.loads(f.read_text(encoding="utf-8")))
    return forks


def bench_report(out, metric="wall"):
    forks = read_forks(out)
    if not forks["A"] or not forks["B"]:
        return "No results"
    if forks["A"][0]["meta"].get("ops"):
        return ops_report(out)
    lines = [f"Benchmark {out.name}"] + builds_of(out)
    lines += [f"metric: {metric} time; per item the median over the iterations of a fork, then the median over "
              f"the forks", ""]
    meta = forks["A"][0]["meta"]
    lines.append(f"forks: {len(forks['A'])}+{len(forks['B'])}, warmup {meta['warmup']}, iterations {meta['iterations']}, "
                 f"heap {meta['maxHeapMb']} MB, raptor threads {meta['raptorThreads']}")
    for side in "AB":
        gc = [(r["meta"]["gcCountMeasure"], r["meta"]["gcMillisMeasure"]) for r in forks[side]]
        lines.append(f"  {side}: GC during measurement (count, ms) per fork, incl. forced GCs: {gc}")
    lines += other_cpu_use(out, forks) + [""]
    lines.append(f"{'level':7} {'items':>5} {'A total':>9} {'B total':>9} {'B/A total':>9} "
                 f"{'B/A geomean [99% CI]':>25} {'alloc B/A':>9} {'mismatch':>8}  verdict")
    details, summary = [], {}
    for level in forks["A"][0]["levels"]:
        a_forks = per_fork(forks["A"], level, metric)
        b_forks = per_fork(forks["B"], level, metric)
        a_items = median_over_forks(a_forks)
        b_items = median_over_forks(b_forks)
        a_alloc = median_over_forks(per_fork(forks["A"], level, "alloc"))
        b_alloc = median_over_forks(per_fork(forks["B"], level, "alloc"))
        ids = [i for i in a_items if all(f.get(i, 0) > 0 for f in a_forks + b_forks)]
        mismatches = fingerprint_mismatches(forks, level)
        a_tot = sum(a_items[i] for i in ids)
        b_tot = sum(b_items[i] for i in ids)
        gm, lo, hi = ratio_interval(a_forks, b_forks, ids)
        alloc_ratio = sum(b_alloc.get(i, 0) for i in ids) / max(1, sum(a_alloc.get(i, 0) for i in ids))
        if mismatches:
            verdict = "INVALID: results differ"
        elif math.isnan(lo):
            # One fork of each build does not show the difference between JVM runs
            verdict = "no verdict (1 fork)"
        elif lo > 1:
            verdict = f"B slower ({(gm - 1) * 100:.1f}%)"
        elif hi < 1:
            verdict = f"B faster ({(1 - gm) * 100:.1f}%)"
        else:
            verdict = "no significant difference"
        lines.append(f"{level:7} {len(ids):5} {fmt_ns(a_tot):>9} {fmt_ns(b_tot):>9} {b_tot / max(1, a_tot):9.3f} "
                     f"{gm:7.3f} [{lo:.3f}, {hi:.3f}] {alloc_ratio:9.3f} {len(mismatches):8}  {verdict}")
        summary[level] = {"items": len(ids), "ratio": gm, "low": None if math.isnan(lo) else lo,
                          "high": None if math.isnan(hi) else hi, "mismatches": len(mismatches), "verdict": verdict}
        if mismatches:
            details.append(f"{level}: results differ between the forks or from the first run, or are missing in a "
                           f"fork, for {len(mismatches)} items: " + " ".join(mismatches[:30]))
        # Items below 1 ms are dominated by timer noise
        big = sorted((i for i in ids if a_items[i] >= 1e6), key=lambda i: b_items[i] / a_items[i])
        details.append(f"{level}: fastest B/A: " + ", ".join(f"{i} {b_items[i] / a_items[i]:.2f}" for i in big[:5]))
        details.append(f"{level}: slowest B/A: " + ", ".join(f"{i} {b_items[i] / a_items[i]:.2f}" for i in big[-5:][::-1]))
        details += by_search(level, a_items, b_items, ids)
        if ids:
            details.append(uneven_forks(forks, level, ids, metric))
    lines.append("")
    lines += details
    lines.append("")
    lines.append("B/A < 1 means B is faster. A fork (one JVM run) is one sample: the CI is the t-interval of the "
                 "difference between the forks of A and of B, so few forks give a wide interval. CPU time is "
                 "useless on Windows (15.6 ms ticks); allocated bytes are the most stable signal.")
    text = "\n".join(lines) + "\n"
    (out / "report.txt").write_text(text, encoding="utf-8")
    (out / "summary.json").write_text(json.dumps({"kind": "time", "levels": summary}, indent=2), encoding="utf-8")
    return text


def ops_report(out):
    """
    The report of a benchmark that counted bytecode instructions (bench --ops). The count of an
    item is the same in every iteration and fork if the code is deterministic; "noise" is how much
    the total of a level differs between the iterations of one build.
    """
    forks = read_forks(out)
    if not forks["A"] or not forks["B"]:
        return "No results"
    lines = [f"Benchmark {out.name}"] + builds_of(out)
    lines += ["metric: ops, the bytecode instructions executed in OTP and its libraries. The same work gives the "
              "same count on any machine; it does not show memory, cache and JIT effects, so confirm a gain "
              "with a time benchmark.", ""]
    lines.append(f"{'level':7} {'items':>5} {'A ops':>10} {'B ops':>10} {'B/A total':>9} {'B/A geomean':>11} "
                 f"{'less':>5} {'more':>5} {'noise':>9} {'mismatch':>8}")
    details, summary = [], {}
    for level in forks["A"][0]["levels"]:
        a, b = ops_samples(forks["A"], level), ops_samples(forks["B"], level)
        ids = [i for i in a if i in b and median(a[i]) > 0 and median(b[i]) > 0]
        a_ops, b_ops = {i: median(a[i]) for i in ids}, {i: median(b[i]) for i in ids}
        a_tot, b_tot = sum(a_ops.values()), sum(b_ops.values())
        less = [i for i in ids if b_ops[i] < a_ops[i] * (1 - OPS_TOLERANCE)]
        more = [i for i in ids if b_ops[i] > a_ops[i] * (1 + OPS_TOLERANCE)]
        noise = max(total_spread(a, ids), total_spread(b, ids))
        mismatches = fingerprint_mismatches(forks, level)
        gm = geomean(a_ops, b_ops, ids) if ids else float("nan")
        lines.append(f"{level:7} {len(ids):5} {a_tot:10.4g} {b_tot:10.4g} {b_tot / max(1, a_tot):9.5f} {gm:11.5f} "
                     f"{len(less):5} {len(more):5} {noise:8.4%} {len(mismatches):8}"
                     + ("  INVALID: results differ" if mismatches else ""))
        summary[level] = {"items": len(ids), "ratio": b_tot / max(1, a_tot), "less": len(less), "more": len(more),
                          "noise": noise, "mismatches": len(mismatches)}
        if mismatches:
            details.append(f"{level}: results differ between the forks or from the first run, or are missing in a "
                           f"fork, for {len(mismatches)} items: " + " ".join(mismatches[:30]))
        ratio = lambda i: b_ops[i] / a_ops[i]
        if less or more:
            details.append(f"{level}: least B/A: " + ", ".join(f"{i} {ratio(i):.3f}" for i in sorted(ids, key=ratio)[:5]))
            details.append(f"{level}: most B/A: " + ", ".join(f"{i} {ratio(i):.3f}" for i in sorted(ids, key=ratio)[-5:][::-1]))
        details += by_search(level, a_ops, b_ops, ids)
        details += ops_by_method(forks, level)
    lines += [""] + details + [f"B/A < 1 means B does less work. less/more: the items that differ by more than "
                               f"{OPS_TOLERANCE:.1%}; maps keyed by identity hash make the counts of two JVM runs "
                               f"differ by about 0.01%."]
    text = "\n".join(lines) + "\n"
    (out / "report.txt").write_text(text, encoding="utf-8")
    (out / "summary.json").write_text(json.dumps({"kind": "ops", "levels": summary}, indent=2), encoding="utf-8")
    return text


def ops_samples(results, level):
    """Item id -> its counts in all iterations of all forks."""
    samples = {}
    for r in results:
        for item in r["levels"][level]:
            samples.setdefault(item["id"], []).extend(item["ops"])
    return samples


def total_spread(samples, ids):
    """How much the total of the items differs between the iterations: (max - min) / min."""
    if not ids:
        return 0.0
    totals = [sum(samples[i][k] for i in ids) for k in range(min(len(samples[i]) for i in ids))]
    return (max(totals) - min(totals)) / max(1, min(totals))


def ops_by_method(forks, level, top=12):
    """The methods that do the most work in A, and the ones whose work changed most in B."""
    def mean(side):
        sums = {}
        for r in forks[side]:
            for method, count in r.get("opsByMethod", {}).get(level, {}).items():
                sums[method] = sums.get(method, 0) + count / len(forks[side])
        return sums

    a, b = mean("A"), mean("B")
    total = max(1, sum(a.values()))
    lines = [f"{level}: the methods with the most ops in A (share of the level)"]
    for m in sorted(a, key=a.get, reverse=True)[:top]:
        lines.append(f"  {a[m] / total:6.2%}  {method_name(m)}")
    diffs = {m: b.get(m, 0) - a.get(m, 0) for m in set(a) | set(b)}
    changed = [m for m in sorted(diffs, key=lambda m: abs(diffs[m]), reverse=True)[:top]
               if abs(diffs[m]) > total * OPS_TOLERANCE / 10]
    if changed:
        lines.append(f"{level}: the methods whose ops changed most, B - A (share of the level)")
        for m in changed:
            lines.append(f"  {diffs[m] / total:+7.3%}  {method_name(m)}")
    return lines + [""]


def method_name(method):
    """org/x/Foo.bar(Lorg/x/Baz;I[J)V as org.x.Foo.bar(Baz, int, long[])."""
    primitives = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float",
                  "D": "double"}
    owner, descriptor = method.split("(")
    parameters = []
    for arrays, primitive, type_name in re.findall(r"(\[*)(?:([ZBCSIJFD])|L([^;]+);)", descriptor.split(")")[0]):
        parameters.append((primitives.get(primitive) or type_name.split("/")[-1]) + "[]" * len(arrays))
    return f"{owner.replace('/', '.')}({', '.join(parameters)})"


# ---------------------------------------------------------------------------------------------
# Profile: where the time and the work of a level go, for one build


def cmd_profile(args):
    """
    Measure the levels of one build twice: with a flight recording (time samples and allocations
    of the measured thread) and with the ops agent (exact work), and print the top methods.
    """
    out = DATA / "profile" / f"{now()}-{args.build}"
    out.mkdir(parents=True)
    common = ["bench", "--dataset", win(dataset_dir(args.dataset)), "--cases", win(cases_file(args.dataset)),
              "--graph", win(graph_file(args.dataset, args.build)), "--levels", args.levels,
              "--raptor-chunk", dataset_settings(args.dataset).get("raptorChunk", 25)] + selection_args(args)
    log(f"Profile of build '{args.build}' ({describe_build(args.build)}), levels {args.levels}, in {out}")
    runs = [("time", ["--warmup", "2", "--iterations", "3", "--jfr", win(out / "time.jfr")], TIME_JVM),
            ("ops", ["--warmup", "1", "--iterations", "1"], ops_jvm())]
    for name, run_args, jvm in runs:
        log(f"Measuring {name}")
        code = run_rig(args.build, common + run_args + ["--out", win(out / f"{name}.json")], out / f"{name}.log",
                       args.heap, jvm, high_priority=True)
        if code != 0:
            print(tail(out / f"{name}.log"))
            die(f"The {name} run failed")
    print(profile_report(out, args.top))


def profile_report(out, top=25):
    timed = json.loads((out / "time.json").read_text(encoding="utf-8"))
    counted = json.loads((out / "ops.json").read_text(encoding="utf-8"))
    samples, allocations = flight_events(out / "time.jfr")
    lines = [f"Profile {out.name}", "Time and allocation: samples of a flight recording of the measured thread. "
             "Ops: bytecode instructions counted by the ops agent; exact, but not time.", ""]
    for level, windows in timed["windows"].items():
        inside = lambda events: [e for e in events if any(start <= e[0] <= end for start, end in windows)]
        lines += top_methods(f"{level}: time", inside(samples), top)
        lines += top_methods(f"{level}: allocated bytes", inside(allocations), top)
        ops = counted.get("opsByMethod", {}).get(level, {})
        lines += top_methods(f"{level}: ops", [(0, method_name(m), "", count) for m, count in ops.items()], top)
    text = "\n".join(lines) + "\n"
    (out / "report.txt").write_text(text, encoding="utf-8")
    return text


def flight_events(recording):
    """
    The execution samples and the allocation samples of the main thread of a flight recording, as
    (time in ns since the epoch, method, calling method, weight): weight 1 for a time sample, the
    bytes for an allocation.
    """
    printed = subprocess.run(["jfr", "print", "--stack-depth", "2", "--events",
                              "jdk.ExecutionSample,jdk.ObjectAllocationSample", str(recording)],
                             capture_output=True, encoding="utf-8", errors="replace", check=True).stdout
    return parse_flight_events(printed)


def parse_flight_events(printed):
    """
    The events of the main thread in the text of 'jfr print', which has one block for each event:
    jdk.ExecutionSample {
      startTime = 01:33:55.866 (2026-11-27)
      sampledThread = "main" (javaThreadId = 3)
      stackTrace = [
        java.util.BitSet.get(int) line: 650
        org.opentripplanner.Foo.bar(String) line: 173
    """
    units = {"bytes": 1, "kB": 1e3, "MB": 1e6, "GB": 1e9}
    samples, allocations = [], []
    for block in printed.split("\n\n"):
        time = re.search(r"startTime = (\d+):(\d+):(\d+)\.(\d+) \((\d+)-(\d+)-(\d+)\)", block)
        frames = re.findall(r"^    (\S+?)\(.*\) line: ", block, re.M)
        if not time or not frames or not re.search(r'(sampledThread|eventThread) = "main"', block):
            continue
        hour, minute, second, fraction, year, month, day = time.groups()
        local = datetime.datetime(int(year), int(month), int(day), int(hour), int(minute), int(second))
        nanos = int(local.timestamp()) * 10 ** 9 + int(fraction.ljust(9, "0"))
        row = (nanos, frames[0], frames[1] if len(frames) > 1 else "")
        weight = re.search(r"weight = ([\d.]+) (\w+)", block)
        if block.lstrip().startswith("jdk.ExecutionSample"):
            samples.append(row + (1,))
        elif weight:
            allocations.append(row + (float(weight.group(1)) * units[weight.group(2)],))
    return samples, allocations


def top_methods(title, events, top):
    """Report lines: the share of each method in the weight of the events, with its main caller."""
    total = sum(e[3] for e in events)
    if not total:
        return [f"{title}: nothing recorded", ""]
    by_method, callers = {}, {}
    for _, method, caller, weight in events:
        by_method[method] = by_method.get(method, 0) + weight
        callers.setdefault(method, {})
        callers[method][caller] = callers[method].get(caller, 0) + weight
    lines = [f"{title} (share of {total:.4g})"]
    for method in sorted(by_method, key=by_method.get, reverse=True)[:top]:
        caller = max(callers[method], key=callers[method].get)
        via = f"   <- {callers[method][caller] / by_method[method]:.0%} from {caller}" if caller else ""
        lines.append(f"  {by_method[method] / total:6.2%}  {method}{via}")
    return lines + [""]


def builds_of(out):
    """The builds a benchmark compared, as report lines."""
    f = out / "meta.json"
    if not f.exists():
        return []
    meta = json.loads(f.read_text(encoding="utf-8"))
    cpus = [f"logical processors: {meta['cpus']}"] if "cpus" in meta else []
    return [f"dataset: {meta['dataset']}", f"A: {meta['A']}", f"B: {meta['B']}"] + cpus


def by_search(level, a_items, b_items, ids):
    """
    Report lines with B/A of the total of each kind of street search. The id of a street item is
    "case:search-MODE", e.g. transit-001:access-WALK.
    """
    totals = {}
    for i in ids:
        if ":" in i:
            total = totals.setdefault(i.split(":")[1], [0, 0, 0])
            total[0] += 1
            total[1] += a_items[i]
            total[2] += b_items[i]
    if not totals:
        return []
    return [f"{level}: B/A of the total by search and mode: "
            + ", ".join(f"{name} {b / a:.3f} ({n} items)" for name, (n, a, b) in sorted(totals.items()))]


def per_fork(results, level, metric):
    """For each fork: item id -> the median over the iterations."""
    return [{item["id"]: median(item[metric]) for item in r["levels"][level]} for r in results]


def median_over_forks(forks):
    ids = forks[0].keys()
    return {i: median([f[i] for f in forks if i in f]) for i in ids}


def fingerprint_mismatches(forks, level):
    """
    The items that do not have the same result in every fork, or are missing in one. An item that
    replays a recorded request ("recorded") must also give the result of the first run.
    """
    runs = [r["levels"][level] for side in "AB" for r in forks[side]]
    prints, count = {}, {}
    for items in runs:
        for item in items:
            prints.setdefault(item["id"], set()).update({item["fingerprint"], item.get("recorded", item["fingerprint"])})
            count[item["id"]] = count.get(item["id"], 0) + 1
    return sorted(k for k in prints if len(prints[k]) > 1 or count[k] != len(runs))


def median(values):
    s = sorted(values)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


def geomean(a_items, b_items, ids):
    logs = [math.log(b_items[i] / a_items[i]) for i in ids]
    return math.exp(sum(logs) / len(logs))


# The 99.5 % quantile of Student's t distribution, for 1, 2, ... degrees of freedom: a 99 % interval.
# Not 95 %: an experiment series makes many comparisons, and with 95 % one in twenty comparisons of
# the same code is "faster" or "slower" (measured: street 2.6 % with 3 forks of each).
T_995 = [63.657, 9.925, 5.841, 4.604, 4.032, 3.707, 3.499, 3.355, 3.250, 3.169, 3.106, 3.055, 3.012, 2.977,
         2.947, 2.921, 2.898, 2.878, 2.861, 2.845]


def fork_levels(forks, ids):
    """For each fork the mean of the logarithm of the item times: the log of their geometric mean."""
    return [sum(math.log(f[i]) for i in ids) / len(ids) for f in forks]


def ratio_interval(a_forks, b_forks, ids):
    """
    The B/A ratio of the geometric mean of the item times, with its 99 % confidence interval.
    A fork is one sample: all its items share the state of that JVM run (JIT decisions, memory
    layout, the cores it ran on), which makes a whole fork faster or slower. The interval is the
    t-interval of the difference between the forks of A and the forks of B, so it is wide with few
    forks, and there is none with one fork each.
    """
    nan = float("nan")
    if not ids:
        return nan, nan, nan
    a, b = fork_levels(a_forks, ids), fork_levels(b_forks, ids)
    mean_a, mean_b = sum(a) / len(a), sum(b) / len(b)
    freedom = len(a) + len(b) - 2
    if freedom < 1:
        return math.exp(mean_b - mean_a), nan, nan
    variance = (sum((x - mean_a) ** 2 for x in a) + sum((x - mean_b) ** 2 for x in b)) / freedom
    t = T_995[freedom - 1] if freedom <= len(T_995) else 2.576
    half = t * math.sqrt(variance * (1 / len(a) + 1 / len(b)))
    return math.exp(mean_b - mean_a), math.exp(mean_b - mean_a - half), math.exp(mean_b - mean_a + half)


def uneven_forks(forks, level, ids, metric):
    """
    A report line with the speed of each fork relative to the mean of all forks. A fork far from
    the others ran on slower cores or was disturbed, whatever build it ran.
    """
    names = [f"{side}{i}" for side in "AB" for i in range(len(forks[side]))]
    levels = fork_levels(per_fork(forks["A"] + forks["B"], level, metric), ids)
    mean = sum(levels) / len(levels)
    relative = [math.exp(x - mean) for x in levels]
    line = f"{level}: time of each fork relative to the mean: " + ", ".join(
        f"{name} {value:.3f}" for name, value in zip(names, relative))
    if max(relative) / min(relative) > UNEVEN_FORKS:
        line += "  UNEVEN: a fork ran on slower cores or was disturbed; run it again"
    return line


def fmt_ns(ns):
    return f"{ns / 1e9:.2f}s" if ns >= 1e9 else f"{ns / 1e6:.1f}ms"


def cmd_report(args):
    print(bench_report(Path(args.dir), args.metric))


# ---------------------------------------------------------------------------------------------


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(description="OTP correctness and performance rig")
    p.add_argument("--dataset", default="oslo")
    p.add_argument("--heap", help="default: heap of the dataset settings, else 6g")
    p.add_argument("--wait", action="store_true", help="wait for a rig.py command that is running, then run")
    sub = p.add_subparsers(dest="command", required=True)

    s = sub.add_parser("build", help="compile the working tree and the rig (build 'wt')")
    s.add_argument("--fast", action="store_true", help="compile only changed files (no full module recompile)")
    s.set_defaults(func=cmd_build)

    s = sub.add_parser("freeze", help="copy the 'wt' build under a name, e.g. 'base'")
    s.add_argument("name")
    s.add_argument("--force", action="store_true")
    s.set_defaults(func=cmd_freeze)

    s = sub.add_parser("builds", help="list builds")
    s.set_defaults(func=cmd_builds)

    s = sub.add_parser("graph", help="build the dataset graph with a build")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.add_argument("--name", help="graph name, default: 'default' for wt, else the build name")
    s.add_argument("--graph-heap", help="default: graphHeap of the dataset settings, else 10g")
    s.set_defaults(func=cmd_graph)

    s = sub.add_parser("graph-fingerprint", help="write the content of a graph as sorted, hashed text")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.add_argument("--graph", help="the graph file, default: the graph of the build")
    s.add_argument("--name", help="fingerprint name, default: the build name")
    s.set_defaults(func=cmd_graph_fingerprint)

    s = sub.add_parser("graph-diff", help="compare two graph fingerprints")
    s.add_argument("a")
    s.add_argument("b")
    s.add_argument("--max-diffs", type=int, default=20)
    s.set_defaults(func=cmd_graph_diff)

    s = sub.add_parser("gen-cases", help="generate the cases file of the dataset")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.add_argument("--seed", default="1")
    s.set_defaults(func=cmd_gen_cases)

    for name, func, help_text in [("record", cmd_record, "record all layers of the results of a build"),
                                  ("check", cmd_check, "record the 'wt' build and compare with a baseline record")]:
        s = sub.add_parser(name, help=help_text)
        if name == "record":
            s.add_argument("name")
            s.add_argument("--build", default=WORKTREE_BUILD)
        else:
            s.add_argument("--baseline", default="base")
            s.add_argument("--build", default=WORKTREE_BUILD)
            s.add_argument("--name", help="record name, default: check-<build>")
            s.add_argument("--max-diffs", type=int, default=10)
            s.add_argument("--tests", choices=["none", "changed", "all"],
                           help="OTP tests to run: default 'changed' (modules changed since the "
                                "baseline build), 'none' with --quick")
        s.add_argument("--quick", action="store_true", help="every 4th case of each category")
        s.add_argument("--only", help="regex on case id or category")
        s.add_argument("--every")
        s.add_argument("--repeat", action="store_true", help="run again in the same JVM and compare")
        s.add_argument("--threads", help="run again on N threads and compare")
        s.set_defaults(func=func)

    s = sub.add_parser("test", help="run the OTP tests and compare with the test record of the baseline")
    s.add_argument("--scope", choices=["changed", "all"], default="changed")
    s.add_argument("--baseline", default="base")
    s.add_argument("--name", default=WORKTREE_BUILD, help="name of the test record to write")
    s.add_argument("--parse-only", action="store_true", help="only read the existing surefire reports")
    s.set_defaults(func=cmd_test)

    s = sub.add_parser("unit", help="the unit tests of the rig itself (Python and Java), seconds")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.set_defaults(func=cmd_unit)

    s = sub.add_parser("selftest", help="prove the rig finds planted bugs (perf-rig/mutations)")
    s.add_argument("--baseline", default="base")
    s.add_argument("--pattern", default="*.patch")
    s.add_argument("--fast", action="store_true", help="build --fast")
    s.add_argument("--quick", action="store_true")
    s.set_defaults(func=cmd_selftest)

    s = sub.add_parser("mutate", help="plant small bugs in the executed code of source files: does check find them?")
    s.add_argument("files", nargs="+", help="Java source files, relative to the repo root")
    s.add_argument("--count", type=int, default=10, help="the number of bugs to try")
    s.add_argument("--seed", type=int, default=1)
    s.add_argument("--baseline", default="base")
    s.add_argument("--timeout", type=int, default=600, help="seconds before a check is stopped (an endless loop)")
    s.set_defaults(func=cmd_mutate)

    s = sub.add_parser("try", help="one round of an experiment: build --fast, check --quick, bench --ops --quick")
    s.add_argument("--levels", default="raptor")
    s.add_argument("--against", default="base", help="the build to compare the work done with")
    s.set_defaults(func=cmd_try)

    s = sub.add_parser("accept", help="before a commit: build, coverage, check --repeat --threads 8 --tests all")
    s.set_defaults(func=cmd_accept)

    s = sub.add_parser("journal", help="commit perf-rig/JOURNAL.md on this branch and on branch 'perf-rig'")
    s.add_argument("message")
    s.set_defaults(func=cmd_journal)

    s = sub.add_parser("coverage", help="the OTP code no case executes: changed methods, and by package")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.add_argument("--baseline", default="base", help="the changes are counted from the commit of this build")
    s.add_argument("--quick", action="store_true")
    s.add_argument("--only")
    s.add_argument("--every")
    s.set_defaults(func=cmd_coverage)

    s = sub.add_parser("diff", help="compare two records")
    s.add_argument("a")
    s.add_argument("b")
    s.add_argument("--max-diffs", type=int, default=10)
    s.set_defaults(func=cmd_diff)

    s = sub.add_parser("bench", help="A/B benchmark of two builds, interleaved forks")
    s.add_argument("--baseline", default="base")
    s.add_argument("--candidate", default=WORKTREE_BUILD)
    s.add_argument("--levels", default="e2e,raptor,street")
    s.add_argument("--ops", action="store_true",
                   help="count the bytecode instructions executed instead of measuring time: no noise")
    s.add_argument("--forks", type=int, help="default 3, with --ops 1")
    s.add_argument("--warmup", help="default 2, with --ops 1")
    s.add_argument("--iterations", help="default 3, with --ops 2")
    s.add_argument("--metric", default="wall", choices=["wall", "cpu", "alloc"])
    s.add_argument("--cpus", default="fast",
                   help="the logical processors a time benchmark runs on: fast (default: the fastest cores, the "
                        "P-cores of a hybrid CPU), slow (the others), all, or a list like 4-11")
    s.add_argument("--quick", action="store_true")
    s.add_argument("--only")
    s.add_argument("--every")
    s.set_defaults(func=cmd_bench)

    s = sub.add_parser("profile", help="where the time and the work of a level go: the top methods of a build")
    s.add_argument("--build", default=WORKTREE_BUILD)
    s.add_argument("--levels", default="raptor")
    s.add_argument("--top", type=int, default=25)
    s.add_argument("--quick", action="store_true")
    s.add_argument("--only")
    s.add_argument("--every")
    s.set_defaults(func=cmd_profile)

    s = sub.add_parser("report", help="print the report of a benchmark directory again")
    s.add_argument("dir")
    s.add_argument("--metric", default="wall", choices=["wall", "cpu", "alloc"])
    s.set_defaults(func=cmd_report)

    args = p.parse_args()
    args.heap = args.heap or dataset_settings(args.dataset).get("heap", "6g")
    if args.command in READ_ONLY or os.environ.get("PERF_RIG_LOCKED"):
        args.func(args)
        return
    lock = acquire_lock(args.command, args.wait)
    os.environ["PERF_RIG_LOCKED"] = "1"  # the commands this one starts run under its lock
    try:
        left = unplant()
        if left:
            log(f"A selftest or mutate was stopped before it removed its bug; it is removed now ({left})")
        args.func(args)
    finally:
        lock.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
