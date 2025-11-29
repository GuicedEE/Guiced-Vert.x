package com.guicedee.vertx.spi;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.guicedee.client.scopes.CallScopeProperties;
import com.guicedee.client.scopes.CallScopeSource;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.scopes.CallScoper;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import com.guicedee.vertx.VertxEventPublisher;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.WorkerExecutor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Registry for Vertx event consumers and publishers
 */
@Log4j2
public class VertxEventRegistry {

    private static final java.util.concurrent.ConcurrentHashMap<String, WorkerExecutor> workerExecutors = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static final Map<String, VertxEventDefinition> eventConsumerDefinitions = new HashMap<>();

    @Getter
    private static final Map<String, Class> eventConsumerClass = new HashMap<>();

    @Getter
    private static final Map<String, VertxEventDefinition> eventPublisherDefinitions = new HashMap<>();

    @Getter
    private static final Map<String, Method> eventConsumerMethods = new HashMap<>();

    @Getter
    private static final Map<String, Class<?>> eventConsumerMethodClasses = new HashMap<>();

    @Getter
    private static Map<String, Key<?>> eventPublisherKeys = new HashMap<>();

    /**
     * Map to store the reference types for consumers
     * Key: address, Value: reference type for the consumer's generic parameter
     */
    @Getter
    private static Map<String, Type> eventConsumerReferenceTypes = new HashMap<>();

