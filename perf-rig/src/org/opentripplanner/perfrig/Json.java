package org.opentripplanner.perfrig;

import java.io.File;
import java.io.IOException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class Json {

  private static final JsonMapper MAPPER = JsonMapper.builder()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build();

  private Json() {}

  static String pretty(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  static void write(File file, Object value) throws IOException {
    MAPPER.writeValue(file, value);
  }
}
