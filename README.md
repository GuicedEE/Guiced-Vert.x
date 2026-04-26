# Guiced Vert.x

[![Build](https://github.com/GuicedEE/Guiced-Vert.x/actions/workflows/build.yml/badge.svg)](https://github.com/GuicedEE/Guiced-Vert.x/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.guicedee/guiced-vertx)](https://central.sonatype.com/artifact/com.guicedee/guiced-vertx)
[![Maven Snapshot](https://img.shields.io/nexus/s/com.guicedee/guiced-vertx?server=https%3A%2F%2Foss.sonatype.org&label=Maven%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/com/guicedee/guiced-vertx/)
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
- **Authentication & Authorization** — annotation-driven auth via `@AuthOptions` with `ChainAuth`, `KeyStoreOptions`, `PubSecKeyOptions`, PRNG, and SPI-based provider registration

## 📦 Installation

```xml
<dependency>
  <groupId>com.guicedee</groupId>
  <artifactId>vertx</artifactId>
</dependency>
```

<details>
<summary>Gradle (Kotlin DSL)</summary>

```kotlin
implementation("com.guicedee:guiced-vertx:2.0.0-RC10")
```
</details>

## 🚀 Quick Start

Bootstrapping is automatic — call `IGuiceContext.instance().inject()` and `VertXModule` is discovered via SPI:

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
import io.smallrye.mutiny.Uni;

public class GreetingConsumers {

    @VertxEventDefinition(value = "greeting.received",
            options = @VertxEventOptions(worker = true))
    public String handleGreeting(Message<Anything> message) {
        return "Hello " + message.body();
    }

    @VertxEventDefinition("user.created")
    public Uni<Void> trackUser(Anything payload) {
        return Uni.createFrom().voidItem();
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
import io.smallrye.mutiny.Uni;

public class GreetingPublisher {

    @Inject
    @Named("greeting.received")
    private VertxEventPublisher<String> publisher;

    public Uni<String> greet(String name) {
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

## 🔐 Authentication & Authorization

Built-in support for [Vert.x Common Auth](https://vertx.io/docs/vertx-auth-common/java/) — annotation-driven configuration with Guice injection for all auth types.

### Quick Setup

Place `@AuthOptions` on a class or `package-info.java`:

```java
@AuthOptions(
    chainMode = AuthOptions.ChainMode.ANY,
    keyStore = @AuthKeyStore(
        path = "/path/to/keystore.pkcs12",
        type = "pkcs12",
        password = "changeit"
    ),
    pubSecKeys = {
        @AuthPubSecKey(algorithm = "RS256", path = "/path/to/public.pem")
    },
    prngAlgorithm = "SHA1PRNG",
    leeway = 5
)
package com.example.auth;
```

### Authentication Providers (SPI)

Register custom authentication providers via `IGuicedAuthenticationProvider`:

```java
public class MyAuthProvider implements IGuicedAuthenticationProvider {
    @Override
    public AuthenticationProvider getAuthenticationProvider() {
        // Return your provider implementation
        // e.g. UsernamePasswordCredentials-based, JWT, OAuth2, etc.
        return myProvider;
    }
}
```

Register in `module-info.java`:
```java
provides IGuicedAuthenticationProvider with MyAuthProvider;
```

Multiple providers are combined via `ChainAuth` (ANY = first match wins, ALL = all must pass).

### Authorization Providers (SPI)

Register custom authorization providers via `IGuicedAuthorizationProvider`:

```java
public class MyAuthzProvider implements IGuicedAuthorizationProvider {
    @Override
    public AuthorizationProvider getAuthorizationProvider() {
        return myAuthorizationProvider;
    }
}
```

### Guice Bindings

| Type | Scope | Description |
|---|---|---|
| `AuthenticationProvider` | Singleton | Primary provider (or `ChainAuth` if multiple) |
| `ChainAuth` | Singleton | The authentication chain |
| `AuthorizationProvider` | Singleton | Primary authorization provider |
| `Set<AuthorizationProvider>` | Multibinder | All registered authorization providers |
| `VertxContextPRNG` | Singleton | Shared PRNG (non-blocking, event-loop safe) |
| `KeyStoreOptions` | Instance | JVM keystore config (if configured) |
| `Set<PubSecKeyOptions>` | Multibinder | PEM key configs (if configured) |

### Using Authentication

```java
public class LoginService {
    @Inject private AuthenticationProvider authProvider;

    public Future<User> login(String username, String password) {
        return authProvider.authenticate(
            new UsernamePasswordCredentials(username, password));
    }
}
```

### Using Authorization

Vert.x provides multiple authorization types:

```java
// Role-based
RoleBasedAuthorization.create("admin").match(user);

// Permission-based
PermissionBasedAuthorization.create("printer:print").match(user);

// Wildcard permission
WildcardPermissionBasedAuthorization.create("printer:*").match(user);

// Logical combinations
AndAuthorization.create()
    .addAuthorization(RoleBasedAuthorization.create("admin"))
    .addAuthorization(PermissionBasedAuthorization.create("users:write"));

OrAuthorization.create()
    .addAuthorization(RoleBasedAuthorization.create("admin"))
    .addAuthorization(RoleBasedAuthorization.create("manager"));

NotAuthorization.create(RoleBasedAuthorization.create("guest"));
```

Load authorizations onto a user:

```java
@Inject private Set<AuthorizationProvider> authzProviders;

public Future<Void> loadAuthorizations(User user) {
    List<Future<Void>> futures = authzProviders.stream()
        .map(p -> p.getAuthorizations(user))
        .toList();
    return Future.all(futures).mapEmpty();
}
```

### User Principal & Attributes

```java
// Access principal data (source identity)
JsonObject principal = user.principal();

// Access computed attributes
JsonObject attributes = user.attributes();

// Unified lookup (attributes first, then principal)
if (user.containsKey("sub")) {
    String sub = user.get("sub");
}

// Token expiration (exp, iat, nbf with leeway)
boolean expired = user.expired();
```

### PRNG (Pseudo Random Number Generator)

The shared `VertxContextPRNG` is event-loop safe and injectable:

```java
@Inject private VertxContextPRNG prng;

String token = prng.nextString(32);
int randomInt = prng.nextInt();
```

Configure via annotation or environment variables:

| Attribute | Variable | Default | System Property |
|---|---|---|---|
| `prngAlgorithm` | `VERTX_AUTH_PRNG_ALGORITHM` | (system) | `io.vertx.ext.auth.prng.algorithm` |
| `prngSeedInterval` | `VERTX_AUTH_PRNG_SEED_INTERVAL` | 300000 ms | `io.vertx.ext.auth.prng.seed.interval` |
| `prngSeedBits` | `VERTX_AUTH_PRNG_SEED_BITS` | 64 | `io.vertx.ext.auth.prng.seed.bits` |

### Working with Keys

**JVM KeyStore** (pkcs12, jks):

```java
@AuthOptions(keyStore = @AuthKeyStore(
    path = "/path/to/keystore.pkcs12",
    type = "pkcs12",
    password = "changeit",
    alias = "mykey",
    aliasPassword = "keypass"
))
```

Import PEM to PKCS12: `openssl pkcs12 -export -in cert.pem -out keystore.pkcs12 -name myAlias -noiter -nomaciter`

**PEM Keys** (PKCS8 format):

```java
@AuthOptions(pubSecKeys = {
    @AuthPubSecKey(algorithm = "RS256", path = "/path/to/public.pem"),
    @AuthPubSecKey(algorithm = "ES256", buffer = "-----BEGIN PUBLIC KEY-----\n...")
})
```

Convert to PKCS8: `openssl pkcs8 -topk8 -inform PEM -in private.pem -out private_key.pem -nocrypt`

### Environment Variable Overrides

| Property | Variable |
|---|---|
| `chainMode()` | `VERTX_AUTH_CHAIN_MODE` |
| `keyStore.path()` | `VERTX_AUTH_KEYSTORE_PATH` |
| `keyStore.type()` | `VERTX_AUTH_KEYSTORE_TYPE` |
| `keyStore.password()` | `VERTX_AUTH_KEYSTORE_PASSWORD` |
| `keyStore.alias()` | `VERTX_AUTH_KEYSTORE_ALIAS` |
| `keyStore.aliasPassword()` | `VERTX_AUTH_KEYSTORE_ALIAS_PASSWORD` |
| `pubSecKeys[n].algorithm()` | `VERTX_AUTH_PUBSECKEY_n_ALGORITHM` |
| `pubSecKeys[n].buffer()` | `VERTX_AUTH_PUBSECKEY_n_BUFFER` |
| `pubSecKeys[n].path()` | `VERTX_AUTH_PUBSECKEY_n_PATH` |
| `prngAlgorithm()` | `VERTX_AUTH_PRNG_ALGORITHM` |
| `prngSeedInterval()` | `VERTX_AUTH_PRNG_SEED_INTERVAL` |
| `prngSeedBits()` | `VERTX_AUTH_PRNG_SEED_BITS` |
| `leeway()` | `VERTX_AUTH_LEEWAY` |

## 🔄 Startup Flow

```
IGuiceContext.instance()
 └─ VertXPreStartup        → builds Vertx, scans events, registers codecs
 └─ VertxAuthPreStartup    → scans @AuthOptions, discovers auth/authz providers,
                              builds ChainAuth, configures PRNG and key stores
     └─ VerticleBuilder     → deploys app verticles from @Verticle annotations
         └─ VertxConsumersStartup → deploys one EventConsumerVerticle per address
```

## 🔌 SPI Hooks

| SPI | Purpose |
|---|---|
| `VertxConfigurator` | Customize `VertxOptions` during startup |
| `ClusterVertxConfigurator` | Configure clustering |
| `VerticleStartup` | Register custom verticles from Guice |
| `IGuicedAuthenticationProvider` | Contribute authentication providers to `ChainAuth` |
| `IGuicedAuthorizationProvider` | Contribute authorization providers |

## 🗺️ Module Graph

```
com.guicedee.vertx
 ├── com.guicedee.client              (SPI contracts)
 ├── com.guicedee.jsonrepresentation  (JSON codec support)
 ├── io.vertx.core                    (Vert.x runtime)
 ├── io.vertx.auth.common             (Authentication & Authorization)
 ├── io.vertx.mutiny                  (Mutiny bindings)
 ├── io.smallrye.mutiny               (reactive streams)
 ├── io.github.classgraph             (annotation scanning)
 └── com.fasterxml.jackson.databind   (JSON mapping)
```

## 🤝 Contributing

Issues and pull requests are welcome — please add tests for new event patterns, codecs, or configurators.

## 📄 License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
