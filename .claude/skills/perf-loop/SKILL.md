---
name: perf-loop
description: Work continuously on finding OTP performance gains with the perf-rig - profile, pick an idea, try it, accept or reject it, commit, write the journal, and go on with the next idea until the user interrupts. Two modes - the normal one while the laptop is in use, and "idle" when the user gives the laptop away (a night), where the loop may close programs and also measures time and the CPU counters. Use when asked to find or continue finding performance improvements in OTP with the rig, to run the perf loop, or to speed up a level (raptor, street, link, e2e).
---

# perf-loop

Find performance gains in OTP, one proven idea at a time, and keep going until the user interrupts
or the session ends. There is no budget, and nothing to ask the user on the way.

The arguments: the level to work on, `raptor` (default), `street`, `link` or `e2e`, and `idle` if
the user gives the laptop away. Stay on that level for the whole session.

- **Normal mode**: the laptop is in use. The loop measures the work done (ops) and the allocated
  bytes, which a busy machine does not change. It measures no time, closes nothing, and leaves
  what needs time for the idle mode.
- **Idle mode**: nobody uses the laptop. The same loop, and also the time and the CPU counters:
  see "Idle mode".

The rig decides what a gain is, not you: an idea counts when `try` and `accept` say so, and it is
written in the journal whether it worked or not.

Read first, every session:

- `perf-rig/README.md`, "The loop" and "Rules": the commands, what each proves, and the rules for
  branches and the journal ("A series of experiments").
- `perf-rig/JOURNAL.md`: what was tried, what was accepted, the idea being tried, and the ideas not
  tried yet.

## Start

Work in this worktree only, from the repo root, one rig command at a time. A command takes from a
minute to half an hour (`accept`, `mutate`, a time `bench`): start it in the background, so no tool
timeout cuts it off, and wait until it has ended. Do not edit any source file while a rig command
runs: it stops when the sources no longer match the build.

1. The branch:
   - on `perf/<name>`: continue that series, unless the README rules say a new branch;
   - on `perf-rig`: start a series with `git switch -c perf/<level>-<n>`, n being the first number
     that no `perf/<level>-` branch has.

   Then get the whole journal: `git checkout perf-rig -- perf-rig/JOURNAL.md`.
2. What an interrupted session left. The journal has the idea that was being tried, if any, under
   "Being tried":
   - `git status` is not clean and an idea is being tried: the changes are that idea's. Go on with
     it at "Try", or undo them (`git restore`) and try the idea from the start.
   - `git status` is clean, an idea is being tried and its code is the last commit: it was
     accepted but not finished. Do the rest of "Accepted", starting with `accept` again.
   - `git status` is not clean and no idea is being tried: stop and say what is there. It may be
     someone's work.
3. `python perf-rig/rig.py builds`: build `base` must be of the commit the journal names. If it is
   missing or of another commit, stop: a new baseline is the user's decision.
4. After a switch to another commit, or if a command says that classes are gone, run a full
   `python perf-rig/rig.py build`. If a command says the sources changed after the build:
   `python perf-rig/rig.py build --fast`.
5. The build to compare with is the last accepted step in the journal (`step<N>`), or `base` if the
   series has none, or if `rig.py builds` does not list that step.

## One round

1. **Pick an idea.** The list "Ideas not tried yet" in the journal says which build its profile is
   of. If that is not the build you compare with (an idea was accepted since: the profile is
   another one now), or nothing in the list is worth trying, run
   `python perf-rig/rig.py profile --quick --levels <level>`, read the code of the top methods, add
   the ideas you see to the list, and write there which build the profile is of. Then take the
   idea that promises most, move it to "Being tried", and commit the journal
   (`rig.py journal "perf-rig: journal: trying <the idea>"`). Never pick an idea listed under
   "Tried", unless what made it fail has changed; then say what.
   - An idea about allocation (fewer objects) does not show in the ops: try it with `--alloc`,
     see below.
   - An idea about memory layout (the same objects, closer together) shows in neither. In the
     normal mode, mark it `needs time` in the list and take another idea. In the idle mode it is
     tried with a time benchmark, see "Idle mode".
2. **Know what the rig would miss.** The first time a series changes a file:
   `python perf-rig/rig.py coverage --quick`, then
   `python perf-rig/rig.py mutate <file> --count 10` (it tries the places the file has, at most 10,
   and ends with exit code 1 if a bug is NOT FOUND). A bug that is not found because it can not
   change any result is no gap: say why in the journal entry. Any other bug that is not found in
   the code the idea touches, or right next to it, means a mistake there goes unnoticed: add a case
   that finds it (below), or pick another idea.
3. **Change the code.** Only what the idea needs: no clean-up, no renames. The results must stay
   identical; an idea that changes a result, a default or a config value is not a performance idea.
4. **Try**: `python perf-rig/rig.py try --levels <level> --against <step or base>`. Read:
   - the check: `CHECK PASSED`, else the idea is wrong;
   - the ops table: `B/A total` and `mismatch` 0, and the methods whose work changed: is it the
     work you meant to remove?
   - for an idea about allocation, with `--alloc` added to the command: the verdict of the
     allocated bytes, and the methods whose allocated bytes changed.
   Adjust and try again, at most three rounds for one idea.
