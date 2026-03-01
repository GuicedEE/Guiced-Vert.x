# Guiced Vert.x

[![Build](https://github.com/GuicedEE/Guiced-Vert.x/actions/workflows/build.yml/badge.svg)](https://github.com/GuicedEE/Guiced-Vert.x/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.guicedee/guiced-vertx)](https://central.sonatype.com/artifact/com.guicedee/guiced-vertx)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)

![Java 25+](https://img.shields.io/badge/Java-25%2B-green)
![Guice 7](https://img.shields.io/badge/Guice-7%2B-green)
![Vert.X 5](https://img.shields.io/badge/Vert.x-5%2B-green)
![Maven 4](https://img.shields.io/badge/Maven-4%2B-green)

Guice-first integration layer for **Vert.x 5**. Bootstraps Vert.x from the [GuicedEE](https://github.com/GuicedEE) lifecycle, wires verticles and event-bus endpoints through dependency injection, and supplies codecs and Mutiny-friendly helpers so you can stay inside one DI container while building reactive services.

## ✨ Features

- **Vert.x lifecycle managed by GuicedEE** — `Vertx` is injected everywhere via `VertXModule`
- **Annotation-driven consumers** — `@VertxEventDefinition` + `@VertxEventOptions` with worker threads, instance counts, local-only, and backpressure hints
- **Injectable publishers** — `@Named` or `@VertxEventDefinition`, returning Vert.x `Future` or Mutiny `Uni`
- **Automatic JSON mapping** — codec registration through `CodecRegistry` and Jackson
- **Per-address consumer verticles** — each consumer runs in its own dedicated verticle
- **Publisher-side throttling** — FIFO queuing with configurable rate, no message loss
- **Runtime configuration** — annotations (`@VertX`, `@EventBusOptions`, `@FileSystemOptions`, `@MetricsOptions`) plus SPI hooks (`VertxConfigurator`, `ClusterVertxConfigurator`, `VerticleStartup`)

## 📦 Installation

```xml
<dependency>
  <groupId>com.guicedee</groupId>
  <artifactId>guiced-vertx</artifactId>
</dependency>
```

<details>
<summary>Gradle (Kotlin DSL)</summary>

```kotlin
implementation("com.guicedee:guiced-vertx:2.0.0-SNAPSHOT")
```
</details>

## 🚀 Quick Start

Bootstrapping is automatic — call `IGuiceContext.instance()` and `VertXModule` is discovered via SPI:

```java
IGuiceContext.registerModuleForScanning.add("my.app");
IGuiceContext.instance();
```

Tune Vert.x at the package or type level:

```java
@VertX(workerPoolSize = 32, haEnabled = true)
@EventBusOptions(clusterPublicHost = "127.0.0.1", preferNativeTransport = true)
class VertxRuntimeConfig {}
```

## 📥 Declaring Consumers

Method-based consumers are the preferred style — the registry scans your classpath and binds them automatically:

```java
public class GreetingConsumers {

    @VertxEventDefinition(value = "greeting.received",
        options = @VertxEventOptions(worker = true))
    public String handleGreeting(Message<String> message) {
        return "Hello " + message.body();
    }

    @VertxEventDefinition("user.created")
    public Future<Void> trackUser(JsonObject payload) {
        return Future.succeededFuture();
    }
}
```

### Processing model

- **One verticle per address** — `VertxConsumersStartup` deploys an `EventConsumerVerticle` for every discovered event address
- **Scaling** — `@VertxEventOptions.instances()` > 1 deploys multiple consumer verticles with round-robin
- **Worker execution** — `options.worker()` dispatches off the event loop to a named worker pool
- **Local-only** — `options.localOnly()` registers a local consumer inside its per-address verticle
- **Type mapping** — non-Vert.x parameter types are mapped automatically via Jackson

## 📤 Publishing Events

Inject a publisher using `@Named`:

```java
public class GreetingPublisher {

    @Inject @Named("greeting.received")
    private VertxEventPublisher<String> publisher;

    public Future<String> greet(String name) {
        return publisher.send(name);       // request/response
    }

    public void broadcast(UserCreated event) {
        publisher.publish(event);           // fire to all consumers
    }
}
```

### Throttling

Publisher-side throttling prevents flooding without message loss:

- `publish()` and fire-and-forget `send()` are enqueued (FIFO) and drained at a configurable rate (default 50 ms)
- `request()` remains immediate — no timeouts introduced
- Per-instance queues — each publisher throttles independently

| Environment variable | Default | Purpose |
|---|---|---|
| `VERTX_PUBLISH_THROTTLE_MS` | `50` | Global throttle period (0 = disabled) |
| `VERTX_PUBLISH_THROTTLE_MS_<ADDR>` | — | Per-address override |
| `VERTX_PUBLISH_QUEUE_WARN` | `1000` | Backlog warning threshold |
| `VERTX_PUBLISH_QUEUE_WARN_<ADDR>` | — | Per-address warning threshold |

> Address normalization: upper-case, replace `.` and `-` with `_` (e.g. `orders.events` → `ORDERS_EVENTS`)

## ⚙️ Runtime Overrides

Override event bus addresses and consumer options at runtime via system properties or environment variables:

| Variable | Type | Purpose |
|---|---|---|
| `VERTX_EVENT_ADDRESS_<ADDR>` | string | Override the resolved address |
| `VERTX_EVENT_LOCAL_ONLY` | boolean | Force local-only consumers |
| `VERTX_EVENT_CONSUMER_COUNT` | int | Default consumer count |
| `VERTX_EVENT_WORKER` | boolean | Default worker mode |
| `VERTX_EVENT_WORKER_POOL` | string | Worker pool name |
| `VERTX_EVENT_WORKER_POOL_SIZE` | int | Worker pool size |
| `VERTX_EVENT_INSTANCES` | int | Verticle instances per address |
| `VERTX_EVENT_TIMEOUT_MS` | long | Consumer timeout |
| `VERTX_EVENT_BATCH_WINDOW_MS` | int | Batch window |
| `VERTX_EVENT_BATCH_MAX` | int | Max batch size |
| `VERTX_EVENT_MAX_BUFFERED_MESSAGES` | int | Backpressure buffer limit |
| `VERTX_EVENT_RESUME_AT_MESSAGES` | int | Resume threshold |

## 🔄 Startup Flow

```
IGuiceContext.instance()
 └─ VertXPreStartup        → builds Vertx, scans events, registers codecs
     └─ VerticleBuilder     → deploys app verticles from @Verticle annotations
         └─ VertxConsumersStartup → deploys one EventConsumerVerticle per address
```

## 🔌 SPI Hooks

| SPI | Purpose |
|---|---|
| `VertxConfigurator` | Customize `VertxOptions` during startup |
| `ClusterVertxConfigurator` | Configure clustering |
| `VerticleStartup` | Register custom verticles from Guice |

## 🗺️ Module Graph

```
com.guicedee.vertx
 ├── com.guicedee.client              (SPI contracts)
 ├── com.guicedee.jsonrepresentation  (JSON codec support)
 ├── io.vertx.core                    (Vert.x runtime)
 ├── io.vertx.mutiny                  (Mutiny bindings)
 ├── io.smallrye.mutiny               (reactive streams)
 ├── com.fasterxml.jackson.databind   (JSON mapping)
 └── jakarta.cdi                      (CDI annotations)
```

## 🤝 Contributing

Issues and pull requests are welcome — please add tests for new event patterns, codecs, or configurators.

## 📄 License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
