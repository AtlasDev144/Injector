package com.playarcanum.olympus.annotations;

import com.playarcanum.olympus.Injector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

/**
 * This tells the {@link Injector} that this Field needs to have its value injected.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface Inject {
    /**
     * An optional name that specifies which of the desired objects to fetch from the {@link Injector}.
     * This is not required and it intended to be used when fetching non-singleton objects.
     * @return
     */
    String name() default "";
}
