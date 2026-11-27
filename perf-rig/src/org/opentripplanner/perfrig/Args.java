package org.opentripplanner.perfrig;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** {@code --name value} and {@code --flag} command line arguments. */
final class Args {

  private final Map<String, String> values = new HashMap<>();

  Args(String[] args, int start) {
    for (int i = start; i < args.length; i++) {
      if (!args[i].startsWith("--")) {
        throw new IllegalArgumentException("Unexpected argument: " + args[i]);
      }
      String name = args[i].substring(2);
      if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
        values.put(name, args[++i]);
      } else {
        values.put(name, "true");
      }
    }
  }

  String get(String name) {
    var value = values.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing --" + name);
    }
    return value;
  }

  String get(String name, String defaultValue) {
    return values.getOrDefault(name, defaultValue);
  }

  int getInt(String name, int defaultValue) {
    return values.containsKey(name) ? Integer.parseInt(values.get(name)) : defaultValue;
  }

  boolean flag(String name) {
    return Boolean.parseBoolean(values.getOrDefault(name, "false"));
  }

  File file(String name) {
    return new File(get(name));
  }

  File fileOrNull(String name) {
    return values.containsKey(name) ? new File(values.get(name)) : null;
  }
}
