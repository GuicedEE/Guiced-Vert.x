# Guiced Vert.x

Guiced Vert.x is a Guice-first integration layer for Vert.x 5. It bootstraps Vert.x from the GuicedEE lifecycle, wires verticles and event-bus endpoints through dependency injection, and supplies codecs and Mutiny-friendly helpers so you can stay inside one DI container while building reactive services.

## Features
- Vert.x lifecycle managed by GuicedEE; `Vertx` is injected everywhere via `VertXModule`.
- Event bus consumers as annotations on classes or methods with `@VertxEventDefinition` and `@VertxEventOptions` (worker threads, instances, local-only, backpressure hints).
- Injectable publishers via `@Named` or `@VertxEventDefinition`, returning Vert.x `Future` or Mutiny `Uni`.
- Automatic JSON mapping and codec registration through `CodecRegistry` and Jackson.
- Runtime configuration through annotations like `@VertX`, `@EventBusOptions`, `@FileSystemOptions`, `@MetricsOptions`, plus SPI hooks (`VertxConfigurator`, `ClusterVertxConfigurator`, `VerticleStartup`).
- Examples and tests covering method-based consumers, publishers, JSON conversion, and registry wiring.

## Install
Add the GuicedEE BOM (recommended) and the Vert.x integration module:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.guicedee</groupId>
      <artifactId>guicedee-bom</artifactId>
      <version>${guicedee.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.guicedee</groupId>
    <artifactId>guiced-vertx</artifactId>
  </dependency>
</dependencies>
```

If you already import a different GuicedEE BOM, reuse that version in place of `${guicedee.version}`.

## Bootstrapping Vert.x with Guice
Bootstrapping is automatic through the GuicedEE SPI. Call `IGuiceContext.instance().inject()` (from the GuicedEE client) to start the container; `VertXModule` is discovered via `IGuiceModule` and registers a fully configured `Vertx` instance for injection.

You can tune Vert.x at the package or type level:

```java
@VertX(workerPoolSize = 32, haEnabled = true)
@EventBusOptions(clusterPublicHost = "127.0.0.1", preferNativeTransport = true)
class VertxRuntimeConfig {}
```

For deeper customization, implement `VertxConfigurator` or `ClusterVertxConfigurator` to adjust `VertxOptions`, `EventBusOptions`, or cluster settings during startup.

## Declaring Consumers
Method-based consumers are the preferred style; the registry scans your classpath and binds them automatically:

```java
public class GreetingConsumers {
  @VertxEventDefinition(value = "greeting.received", options = @VertxEventOptions(worker = true))
  public String handleGreeting(Message<String> message) {
    return "Hello " + message.body();
  }

  @VertxEventDefinition("user.created")
  public Future<Void> trackUser(JsonObject payload) {
    // automatic Jackson mapping works for POJOs too
    return Future.succeededFuture();
  }
}
```

You can still implement the legacy interface-based style if you prefer, but annotation-based methods and classes are fully supported with configurable worker pools, instance counts, and local-only bindings.

## Publishing Events
Inject a publisher using `@Named` (or repeat `@VertxEventDefinition` for symmetry). Codecs are selected automatically when you publish non-core types:

```java
public class GreetingPublisher {
  @Inject
  @Named("greeting.received")
  private VertxEventPublisher<String> publisher;

  public Future<String> greet(String name) {
    return publisher.send(name); // request/response
  }

  public void broadcast(UserCreated event) {
    publisher.publish(event); // publish to all consumers, using a registered codec
  }
}
```

Mutiny users can obtain `Uni` results via `Uni.createFrom().future(publisher.send(...))`.

## Verticles and SPI hooks
Use `VerticleStartup` and `VerticleBuilder` to register custom verticles from Guice. Provide a `VertxConfigurator` if you need to tweak options (metrics, file system, address resolver, clustering). Examples live under `src/main/java/com/guicedee/vertx/spi/examples`.

## Development
- Build and test locally with `mvn test`.
- Key examples/tests: `src/test/java/com/guicedee/vertx/test/MethodBasedConsumerTest.java`, `MethodBasedPublisherTest.java`, `JsonConversionTest.java`, `VertxEventRegistryIntegrationTest.java`.
- License: Apache 2.0 (see `LICENSE`).

Issues and contributions are welcome—please add tests for new event patterns, codecs, or configurators.
