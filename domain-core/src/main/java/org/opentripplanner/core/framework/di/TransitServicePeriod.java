package org.opentripplanner.core.framework.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * This qualifier is used to inject the TransitServicePeriod config parameter. {@code RUNTIME}
 * retention is required so the Spring DI container can resolve the qualified injection — Spring
 * uses reflection and cannot see CLASS-retained qualifiers.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface TransitServicePeriod {}
