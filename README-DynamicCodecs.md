# Dynamic Codec Registration for Vertx Event Bus

This implementation provides automatic registration of dynamic codecs for the Vertx event bus. It ensures that codecs are created at most once for each type, improving efficiency and preventing duplicate registration errors.

## Components

### DynamicCodec

A generic codec implementation that can be instantiated for any type. It uses Jackson for serialization and deserialization of objects.

```java
public class DynamicCodec<T> implements MessageCodec<T, T> {
    // Implementation details
}
```

### CodecRegistry

A registry that manages the creation and registration of codecs. It ensures that codecs are created only once for each type.

```java
public class CodecRegistry {
    // Implementation details
}
```

## How It Works

1. During Vertx initialization in `VertXPreStartup`, after scanning for events but before registering consumers, the `CodecRegistry.createAndRegisterCodecsForAllEventTypes(vertx)` method is called.

2. This method iterates through all event consumer and publisher types in the `VertxEventRegistry` and creates codecs for them.

3. When a codec is created, it is registered with the Vertx event bus and added to the registry to prevent duplicate registration.

4. The `VertxEventPublisher` uses the `CodecRegistry` to get codec names for messages, ensuring consistency between the publisher and the registry.

## Usage

No additional configuration is required. Codecs are automatically created and registered for all event types during Vertx initialization.

When publishing messages, the appropriate codec is automatically used:

```java
// The codec is automatically used when publishing messages
vertxEventPublisher.publish(myMessage);
```

## Benefits

- Automatic codec registration for all event types
- No duplicate codec registration
- Consistent naming of codecs
- Improved efficiency by reusing codecs
- Simplified event publishing and consuming