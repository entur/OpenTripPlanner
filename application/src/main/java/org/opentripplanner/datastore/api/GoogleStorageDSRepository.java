package org.opentripplanner.datastore.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;
import org.opentripplanner.framework.application.OTPFeature;

/**
 * This qualifier is used to inject the Google Storage Data Source Repository. Enable the
 * {@link OTPFeature#GoogleCloudStorage} and the repository
 * is initialized automatically.
 * <p>
 * {@code RUNTIME} retention is required so Spring's reflection-based DI can match this qualifier;
 * Dagger (compile-time) works with any retention.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface GoogleStorageDSRepository {}