    /**
     * Scans for classes with @VertxEventDefinition annotation and registers them
     */
    public static void scanAndRegisterEvents() {
        log.info("Scanning for Vertx event consumers and publishers");

        // Scan for classes with @VertxEventDefinition annotation
        var consumerClasses = IGuiceContext.instance().getScanResult()
                .getClassesWithAnnotation(VertxEventDefinition.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterfaceOrAnnotation() && !classInfo.isAbstract())
                .toList();

        for (var consumerClassInfo : consumerClasses) {
            try {
                Class consumerClass = consumerClassInfo.loadClass();
                VertxEventDefinition eventDefinition = (VertxEventDefinition) consumerClass.getAnnotation(VertxEventDefinition.class);
                String address = eventDefinition.value();

                log.debug("Registering Vertx event consumer for address: {}", address);
                eventConsumerDefinitions.put(address, eventDefinition);
                eventConsumerClass.put(address, consumerClass);

                // Look for a consume method that takes a Message parameter
                try {
                    Method consumeMethod = consumerClass.getMethod("consume", Message.class);
                    if (consumeMethod != null) {
                        // Extract the parameter type from the consume method
                        Parameter[] parameters = consumeMethod.getParameters();
                        if (parameters.length > 0 && Message.class.isAssignableFrom(parameters[0].getType())) {
                            Type paramType = parameters[0].getParameterizedType();
                            if (paramType instanceof ParameterizedType) {
                                ParameterizedType parameterizedType = (ParameterizedType) paramType;
                                Type[] typeArgs = parameterizedType.getActualTypeArguments();
                                if (typeArgs.length > 0) {
                                    Type consumerType = typeArgs[0];
                                    log.debug("Found reference type {} for consumer at address: {}", consumerType.getTypeName(), address);
                                    eventConsumerReferenceTypes.put(address, consumerType);
                                }
                            }
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // No consume method found, that's okay
                    log.debug("No consume method found for class {}", consumerClass.getName());
                }
            } catch (Exception e) {
                log.error("Error registering Vertx event consumer", e);
            }
        }

        // Scan for methods annotated with @VertxEventDefinition
        var methodConsumerClasses = IGuiceContext.instance().getScanResult()
                .getClassesWithMethodAnnotation(VertxEventDefinition.class)
                .filter(classInfo -> !classInfo.isInterfaceOrAnnotation() && !classInfo.isAbstract())
                .stream()
                .toList();

        for (var classInfo : methodConsumerClasses) {
            try {
                Class<?> clazz = classInfo.loadClass();
                for (Method method : clazz.getDeclaredMethods()) {
                    VertxEventDefinition eventDefinition = method.getAnnotation(VertxEventDefinition.class);
                    if (eventDefinition != null) {
                        String address = eventDefinition.value();

                        log.debug("Registering Vertx event consumer method for address: {}", address);
                        eventConsumerDefinitions.put(address, eventDefinition);
                        eventConsumerMethods.put(address, method);
                        eventConsumerMethodClasses.put(address, clazz);

                        // Extract the reference type from the method parameters
                        Parameter[] parameters = method.getParameters();
                        for (Parameter param : parameters) {
                            // Skip Message parameters
                            if (Message.class.isAssignableFrom(param.getType())) {
                                continue;
                            }

                            // Get the parameter type
                            Type paramType = param.getParameterizedType();
                            if (paramType != null) {
                                log.debug("Found reference type {} for method consumer at address: {}", paramType.getTypeName(), address);
                                eventConsumerReferenceTypes.put(address, paramType);
                                break; // Use the first non-Message parameter
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error registering Vertx event consumer method", e);
            }
        }

        // Scan for classes with fields annotated with @VertxEventDefinition or @Named
        var publisherClasses = IGuiceContext.instance().getScanResult()
                .getAllClasses()
                .stream()
                .filter(info -> info.hasDeclaredFieldAnnotation(VertxEventDefinition.class) ||
                        info.hasDeclaredFieldAnnotation(com.google.inject.name.Named.class))
                .toList();

        for (var classInfo : publisherClasses) {
            try {
                Class<?> clazz = classInfo.loadClass();
                for (Field field : clazz.getDeclaredFields()) {
                    // Check if field type is VertxEventPublisher
                    if (field.getType().equals(VertxEventPublisher.class)) {
                        String address = null;
                        VertxEventDefinition eventDefinition = field.getAnnotation(VertxEventDefinition.class);

                        // If @VertxEventDefinition is present, use its value as address
                        if (eventDefinition != null) {
                            address = eventDefinition.value();
                        }
                        // Otherwise, check for @Named annotation
                        else {
                            com.google.inject.name.Named named = field.getAnnotation(com.google.inject.name.Named.class);
                            if (named != null) {
                                address = named.value();
                                // Create a default event definition
                                eventDefinition = createDefaultEventDefinition(address);
                            }
                        }

                        if (address != null) {
                            if (!eventPublisherKeys.containsKey(address)) {
                                log.debug("Registering Vertx event publisher for address: {}", address);
                                eventPublisherDefinitions.put(address, eventDefinition);
                                // Extract the generic type parameter
                                Type genericType = field.getGenericType();
                                eventPublisherKeys.put(address, createGuiceKey(genericType, address));
                            }


                            /*
                            if (genericType instanceof ParameterizedType) {
                                ParameterizedType paramType = (ParameterizedType) genericType;
                                Type[] typeArguments = paramType.getActualTypeArguments();
                                if (typeArguments.length > 0) {
                                    Type typeArg = typeArguments[0];

                                    // Create TypeLiteral for complex generic types
                                    TypeLiteral<?> typeLiteral = createTypeLiteral(typeArg);
                                    Class<?> rawType = getRawType(typeArg);

                                    log.info("Found generic type {} (raw: {}) for publisher at address: {}",
                                            typeArg.getTypeName(), rawType.getName(), address);

                                    // Store both the TypeLiteral for Guice binding and raw type for convenience
                                    eventPublisherTypeLiterals.put(address, typeLiteral);
                                    eventPublisherGenericTypes.put(address, rawType);
                                    eventPublisherKeys.put(address, createGuiceKey(typeArg,address));
                                } else {
                                    log.warn("No type arguments found for publisher at address: {}", address);
                                    eventPublisherGenericTypes.put(address, Object.class);
                                    eventPublisherTypeLiterals.put(address, TypeLiteral.get(Object.class));
                                }
                            } else {
                                log.warn("No generic type information found for publisher at address: {}", address);
                                eventPublisherGenericTypes.put(address, Object.class);
                                eventPublisherTypeLiterals.put(address, TypeLiteral.get(Object.class));
                            }*/
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error registering Vertx event publisher", e);
            }
        }
    }

    /**
     * Registers event consumers with the Vertx event bus
     */
    public static void registerEventConsumers() {
        Vertx vertx = VertXPreStartup.getVertx();

        // Register class-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerClass.containsKey(address)) {
                log.debug("Registering class-based event consumer for address: {}", address);

                int instances = Math.max(1, eventDefinition.options().instances() > 0 ? eventDefinition.options().instances() : eventDefinition.options().consumerCount());
                boolean local = eventDefinition.options().localOnly();
                for (int i = 0; i < instances; i++) {
                    Method consumeMethod;
                    Class<?> consumerClass = eventConsumerClass.get(address);
                    try {
                        consumeMethod = consumerClass.getMethod("consume", Message.class);
                    } catch (Exception e) {
                        log.error("No consume(Message) method found for class {}", consumerClass.getName());
                        continue;
                    }

                    MessageConsumer<Object> mc = local
                            ? vertx.eventBus().localConsumer(address, message -> dispatch(vertx, message, consumeMethod, consumerClass, eventDefinition))
                            : vertx.eventBus().consumer(address, message -> dispatch(vertx, message, consumeMethod, consumerClass, eventDefinition));

                    // maxBufferedMessages advisory; MessageConsumer#setMaxBufferedMessages may not be available in all Vert.x versions
                    // if (eventDefinition.options().maxBufferedMessages() > 0) {
                    //     mc.setMaxBufferedMessages(eventDefinition.options().maxBufferedMessages());
                    // }
                }
            }
        });

        // Register method-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerMethods.containsKey(address)) {
                log.debug("Registering method-based event consumer for address: {}", address);
                Method method = eventConsumerMethods.get(address);
                Class<?> methodClass = eventConsumerMethodClasses.get(address);

                int instances = Math.max(1, eventDefinition.options().instances() > 0 ? eventDefinition.options().instances() : eventDefinition.options().consumerCount());
                boolean local = eventDefinition.options().localOnly();
                for (int i = 0; i < instances; i++) {
                    MessageConsumer<Object> mc = local
                            ? vertx.eventBus().localConsumer(address, message -> dispatch(vertx, message, method, methodClass, eventDefinition))
                            : vertx.eventBus().consumer(address, message -> dispatch(vertx, message, method, methodClass, eventDefinition));
                    // Note: setMaxBufferedMessages is not available on all Vert.x targets; advisory only
                }
            }
        });
    }

    /**
     * Dispatches a received message according to event options (e.g., worker pool)
     */
    private static void dispatch(Vertx vertx, Message<?> message, Method method, Class<?> methodClass, VertxEventDefinition eventDefinition) {
        try {
            if (eventDefinition != null && eventDefinition.options().worker()) {
                String pool = eventDefinition.options().workerPool();
                if (pool != null && !pool.isEmpty()) {
                    int size = eventDefinition.options().workerPoolSize() > 0 ? eventDefinition.options().workerPoolSize() : 20;
                    WorkerExecutor exec = workerExecutors.computeIfAbsent(pool, name -> vertx.createSharedWorkerExecutor(name, size));
                    exec.executeBlocking(() -> {
                        handleMethodBasedConsumer(message, method, methodClass);
                        return null;
                    }, false);
                } else {
                    vertx.executeBlocking(() -> {
                        handleMethodBasedConsumer(message, method, methodClass);
                        return null;
                    }, false);
                }
            } else {
                handleMethodBasedConsumer(message, method, methodClass);
            }
        } catch (Throwable t) {
            log.error("Error dispatching message on address {}: {}", message.address(), t.getMessage(), t);
            try { message.fail(500, t.getMessage()); } catch (Throwable ignored) {}
        }
    }

    /**
     * Handles a message by invoking a method-based consumer
     */
    private static void handleMethodBasedConsumer(Message<?> message, Method method, Class<?> methodClass) {
        CallScoper scoper = IGuiceContext.get(CallScoper.class);
        try {
            // Get an instance of the class from Guice
            Object instance = IGuiceContext.get(methodClass);
            // Get the current Vertx context
            var context = Vertx.currentContext();

            if (context != null) {
                // Execute the method in a blocking context
                context.runOnContext((_) -> {
                    try {
                        // Prepare method parameters
                        Object[] params = prepareMethodParameters(method, message);
                        if(!scoper.isStartedScope()) {
                            scoper.enter();
                        }
                        CallScopeProperties csp = IGuiceContext.get(CallScopeProperties.class);
                        csp.setSource(CallScopeSource.VertXConsumer);

                        // Invoke the method
                        Object result = method.invoke(instance, params);

                        // Handle the result based on its type
                        handleMethodResult(result, message);

                        //return null;
                    } catch (Exception e) {
                        log.error("Error invoking method-based consumer", e);
                        message.fail(500, e.getMessage());
                        throw new RuntimeException(e);
                    } finally {
                        if (scoper.isStartedScope()) {
                            try {
                                scoper.exit();
                            } catch (Exception e) {
                                log.error("Error exiting call scope: {}", e.getMessage(), e);
                            }
                        }
                    }
                });
            } else {
                log.error("No Vertx context found, cannot invoke method-based consumer - {}.{}()", methodClass.getSimpleName(), method.getName());
            }
        } catch (Exception e) {
            log.error("Error processing event message", e);
            message.fail(500, e.getMessage());
        }
    }

    /**
     * Prepares the parameters for a method invocation
     */
    private static Object[] prepareMethodParameters(Method method, Message<?> message) {
        Parameter[] parameters = method.getParameters();
        Object[] params = new Object[parameters.length];
        String address = message.address();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            if (Message.class.isAssignableFrom(paramType)) {
                // Pass the Message object
                params[i] = message;
            } else {
                Object body = message.body();

                // Check if the body is a JsonObject and the parameter type is not a default Vertx type
                if (body instanceof JsonObject &&
                        !isDefaultVertxType(paramType)) {
                    try {
                        // Convert JsonObject to the expected parameter type using Jackson
                        JsonObject jsonObject = (JsonObject) body;

                        // Check if we have a stored reference type for this address
                        Type referenceType = eventConsumerReferenceTypes.get(address);
                        if (referenceType != null) {
                            // Use the stored reference type for deserialization
                            log.debug("Using stored reference type {} for deserialization at address: {}",
                                    referenceType.getTypeName(), address);
                            params[i] = IJsonRepresentation.getObjectMapper()
                                    .readValue(jsonObject.encode(),
                                            IJsonRepresentation.getObjectMapper().getTypeFactory().constructType(referenceType));
                        } else {
                            // Fall back to using the parameter's class type
                            params[i] = IJsonRepresentation.getObjectMapper()
                                    .readValue(jsonObject.encode(), paramType);
                        }
                        log.debug("Converted JsonObject to {}: {}", paramType.getName(), params[i]);
                    } catch (Exception e) {
                        log.error("Error converting JsonObject to " + paramType.getName(), e);
                        params[i] = body;
                    }
                } else {
                    // Pass the message body directly
                    params[i] = body;
                }
            }
        }

        return params;
    }

    /**
     * Checks if a type is a default Vertx published type
     */
    private static boolean isDefaultVertxType(Class<?> type) {
        return type.equals(String.class) ||
                type.equals(Object.class) ||
                type.equals(JsonObject.class) ||
                type.equals(JsonArray.class) ||
                type.isPrimitive() ||
                Number.class.isAssignableFrom(type) ||
                Boolean.class.equals(type) ||
                Character.class.equals(type);
    }

    /**
     * Handles the result of a method invocation
     */
    private static void handleMethodResult(Object result, Message<?> message) {
        if (result == null) {
            // Void method, no reply needed
            return;
        }

        if (result instanceof Future) {
            // Handle Future result
            ((Future<?>) result).onComplete(ar -> {
                if (ar.succeeded()) {
                    message.reply(ar.result());
                } else {
                    message.fail(500, ar.cause().getMessage());
                }
            });
        } else if (result instanceof CompletableFuture) {
            // Handle CompletableFuture result
            ((CompletableFuture<?>) result).whenComplete((res, ex) -> {
                if (ex == null) {
                    message.reply(res);
                } else {
                    message.fail(500, ex.getMessage());
                }
            });
        } else if (result instanceof Uni) {
            // Handle Uni result
            ((Uni<?>) result).subscribe().with(
                res -> message.reply(res),
                ex -> message.fail(500, ex.getMessage())
            );
        } else {
            // Handle direct result
            message.reply(result);
        }
    }

    /**
     * Creates a default VertxEventDefinition for fields with only @Named annotation
     *
     * @param address The address from the @Named annotation
     * @return A default VertxEventDefinition
     */
    private static VertxEventDefinition createDefaultEventDefinition(String address) {
        return new VertxEventDefinition() {
            @Override
            public String value() {
                return address;
            }

            @Override
            public VertxEventOptions options() {
                return new VertxEventOptions() {
                    @Override
                    public boolean localOnly() {
                        return false;
                    }

                    @Override
                    public boolean autobind() {
                        return true;
                    }

                    @Override
                    public int consumerCount() {
                        return 1;
                    }

                    @Override
                    public boolean worker() {
                        return false;
                    }

                    @Override
                    public String workerPool() {
                        return "";
                    }

                    @Override
                    public int workerPoolSize() {
                        return 0;
                    }

                    @Override
                    public int instances() {
                        return 0;
                    }

                    @Override
                    public String orderedByHeader() {
                        return "";
                    }

                    @Override
                    public int maxBufferedMessages() {
                        return 0;
                    }

                    @Override
                    public int resumeAtMessages() {
                        return 0;
                    }

                    @Override
                    public int batchWindowMs() {
                        return 0;
                    }

                    @Override
                    public int batchMax() {
                        return 0;
                    }

                    @Override
                    public long timeoutMs() {
                        return 0L;
                    }

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return VertxEventOptions.class;
                    }
                };
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return VertxEventDefinition.class;
            }
        };
    }

    /**
     * Creates a TypeLiteral from a Type, handling complex generic types
     */
    @SuppressWarnings("unchecked")
    private static TypeLiteral<?> createTypeLiteral(Type type) {
        return (TypeLiteral<?>) TypeLiteral.get(type);
    }

    /**
     * Extracts the raw type from any Type, including parameterized types
     */
    private static Class<?> getRawType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            return (Class<?>) paramType.getRawType();
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            Class<?> componentClass = getRawType(arrayType.getGenericComponentType());
            return Array.newInstance(componentClass, 0).getClass();
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) {
                return getRawType(upperBounds[0]);
            }
            return Object.class;
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            Type[] bounds = typeVar.getBounds();
            if (bounds.length > 0) {
                return getRawType(bounds[0]);
            }
            return Object.class;
        }

        // Fallback for any other type
        log.warn("Unknown type encountered: {}, defaulting to Object", type.getClass().getName());
        return Object.class;
    }

    /**
     * Alternative approach: Create a proper Key for Guice binding with full type information
     */
    private static Key<?> createGuiceKey(Type type, String address) {
        try {
            if (type instanceof Class) {
                return Key.get((Class<?>) type, Names.named(address));
            } else {
                // For complex generic types, use TypeLiteral
                TypeLiteral<?> typeLiteral = TypeLiteral.get(type);
                return Key.get(typeLiteral, Names.named(address));
            }
        } catch (Exception e) {
            log.warn("Failed to create Guice key for type {} at address {}: {}",
                    type.getTypeName(), address, e.getMessage());
            return Key.get(Object.class, Names.named(address));
        }
    }

    /**
     * Extracts the generic type parameter from a class with a consume method that takes a Message parameter
     *
     * @param clazz The class to extract the generic type parameter from
     * @return The generic type parameter, or null if not found
     */
    private static Type extractConsumerGenericType(Class<?> clazz) {
        try {
            // Look for a consume method that takes a Message parameter
            try {
                Method consumeMethod = clazz.getMethod("consume", Message.class);
                if (consumeMethod != null) {
                    // Extract the parameter type from the consume method
                    Parameter[] parameters = consumeMethod.getParameters();
                    if (parameters.length > 0 && Message.class.isAssignableFrom(parameters[0].getType())) {
                        Type paramType = parameters[0].getParameterizedType();
                        if (paramType instanceof ParameterizedType) {
                            ParameterizedType parameterizedType = (ParameterizedType) paramType;
                            Type[] typeArgs = parameterizedType.getActualTypeArguments();
                            if (typeArgs.length > 0) {
                                return typeArgs[0];
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // No consume method found, that's okay
                log.debug("No consume method found for class {}", clazz.getName());
            }

            // If not found, return null
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract generic type from consumer class {}: {}",
                    clazz.getName(), e.getMessage());
            return null;
        }
    }
}
