package com.playarcanum.inject;

import com.playarcanum.Nullable;
import com.playarcanum.inject.annotations.Assisted;
import com.playarcanum.inject.annotations.Inject;
import com.playarcanum.inject.annotations.Singleton;
import com.playarcanum.inject.module.AbstractInjectorModule;
import lombok.Getter;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

/**
 * This processes the {@link Inject} annotation and tries to inject classes into the annotated fields.
 * <p>
 * To use this:
 *
 * TODO fill this out
 * 1) Singleton
 * 2) Inject
 * 3) Assisted
 * 4) Injector#create
 */
@SuppressWarnings("ALL")
public final class Injector {
    @Getter
    private final Registrar registrar;

    private final Set<Object> injectables = new HashSet<>();
    private final Map<String, Object> namedInjectables = new HashMap<>();

    private final Logger logger;

    public Injector() {
        this.logger = Logger.getLogger("Injector");
        this.registrar = new Registrar(this);
    }

    /**
     * Construct a new instance of the given {@link Class} type with optional {@code parameters}.
     * This method will attempt to provide any {@link Assisted} parameters in the given {@code type}'s constructor.
     * This will also attempt to inject any {@link Inject} annotated fields in the created instance.
     * @param type
     * @param parameters
     * @param <T>
     * @return
     */
    public <T> @Nullable T construct(final @NonNull Class<T> type, final Object... parameters) {
        try {
            //Find the constructor that contains the given args
            Constructor<T> constructor = null;

            //Get classes of given parameters
            final Class<?>[] params = new Class[parameters == null ? 0 : parameters.length];
            if(params.length > 0) {
                for(int i = 0; i < parameters.length; i++) {
                    params[i] = parameters[i].getClass();
                }
            }

            //Iterate through each constructor, looking for one we can use
            for(final Constructor<?> con : type.getDeclaredConstructors()) {
                if(constructor != null) continue;

                //If it's a default constructor, return a new instance
                if(con.getParameterCount() == 0) return (T) con.newInstance();

                //The parameter values to give the constructor
                final Object[] assistedParams = new Object[con.getParameterCount()];

                final Queue<Object> parametersCopy = new ArrayDeque<>();
                for(final Object param : parameters) {
                    parametersCopy.add(param);
                }

                //Classes of this constructor's arguments
                final Class<?>[] argsClasses = con.getParameterTypes();

                //The annotations of each argument
                final Annotation[][] argAnnotations = con.getParameterAnnotations();

                //Iterate each argument
                for(int i = 0; i < argsClasses.length; i++) {
                    //We need to ensure every argument is account for, either by @Assisted or by the parameters passed in
                    final Class<?> arg = argsClasses[i];

                    boolean assistedHandled = false;
                    if(argAnnotations[i] != null) {
                        for(final Annotation annotation : argAnnotations[i]) {
                            if(annotation instanceof Assisted) {
                                //See if we're looking for a named object
                                final String name = ((Assisted)annotation).name();

                                //Find the named object or the singleton object if not named
                                Object injection;
                                if(!name.isEmpty()) {
                                    injection = this.namedInjectables.get(name);
                                } else {
                                    injection = this.find(arg).orElse(null);
                                }

                                //Found an applicable injectable dependency
                                if(injection != null) {
                                    assistedParams[i] = arg.cast(injection);
                                    assistedHandled = true;
                                } else throw name.isEmpty() ? new InjectorException("Cannot find an injectable dependency for @Assisted paramter: "
                                + params[i].getSimpleName() + ", which is in the constructor of class: " + arg.getSimpleName() + ". " +
                                        "Was a corresponding depency registered for this?") :
                                        new InjectorException("Cannot find an injectable dependency for @Assisted named paramter: "
                                                + params[i].getSimpleName() + ", of assisted named: " + name + ", which is in the constructor of class: " + arg.getSimpleName() + ". " +
                                                "Was a corresponding depency registered for this?");
                            }
                        }
                    }

                    //This arg can't be found in the assisted values present in the Injector, so get the next passed param
                    //This assumes the passed params were passed in order of necessity by the developer
                    if(!assistedHandled) {
                        final Object param = parametersCopy.poll();
                        if(!param.getClass().isAssignableFrom(arg) && !arg.isAssignableFrom(param.getClass())) {
                            try {
                                logger.severe("--- arg: " + arg.getSimpleName() + " param: " + param.getClass().getSimpleName());
                                assistedParams[i] = arg.cast(param);
                            } catch (ClassCastException e) {
                                throw new InjectorException("A value for argument type: " + arg.getSimpleName() + " can't be found" +
                                    " while trying to instantiate class of type: " + type.getSimpleName() + " via Injector#construct");
                            }

                        } else {
                            //Else if we have the value in the parameters, add it
                            assistedParams[i] = param;
                        }
                    }
                }

                //We've now found an applicable constructor because every argument has been accounted for.
                constructor = (Constructor<T>) con;
                constructor.setAccessible(true);
                T instance = constructor.newInstance(assistedParams);

                this.inject(instance);
                return instance;
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InjectorException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static class InjectorException extends Exception {
        public InjectorException(final String message) {
            super(message);
        }
    }

    /**
     * This should only be used by the {@link Registrar#register(Class)}.
     * Register a class for injection later.
     *
     * @param injection
     */
    protected <T extends Object> void register(final T injection) {
        if (injection.getClass().isAnnotationPresent(Singleton.class)) {
            for(final Object o : this.injectables) {
                if(o.getClass().isInstance(injection)) {
                    this.logger.severe("Tried registering multiple instances of type: " + injection.getClass().getSimpleName() + ", yet this class is marked as a Singleton.");
                    return;
                }
            }

            this.injectables.add(injection);
            this.logger.info("Injector registered: " + injection.getClass().getSimpleName());
        } else {
            this.logger.severe("Error in Injector#register: " + injection.getClass().getSimpleName() + " doesn't have the @Singleton annotation." +
                    "This annotation must be present for a class that isn't named to be registered in the Injector and is the developer's guarantee that only one instance of this class will exist.");
        }
    }

    /**
     * Register a named class for injection.
     * @param injection
     * @param name
     * @param <T>
     */
    protected <T extends Object> void register(final T injection, final String name) {
        if (injection.getClass().isAnnotationPresent(Singleton.class)) {
            this.logger.severe("Tried registering an @Singleton class as a named class: " + injection.getClass().getSimpleName());
        } else {
            this.namedInjectables.put(name, injection);
            this.logger.info("Injector registered: " + injection.getClass().getSimpleName() + " with name: " + name);
        }
    }

    /**
     * Find an Injectable class of type {@code type}.
     * @param type
     * @param <T>
     * @return
     */
    public <T> Optional<T> find(Class<T> type) {
        return this.injectables.stream().filter(i -> i.getClass().isAssignableFrom(type)).map(i -> (T) i).findFirst();
    }

    /**
     * Get a named injectable class.
     * @param name
     * @param <T>
     * @return
     */
    public <T> @Nullable T find(final @NonNull String name) {
        return (T) this.namedInjectables.get(name);
    }

    /**
     * Inject all values annotated with {@link Inject} in the specified {@link Object}.
     * @param parent
     */
    public boolean inject(Object parent) {
        //Collect all fields in superclasses so that no field that needs to be injected is missed.
        List<Field> fields = new ArrayList<>();
        Class<? extends Object> superClass = parent.getClass().getSuperclass();
        Class<? extends Object> current = parent.getClass();
        fields.addAll(Arrays.asList(current.getDeclaredFields()));

        while (superClass != null) {
            current = superClass;
            superClass = current.getSuperclass();
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }

        //Inject all fields that need to be
        for (Field field : fields) {
            if (field.isAnnotationPresent(Inject.class)) {
                field.setAccessible(true);

                Object injection = null;
                //Is this named?
                final String name = field.getAnnotation(Inject.class).name();
                if(!name.isEmpty()) {
                    injection = this.namedInjectables.get(name);
                } else {
                    //Search singletons
                    for (Object i : this.injectables) {
                        if (i.getClass().isAssignableFrom(field.getType())) {
                            injection = i;
                        }
                    }
                }

                if (injection == null) {
                    this.logger.severe("Intended injected class cannot be found in the Injector. Did you register it in the Registrar?");
                    return false;
                }

                try {
                    field.set(parent, injection);
                } catch (IllegalAccessException e) {
                    this.logger.severe("Couldn't access field InjectProcessor: " + field.getName());
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    public static class Registrar {
        private final Injector injector;
        private final Logger debug;

        public Registrar(Injector injector) {
            this.injector = injector;
            this.debug = injector.logger;
        }

        /**
         * Register injectable classes via an {@link AbstractInjectorModule}. This is the preferred method of registering
         * injectable classes.
         * @param module
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final @NonNull AbstractInjectorModule module) throws InstantiationException, IllegalAccessException {
            module.enable();
            return this.register(module.injectables()).register(module.namedInjectables());
        }

        /**
         * Register the named injectables from an {@link AbstractInjectorModule}.
         * @param named
         * @return
         */
        private Registrar register(final @NonNull Map<String, Class<?>> named) {
            named.forEach((string, clazz) -> {
                debug.info("Beginning Injector Registration of named type " + string + "...");
                //Get all fields marked for injection within this class, ensuring this class' dependencies are already lodaded
                List<Class<?>> injected = new ArrayList<>();
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(Inject.class)) {
                        injected.add(field.getType());
                    }
                }

                //Ensure all necessary injected classes that this class needs are already created
                if (!injected.isEmpty()) {
                    for (Class<?> inject : injected) {
                        Optional<?> loaded = this.injector.find(inject);
                        if (!loaded.isPresent()) {
                            try {
                                this.register(inject);
                            } catch (InstantiationException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                            loaded = this.injector.find(inject);
                        }
                    }
                }
                try {
                    this.injector.register(clazz.newInstance(), string);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                debug.info("Finished Injector Registration!");
            });
            return this;
        }

        /**
         * Register a Collection of classes as injectable sources. {@link Registrar#register(AbstractInjectorModule)} is
         * preferred over this.
         * @param classes
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Collection<Class<?>> classes) throws InstantiationException, IllegalAccessException{
            debug.info("Beginning Injector Registration...");
            for (Class<?> aClass : classes) {
                this.register(aClass);
            }
            debug.info("Finished Injector Registration!");
            return this;
        }

        /**
         * Register an array of classes as injectable sources. {@link Registrar#register(AbstractInjectorModule)} is
         * preferred over this.
         * @param classes
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Class<?>... classes) throws InstantiationException, IllegalAccessException {
            debug.info("Beginning Injector Registration...");
            for (Class<?> c : classes) {
                this.register(c);
            }
            debug.info("Finished Injector Registration!");
            return this;
        }

        /**
         * Registers a class to be used for injection and tries to instantiate all embedded injected classes this needs.
         * Assumes the passed class and all embedded injected classes all have a default constructor.
         * {@link Registrar#register(AbstractInjectorModule)} is preferred over this.
         * @param clazz
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Class<?> clazz) throws InstantiationException, IllegalAccessException {
            debug.info("Beginning Injector Registration...");
            //Get all fields marked for injection within this class, ensuring this class' dependencies are already lodaded
            List<Class<?>> injected = new ArrayList<>();
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injected.add(field.getType());
                }
            }

            //Ensure all necessary injected classes that this class needs are already created
            if (!injected.isEmpty()) {
                for (Class<?> inject : injected) {
                    Optional<?> loaded = this.injector.find(inject);
                    if (!loaded.isPresent()) {
                        this.register(inject);
                        loaded = this.injector.find(inject);
                    }
                }
            }
            this.injector.register(clazz.newInstance());
            debug.info("Finished Injector Registration!");
            return this;
        }

        /**
         * Registers a class instance to be used for injection and tries to instantiate all embedded injected classes this needs.
         * Assumes the passed class and all embedded injected classes all have a default constructor.
         * Always try to use the {@link Registrar#register(Class)} instead.
         *
         * @param instance
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Object instance) throws InstantiationException, IllegalAccessException {
            debug.info("Beginning Injector Registration...");
            //Get all fields marked for injection within this class
            List<Class<?>> injected = new ArrayList<>();
            Field[] fields = instance.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injected.add(field.getType());
                }
            }

            //Ensure all necessary injected classes that this class needs are already created
            if (!injected.isEmpty()) {
                for (Class<?> inject : injected) {
                    Optional<?> loaded = this.injector.find(inject);
                    if (!loaded.isPresent()) {
                        this.register(inject);
                        loaded = this.injector.find(inject);
                    }
                }
            }
            this.injector.register(instance);
            debug.info("Finished Injector Registration!");
            return this;
        }
    }

}
