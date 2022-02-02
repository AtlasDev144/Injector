package com.playarcanum.inject.module;

import com.playarcanum.inject.annotations.Singleton;
import lombok.NonNull;

/**
 * A module which registers objects for use with injections.
 */
public interface IInjectorModule<T extends IInjectorModule> {
    /**
     * This is where you should do all your bindings.
     * Simply returns an instance of itself.
     * @return
     */
    IInjectorModule<T> enable();

    /**
     * Register a {@link Singleton} annotated class.
     * @param type
     * @return
     */
    IInjectorModule<T> bind(final Class<?> type);

    /**
     * Register a non-{@link Singleton} annotated class with a corresponding name.
     * @param type
     * @param name
     * @return
     */
    IInjectorModule<T> bind(final Class<?> type, final @NonNull String name);

    /**
     * Register a submodule and all of its values.
     * @param submodule
     * @return
     */
    IInjectorModule<T> bind(final T submodule);
}
