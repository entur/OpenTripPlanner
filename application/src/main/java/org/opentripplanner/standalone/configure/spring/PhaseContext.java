package org.opentripplanner.standalone.configure.spring;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * A thin wrapper around a Spring {@link AnnotationConfigApplicationContext} that mirrors one
 * phase of OTP's application assembly, replacing a single Dagger {@code @Component} factory.
 * <p>
 * OTP is assembled in three sequential phases (load → graph-build → construct-server). Each phase
 * was historically a generated {@code DaggerXxxFactory}: a set of {@code @Module} bindings plus a
 * {@code @Component.Builder} into which the previous phase's concrete instances (the graph,
 * repositories, configuration) were bound via {@code @BindsInstance}. This class reproduces that
 * shape:
 * <ul>
 *   <li>{@link #registerInstance} replaces {@code @BindsInstance} — a pre-built object from a
 *       prior phase is exposed as a singleton bean.</li>
 *   <li>{@link #registerConfig} replaces a {@code @Component(modules = ...)} entry — a
 *       {@code @Configuration} class whose {@code @Bean} methods replace {@code @Provides}/
 *       {@code @Binds}.</li>
 *   <li>Optional, feature-gated modules are registered with an ordinary {@code if} on
 *       {@link org.opentripplanner.framework.application.OTPFeature} <em>at build time</em>,
 *       preserving Dagger's runtime-evaluated semantics (and the ability of tests to toggle
 *       features). This is why {@code @Conditional}/{@code @ComponentScan} are deliberately
 *       NOT used.</li>
 * </ul>
 * Wiring is explicit and programmatic; no classpath scanning is performed. Because the lost
 * compile-time validation of {@code @BindsInstance} is the main risk of the migration,
 * {@link #registerInstance} rejects {@code null} for non-nullable instances so a missing
 * hand-off fails fast with a clear message rather than surfacing deep inside startup.
 */
public class PhaseContext implements AutoCloseable {

  private final AnnotationConfigApplicationContext context;
  private boolean refreshed = false;

  public PhaseContext() {
    this(false);
  }

  /**
   * @param lazyInit when {@code true}, every bean is created on first access rather than eagerly at
   *   {@link #refresh()}. This mirrors Dagger's lazy instance creation and is required for the load
   *   phase, where construction must stay cheap and side-effecting IO (e.g. opening the data store,
   *   reading config) must be deferred to an explicit, controlled access point — not run in the
   *   {@code LoadApplication} constructor.
   */
  public PhaseContext(boolean lazyInit) {
    this.context = new AnnotationConfigApplicationContext();
    if (lazyInit) {
      // Marks all (regular) bean definitions lazy after the @Configuration classes have been
      // processed. BeanFactoryPostProcessors/BeanPostProcessors are still created eagerly by Spring.
      context.addBeanFactoryPostProcessor(beanFactory -> {
        for (String name : beanFactory.getBeanDefinitionNames()) {
          beanFactory.getBeanDefinition(name).setLazyInit(true);
        }
      });
    }
  }

  /**
   * Register a non-null, pre-built instance from a prior phase as a singleton bean of the given
   * type (the {@code @BindsInstance} replacement). Throws if {@code instance} is {@code null}.
   */
  public <T> PhaseContext registerInstance(Class<T> type, T instance) {
    if (instance == null) {
      throw new IllegalArgumentException(
        "Required phase instance of type %s was null.".formatted(type.getName())
      );
    }
    return registerSupplier(type.getSimpleName(), type, () -> instance);
  }

  /**
   * Register a possibly-null, pre-built instance as a singleton bean. When {@code instance} is
   * {@code null} nothing is registered, so downstream consumers must inject it as
   * {@code @Nullable} (or via {@code ObjectProvider}). This mirrors Dagger's {@code @Nullable
   * @BindsInstance}.
   */
  public <T> PhaseContext registerNullableInstance(Class<T> type, @Nullable T instance) {
    if (instance == null) {
      return this;
    }
    return registerSupplier(type.getSimpleName(), type, () -> instance);
  }

  private <T> PhaseContext registerSupplier(String name, Class<T> type, Supplier<T> supplier) {
    assertNotRefreshed();
    context.registerBean(name, type, supplier);
    return this;
  }

  /**
   * Register one or more {@code @Configuration} classes whose {@code @Bean} methods supply this
   * phase's services (the {@code @Component(modules = ...)} replacement).
   */
  public PhaseContext registerConfig(Class<?>... configClasses) {
    assertNotRefreshed();
    context.register(configClasses);
    return this;
  }

  /**
   * Instantiate every singleton and resolve all dependencies. A missing or unsatisfiable
   * dependency throws here — the runtime equivalent of Dagger's compile-time graph validation.
   */
  public PhaseContext refresh() {
    assertNotRefreshed();
    context.refresh();
    refreshed = true;
    return this;
  }

  /** Retrieve a required bean; throws if absent. */
  public <T> T get(Class<T> type) {
    return context.getBean(type);
  }

  /** Retrieve an optional bean, returning {@code null} when no such bean is defined. */
  @Nullable
  public <T> T getNullable(Class<T> type) {
    return context.getBeanProvider(type).getIfAvailable();
  }

  /** Retrieve all beans of the given type (the {@code List<T>} injection equivalent). */
  public <T> List<T> getAll(Class<T> type) {
    return context.getBeanProvider(type).stream().toList();
  }

  @Override
  public void close() {
    context.close();
  }

  private void assertNotRefreshed() {
    if (refreshed) {
      throw new IllegalStateException("PhaseContext has already been refreshed.");
    }
  }
}
