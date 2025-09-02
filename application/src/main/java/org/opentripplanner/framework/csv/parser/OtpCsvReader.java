package org.opentripplanner.framework.csv.parser;

import com.csvreader.CsvReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opentripplanner.datastore.api.DataSource;
import org.opentripplanner.utils.logging.ProgressTracker;
import org.slf4j.Logger;

/**
 * Use this class to read in a CSV file from a {@link DataSource}. You must provide (requierd):
 * <ul>
 *   <li>the {@code datasource} - the csv file to read</li>
 *   <li>the {@code logger} - the logger ro write progress tracking events.</li>
 *   <li>the {@code parserFactory} the factory to create a CSV parser for mapping into the row type.</li>
 *   <li>the {@code rowHandler} a handler for each row read. </li>
 * </ul>
 *
 * The class is responsible for processing the CSV file and orcastrating the parsing process.
 * A progress tracker is created and will be used to track the reading of the datasource.
 *
 * @param <T> The row type.
 */
public class OtpCsvReader<T> {

  private DataSource dataSource;
  private Function<CsvReader, AbstractCsvParser<T>> parserFactory;
  private Consumer<T> rowHandler;
  @Nullable
  private Logger logger;

  private OtpCsvReader() {}

  public static <S> OtpCsvReader<S> of() {
    return new OtpCsvReader<S>();
  }

  public OtpCsvReader<T> withDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
    return this;
  }

  /**
   * The logger is optional. If pressent, a {@link ProgressTracker} is created and the
   * log events is written to the logger.
   */
  public OtpCsvReader<T> withLogger(Logger logger) {
    this.logger = logger;
    return this;
  }

  public OtpCsvReader<T> withParserFactory(
    Function<com.csvreader.CsvReader, AbstractCsvParser<T>> parserFactory
  ) {
    this.parserFactory = parserFactory;
    return this;
  }

  public OtpCsvReader<T> withRowHandler(Consumer<T> rowHandler) {
    this.rowHandler = rowHandler;
    return this;
  }

  public void read() {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(parserFactory);
    Objects.requireNonNull(rowHandler);
    read(rowHandler);
  }

  private void read(Consumer<T> rowHandler) {
    ProgressTracker progress = null;

    if (!dataSource.exists()) {
      return;
    }
    if (logger != null) {
      progress = ProgressTracker.track("Read " + dataSource.name(), 10_000, -1);
      logger.info(progress.startMessage());
    }
    var reader = new com.csvreader.CsvReader(dataSource.asInputStream(), StandardCharsets.UTF_8);
    var parser = parserFactory.apply(reader);

    if (!parser.headersMatch()) {
      return;
    }

    while (parser.hasNext()) {
      // Do not convert this to a lambda expression. The logger will not be
      // able to log the correct source and line number for the caller.
      if (progress != null) {
        progress.step(m -> logger.info(m));
      }
      rowHandler.accept(parser.next());
    }

    if (progress != null) {
      logger.info(progress.completeMessage());
    }
    return;
  }
}
