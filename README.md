# Guiced Vert.x

Guiced Vert.x is a Guice-first integration layer for Vert.x 5. It bootstraps Vert.x from the GuicedEE lifecycle, wires verticles and event-bus endpoints through dependency injection, and supplies codecs and Mutiny-friendly helpers so you can stay inside one DI container while building reactive services.

## Features
- Vert.x lifecycle managed by GuicedEE; `Vertx` is injected everywhere via `VertXModule`.
- Event bus consumers as annotations on classes or methods with `@VertxEventDefinition` and `@VertxEventOptions` (worker threads, instances, local-only, backpressure hints) — each consumer now runs in its own dedicated verticle (per address).
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

### Processing model (consumers)
Recent change: one verticle per `@VertxEventDefinition` address.

- Dedicated verticle per address: On startup, `VertxConsumersStartup` deploys an `EventConsumerVerticle` for every discovered event address. The consumer’s handler is registered in that verticle’s context, so published messages execute on that verticle’s event-loop.
- Instances and distribution: If `@VertxEventOptions.instances()` (or `consumerCount()`) > 1, Vert.x deploys multiple consumer verticles for the same address and round-robins messages across them.
- localOnly: When `options.localOnly()` is true, the consumer is registered as a local consumer, still inside its per-address verticle.
- Worker execution: When `options.worker()` is true, the consumer invocation is dispatched off the event loop to a worker pool (named and sized via `workerPool()`/`workerPoolSize()`), but the registration and trigger remain in the dedicated consumer verticle.
- Method- and class-based consumers are both supported. The dispatch path is unified via `VertxEventRegistry.dispatch(...)`, preserving reply semantics for `Uni`, `Future`, `CompletableFuture`, synchronous return values, and `void`.
- Type mapping: If your handler accepts non-Vert.x types, JSON bodies are mapped to the parameter type automatically using Jackson. For method-based consumers, the first non-`Message` parameter is used to infer the target type.

No behavior regressions were introduced for request/reply or error propagation; failures are still signaled using `message.fail(...)` with HTTP-like status codes.

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

### Processing model (publishers)
Recent change: publisher-side throttling without message loss.

- Throttled publish/send: `publish(...)` and fire-and-forget `send(...)` are emitted at most once every 50 ms by default. Calls are enqueued (FIFO) per `VertxEventPublisher` instance and drained by a periodic timer — no drops, ordering preserved.
- Backlog warnings: If the queue size exceeds a configurable threshold, warnings are logged with bucketed increments to avoid spam.
- Request/reply is not throttled: `request(...)` remains immediate to avoid unintended timeouts and preserve semantics.
- Local variants: `publishLocal(...)` / `sendLocal(...)` and header-bearing overloads participate in the same throttling behavior when using publish/send; delivery options (headers, codec, localOnly) are preserved.
- Per-instance throttling: Each publisher instance maintains its own queue/timer. If you create multiple publishers for the same address, each throttles independently.

Configuration (system properties or environment variables):
- VERTX_PUBLISH_THROTTLE_MS — global throttle period in ms (default 50). Set to `0` to disable throttling globally.
- VERTX_PUBLISH_THROTTLE_MS_<NORMALIZED_ADDRESS> — per-address override. Normalize address to UPPER_CASE and replace dots/dashes with underscores, e.g. `orders.events` → `VERTX_PUBLISH_THROTTLE_MS_ORDERS_EVENTS`.
- VERTX_PUBLISH_QUEUE_WARN — global backlog size threshold for warnings (default 1000).
- VERTX_PUBLISH_QUEUE_WARN_<NORMALIZED_ADDRESS> — per-address warning threshold override.

If throttling is disabled (0), publishers behave exactly as before (immediate dispatch).

## Verticles and SPI hooks
Use `VerticleStartup` and `VerticleBuilder` to register custom verticles from Guice. Provide a `VertxConfigurator` if you need to tweak options (metrics, file system, address resolver, clustering). Examples live under `src/main/java/com/guicedee/vertx/spi/examples`.

### Per-address consumer verticles
The Vert.x bridge deploys a dedicated `EventConsumerVerticle` per `@VertxEventDefinition` address. Startup flow:
1. `VertXPreStartup` builds the shared `Vertx` instance, scans for event definitions, and pre-registers codecs.
2. `VerticleBuilder` discovers and deploys application verticles based on `@Verticle` annotations (plus a default catch‑all verticle for uncovered packages).
3. `VertxConsumersStartup` runs inside each deployed verticle and deploys one `EventConsumerVerticle` per discovered address that belongs to the verticle’s assigned package. Messages for that address execute on the consumer verticle’s event-loop; worker-enabled consumers offload work to the configured worker pool.

This model isolates consumer work per address and improves clarity around execution context and scaling knobs (`instances`, `workerPool`, `localOnly`).

## Runtime overrides for addresses and options
You can override event bus addresses and consumer options at runtime using system properties or environment variables:

- Per-address override: `VERTX_EVENT_ADDRESS_<NORMALIZED_ADDRESS>` changes the address resolved from `@VertxEventDefinition("...")`. Normalization: upper-case, replace `.` and `-` with `_`.
- Global option overrides (apply to all consumers unless you provide a more specific mechanism):
  - `VERTX_EVENT_LOCAL_ONLY` (boolean)
  - `VERTX_EVENT_AUTOBIND` (boolean)
  - `VERTX_EVENT_CONSUMER_COUNT` (int)
  - `VERTX_EVENT_WORKER` (boolean)
  - `VERTX_EVENT_WORKER_POOL` (string)
  - `VERTX_EVENT_WORKER_POOL_SIZE` (int)
  - `VERTX_EVENT_INSTANCES` (int)
  - `VERTX_EVENT_ORDERED_BY_HEADER` (string)
  - `VERTX_EVENT_MAX_BUFFERED_MESSAGES` (int)
  - `VERTX_EVENT_RESUME_AT_MESSAGES` (int)
  - `VERTX_EVENT_BATCH_WINDOW_MS` (int)
  - `VERTX_EVENT_BATCH_MAX` (int)
  - `VERTX_EVENT_TIMEOUT_MS` (long)

These are read at startup; set them via JVM `-D` properties or environment variables (env wins if both are provided via the helper in use).

## Development
- Build and test locally with `mvn test`.
- Key examples/tests: `src/test/java/com/guicedee/vertx/test/MethodBasedConsumerTest.java`, `MethodBasedPublisherTest.java`, `JsonConversionTest.java`, `VertxEventRegistryIntegrationTest.java`.
- License: Apache 2.0 (see `LICENSE`).

Issues and contributions are welcome—please add tests for new event patterns, codecs, or configurators.
