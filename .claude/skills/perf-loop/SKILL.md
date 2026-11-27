---
name: perf-loop
description: Work continuously on finding OTP performance gains with the perf-rig - profile, pick an idea, try it, accept or reject it, commit, write the journal, and go on with the next idea until they run out. Use when asked to find or continue finding performance improvements in OTP with the rig, to run the perf loop, or to speed up a level (raptor, street, link, e2e). Also says how the separate time check of the accepted steps is done, when the user asks for that.
---

# perf-loop

Find performance gains in OTP, one proven idea at a time, and keep going until a stop condition.
The argument is the level to work on: `raptor` (default), `street`, `link` or `e2e`.

The rig decides what a gain is, not you: an idea counts when `try` and `accept` say so, and it is
written in the journal whether it worked or not. The loop measures the work done (ops), which is
the same on a busy machine. It never measures time: that is a check of its own, see "The time
check".

Read first, every session:

- `perf-rig/README.md`, "The loop" and "Rules": the commands, what each proves, and the rules for
  branches and the journal ("A series of experiments").
- `perf-rig/JOURNAL.md`: what was tried, what was accepted, and the ideas not tried yet.

## Start

Work in this worktree only, from the repo root, one rig command at a time. A command takes from a
minute to half an hour (`accept`, `mutate`, a time `bench`): start it in the background, so no tool
timeout cuts it off, and wait until it has ended. Do not edit any source file while a rig command
runs: it stops when the sources no longer match the build.

1. `git status` must be clean. If it is not, stop and say what is there: it may be someone's work.
2. `python perf-rig/rig.py builds`: build `base` must be of the commit the journal names. If it is
   missing or of another commit, stop: a new baseline is the user's decision.
3. The branch:
   - on `perf/<name>`: continue that series, unless the README rules say a new branch;
   - on `perf-rig`: start a series with `git switch -c perf/<level>-<n>`.

   Then get the whole journal: `git checkout perf-rig -- perf-rig/JOURNAL.md`.
4. After a switch to another commit, run a full `python perf-rig/rig.py build` (the checkout
   clears the compiled classes). If a command says the sources changed after the build:
   `python perf-rig/rig.py build --fast`.
5. The build to compare with is the last accepted step in the journal (`step<N>`), or `base` if the
   series has none, or if `rig.py builds` does not list that step.

## One round

1. **Pick an idea.** Take one from "Ideas not tried yet" in the journal. If there are none worth
   trying, run `python perf-rig/rig.py profile --quick --levels <level>`, read the code of the top
   methods, and add the ideas you see to that list (`rig.py journal "perf-rig: journal: ideas"`).
   Never pick an idea listed under
   "Tried", unless what made it fail has changed; then say what. An idea that only time can judge
   (fewer allocations, a denser layout) does not show in the ops: mark it `needs time` in the list
   and leave it for the time check.
2. **Know what the rig would miss.** The first time a series changes a file:
   `python perf-rig/rig.py coverage --quick`, then
   `python perf-rig/rig.py mutate <file> --count 10`. A bug that is NOT FOUND in the code the idea
   touches means a mistake there goes unnoticed: add a case that runs it (below), or pick another
   idea.
3. **Change the code.** Only what the idea needs: no clean-up, no renames. The results must stay
   identical; an idea that changes a result, a default or a config value is not a performance idea.
4. **Try**: `python perf-rig/rig.py try --levels <level> --against <step or base>`. Read:
   - the check: `CHECK PASSED`, else the idea is wrong;
   - the ops table: `B/A total` and `mismatch` 0, and the methods whose work changed: is it the
     work you meant to remove?
   Adjust and try again, at most three rounds for one idea.
5. **Decide.**
   - Accept when `B/A total` is 0.999 or lower at the level and the code is no harder to read than
     the gain is worth.
   - Anything else is rejected. A rejected idea is as useful in the journal as an accepted one.
6. **Accepted**: `python perf-rig/rig.py accept` must pass (if it does not: fix the idea, or reject
   it). Commit the code alone. `python perf-rig/rig.py freeze step<N>`, N being the number of the
   journal entry. Then write the entry and commit the journal (below).
   **Rejected**: undo the change (`git restore` the files of the idea), write the entry with the
   reason, and commit the journal.

Then the next round.

## The time check

Not a part of the loop. Do it only when the user asks for it, on a machine that does nothing else:
it is step 6 of "The loop" in the README (the time of the accepted steps, the Norway cases, the
whole OTP test suite). Its result is a journal entry, committed with
`python perf-rig/rig.py journal "perf-rig: journal: <N> time check"`.

## Adding a case

A case for code no case runs goes in `perf-rig/cases/<dataset>.extra.json`. Then
`python perf-rig/rig.py unit` and
`python perf-rig/rig.py record base --build base --repeat --threads 8`: the frozen `base` build
makes the expected results, never the working tree. Commit the case alone.

## Never

- Never `freeze base`, never record `base` with another build than `base`, never change a case,
  the rig or `perf-rig/mutations` to make a check pass. A difference means the idea is wrong.
- Never push, and never change anything outside this worktree and `../perf-rig-data`.
- Never change or skip an OTP test to get through `accept`.

## Commits

One line each, no body and no trailer. The code of an idea is one commit with nothing else in it,
worded so it could go upstream as it is.

The journal is never committed with `git commit`, but with
`python perf-rig/rig.py journal "perf-rig: journal: <N> <accepted|rejected>"`, which commits it on
this branch and on `perf-rig`: `perf-rig` has the whole journal, whatever branch the idea was
tried on. If it says the file lacks entries, another branch added them: get the journal again as
in "Start" and write your entry once more.

## Stop

Stop and report when:

- five ideas in a row were rejected and the ideas left are no better: the level is dry on this
  branch (the README says when a new branch is the answer);
- a check differs and you cannot explain it from your change, or the rig itself fails: that is a
  problem with the rig or with OTP, not something to work around;
- the user's budget of time or rounds is used.

The report: the steps accepted with their ops numbers, the ideas rejected and why in a line each,
the ideas left in the journal, and the steps that have had no time check yet.
