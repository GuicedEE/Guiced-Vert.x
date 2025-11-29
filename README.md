# GuicedVertx

Adopted Rules Repository (see `rules/` submodule). For current docs:
- PACT.md, RULES.md, GUIDES.md, IMPLEMENTATION.md, IMPLEMENTATION_PLAN.md, GLOSSARY.md
- Architecture index: docs/architecture/README.md (C4, sequences, ERD)
- Design validation: docs/design-validation.md
- Prompt reference: docs/PROMPT_REFERENCE.md
- Topic rules index: rules/generative/backend/guicedee/vertx/README.md

Environment/CI:
- Copy `.env.example` and set deployment-specific values (no secrets committed).
- GitHub Actions workflow at `.github/workflows/build.yml` builds/tests with Java 25.

## How to use these rules
- Load docs/PROMPT_REFERENCE.md with RULES.md, PACT.md, GUIDES.md, IMPLEMENTATION.md, and docs/architecture/* before generating changes.
- Apply Guiced Vert.x guidance from `rules/generative/backend/guicedee/vertx/README.md` (lifecycle, event definitions, publishers, codecs, configurators, verticles).
- Follow CRTP fluent API rules (`rules/generative/backend/fluent-api/crtp.rules.md`) and Vert.x 5 guidance (`rules/generative/backend/vertx/README.md`); avoid Lombok @Builder.
- Use CI/CD and secrets rules from `rules/generative/platform/ci-cd/providers/github-actions.md` and `rules/generative/platform/secrets-config/env-variables.md`.

## Prompt Language Alignment & Glossary
- Topic-first glossary: `rules/generative/backend/guicedee/vertx/GLOSSARY.md` is authoritative for GuicedEE ↔ Vert.x bridge terminology.
- Host glossary index: GLOSSARY.md links to selected stacks; only copy enforced prompt-alignment names into host glossaries, otherwise link back to the topic glossary.
- Keep naming consistent with Vert.x 5 and CRTP terminology (no Builder semantics).

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
- Automatic JSON conversion for custom object types (see below)

#### JSON Conversion for Custom Object Types

When a method parameter is not a default Vertx published type (String, Object, JsonObject, etc.) and the event received is a JsonObject, GuicedVertx will automatically convert the JsonObject to an instance of the expected type:

```java
public class MyData {
    private String name;
    private int age;
    private boolean active;

    // Getters and setters...
}

public class MyConsumer {
    @VertxEventDefinition("my.data.event")
    public void handleData(MyData data) {
        System.out.println("Received data: " + data.getName());
    }

    @VertxEventDefinition("my.data.message.event")
    public void handleDataMessage(Message<MyData> message) {
        MyData data = message.body();
        System.out.println("Received data via message: " + data.getName());
    }
}
```

When publishing to these consumers, you can send a JsonObject:

```java
JsonObject jsonObject = new JsonObject()
    .put("name", "Test User")
    .put("age", 30)
    .put("active", true);

vertx.eventBus().publish("my.data.event", jsonObject);
```

The conversion is handled automatically using Jackson via the `IJsonRepresentation.getObjectMapper()` method.

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
- `JsonConversionTest`: Example of JSON conversion for custom object types


### Special Test Cases
- The guiced-injection cannot be added for tests due to cyclic dependency on the branch. Add this locally before executing tests
