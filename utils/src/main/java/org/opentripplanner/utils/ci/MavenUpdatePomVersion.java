package org.opentripplanner.utils.ci;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This class is used by the release scripts; Hence not part of the main OTP.
 * TODO: Convert this to a script.
 */
public class MavenUpdatePomVersion {

  private static final String VERSION_SEP = "-";
  private static final String POM_FILE_NAME = "pom.xml";

  private final List<String> tags = new ArrayList<>();
  private final List<String> pomFile = new ArrayList<>();
  private String mainVersion;
  private int versionNumber = 0;
  private String qualifierName;
  private String newVersion;

  public static void main(String[] args) {
    try {
      new MavenUpdatePomVersion().withArgs(args).run();
    } catch (Exception e) {
      System.err.println(e.getMessage());
      e.printStackTrace(System.err);
      System.exit(10);
    }
  }

  private MavenUpdatePomVersion withArgs(String[] args) throws IOException {
    if (args.length != 2 || Arrays.stream(args).anyMatch(s -> s.matches("(?i)-h|--help"))) {
      printHelp();
      System.exit(1);
    }
    qualifierName = verifyQualifierName(args[0]);

    String version = args[1];

    if (version.matches("\\d+")) {
      versionNumber = resolveVersionFromNumericString(version);
    } else {
      tags.addAll(readTagsFromFile(version));
    }
    return this;
  }

  private void run() throws IOException {
    for (Path pom : listPomFiles()) {
      readAndReplaceVersion(pom);
      replacePomFile(pom);
    }
    System.out.println(newVersion);
  }

  public void readAndReplaceVersion(Path pom) throws IOException {
    pomFile.clear();
    var pattern = Pattern.compile(
      "(\\s*<version>)(\\d+.\\d+.\\d+)" +
      VERSION_SEP +
      "(" +
      qualifierName +
      VERSION_SEP +
      "\\d+|SNAPSHOT)(</version>\\s*)"
    );
    boolean found = false;
    int i = 0;

    for (String line : Files.readAllLines(pom, UTF_8)) {
      // Look for the version in the 25 first lines
      if (!found) {
        var m = pattern.matcher(line);
        if (m.matches()) {
          mainVersion = m.group(2);
          newVersion =
            mainVersion + VERSION_SEP + qualifierName + VERSION_SEP + resolveVersionNumber();
          line = m.group(1) + newVersion + m.group(4);
          found = true;
        }
        if (++i == 25) {
          throw new IllegalStateException(
            "Version not found in first 25 lines of the pom.xml file."
          );
        }
      }
      pomFile.add(line);
    }
    if (!found) {
      throw new IllegalStateException(
        "Version not found in 'pom.xml'. Nothing matching pattern: " + pattern
      );
    }
  }

  public void replacePomFile(Path pom) throws IOException {
    Files.delete(pom);
    Files.write(pom, pomFile, UTF_8);
  }

  private static void printHelp() {
    System.err.println(
      "Use this small program to replace the OTP version '2.1.0-SNAPSHOT' \n" +
      "with a new version number with your own qualifier like '2.1.0-<ORG>-1'.\n" +
      "\n" +
      "Usage:\n" +
      "  $ java -cp script/target/classes  " +
      MavenUpdatePomVersion.class.getName() +
      " <ORG> (<NEW-VERSION-NUMBER>|<FILE-WITH-GIT-TAGS>)\n"
    );
  }

  private int resolveVersionNumber() {
    var pattern = Pattern.compile(
      "v" + mainVersion + VERSION_SEP + qualifierName + VERSION_SEP + "(\\d+)"
    );
    int maxTagVersion = tags
      .stream()
      .mapToInt(tag -> {
        var m = pattern.matcher(tag);
        return m.matches() ? Integer.parseInt(m.group(1)) : -999;
      })
      .max()
      .orElse(-999);

    return 1 + Math.max(maxTagVersion, versionNumber);
  }

  public static int resolveVersionFromNumericString(String arg) {
    try {
      return Integer.parseInt(arg);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
        "Unable to parse input, decimal number expected: '" + arg + "'"
      );
    }
  }

  private static Collection<String> readTagsFromFile(String arg) throws IOException {
    var tags = Files.readAllLines(Path.of(arg));
    if (tags.isEmpty()) {
      throw new IllegalStateException("Unable to load git tags from file: " + arg);
    }
    return tags;
  }

  private List<Path> listPomFiles() throws IOException {
    try (Stream<Path> stream = Files.walk(Paths.get(""))) {
      return stream
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().equals(POM_FILE_NAME))
        .toList();
    }
  }

  private static String verifyQualifierName(String qualifierName) {
    if (qualifierName.isBlank() || !qualifierName.matches("[A-Za-z_]+")) {
      throw new IllegalArgumentException(
        "The specified qualifier name is empty or contains illegal characters. Only [A-Za-z_] is " +
        "allowed. Input: '" +
        qualifierName +
        "'"
      );
    }
    return qualifierName;
  }
}
