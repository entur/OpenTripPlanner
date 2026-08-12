package org.opentripplanner.standalone.configure;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * A hand-rolled test double for {@link RequestScopedFactory}: records how many times each
 * accessor was called, without pulling in a mocking framework. Unstubbed accessors return
 * {@code null}; stub one with {@link #stub}.
 */
public final class RecordingRequestScopedFactory implements InvocationHandler {

  private final Map<String, Integer> callCounts = new HashMap<>();
  private final Map<String, Object> stubs = new HashMap<>();
  private final RequestScopedFactory proxy = (RequestScopedFactory) Proxy.newProxyInstance(
    RequestScopedFactory.class.getClassLoader(),
    new Class<?>[] { RequestScopedFactory.class },
    this
  );

  public RequestScopedFactory factory() {
    return proxy;
  }

  public void stub(String methodName, Object value) {
    stubs.put(methodName, value);
  }

  public int callCount(String methodName) {
    return callCounts.getOrDefault(methodName, 0);
  }

  /** The full set of accessors touched so far, each with its call count. */
  public Map<String, Integer> callCounts() {
    return Map.copyOf(callCounts);
  }

  @Override
  public Object invoke(Object proxyArg, Method method, Object[] args) {
    callCounts.merge(method.getName(), 1, Integer::sum);
    return stubs.get(method.getName());
  }
}
