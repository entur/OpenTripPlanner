package org.opentripplanner.updater.spi;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Routes each write domain to the callback of its writer thread. The registration method ties
 * the domain token and the callback to the same context type, which makes the single internal
 * cast sound — unlike a bare map from domain to wildcard callback, where the pairing would rest
 * on every call site.
 */
public class WriteToGraphCallbacks {

  private final Map<WriteDomain<?>, WriteToGraphCallback<?>> byDomain = new HashMap<>();

  public <C> WriteToGraphCallbacks with(WriteDomain<C> domain, WriteToGraphCallback<C> callback) {
    byDomain.put(domain, callback);
    return this;
  }

  /**
   * Route every write domain to the same callback — the behavior before the write-domain split,
   * still useful in tests. The callback must ignore its task context (e.g.
   * {@link WriteToGraphCallback#noop()}), since this deliberately bypasses the domain/context
   * pairing that {@link #with(WriteDomain, WriteToGraphCallback)} enforces.
   */
  public static WriteToGraphCallbacks sameForAllDomains(WriteToGraphCallback<?> callback) {
    var callbacks = new WriteToGraphCallbacks();
    for (var domain : WriteDomain.values()) {
      callbacks.byDomain.put(domain, callback);
    }
    return callbacks;
  }

  /**
   * The cast is sound because {@link #with(WriteDomain, WriteToGraphCallback)} only accepts a
   * callback whose context type matches the domain token used as its key.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <C> WriteToGraphCallback<C> forDomain(WriteDomain<C> domain) {
    return (WriteToGraphCallback<C>) byDomain.get(domain);
  }
}
