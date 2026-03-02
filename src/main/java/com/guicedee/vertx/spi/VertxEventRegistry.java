package com.guicedee.vertx.spi;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.guicedee.client.IGuiceContext;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import com.guicedee.vertx.VertxEventPublisher;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Map;

/**
 * Registry for Vertx event consumers and publishers
 */
@Log4j2
public class VertxEventRegistry {

    private static final java.util.concurrent.ConcurrentHashMap<String, WorkerExecutor> workerExecutors = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Set of addresses that have already been registered to prevent duplicate consumer registration.
     */
    @Getter
    private static final java.util.Set<String> registeredAddresses = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Getter
    private static final Map<String, VertxEventDefinition> eventConsumerDefinitions = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Class> eventConsumerClass = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static final Map<String, VertxEventDefinition> eventPublisherDefinitions = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Method> eventConsumerMethods = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Class<?>> eventConsumerMethodClasses = new java.util.concurrent.ConcurrentHashMap<>();

    @Getter
    private static Map<String, Key<?>> eventPublisherKeys = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Map to store the reference types for consumers
     * Key: address, Value: reference type for the consumer's generic parameter
     */
    @Getter
    private static Map<String, Type> eventConsumerReferenceTypes = new java.util.concurrent.ConcurrentHashMap<>();

    private static VertxEventDefinition wrapEventDefinition(VertxEventDefinition definition) {
        if (definition == null) return null;
        // Capture the original address value before creating the wrapper
        final String originalAddress = definition.value();
        return new VertxEventDefinition() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return VertxEventDefinition.class;
            }

            @Override
            public String value() {
                // Allow per-address override via VERTX_EVENT_ADDRESS_<NORMALIZED_ADDRESS> environment variable
                // e.g., for address "my.event.address", check VERTX_EVENT_ADDRESS_MY_EVENT_ADDRESS
                String normalizedAddress = originalAddress.toUpperCase().replace('.', '_').replace('-', '_');
                String envKey = "VERTX_EVENT_ADDRESS_" + normalizedAddress;
                String addressOverride = System.getProperty(envKey, System.getenv(envKey));
                return (addressOverride != null && !addressOverride.isEmpty()) ? addressOverride : originalAddress;
            }

