package com.playarcanum.olympus.module;

import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An abstract implementation of an {@link IInjectorModule}.
 */
public abstract class AbstractInjectorModule implements IInjectorModule<AbstractInjectorModule>{
    @Getter private final Set<Class<?>> injectables;
    @Getter private final Map<String, Class<?>> namedInjectables;

    public AbstractInjectorModule() {
        this.injectables = new HashSet<>();
        this.namedInjectables = new HashMap<>();
    }

    /**
     * Override this in derived classes and bind your injectable classes here.
     */
    public abstract AbstractInjectorModule enable();

    /**
     * Register a class to be an injectable source.
     * @param type
     */
    @Override
    public AbstractInjectorModule bind(final Class<?> type) {
        this.injectables.add(type);
        return this;
    }

    /**
     * Register a class with a corresponding name to be an injectable source.
     */
    @Override
    public AbstractInjectorModule bind(final Class<?> type, final @NonNull String name) {
        this.namedInjectables.put(name, type);
        return this;
    }

    /**
     * Register a submodule of injectable classes.
     * @param module
     */
    @Override
    public AbstractInjectorModule bind(final @NonNull AbstractInjectorModule module) {
        this.injectables.addAll(module.enable().injectables);
        return this;
    }
}
