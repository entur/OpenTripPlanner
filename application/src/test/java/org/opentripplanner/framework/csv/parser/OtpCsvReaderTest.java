package org.opentripplanner.framework.csv.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.csvreader.CsvReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.opentripplanner.datastore.api.FileType;
import org.opentripplanner.datastore.base.ByteArrayDataSource;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.utils.lang.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtpCsvReaderTest {

  private static final Logger LOG = LoggerFactory.getLogger(OtpCsvReaderTest.class);

  @Test
  void read() {
    var ds = new ByteArrayDataSource(
      "path",
      "OtpCsvReaderTestDataSource",
      FileType.GTFS,
      2,
      System.currentTimeMillis(),
      false
    );
    ds.withBytes(
      """
      a,b,c
      1, "Cat", 1
      2, "Boot", 0
      """.getBytes(StandardCharsets.UTF_8)
    );
    var expected = List.of(new AType(1, "Cat", true), new AType(2, "Boot", false));

    var list = new ArrayList<AType>();

    // Without logger
    OtpCsvReader.<AType>of()
      .withLogger(null)
      .withDataSource(ds)
      .withParserFactory(Parser::new)
      .withRowHandler(row -> list.add(row))
      .read();

    assertEquals(expected, list);

    // With logger
    list.clear();
    OtpCsvReader.<AType>of()
      .withLogger(LOG)
      .withDataSource(ds)
      .withParserFactory(Parser::new)
      .withRowHandler(row -> list.add(row))
      .read();
    assertEquals(expected, list);
  }

  static class Parser extends AbstractCsvParser<AType> {

    public Parser(CsvReader reader) {
      super(DataImportIssueStore.NOOP, reader, "issueType");
    }

    @Override
    protected List<String> headers() {
      return List.of("a", "b", "c");
    }

    @Nullable
    @Override
    protected AType createNextRow() throws HandledCsvParseException {
      return new AType(
        getInt("a", IntRange.ofInclusive(0, 10)),
        getString("b"),
        getGtfsBoolean("c")
      );
    }
  }

  record AType(int nr, String name, boolean ok) {}
}
