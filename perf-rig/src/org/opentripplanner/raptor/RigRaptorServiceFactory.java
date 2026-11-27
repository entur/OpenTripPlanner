package org.opentripplanner.raptor;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.request.RaptorEnvironment;
import org.opentripplanner.raptor.api.request.RaptorRequest;
import org.opentripplanner.raptor.api.request.RaptorTuningParameters;
import org.opentripplanner.raptor.api.response.RaptorResponse;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.raptor.extensions.direct.api.RaptorDirectTransitRequest;
import org.opentripplanner.raptor.extensions.extrasearch.ExtraMcRouterSearch;
import org.opentripplanner.raptor.spi.RaptorDataProvider;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * perf-rig: a {@link RaptorServiceFactory} that reports every Raptor call to the listener of the
 * calling thread. It lives in this package because the {@link RaptorService} constructors are
 * package private.
 */
public class RigRaptorServiceFactory<T extends RaptorTripSchedule> extends RaptorServiceFactory<T> {

  /** Receives the Raptor calls made by the current thread. */
  public interface Listener<T extends RaptorTripSchedule> {
    void route(
      RaptorRequest<T> request,
      RaptorDataProvider<T> data,
      @Nullable ExtraMcRouterSearch<T> extraMcSearch,
      RaptorResponse<T> response
    );

    void findAllDirectTransit(
      RaptorDirectTransitRequest request,
      RaptorTransitDataProvider<T> data,
      Collection<RaptorPath<T>> paths
    );
  }

  private static final ThreadLocal<Listener<?>> LISTENER = new ThreadLocal<>();

  private final RaptorConfig<T> config;

  public RigRaptorServiceFactory(
    RaptorTuningParameters tuningParameters,
    RaptorEnvironment environment
  ) {
    super(tuningParameters, environment);
    this.config = new RaptorConfig<>(tuningParameters, environment);
  }

  /** Set (or with {@code null} remove) the listener of the current thread. */
  public static void setListener(@Nullable Listener<?> listener) {
    if (listener == null) {
      LISTENER.remove();
    } else {
      LISTENER.set(listener);
    }
  }

  @Override
  public RaptorService<T> createService() {
    return createService(null);
  }

  @Override
  public RaptorService<T> createService(@Nullable ExtraMcRouterSearch<T> extraMcSearch) {
    return new ListeningRaptorService<>(config, extraMcSearch);
  }

  /** The plain service, without the listener; used to replay captured requests. */
  public RaptorService<T> createPlainService(@Nullable ExtraMcRouterSearch<T> extraMcSearch) {
    return new RaptorService<>(config, extraMcSearch);
  }

  private static final class ListeningRaptorService<T extends RaptorTripSchedule>
    extends RaptorService<T> {

    @Nullable
    private final ExtraMcRouterSearch<T> extraMcSearch;

    ListeningRaptorService(RaptorConfig<T> config, @Nullable ExtraMcRouterSearch<T> extraMcSearch) {
      super(config, extraMcSearch);
      this.extraMcSearch = extraMcSearch;
    }

    @Override
    public RaptorResponse<T> route(RaptorRequest<T> request, RaptorDataProvider<T> data) {
      var response = super.route(request, data);
      var listener = listener();
      if (listener != null) {
        listener.route(request, data, extraMcSearch, response);
      }
      return response;
    }

    @Override
    public Collection<RaptorPath<T>> findAllDirectTransit(
      RaptorDirectTransitRequest request,
      RaptorTransitDataProvider<T> transitData
    ) {
      var paths = super.findAllDirectTransit(request, transitData);
      var listener = listener();
      if (listener != null) {
        listener.findAllDirectTransit(request, transitData, paths);
      }
      return paths;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Listener<T> listener() {
      return (Listener<T>) LISTENER.get();
    }
  }
}
