package com.playarcanum.olympus.module;

import com.playarcanum.olympus.annotations.Singleton;
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
     * When the given {@code type} is the value wanting to be injected, the given {@code object}
     * will actually be the given value.
     * @param type
     * @param implementation
     * @return
     */
    <E> IInjectorModule<T> implementation(final Class<E> type, final @NonNull Class<? extends E> implementation);

    /**
     * When the given {@code type} is the value wanting to be injected, the given {@code object}
     * will actually be the given value and given name.
     * @param type
     * @param implementation
     * @return
     */
    <E> IInjectorModule<T> implementation(final Class<E> type, final @NonNull Class<? extends E> implementation, final @NonNull String name);

    /**
     * Register a submodule and all of its values.
     * @param submodule
     * @return
     */
    IInjectorModule<T> bind(final T submodule);
}
