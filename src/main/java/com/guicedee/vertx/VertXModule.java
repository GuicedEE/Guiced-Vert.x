package com.guicedee.vertx;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.spi.VertxEventRegistry;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

@Log4j2
public class VertXModule extends PrivateModule implements IGuiceModule<VertXModule>
{
    // Set to track which addresses have already been bound
    private static final Set<String> boundAddresses = new HashSet<>();

    @Override
    protected void configure()
    {
        // Bind the Vertx instance
        bind(Vertx.class).toInstance(VertXPreStartup.getVertx());
        expose(Vertx.class);

        // VertxEventRegistry is initialized in VertXPreStartup

        // Bind VertxConsumer implementations
        VertxEventRegistry.getEventConsumerDefinitions().forEach((address, eventDefinition) -> {
            // Check if this is an interface-based consumer and if it hasn't been bound yet
            if (VertxEventRegistry.getEventConsumerClass().containsKey(address) && !boundAddresses.contains("consumer:" + address)) {
                Class clazz = VertxEventRegistry.getEventConsumerClass().get(address);

                // Mark this address as bound
                boundAddresses.add("consumer:" + address);

                // Bind the consumer class as a singleton
                bind(clazz).in(Singleton.class);

                // Bind the consumer class with a named binding using the address
                bind(Key.get(clazz, Names.named(address)))
                        .to(clazz);

   /*             // Bind the VertxConsumer interface with the same named binding to the consumer class
                bind(Key.get(VertxConsumer.class, Names.named(address)))
                        .to(clazz);

                // Expose the bindings
                expose(Key.get(clazz, Names.named(address)));
                expose(Key.get(VertxConsumer.class, Names.named(address)));
*/
                 log.debug("Binding interface-based VertxConsumer for address: {}", address);
            } else if (VertxEventRegistry.getEventConsumerClass().containsKey(address)) {
                log.trace("Skipping already bound interface-based VertxConsumer for address: {}", address);
            }
        });

        // Bind classes containing method-based consumers
        VertxEventRegistry.getEventConsumerMethodClasses().forEach((address, clazz) -> {
            // Check if this method-based consumer hasn't been bound yet
            if (!boundAddresses.contains("method:" + address)) {
                log.info("Binding method-based consumer class for address: {}", address);

                // Mark this address as bound
                boundAddresses.add("method:" + address);

                // Bind the class as a singleton
                bind(clazz).in(Singleton.class);

                // Expose the binding
                expose(clazz);
            } else {
                log.trace("Skipping already bound method-based consumer class for address: {}", address);
            }
        });

        // Bind VertxEventPublisher for each event definition
        // We detect the generic type for each publisher and use it to create a TypeLiteral
        // This allows injection of VertxEventPublisher with the correct generic type
        // For example, if the publisher is defined as VertxEventPublisher<User>,
        // it can be injected as VertxEventPublisher<User> instead of VertxEventPublisher<Object>
        VertxEventRegistry.getEventPublisherDefinitions().forEach((address, eventDefinition) -> {
            // Check if this publisher hasn't been bound yet
            if (!boundAddresses.contains("publisher:" + address)) {
                log.info("Binding VertxEventPublisher for address: {}", address);

                // Mark this address as bound
                boundAddresses.add("publisher:" + address);

               // TypeLiteral<VertxEventPublisher<?>> type = (TypeLiteral<VertxEventPublisher<?>>) VertxEventRegistry.getEventPublisherTypeLiterals().get(address);
                var gKey = VertxEventRegistry.getEventPublisherKeys().get(address);

                // Create a provider for the publisher with the correct generic type
                @SuppressWarnings("unchecked")
                Provider<VertxEventPublisher> publisherProvider = () -> {
                    Vertx vertx = VertXPreStartup.getVertx();
                    // Extract the type from the key
                    Type referenceType = Object.class;
                    if (gKey.getTypeLiteral() != null && gKey.getTypeLiteral().getType() instanceof ParameterizedType) {
                        ParameterizedType paramType = (ParameterizedType) gKey.getTypeLiteral().getType();
                        Type[] typeArgs = paramType.getActualTypeArguments();
                        if (typeArgs.length > 0) {
                            referenceType = typeArgs[0];
                            log.debug("Using reference type {} for publisher at address: {}", referenceType.getTypeName(), address);
                        }
                    }
                    // The VertxEventPublisher is created with the correct generic type and reference type
                    return new VertxEventPublisher<>(vertx, address, eventDefinition, referenceType);
                };

                // Bind the generic key to the specific key
                //bind(genericKey).to(specificKey);
                bind(gKey).toProvider((Provider)publisherProvider).in(Singleton.class);

                // For backward compatibility, also bind the raw type with named binding
                // This allows injection of VertxEventPublisher without specifying the generic type
                bind(Key.get(VertxEventPublisher.class, Names.named(address))).to((Key<? extends VertxEventPublisher>) gKey);

                // Expose the bindings
                //expose(genericKey);
                //expose(specificKey);
                expose(gKey);
                expose(Key.get(VertxEventPublisher.class, Names.named(address)));
            } else {
                log.info("Skipping already bound VertxEventPublisher for address: {}", address);
            }
        });

        // Event consumers are registered in VertXPreStartup
    }

}
