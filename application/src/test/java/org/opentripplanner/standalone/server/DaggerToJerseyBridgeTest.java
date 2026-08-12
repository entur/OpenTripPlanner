package org.opentripplanner.standalone.server;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Injections;
import org.glassfish.jersey.process.internal.RequestScope;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.standalone.configure.RecordingRequestScopedFactory;
import org.opentripplanner.standalone.configure.RequestScopedFactory;
import org.opentripplanner.transit.service.TransitService;

class DaggerToJerseyBridgeTest {

  @Test
  void allAccessorsWithinOneRequestShareTheSameFactoryInstance() throws Exception {
    Supplier<RequestScopedFactory> factorySupplier = () -> {
      var factory = new RecordingRequestScopedFactory();
      factory.stub("transitService", dummy(TransitService.class));
      factory.stub("routingService", dummy(RoutingService.class));
      return factory.factory();
    };

    InjectionManager im = Injections.createInjectionManager();
    im.register(new DaggerToJerseyBridge(factorySupplier));
    im.completeRegistration();

    RequestScope requestScope = im.getInstance(RequestScope.class);

    RequestScopedFactory[] factoryFromRequestA = new RequestScopedFactory[1];
    requestScope.runInScope(() -> {
      RequestScopedFactory factory = im.getInstance(RequestScopedFactory.class);
      factoryFromRequestA[0] = factory;

      TransitService expectedTs = factory.transitService();
      RoutingService expectedRoutingService = factory.routingService();

      TransitService ts = im.getInstance(TransitService.class);
      RoutingService routingService = im.getInstance(RoutingService.class);
      assertSame(expectedTs, ts);
      assertSame(expectedRoutingService, routingService);

      assertSame(factory, im.getInstance(RequestScopedFactory.class));
    });

    RequestScopedFactory[] factoryFromRequestB = new RequestScopedFactory[1];
    requestScope.runInScope(() -> {
      factoryFromRequestB[0] = im.getInstance(RequestScopedFactory.class);
    });

    assertNotSame(factoryFromRequestA[0], factoryFromRequestB[0]);
  }

  /** A distinct, behaviorless stand-in for {@code type}, only ever used for identity checks. */
  private static <T> T dummy(Class<T> type) {
    return type.cast(
      Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, m, a) -> null)
    );
  }
}
