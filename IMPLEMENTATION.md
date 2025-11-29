# IMPLEMENTATION — Guiced-Vert.x

## Modules & Layout
- Maven artifact: `com.guicedee:guiced-vertx` (Java 25, JPMS module `com.guicedee.vertx`).
- Code: `src/main/java/com/guicedee/vertx` (annotations/options/publisher/Guice module) and `com/guicedee/vertx/spi` (registry, lifecycle hooks, codecs, Verticle SPI).
- Services: `module-info.java` provides `IGuicePreStartup` (VertXPreStartup), `IGuicePreDestroy` (VertXPostStartup), `IGuiceModule` (VertXModule); uses `VertxConfigurator` and `VerticleStartup` via SPI.
- Tests: `src/test/java/com/guicedee/vertx` cover consumer/publisher behaviors, JSON conversion, codecs.

## Runtime Flow (see docs/architecture diagrams)
- Startup: GuicedEE lifecycle calls `VertXPreStartup` → boots Vert.x → scans annotations → populates `VertxEventRegistry` → registers codecs → Guice binds consumers/publishers in `VertXModule` → optional post-startup hooks.
- Event publish/consume: Producers inject `VertxEventPublisher<T>` via `@Named(address)`; registry supplies type info and codecs; Vert.x event bus routes to consumers (method- or interface-based).

## Key Components
- `VertxEventRegistry`: Collects event definitions for consumers/publishers; exposes type literals/keys for Guice.
- `VertXModule`: PrivateModule exposing Vert.x instance and dynamic bindings for consumers/publishers.
- `VertxEventPublisher`: Generic helper for publish/send with codec resolution.
- `CodecRegistry`: Creates/registers codecs per message type to avoid duplicates.
- `VertXPreStartup` / `VertXPostStartup`: Lifecycle entry points for boot/teardown tasks.

## CI/Env
- CI: GitHub Actions at `.github/workflows/build.yml` (Java 25, Maven cache, mvn -B -ntp test).
- Env/config: `.env.example` checked in per secrets-config rules; copy and set deployment-specific values; no sensitive values committed.

## Traceability
- Guides ↔ this implementation: see GUIDES.md for how rules map to these components.
- Topic rules index: rules/generative/backend/guicedee/vertx/README.md governs lifecycle, codecs, configurators, and testing requirements.
- Glossary alignment: use GLOSSARY.md for naming (CRTP, Vert.x/GuicedEE terms).
- Architecture views: docs/architecture/README.md links C4/context/container/component, sequences, ERD.
- Design validation: docs/design-validation.md captures Stage 2 mappings, API sketches, migration notes, test strategy, and acceptance criteria.
- Implementation plan: IMPLEMENTATION_PLAN.md describes Stage 3 scaffolding/CI/env tasks and validation approach.
