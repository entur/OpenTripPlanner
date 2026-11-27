package org.opentripplanner.perfrig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Runs the cases with the ops agent and writes, for every method the agent counts, the
 * instructions it executed while the cases ran: a method with none is not tested by the cases.
 */
final class CoverageCommand {

  private CoverageCommand() {}

  static int run(Args args) throws Exception {
    if (!OpsCounter.active()) {
      throw new IllegalStateException("The coverage command needs the ops agent");
    }
    var server = RigServer.load(args.file("dataset"), args.fileOrNull("graph"));
    var cases = RecordCommand.selectCases(args);
    var runner = new CaseRunner(server);
    RecordCommand.log("Graph loaded. Running " + cases.size() + " cases.");

    // What ran while the graph was loaded is not counted
    long[] loaded = OpsCounter.byMethod();
    for (var c : cases) {
      runner.run(c, false);
    }
    var text = new StringBuilder("method\tfirstLine\tlastLine\tops\n");
    for (var m : OpsCounter.methodsSince(loaded)) {
      text.append(m.name()).append('\t').append(m.firstLine()).append('\t');
      text.append(m.lastLine()).append('\t').append(m.ops()).append('\n');
    }
    Files.writeString(args.file("out").toPath(), text, StandardCharsets.UTF_8);
    RecordCommand.log("Done");
    return 0;
  }
}