5. **Decide.**
   - Accept when `B/A total` of the ops is 0.999 or lower at the level and the code is no harder
     to read than the gain is worth.
   - Accept an idea about allocation when the verdict is `B allocates less` and the ops are not
     more (`B/A total` at most 1.001). `no verdict: the forks of A and B overlap` is no gain: the
     JIT compiler removes that allocation in some runs already.
   - Anything else is rejected. A rejected idea is as useful in the journal as an accepted one.
6. **Accepted**: `python perf-rig/rig.py accept` must pass (if it does not: fix the idea, or reject
   it). Commit the code alone. `python perf-rig/rig.py freeze step<N>`, N being the number of the
   journal entry. Then write the entry, take the idea out of "Being tried", and commit the journal
   (below). In the entry, the ratio against `base` is the product of the ratios of the steps, not
   a measurement.
   **Rejected**: undo the change (`git restore` the files of the idea), write the entry with the
   reason, take the idea out of "Being tried", and commit the journal.

Then the next round.

## Keep going

Ideas do not run out, and the level does not change: when the ones a profile shows at once are
tried, think harder about the same level. There is no limit to how long you may think about it.

- Read the whole path of a search, not only the methods at the top of the profile, and ask what
  work the result does not need. Work that is not done gains more than work done faster.
- Profile with more methods (`--top 60`), and compare the three lists: a method with far more of
  the time than of the ops waits for memory.
- Look at which cases cost most in the report of `bench --ops`, and why those.
- Profile the other dataset (`--dataset norway`): long searches spend their work elsewhere.
- Read why an idea was rejected: another form of it may work.
- A larger change, of an algorithm or of how the data is laid out, goes on a new branch, as the
  README says.

Stop by yourself only when a check differs and you cannot explain it from your change, or when
the rig itself fails: that is a problem with the rig or with OTP, not something to work around.
Then report: the steps accepted with their numbers, the ideas rejected and why in a line each, the
ideas left, and the steps that have had no time check.

## Idle mode

The user has given the laptop away, and has said that anything on it may be closed. Time and the
CPU counters can be measured now: on a quiet laptop the forks of a build agree to 0.3 % in time and
cycles and 0.7 % in cache misses, on one in use they do not.

- **Make it quiet**, at the start and before every time benchmark: measure for a few seconds
  which processes use the CPU, and stop the programs that do (browsers, chat, music, an IDE,
  `wsl --shutdown`). Never stop the processes this session runs in (the parents of your shell),
  the security software, or Windows itself.
- **Keep it quiet**: while a time benchmark runs, do nothing. Start it in the background, end your
  turn and wait for the notification: output in the terminal and other commands disturb it.
- **A time benchmark** is `python perf-rig/rig.py bench --quick --levels <level> --forks 5
  --counters --baseline <build>`; the README, step 6, says how to read it. The counters need a
  session that was started as administrator; if the benchmark says the rights were not given,
  leave `--counters` out. A fork that was disturbed is run again by the rig. If the report still
  has the WARNING about other processes, the laptop is in use after all: its result does not
  count, and you go on as in the normal mode until the next accepted idea.

What the idle mode does, in this order, and then again from the top:

1. **The steps that have no time check**: a time benchmark of the working tree against `base`.
   A time check whose report has the WARNING is none. Write the result as a journal entry
   (`rig.py journal "perf-rig: journal: <N> time check"`). If the verdict is `B slower`, compare
   the steps one by one, `git revert` the one that is slower, and write why.
2. **The ideas marked `needs time`**: one round each as in "One round", but with a full
   `rig.py build`, `rig.py check --quick`, and a time benchmark against the last step in place of
   `try`. Accept on the verdict `B faster`; the counters say why (fewer cache misses and cycles
   for the same instructions).
3. **Rounds as in the normal mode**, with a time benchmark against the step before after every
   accepted idea, written in its journal entry. An idea that was accepted on the ops stays
   accepted if the time shows no difference; `B slower` takes it out again (`git revert`).

Windows does not go to sleep while a rig command runs, but it does if the lid is closed.

## Adding a case

A case for code no case runs goes in `perf-rig/cases/<dataset>.extra.json`. Then
`python perf-rig/rig.py unit` and
`python perf-rig/rig.py record base --build base --repeat --threads 8`: the frozen `base` build
makes the expected results, never the working tree. Commit the case alone.

## Never

- Never `freeze base`, never record `base` with another build than `base`, never change a case,
  the rig or `perf-rig/mutations` to make a check pass. A difference means the idea is wrong.
- Never push, and never change anything outside this worktree and `../perf-rig-data`. Stopping
  programs in the idle mode is the one exception.
- Never change or skip an OTP test to get through `accept`.

## Commits

One line each, no body and no trailer. The code of an idea is one commit with nothing else in it,
worded so it could go upstream as it is.

The journal is never committed with `git commit`, but with
`python perf-rig/rig.py journal "perf-rig: journal: <N> <accepted|rejected>"`, which commits it on
this branch and on `perf-rig`: `perf-rig` has the whole journal, whatever branch the idea was
tried on. If it says the file lacks entries, another branch added them: get the journal again as
in "Start" and write your entry once more.
