package com.guicedee.vertx;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.spi.VertxEventRegistry;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class VertXModule extends PrivateModule implements IGuiceModule<VertXModule>
{
    @Override
    protected void configure()
    {
        // Bind the Vertx instance
        bind(Vertx.class).toInstance(VertXPreStartup.getVertx());
        expose(Vertx.class);

        // VertxEventRegistry is initialized in VertXPreStartup

        // Bind VertxConsumer implementations
        VertxEventRegistry.getEventConsumerDefinitions().forEach((address, eventDefinition) -> {
            // Check if this is an interface-based consumer
            if (VertxEventRegistry.getEventConsumerClass().containsKey(address)) {
                Class clazz = VertxEventRegistry.getEventConsumerClass().get(address);

                log.info("Binding interface-based VertxConsumer for address: {}", address);

                // Bind the consumer class as a singleton
                bind(clazz).in(Singleton.class);

                // Bind the consumer class with a named binding using the address
                bind(Key.get(clazz, Names.named(address)))
                        .to(clazz);

                // Bind the VertxConsumer interface with the same named binding to the consumer class
                bind(Key.get(VertxConsumer.class, Names.named(address)))
                        .to(clazz);

                // Expose the bindings
                expose(Key.get(clazz, Names.named(address)));
                expose(Key.get(VertxConsumer.class, Names.named(address)));
            }
        });

        // Bind classes containing method-based consumers
        VertxEventRegistry.getEventConsumerMethodClasses().forEach((address, clazz) -> {
            log.info("Binding method-based consumer class for address: {}", address);

            // Bind the class as a singleton
            bind(clazz).in(Singleton.class);

            // Expose the binding
            expose(clazz);
        });

        // Bind VertxEventPublisher for each event definition
        VertxEventRegistry.getEventPublisherDefinitions().forEach((address, eventDefinition) -> {
            log.info("Binding VertxEventPublisher for address: {}", address);

            bind(Key.get(VertxEventPublisher.class, Names.named(address)))
                    .toProvider(() -> {
                        Vertx vertx = VertXPreStartup.getVertx();
                        return new VertxEventPublisher<>(vertx, address, eventDefinition);
                    }).in(Singleton.class);

            // Expose the binding
            expose(Key.get(VertxEventPublisher.class, Names.named(address)));
        });

        // Event consumers are registered in VertXPreStartup
    }
}
