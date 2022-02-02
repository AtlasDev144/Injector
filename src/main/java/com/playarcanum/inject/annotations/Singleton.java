package com.playarcanum.inject.annotations;

import com.playarcanum.inject.Injector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a class contains this, it signals to the {@link Injector} that only it can only have one instance of this class.
 * If duplicates af a class are being registered in the {@link Injector}, they must be named.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Singleton {}
