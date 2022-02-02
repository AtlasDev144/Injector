package com.playarcanum.inject.annotations;

import com.playarcanum.inject.Injector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;

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
