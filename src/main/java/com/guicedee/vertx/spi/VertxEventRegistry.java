package com.guicedee.vertx.spi;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxConsumer;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import com.guicedee.vertx.VertxEventPublisher;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Registry for Vertx event consumers and publishers
 */
@Log4j2
public class VertxEventRegistry {

    @Getter
    private static final Map<String, VertxEventDefinition> eventConsumerDefinitions = new HashMap<>();

    @Getter
    private static final Map<String, Class> eventConsumerClass = new HashMap<>();

    @Getter
    private static final Map<String, VertxEventDefinition> eventPublisherDefinitions = new HashMap<>();

    // New data structures for method-based consumers
    @Getter
    private static final Map<String, Method> eventConsumerMethods = new HashMap<>();

    @Getter
    private static final Map<String, Class<?>> eventConsumerMethodClasses = new HashMap<>();

    /**
     * Scans for classes with @VertxEventDefinition annotation and registers them
     */
    public static void scanAndRegisterEvents() {
        log.info("Scanning for Vertx event consumers and publishers");

        // Scan for classes implementing VertxConsumer with @VertxEventDefinition annotation
        var consumerClasses = IGuiceContext.instance().getScanResult()
                .getClassesImplementing(VertxConsumer.class.getName())
                .stream()
                .filter(info -> info.hasAnnotation(VertxEventDefinition.class))
                .toList();

        for (var consumerClassInfo : consumerClasses) {
            try {
                Class consumerClass = consumerClassInfo.loadClass();
                VertxEventDefinition eventDefinition = (VertxEventDefinition) consumerClass.getAnnotation(VertxEventDefinition.class);
                String address = eventDefinition.value();

                log.info("Registering Vertx event consumer for address: {}", address);
                eventConsumerDefinitions.put(address, eventDefinition);
                eventConsumerClass.put(address, consumerClass);
            } catch (Exception e) {
                log.error("Error registering Vertx event consumer", e);
            }
        }

        // Scan for methods annotated with @VertxEventDefinition
        var methodConsumerClasses = IGuiceContext.instance().getScanResult()
                .getClassesWithMethodAnnotation(VertxEventDefinition.class)
                .stream()
                .toList();

        for (var classInfo : methodConsumerClasses) {
            try {
                Class<?> clazz = classInfo.loadClass();
                for (Method method : clazz.getDeclaredMethods()) {
                    VertxEventDefinition eventDefinition = method.getAnnotation(VertxEventDefinition.class);
                    if (eventDefinition != null) {
                        String address = eventDefinition.value();

                        log.info("Registering Vertx event consumer method for address: {}", address);
                        eventConsumerDefinitions.put(address, eventDefinition);
                        eventConsumerMethods.put(address, method);
                        eventConsumerMethodClasses.put(address, clazz);
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
                            log.info("Registering Vertx event publisher for address: {}", address);
                            eventPublisherDefinitions.put(address, eventDefinition);
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

        // Register interface-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerClass.containsKey(address)) {
                log.info("Registering interface-based event consumer for address: {}", address);

                for (int i = 0; i < eventDefinition.options().consumerCount(); i++) {
                    if (eventDefinition.options().localOnly()) {
                        vertx.eventBus().localConsumer(address, message -> {
                            try {
                                Class consumerClass = eventConsumerClass.get(address);
                                VertxConsumer consumer = (VertxConsumer) IGuiceContext.get(consumerClass);
                                consumer.consume(message);
                            } catch (Exception e) {
                                log.error("Error processing event message", e);
                                message.fail(500, e.getMessage());
                            }
                        });
                    } else {
                        vertx.eventBus().consumer(address, message -> {
                            try {
                                Class consumerClass = eventConsumerClass.get(address);
                                VertxConsumer consumer = (VertxConsumer) IGuiceContext.get(consumerClass);
                                consumer.consume(message);
                            } catch (Exception e) {
                                log.error("Error processing event message", e);
                                message.fail(500, e.getMessage());
                            }
                        });
                    }
                }
            }
        });

        // Register method-based consumers
        eventConsumerDefinitions.forEach((address, eventDefinition) -> {
            if (eventDefinition.options().autobind() && eventConsumerMethods.containsKey(address)) {
                log.info("Registering method-based event consumer for address: {}", address);
                Method method = eventConsumerMethods.get(address);
                Class<?> methodClass = eventConsumerMethodClasses.get(address);

                for (int i = 0; i < eventDefinition.options().consumerCount(); i++) {
                    if (eventDefinition.options().localOnly()) {
                        vertx.eventBus().localConsumer(address, message -> {
                            handleMethodBasedConsumer(message, method, methodClass);
                        });
                    } else {
                        vertx.eventBus().consumer(address, message -> {
                            handleMethodBasedConsumer(message, method, methodClass);
                        });
                    }
                }
            }
        });
    }

    /**
     * Handles a message by invoking a method-based consumer
     */
    private static void handleMethodBasedConsumer(Message<?> message, Method method, Class<?> methodClass) {
        try {
            // Get an instance of the class from Guice
            Object instance = IGuiceContext.get(methodClass);

            // Get the current Vertx context
            var context = Vertx.currentContext();

            if (context != null) {
                // Execute the method in a blocking context
                context.executeBlocking(() -> {
                    try {
                        // Prepare method parameters
                        Object[] params = prepareMethodParameters(method, message);

                        // Invoke the method
                        Object result = method.invoke(instance, params);

                        // Handle the result based on its type
                        handleMethodResult(result, message);

                        return null;
                    } catch (Exception e) {
                        log.error("Error invoking method-based consumer", e);
                        message.fail(500, e.getMessage());
                        throw new RuntimeException(e);
                    }
                }, false);
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

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            if (Message.class.isAssignableFrom(paramType)) {
                // Pass the Message object
                params[i] = message;
            } else {
                // Pass the message body
                params[i] = message.body();
            }
        }

        return params;
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
}
