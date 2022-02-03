package com.playarcanum.olympus.annotations;

import com.playarcanum.olympus.Injector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;

/**
 * An annotation that tells the {@link Injector} which constructor arguments to provide values for
 * when {@link Injector#construct(Class, Object...)} is used to instantiate a class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(PARAMETER)
public @interface Assisted {
    /**
     * An optional name that specifies which of the desired objects to fetch from the {@link Injector}.
     * This is not required and it intended to be used when fetching non-singleton objects.
     * @return
     */
    String name() default "";
}
