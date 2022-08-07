package com.playarcanum.olympus;

import com.playarcanum.Debugger;
import com.playarcanum.Nullable;
import com.playarcanum.olympus.annotations.Assisted;
import com.playarcanum.olympus.annotations.Inject;
import com.playarcanum.olympus.annotations.Singleton;
import com.playarcanum.olympus.module.AbstractInjectorModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * This processes the {@link Inject} annotation and tries to inject classes into the annotated fields.
 *
 * {@link Singleton} tells the {@link Injector} that it can only contain one of those classes. This annotation, although
 * not necessary, promotes good practice and will probably be on the majority of the objects in an {@link Injector}.
 *
 * {@link Inject} tells the {@link Injector} which fields to provide values for when an object calls
 * {@link Injector#inject(Object)} in its constructor. This <i>must</i> be on any field that needs to be injected.
 *
 * {@link Assisted} tells the {@link Injector} which arguments to provide values for. {@link Assisted} only works
 * on constructor arguments. For {@link Assisted} values to be provided, {@link Injector#construct(Class, Object...)}
 * must be used to create that instantiated class.
 *
 * {@link Injector#construct(Class, Object...)} is the method to use when you need {@link Assisted} values to be
 * provided as constructor arguments.
 */
@SuppressWarnings("ALL")
public final class Injector {
    @Getter
    private final Registrar registrar;

    private final Set<Object> injectables = new HashSet<>();
    private final Map<String, Object> namedInjectables = new HashMap<>();
    private final Map<Class<?>, Object> implementations = new HashMap<>();
    private final Set<Injector.NamedImp> namedImpls = new HashSet<>();

    private final Debugger.Section logger;

    public Injector(final Debugger.Section logger) {
        this.logger = logger;
        this.registrar = new Registrar(this);
    }

    public static class InjectorException extends Exception {
        public InjectorException(final String message) {
            super(message);
        }
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
                                    //Check named impls
                                    injection = this.namedImpls.stream().filter(na -> na.name.equals(name)).findFirst().map(NamedImp::implementation).orElse(null);

                                    if(injection == null) {
                                        //Search named injectables
                                        injection = this.namedInjectables.get(name);
                                    }
                                } else {
                                    //Check implementations
                                    if(this.implementations.containsKey(arg)) {
                                        injection = this.implementations.get(arg);
                                    } else injection = this.find(arg).orElse(null);
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
                        assistedParams[i] = param;
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

    /**
     * Invokes the specified static {@code method} on the given {@code class}, providing {@link Assisted} arguments
     * and using the given optional {@code arguments}.
     * @param clazz
     * @param method
     * @param arguments
     * @param <T>
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InjectorException
     */
    public <T> T invokeStatic(final Class<?> clazz, final @NonNull String method, final Object... arguments) throws InvocationTargetException, IllegalAccessException, InjectorException {
        final Method[] methods = clazz.getDeclaredMethods();

        //Get classes of given parameters
        final Class<?>[] params = new Class[arguments == null ? 0 : arguments.length];
        if(params.length > 0) {
            for(int i = 0; i < arguments.length; i++) {
                params[i] = arguments[i].getClass();
            }
        }

        //Find the specified method
        for(final Method m : methods) {
            if(m.getName().equalsIgnoreCase(method)) {
                m.setAccessible(true);

                //If it has no arguments
                if(m.getParameterCount() == 0) return (T) m.invoke(null);

                //The parameter values to give the method
                final Object[] assistedParams = new Object[m.getParameterCount()];

                final Queue<Object> parametersCopy = new ArrayDeque<>();
                for(final Object param : arguments) {
                    parametersCopy.add(param);
                }

                //Classes of this method's arguments
                final Class<?>[] argsClasses = m.getParameterTypes();

                //The annotations of each argument
                final Annotation[][] argAnnotations = m.getParameterAnnotations();

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
                                    //Check named impls
                                    injection = this.namedImpls.stream().filter(na -> na.name.equals(name)).findFirst().map(NamedImp::implementation).orElse(null);

                                    if(injection == null) {
                                        //Search named injectables
                                        injection = this.namedInjectables.get(name);
                                    }
                                } else {
                                    if(this.implementations.containsKey(arg)) {
                                        injection = this.implementations.get(arg);
                                    } else injection = this.find(arg).orElse(null);
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
                        assistedParams[i] = param;
                    }
                }

                boolean canInvoke = true;
                for(int i = 0; i < assistedParams.length; i++) {
                    if(assistedParams[i] == null) canInvoke = false;
                }

                if(canInvoke) return (T) m.invoke(null, assistedParams);
            }
        }
        return null;
    }

    /**
     * Invokes the specified {@code method} on the given {@code instance}, providing {@link Assisted} arguments
     * and using the given optional {@code arguments}.
     * @param instance
     * @param method
     * @param arguments
     * @param <T>
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InjectorException
     */
    public <T> T invoke(final Object instance, final @NonNull String method, final Object... arguments) throws InvocationTargetException, IllegalAccessException, InjectorException {
        final Method[] methods = instance.getClass().getDeclaredMethods();

        //Get classes of given parameters
        final Class<?>[] params = new Class[arguments == null ? 0 : arguments.length];
        if(params.length > 0) {
            for(int i = 0; i < arguments.length; i++) {
                params[i] = arguments[i].getClass();
            }
        }

        //Find the specified method
        for(final Method m : methods) {
            if(m.getName().equalsIgnoreCase(method)) {
                m.setAccessible(true);
                
                //If it has no arguments
                if(m.getParameterCount() == 0) return (T) m.invoke(instance);

                //The parameter values to give the method
                final Object[] assistedParams = new Object[m.getParameterCount()];

                final Queue<Object> parametersCopy = new ArrayDeque<>();
                for(final Object param : arguments) {
                    parametersCopy.add(param);
                }

                //Classes of this method's arguments
                final Class<?>[] argsClasses = m.getParameterTypes();

                //The annotations of each argument
                final Annotation[][] argAnnotations = m.getParameterAnnotations();

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
                                    //Check named impls
                                    injection = this.namedImpls.stream().filter(na -> na.name.equals(name)).findFirst().map(NamedImp::implementation).orElse(null);

                                    if(injection == null) {
                                        //Search named injectables
                                        injection = this.namedInjectables.get(name);
                                    }
                                } else {
                                    if(this.implementations.containsKey(arg)) {
                                        injection = this.implementations.get(arg);
                                    } else injection = this.find(arg).orElse(null);
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
                        assistedParams[i] = param;
                    }
                }

                boolean canInvoke = true;
                for(int i = 0; i < assistedParams.length; i++) {
                    if(assistedParams[i] == null) canInvoke = false;
                }

                if(canInvoke) return (T) m.invoke(instance, assistedParams);
            }
        }
        return null;
    }

    /**
     * This should ideally only be used by the {@link Registrar#register(Class)}.
     * Register a class for injection later.
     *
     * @param injection
     */
    public <T extends Object> void register(final T injection) {
        if (injection.getClass().isAnnotationPresent(Singleton.class)) {
            for(final Object o : this.injectables) {
                if(o.getClass().isInstance(injection)) {
                    this.logger.error("Tried registering multiple instances of type: " + injection.getClass().getSimpleName() + ", yet this class is marked as a Singleton.");
                    return;
                }
            }

            this.injectables.add(injection);
            this.logger.debug("Injector registered: " + injection.getClass().getSimpleName());
        } else {
            this.logger.error("Error in Injector#register: " + injection.getClass().getSimpleName() + " doesn't have the @Singleton annotation." +
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
            this.logger.error("Tried registering an @Singleton class as a named class: " + injection.getClass().getSimpleName());
        } else {
            this.namedInjectables.put(name, injection);
            this.logger.debug("Injector registered: " + injection.getClass().getSimpleName() + " with name: " + name);
        }
    }

    /**
     * Register an object to be given as the value when the given {@code clazz} is the type being queried.
     * @param clazz
     * @param binding
     * @param <T>
     */
    protected <T extends Object> void register(final Class<?> clazz, final T binding) {
        if(binding.getClass().isAnnotationPresent(Singleton.class)) {
            this.implementations.put(clazz, binding);
        } else {
            this.logger.error("Error in Injector#register: " + binding.getClass().getSimpleName() + " doesn't have the @Singleton annotation." +
                    "This annotation must be present for a class that isn't named to be registered in the Injector and is the developer's guarantee that only one instance of this class will exist.");
        }
    }

    /**
     * Registers a named implementation.
     * @param namedImp
     */
    protected void register(final @NonNull NamedImp namedImp) {
        this.namedImpls.add(namedImp);
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
                    //Check named impls
                    injection = this.namedImpls.stream().filter(na -> na.name.equals(name)).findFirst().map(NamedImp::implementation).orElse(null);

                    if(injection == null) {
                        //Search named injectables
                        injection = this.namedInjectables.get(name);
                    }
                } else {
                    //Search bindings
                    injection = this.implementations.get(field.getType());

                    if(injection == null) {
                        //Search singletons
                        for (Object i : this.injectables) {
                            if (i.getClass().isAssignableFrom(field.getType())) {
                                injection = i;
                            }
                        }
                    }
                }

                if (injection == null) {
                    this.logger.error("Intended injected class cannot be found in the Injector. Did you register it in the Registrar?");
                    return false;
                }

                try {
                    field.set(parent, injection);
                } catch (IllegalAccessException e) {
                    this.logger.error("Couldn't access field InjectProcessor: " + field.getName());
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    public static class Registrar {
        private final Injector injector;
        private final Debugger.Section debug;

        public Registrar(Injector injector) {
            this.injector = injector;
            this.debug = injector.logger;
        }

        /**
         * Register injectable classes via an {@link AbstractInjectorModule}. This is the preferred method of registering
         * injectable classes.
         *
         * @param module
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final @NonNull AbstractInjectorModule module) throws InstantiationException, IllegalAccessException {
            module.enable();
            return this.injectables(module.injectables())
                    .implementations(module.implementations())
                    .named(module.namedInjectables())
                    .namedImpl(module.namedImpls());
        }

        private Registrar namedImpl(final Set<AbstractInjectorModule.NamedImp<?>> namedImps) {
            namedImps.forEach(n -> {
                debug.debug("Beginning Injector Registration of named implemented type " + n.type().getSimpleName()
                        + " with implementation " + n.implementation().getSimpleName()
                        + " with name " + n.name());
                //Get all fields marked for injection within this class, ensuring this class' dependencies are already lodaded
                List<Class<?>> injected = new ArrayList<>();
                Field[] fields = n.implementation().getDeclaredFields();
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

                    this.injector.register(new NamedImp(n.type(), n.implementation().newInstance(), n.name()));
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                debug.debug("Finished Injector Registration!");
            });
            return this;
        }

        private Registrar implementations(final @NonNull Map<Class<?>, Class<?>> implementations) {
            implementations.forEach((type, imp) -> {
                debug.debug("Beginning Injector Registration of implemented type " + type.getSimpleName() + " with implementation " + imp.getSimpleName());
                //Get all fields marked for injection within this class, ensuring this class' dependencies are already lodaded
                List<Class<?>> injected = new ArrayList<>();
                Field[] fields = imp.getDeclaredFields();
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
                    this.injector.register(type, imp.newInstance());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                debug.debug("Finished Injector Registration!");
            });

            return this;
        }

        /**
         * Register the named injectables from an {@link AbstractInjectorModule}.
         *
         * @param named
         * @return
         */
        private Registrar named(final @NonNull Map<String, Class<?>> named) {
            named.forEach((string, clazz) -> {
                debug.debug("Beginning Injector Registration of named type " + string + "...");
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
                debug.debug("Finished Injector Registration!");
            });
            return this;
        }

        /**
         * Register a Collection of classes as injectable sources. {@link Registrar#register(AbstractInjectorModule)} is
         * preferred over this.
         *
         * @param classes
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar injectables(final Collection<Class<?>> classes) throws InstantiationException, IllegalAccessException {
            debug.debug("Beginning Injector Registration...");
            for (Class<?> aClass : classes) {
                this.register(aClass);
            }
            debug.debug("Finished Injector Registration!");
            return this;
        }

        /**
         * Register an array of classes as injectable sources. {@link Registrar#register(AbstractInjectorModule)} is
         * preferred over this.
         *
         * @param classes
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Class<?>... classes) throws InstantiationException, IllegalAccessException {
            debug.debug("Beginning Injector Registration...");
            for (Class<?> c : classes) {
                this.register(c);
            }
            debug.debug("Finished Injector Registration!");
            return this;
        }

        /**
         * Registers a class to be used for injection and tries to instantiate all embedded injected classes this needs.
         * Assumes the passed class and all embedded injected classes all have a default constructor.
         * {@link Registrar#register(AbstractInjectorModule)} is preferred over this.
         *
         * @param clazz
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         */
        public Registrar register(final Class<?> clazz) throws InstantiationException, IllegalAccessException {
            debug.debug("Beginning Injector Registration...");
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
            this.injector.register(this.injector.construct(clazz));
            debug.debug("Finished Injector Registration!");
            return this;
        }
    }

    @AllArgsConstructor
    @Getter
    public static final class NamedImp {
        private final Class<?> type;
        private final @NonNull Object implementation;
        private final @NonNull String name;
    }

}
