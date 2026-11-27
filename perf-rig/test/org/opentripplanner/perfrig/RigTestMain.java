package org.opentripplanner.perfrig;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Runs the unit tests of the rig: the tests in the class directory given as argument. */
public final class RigTestMain {

  public static void main(String[] args) {
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(Path.of(args[0]))))
      .build();
    var listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    var summary = listener.getSummary();
    var out = new PrintWriter(System.out, true);
    summary.printFailuresTo(out, 20);
    summary.printTo(out);
    boolean ok = summary.getTotalFailureCount() == 0 && summary.getTestsFoundCount() > 0;
    System.exit(ok ? 0 : 1);
  }
}
