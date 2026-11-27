package org.opentripplanner.perfrig;

/**
 * The perf-rig entry point. Commands:
 * <pre>
 * record    --dataset DIR [--graph FILE] --cases FILE --out DIR [--repeat] [--threads N]
 *           [--only REGEX] [--every N]
 * bench     --dataset DIR [--graph FILE] --cases FILE --out FILE --levels e2e,raptor,street,api
 *           [--warmup N] [--iterations N] [--only REGEX] [--every N] [--jfr FILE]
 * coverage  --dataset DIR [--graph FILE] --cases FILE --out FILE [--only REGEX] [--every N]
 * gen-cases --dataset DIR [--graph FILE] --out FILE [--seed N]
 * graph-fingerprint --graph FILE --out DIR
 * </pre>
 * The script {@code perf-rig/rig.py} builds the code and runs these commands.
 */
public final class RigMain {

  private RigMain() {}

  public static void main(String[] args) {
    int exitCode;
    try {
      if (args.length == 0) {
        throw new IllegalArgumentException("Missing command");
      }
      var options = new Args(args, 1);
      exitCode = switch (args[0]) {
        case "record" -> RecordCommand.run(options);
        case "bench" -> BenchCommand.run(options);
        case "coverage" -> CoverageCommand.run(options);
        case "gen-cases" -> GenCasesCommand.run(options);
        case "graph-fingerprint" -> GraphFingerprintCommand.run(options);
        default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
      };
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exitCode = 2;
    }
    System.exit(exitCode);
  }
}
