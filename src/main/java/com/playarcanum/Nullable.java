package com.playarcanum;

import java.lang.annotation.*;

/**
 * This essentially serves as a warning to the developer that a certain value can be null.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Nullable {}

