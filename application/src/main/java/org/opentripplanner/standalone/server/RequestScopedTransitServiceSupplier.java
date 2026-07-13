package org.opentripplanner.standalone.server;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import java.util.function.Supplier;
import org.opentripplanner.standalone.configure.RequestScopedFactory;
import org.opentripplanner.transit.service.TransitService;

/**
 * HK2-constructed bridge that resolves {@link TransitService} from the current HTTP request's
 * Dagger {@link RequestScopedFactory}. See {@link OTPWebApplication#makeRequestScopedBinder}.
 */
class RequestScopedTransitServiceSupplier implements Supplier<TransitService> {

  private final Provider<RequestScopedFactory> requestScopedComponent;

  @Inject
  RequestScopedTransitServiceSupplier(Provider<RequestScopedFactory> requestScopedFactory) {
    this.requestScopedComponent = requestScopedFactory;
  }

  @Override
  public TransitService get() {
    return requestScopedComponent.get().transitService();
  }
}
