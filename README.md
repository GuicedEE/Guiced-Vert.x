# GuicedVertx

GuicedVertx provides integration between Vert.x and Guice, allowing for dependency injection in Vert.x applications.

## Features

- Automatic initialization of Vert.x
- Guice integration for Vert.x components
- Event bus consumer and publisher support with dependency injection

## Usage

### Event Bus Consumers

#### Method-Based Consumers

You can create event bus consumers by annotating methods with `@VertxEventDefinition`. This approach doesn't require implementing any interface:

```java
public class MyEventConsumerClass {

    @VertxEventDefinition(value = "my.event.address", options = @VertxEventOptions(localOnly = true))
    public String handleEvent(String message) {
        System.out.println("Received message: " + message);
        return "Message processed: " + message;
    }

    @VertxEventDefinition("my.future.event")
    public Future<String> handleFutureEvent(Message<String> message) {
        System.out.println("Received message: " + message.body());
        return Future.succeededFuture("Future processed: " + message.body());
    }

    @VertxEventDefinition("my.completable.event")
    public CompletableFuture<String> handleCompletableFutureEvent(String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.complete("CompletableFuture processed: " + message);
        return future;
    }

    @VertxEventDefinition("my.void.event")
    public void handleVoidEvent(String message) {
        System.out.println("Received message in void method: " + message);
        // No reply sent for void methods
    }
}
```

Method-based consumers support:
- Different return types: `void`, direct values, `Future`, `CompletableFuture`
- Different parameter types: message body directly or `Message<T>` object
- Vertx context is automatically available during method execution

#### Interface-Based Consumers (Legacy)

Alternatively, you can implement the `VertxConsumer` interface and annotate your class with `@VertxEventDefinition`:

```java
@VertxEventDefinition(value = "my.event.address", options = @VertxEventOptions(localOnly = true))
public class MyEventConsumer implements VertxConsumer<String> {

    @Override
    public void consume(Message<String> message) {
        System.out.println("Received message: " + message.body());
        message.reply("Message received: " + message.body());
    }
}
```

The `@VertxEventDefinition` annotation specifies the event bus address to listen on, and the `@VertxEventOptions` annotation provides additional configuration options:

- `localOnly`: Whether to register the consumer with a local-only handler (default: false)
- `autobind`: Auto start/bind the consumer on startup (default: true)
- `consumerCount`: Number of consumer instances to create (default: 1)

### Event Bus Publishers

To publish messages to the event bus, inject a `VertxEventPublisher` with the `@Named` annotation:

```java
public class MyEventPublisher {

    @Inject
    @Named("my.event.address")
    private VertxEventPublisher<String> eventPublisher;

    public void publishMessage(String message) {
        eventPublisher.publish(message);
    }

    public Future<String> sendMessage(String message) {
        return eventPublisher.send(message);
    }
}
```

The `@VertxEventDefinition` annotation is optional for publishers. If provided, it can be used to specify additional options:

```java
public class MyEventPublisherWithOptions {

    @Inject
    @Named("my.event.address")
    @VertxEventDefinition(value = "my.event.address", options = @VertxEventOptions(localOnly = true))
    private VertxEventPublisher<String> eventPublisher;

    // Methods as above
}
```

The `VertxEventPublisher` provides methods for both publishing messages (one-to-many) and sending messages (point-to-point with reply):

- `publish(T message)`: Publish a message to all consumers
- `send(T message)`: Send a message to a single consumer and get a reply
- `publish(T message, DeliveryOptions options)`: Publish a message with delivery options
- `send(T message, DeliveryOptions options)`: Send a message with delivery options

## Examples

See the test classes for examples of how to use the event bus consumer and publisher:

### Interface-Based Consumers
- `TestVertxConsumer`: Example of a Vert.x event consumer using the interface approach
- `TestVertxPublisher`: Example of a class that publishes Vert.x events

### Method-Based Consumers
- `MethodBasedConsumerTest`: Example of method-based consumers with different return types and parameter types
- `MethodBasedPublisherTest`: Example of publishing messages to method-based consumers
