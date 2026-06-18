package org.opentripplanner.datastore.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * This qualifier is used to inject the OTP base directory where the configuration files are
 * located.
 * <p>
 * {@code RUNTIME} retention is required so Spring's reflection-based DI can match this qualifier;
 * Dagger (compile-time) works with any retention.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface OtpBaseDirectory {}
