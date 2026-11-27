package org.opentripplanner.perfrig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * A rig case: the variables of the Transmodel trip query, and how many pages to follow with the
 * cursors of the first response.
 */
record RigCase(String id, String category, int next, int prev, Map<String, Object> variables) {
  static final JsonMapper JSON = JsonMapper.builder().build();

  /**
   * The cases of the file, and the hand-written cases of the file next to it with the extension
   * {@code .extra.json}, if there is one.
   */
  static List<RigCase> read(File file) {
    var result = new ArrayList<>(readFile(file));
    var extra = new File(file.getPath().replaceFirst("\\.json$", ".extra.json"));
    if (extra.exists()) {
      result.addAll(readFile(extra));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static List<RigCase> readFile(File file) {
    try {
      Map<String, Object> root = JSON.readValue(file, LinkedHashMap.class);
      var result = new ArrayList<RigCase>();
      for (var it : (List<Map<String, Object>>) root.get("cases")) {
        result.add(
          new RigCase(
            (String) it.get("id"),
            (String) it.get("category"),
            ((Number) it.getOrDefault("next", 0)).intValue(),
            ((Number) it.getOrDefault("prev", 0)).intValue(),
            (Map<String, Object>) it.get("variables")
          )
        );
      }
      return result;
    } catch (Exception e) {
      throw new IllegalArgumentException("Can not read cases from " + file + ": " + e, e);
    }
  }

  static void write(File file, String dataset, List<RigCase> cases) throws IOException {
    var root = new LinkedHashMap<String, Object>();
    root.put("dataset", dataset);
    var list = new ArrayList<Map<String, Object>>();
    for (var c : cases) {
      var m = new LinkedHashMap<String, Object>();
      m.put("id", c.id());
      m.put("category", c.category());
      if (c.next() > 0) {
        m.put("next", c.next());
      }
      if (c.prev() > 0) {
        m.put("prev", c.prev());
      }
      m.put("variables", c.variables());
      list.add(m);
    }
    root.put("cases", list);
    JSON.writerWithDefaultPrettyPrinter().writeValue(file, root);
  }
}