            @Override
            public VertxEventOptions options() {
                return wrapEventOptions(originalAddress, definition.options());
            }
        };
    }

    /**
     * Resolves an option value with per-address override support.
     * <p>
     * Lookup order:
     * <ol>
     *   <li>{@code VERTX_EVENT_<OPTION>_<NORMALIZED_ADDRESS>} — per-address override</li>
     *   <li>{@code VERTX_EVENT_<OPTION>} — global override for all events</li>
     *   <li>The annotation default value</li>
     * </ol>
     */
    private static String resolveOption(String address, String optionKey, String defaultValue) {
        // 1. Per-address: VERTX_EVENT_WORKER_MY_EVENT_ADDRESS
        String normalizedAddress = address.toUpperCase().replace('.', '_').replace('-', '_');
        String perAddressKey = optionKey + "_" + normalizedAddress;
        // Check system property and env var directly to avoid Environment's empty-string fallback
        String perAddress = System.getProperty(perAddressKey);
        if (perAddress == null) {
            perAddress = System.getenv(perAddressKey);
        }
        if (perAddress != null) {
            return perAddress;
        }
        // 2. Global: VERTX_EVENT_WORKER
        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment(optionKey, defaultValue);
    }

    private static VertxEventOptions wrapEventOptions(String address, VertxEventOptions options) {
        if (options == null) return null;
        return new VertxEventOptions() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return VertxEventOptions.class;
            }

            @Override
            public boolean localOnly() {
                return Boolean.parseBoolean(resolveOption(address, "VERTX_EVENT_LOCAL_ONLY", String.valueOf(options.localOnly())));
            }

            @Override
            public boolean autobind() {
                return Boolean.parseBoolean(resolveOption(address, "VERTX_EVENT_AUTOBIND", String.valueOf(options.autobind())));
            }

            @Override
            public int consumerCount() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_CONSUMER_COUNT", String.valueOf(options.consumerCount())));
            }

            @Override
            public boolean worker() {
                return Boolean.parseBoolean(resolveOption(address, "VERTX_EVENT_WORKER", String.valueOf(options.worker())));
            }

            @Override
            public String workerPool() {
                return resolveOption(address, "VERTX_EVENT_WORKER_POOL", options.workerPool());
            }

            @Override
            public int workerPoolSize() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_WORKER_POOL_SIZE", String.valueOf(options.workerPoolSize())));
            }

            @Override
            public int instances() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_INSTANCES", String.valueOf(options.instances())));
            }

            @Override
            public String orderedByHeader() {
                return resolveOption(address, "VERTX_EVENT_ORDERED_BY_HEADER", options.orderedByHeader());
            }

            @Override
            public int maxBufferedMessages() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_MAX_BUFFERED_MESSAGES", String.valueOf(options.maxBufferedMessages())));
            }

            @Override
            public int resumeAtMessages() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_RESUME_AT_MESSAGES", String.valueOf(options.resumeAtMessages())));
            }

            @Override
            public int batchWindowMs() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_BATCH_WINDOW_MS", String.valueOf(options.batchWindowMs())));
            }

            @Override
            public int batchMax() {
                return Integer.parseInt(resolveOption(address, "VERTX_EVENT_BATCH_MAX", String.valueOf(options.batchMax())));
            }

            @Override
            public long timeoutMs() {
                return Long.parseLong(resolveOption(address, "VERTX_EVENT_TIMEOUT_MS", String.valueOf(options.timeoutMs())));
            }
        };
    }

    /**
     * Guard to ensure scanning only happens once.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean scanned = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Scans for classes with @VertxEventDefinition annotation and registers them.
     * This method is idempotent — subsequent calls after the first are no-ops.
     */
    public static void scanAndRegisterEvents() {
        if (!scanned.compareAndSet(false, true)) {
            log.debug("scanAndRegisterEvents() already completed, skipping re-scan");
            return;
        }
        log.info("Scanning for Vertx event consumers and publishers");

        // Scan for classes with @VertxEventDefinition annotation
        var consumerClasses = IGuiceContext.instance().getScanResult()
                .getClassesWithAnnotation(VertxEventDefinition.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterfaceOrAnnotation() && !classInfo.isAbstract())
                .toList();

        log.info("Found {} consumer classes with @VertxEventDefinition", consumerClasses.size());

        for (var consumerClassInfo : consumerClasses) {
            try {
                Class consumerClass = consumerClassInfo.loadClass();
                VertxEventDefinition eventDefinition = wrapEventDefinition((VertxEventDefinition) consumerClass.getAnnotation(VertxEventDefinition.class));
                String address = eventDefinition.value();

                log.info("Registering Vertx event consumer class {} for address: {}", consumerClass.getSimpleName(), address);
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
                    VertxEventDefinition eventDefinition = wrapEventDefinition(method.getAnnotation(VertxEventDefinition.class));
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
                        VertxEventDefinition eventDefinition = wrapEventDefinition(field.getAnnotation(VertxEventDefinition.class));

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
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error registering Vertx event publisher", e);
            }
        }
    }


    /**
     * Registers event consumers filtered by assigned package or excluded prefixes, for per-verticle startup.
     *
     * @param assignedPackage  The package assigned to the current verticle. If non-empty, only consumers whose
     *                         classes are under this package are registered. If empty, only consumers NOT under any
     *                         of the excluded prefixes are registered (default verticle).
     * @param excludedPrefixes Package prefixes that are handled by annotated verticles and should be excluded from
     *                         the default verticle registration. May be null or empty.
     */
    public static void registerEventConsumersFiltered(String assignedPackage, java.util.List<String> excludedPrefixes) {
        Vertx vertx = VertXPreStartup.getVertx();

        log.debug("registerEventConsumersFiltered called for package='{}', excludedPrefixes={}", assignedPackage, excludedPrefixes);

        java.util.function.Predicate<String> includeByPackage;
        if (assignedPackage != null && !assignedPackage.isEmpty()) {
            includeByPackage = pkg -> pkg != null && pkg.startsWith(assignedPackage);
        } else {
            java.util.List<String> excludes = excludedPrefixes == null ? java.util.List.of() : excludedPrefixes;
            includeByPackage = pkg -> pkg != null && excludes.stream().noneMatch(p -> !p.isEmpty() && pkg.startsWith(p));
        }

        // Class-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerClass.containsKey(address)) {
                Class<?> consumerClass = eventConsumerClass.get(address);
                if (!includeByPackage.test(consumerClass.getPackageName())) {
                    return;
                }

                // Skip if already registered
                if (registeredAddresses.contains(address)) {
                    log.debug("[{}] Consumer for address {} already registered, skipping", assignedPackage, address);
                    return;
                }

                log.debug("[{}] Registering class-based consumer for address: {}", assignedPackage, address);

                int instances = Math.max(1, eventDefinition.options().instances() > 0 ? eventDefinition.options().instances() : eventDefinition.options().consumerCount());
                boolean local = eventDefinition.options().localOnly();
                for (int i = 0; i < instances; i++) {
                    Method consumeMethod;
                    try {
                        consumeMethod = consumerClass.getMethod("consume", Message.class);
                    } catch (Exception e) {
                        log.error("No consume(Message) method found for class {}", consumerClass.getName());
                        continue;
                    }

                    if (local) {
                        vertx.eventBus().localConsumer(address, message -> {
                            dispatch(vertx, message, consumeMethod, consumerClass, eventDefinition)
                                    .subscribe().with(
                                            ignored -> { /* no-op */ },
                                            ex -> {
                                                Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause() != null)
                                                        ? ex.getCause() : ex;
                                                log.error("Error dispatching message for {}: {}", message.address(), cause.getMessage(), cause);
                                                try {
                                                    message.fail(500, String.valueOf(cause.getMessage()));
                                                } catch (Throwable ignored2) {
                                                }
                                            }
                                    );
                        });
                    } else {
                        vertx.eventBus().consumer(address, message -> {
                            dispatch(vertx, message, consumeMethod, consumerClass, eventDefinition)
                                    .subscribe().with(
                                            ignored -> { /* no-op */ },
                                            ex -> {
                                                Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause() != null)
                                                        ? ex.getCause() : ex;
                                                log.error("Error dispatching message for {}: {}", message.address(), cause.getMessage(), cause);
                                                try {
                                                    message.fail(500, String.valueOf(cause.getMessage()));
                                                } catch (Throwable ignored2) {
                                                }
                                            }
                                    );
                        });
                    }
                }
                // Mark this address as registered
                registeredAddresses.add(address);
            }
        });

        // Method-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerMethods.containsKey(address)) {
                Method method = eventConsumerMethods.get(address);
                Class<?> methodClass = eventConsumerMethodClasses.get(address);
                if (!includeByPackage.test(methodClass.getPackageName())) {
                    return;
                }

                // Skip if already registered
                if (registeredAddresses.contains(address)) {
                    log.debug("[{}] Method consumer for address {} already registered, skipping", assignedPackage, address);
                    return;
                }

                log.debug("[{}] Registering method-based consumer for address: {}", assignedPackage, address);

                int instances = Math.max(1, eventDefinition.options().instances() > 0 ? eventDefinition.options().instances() : eventDefinition.options().consumerCount());
                boolean local = eventDefinition.options().localOnly();
                for (int i = 0; i < instances; i++) {
                    if (local) {
                        vertx.eventBus().localConsumer(address, message -> {
                            dispatch(vertx, message, method, methodClass, eventDefinition)
                                    .subscribe().with(
                                            ignored -> { /* no-op */ },
                                            ex -> {
                                                Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause() != null)
                                                        ? ex.getCause() : ex;
                                                log.error("Error dispatching message for {}: {}", message.address(), cause.getMessage(), cause);
                                                try {
                                                    message.fail(500, String.valueOf(cause.getMessage()));
                                                } catch (Throwable ignored2) {
                                                }
                                            }
                                    );
                        });
                    } else {
                        vertx.eventBus().consumer(address, message -> {
                            dispatch(vertx, message, method, methodClass, eventDefinition)
                                    .subscribe().with(
                                            ignored -> { /* no-op */ },
                                            ex -> {
                                                Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause() != null)
                                                        ? ex.getCause() : ex;
                                                log.error("Error dispatching message for {}: {}", message.address(), cause.getMessage(), cause);
                                                try {
                                                    message.fail(500, String.valueOf(cause.getMessage()));
                                                } catch (Throwable ignored2) {
                                                }
                                            }
                                    );
                        });
                    }
                }
                // Mark this address as registered
                registeredAddresses.add(address);
            }
        });
    }

    /**
     * Dispatches a received message according to event options (e.g., worker pool).
     * <p>
     * When the {@code @VertxEventOptions} does not specify a worker pool, the dispatcher
     * falls back to the enclosing {@code @Verticle}'s worker pool for the consumer class.
     */
    public static Uni<Void> dispatch(Vertx vertx, Message<?> message, Method method, Class<?> methodClass, VertxEventDefinition eventDefinition) {
        try {
            boolean isWorker = eventDefinition != null && eventDefinition.options().worker();

            // Resolve the effective worker pool: event-level → verticle-level → none
            String resolvedPool = null;
            int resolvedPoolSize = 20;
            if (eventDefinition != null) {
                resolvedPool = eventDefinition.options().workerPool();
                if (eventDefinition.options().workerPoolSize() > 0) {
                    resolvedPoolSize = eventDefinition.options().workerPoolSize();
                }
            }
            if (resolvedPool == null || resolvedPool.isEmpty()) {
                var verticleAnnotation = VerticleBuilder.getVerticleAnnotation(methodClass);
                if (verticleAnnotation.isPresent()) {
                    var va = verticleAnnotation.get();
                    if (va.value() != null && !va.value().isEmpty()) {
                        resolvedPool = va.value();
                        if (va.workerPoolSize() > 0) {
                            resolvedPoolSize = va.workerPoolSize();
                        }
                    }
                }
            }

            log.debug("Dispatching message on address {}, worker={}, pool={}", message.address(), isWorker, resolvedPool);

            if (isWorker) {
                if (resolvedPool != null && !resolvedPool.isEmpty()) {
                    final int size = resolvedPoolSize;
                    final String poolName = resolvedPool;
                    WorkerExecutor exec = workerExecutors.computeIfAbsent(poolName, name -> vertx.createSharedWorkerExecutor(name, size));
                    Future<Void> fut = exec.<Void>executeBlocking(() -> {
                        log.debug("Executing on named worker pool: {}", poolName);
                        invokeConsumerMethod(message, method, methodClass);
                        return null;
                    }, false);
                    return Uni.createFrom().completionStage(fut.toCompletionStage())
                            .onFailure().invoke(ex -> log.error("Worker dispatch setup failed for {}: {}", message.address(), ex.getMessage(), ex));
                } else {
                    var currentContext = Vertx.currentContext();
                    Future<Void> fut = currentContext.<Void>executeBlocking(() -> {
                        log.debug("Executing on default worker pool");
                        invokeConsumerMethod(message, method, methodClass);
                        return null;
                    }, false);
                    return Uni.createFrom().completionStage(fut.toCompletionStage())
                            .onFailure().invoke(ex -> log.error("Worker dispatch setup failed for {}: {}", message.address(), ex.getMessage(), ex));
                }
            } else {
                // Defer so the CallScope is established at subscription time
                handleMethodBasedConsumer(message, method, methodClass);
                return Uni.createFrom().voidItem();
            }
        } catch (Throwable t) {
            log.error("Error dispatching message on address {}: {}", message.address(), t.getMessage(), t);
            try {
                message.fail(500, t.getMessage());
            } catch (Throwable ignored) {
            }
            return Uni.createFrom().failure(t);
        }
    }

    /**
     * Eagerly invokes a consumer method on the current thread and handles the reply.
     * <p>
     * Used by worker dispatch to ensure the method executes on the worker thread
     * rather than being deferred back to the event-loop via Uni subscription.
     */
    private static void invokeConsumerMethod(Message<?> message, Method method, Class<?> methodClass) {
        try {
            Object instance = IGuiceContext.get(methodClass);
            Object[] params = prepareMethodParameters(method, message);
            Object invocationResult = method.invoke(instance, params);

            if (invocationResult instanceof Uni<?> uniResult) {
                uniResult.subscribe().asCompletionStage().toCompletableFuture().join();
            } else if (invocationResult instanceof Future<?> futResult) {
                Object res = futResult.toCompletionStage().toCompletableFuture().join();
                try {
                    message.reply(res);
                } catch (Throwable t) {
                    log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                }
            } else if (invocationResult instanceof java.util.concurrent.CompletableFuture<?> cfResult) {
                Object res = cfResult.join();
                try {
                    message.reply(res);
                } catch (Throwable t) {
                    log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                }
            } else if (invocationResult != null) {
                try {
                    message.reply(invocationResult);
                } catch (Throwable t) {
                    log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                }
            }
            // null result = void method, no reply needed
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null)
                    ? t.getCause() : t;
            log.error("Error invoking worker consumer {}.{}(): {}", methodClass.getSimpleName(), method.getName(), cause.getMessage(), cause);
            try {
                message.fail(500, String.valueOf(cause.getMessage()));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Handles a message by invoking a method-based consumer
     */
    private static void handleMethodBasedConsumer(Message<?> message, Method method, Class<?> methodClass) {
        // Execute the consumer invocation within a Uni so interceptors/scopes can participate.
        VertXPreStartup.getVertx().runOnContext(_ -> {
            // Obtain target instance from Guice
            Object instance = IGuiceContext.get(methodClass);

            // Prepare parameters
            Object[] params = prepareMethodParameters(method, message);
            // Invoke on the current thread (event-loop or worker depending on dispatch)
            Object invocationResult;
            try {
                invocationResult = method.invoke(instance, params);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
                log.error("Error invoking consumer {}.{}(): {}", methodClass.getSimpleName(), method.getName(), cause.getMessage(), cause);
                try {
                    message.fail(500, String.valueOf(cause.getMessage()));
                } catch (Throwable ignored) {
                }
                return;
            }

            // If the invoked method already returned a Uni, integrate it into the chain.
            if (invocationResult instanceof Uni<?>) {
                @SuppressWarnings("unchecked")
                Uni<Object> uniResult = (Uni<Object>) invocationResult;
                uniResult
                        .onItem().invoke(res -> {
                            // Reply with the resulting item (may be null)
                            try {
                                message.reply(res);
                            } catch (Throwable t) {
                                log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                            }
                        })
                        .onFailure().invoke(ex -> {
                            log.error("Uni failure for consumer on {}: {}", message.address(), ex.getMessage(), ex);
                            try {
                                message.fail(500, String.valueOf(ex.getMessage()));
                            } catch (Throwable ignored) {
                            }
                        })
                        .subscribe().with(
                                ignored -> { /* terminal — reply already sent above */ },
                                ex -> log.error("Unexpected error in Uni subscription for {}: {}", message.address(), ex.getMessage(), ex)
                        );
            }
            // Handle Vert.x Future result by converting to Uni
            else if (invocationResult instanceof Future<?> fut) {
                Uni.createFrom().completionStage(fut.toCompletionStage())
                        .onItem().invoke(res -> {
                            try {
                                message.reply(res);
                            } catch (Throwable t) {
                                log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                            }
                        })
                        .onFailure().invoke(ex -> {
                            log.error("Future failure for consumer on {}: {}", message.address(), ex.getMessage(), ex);
                            try {
                                message.fail(500, String.valueOf(ex.getMessage()));
                            } catch (Throwable ignored) {
                            }
                        })
                        .subscribe().with(
                                ignored -> { /* terminal — reply already sent above */ },
                                ex -> log.error("Unexpected error in Future subscription for {}: {}", message.address(), ex.getMessage(), ex)
                        );
            }
            // Handle CompletableFuture similarly
            else if (invocationResult instanceof java.util.concurrent.CompletableFuture<?> cf) {
                Uni.createFrom().completionStage(cf)
                        .onItem().invoke(res -> {
                            try {
                                message.reply(res);
                            } catch (Throwable t) {
                                log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                            }
                        })
                        .onFailure().invoke(ex -> {
                            log.error("CompletableFuture failure for consumer on {}: {}", message.address(), ex.getMessage(), ex);
                            try {
                                message.fail(500, String.valueOf(ex.getMessage()));
                            } catch (Throwable ignored) {
                            }
                        })
                        .subscribe().with(
                                ignored -> { /* terminal — reply already sent above */ },
                                ex -> log.error("Unexpected error in CompletableFuture subscription for {}: {}", message.address(), ex.getMessage(), ex)
                        );
            }
            // Synchronous result
            else if (invocationResult != null) {
                try {
                    message.reply(invocationResult);
                } catch (Throwable t) {
                    log.error("Failed to reply to message on {}: {}", message.address(), t.getMessage(), t);
                }
            }
            // null result = void method, no reply needed
        });
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
                // Check if the Message body is a JsonObject that needs conversion
                // to the generic type argument (e.g. Message<TestData>)
                Message<?> resolvedMessage = message;
                Object body = message.body();
                if (body instanceof JsonObject) {
                    Type genericType = param.getParameterizedType();
                    if (genericType instanceof ParameterizedType pt) {
                        Type[] typeArgs = pt.getActualTypeArguments();
                        if (typeArgs.length == 1 && typeArgs[0] instanceof Class<?> bodyType
                                && !isDefaultVertxType(bodyType)) {
                            try {
                                JsonObject jsonObject = (JsonObject) body;
                                Object converted = IJsonRepresentation.getObjectMapper()
                                        .readValue(jsonObject.encode(), bodyType);
                                log.debug("Converted Message body JsonObject to {}", bodyType.getName());
                                // Wrap the original message with the converted body
                                resolvedMessage = new MessageWrapper<>(message, converted);
                            } catch (Exception e) {
                                log.error("Error converting Message body JsonObject to " + bodyType.getName(), e);
                            }
                        }
                    }
                }
                params[i] = resolvedMessage;
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
     * Creates a default VertxEventDefinition for fields with only @Named annotation
     *
     * @param address The address from the @Named annotation
     * @return A default VertxEventDefinition
     */
    private static VertxEventDefinition createDefaultEventDefinition(String address) {
        return wrapEventDefinition(new VertxEventDefinition() {
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
        });
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
     * A delegating {@link Message} wrapper that overrides {@link #body()} to return
     * a pre-converted value instead of the original {@link JsonObject}.
     * All other methods delegate to the original message.
     *
     * @param <T> the converted body type
     */
    private static class MessageWrapper<T> implements Message<T> {
        private final Message<?> delegate;
        private final T convertedBody;

        MessageWrapper(Message<?> delegate, T convertedBody) {
            this.delegate = delegate;
            this.convertedBody = convertedBody;
        }

        @Override
        public T body() {
            return convertedBody;
        }

        @Override
        public String address() {
            return delegate.address();
        }

        @Override
        public io.vertx.core.MultiMap headers() {
            return delegate.headers();
        }

        @Override
        public String replyAddress() {
            return delegate.replyAddress();
        }

        @Override
        public boolean isSend() {
            return delegate.isSend();
        }

        @Override
        public void reply(Object msg) {
            delegate.reply(msg);
        }

        @Override
        public void reply(Object msg, io.vertx.core.eventbus.DeliveryOptions options) {
            delegate.reply(msg, options);
        }

        @Override
        public <R> Future<Message<R>> replyAndRequest(Object msg) {
            return delegate.replyAndRequest(msg);
        }

        @Override
        public <R> Future<Message<R>> replyAndRequest(Object msg, io.vertx.core.eventbus.DeliveryOptions options) {
            return delegate.replyAndRequest(msg, options);
        }

        @Override
        public void fail(int failureCode, String msg) {
            delegate.fail(failureCode, msg);
        }
    }
}
