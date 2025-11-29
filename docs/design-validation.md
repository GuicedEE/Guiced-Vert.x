# Guides & Design Validation (Stage 2) — Guiced-Vert.x

## RULES Mapping (selected stacks)
- Java 25 LTS + Maven — `rules/generative/language/java/java-25.rules.md`, `rules/generative/language/java/build-tooling.md`.
- Guiced Vert.x bridge rules — `rules/generative/backend/guicedee/vertx/README.md` (glossary, lifecycle, codecs, configurators, verticles, testing).
- Vert.x 5 reactive patterns — `rules/generative/backend/vertx/README.md`.
- GuicedEE Client functions — `rules/generative/backend/guicedee/README.md`, `rules/generative/backend/guicedee/functions/guiced-vertx-rules.md`.
- Fluent API: CRTP (no Builder) — `rules/generative/backend/fluent-api/crtp.rules.md`.
- Logging — `rules/generative/backend/logging/README.md`.
- JSpecify — `rules/generative/backend/jspecify/README.md`.
- CI/CD — `rules/generative/platform/ci-cd/README.md`, provider `rules/generative/platform/ci-cd/providers/github-actions.md`.
- Secrets/config — `rules/generative/platform/secrets-config/README.md`.
- Architecture framing — `rules/generative/architecture/README.md`.

## How to Apply (project-specific)
- **Lifecycle**: Follow `docs/architecture/sequence-startup.md` and `rules/generative/backend/guicedee/vertx/lifecycle.rules.md`; `VertXPreStartup` boots Vert.x, scans `@VertxEventDefinition`, registers codecs, then `VertXModule` binds and exposes consumers/publishers; `VertXPostStartup` deploys verticles and sets the static accessor.
- **Event annotations**: Use `@VertxEventDefinition(address, options=@VertxEventOptions(...))`; prefer method-based consumers; keep addresses unique; set `localOnly/autobind/consumerCount` per rules (`event-definitions.rules.md`).
- **Publishers**: Inject `VertxEventPublisher<T>` with `@Named(address)`; use `send` for request/reply futures and `publish` for fan-out. Keep payload types codec-friendly (Jackson); see `publishers.rules.md`.
- **Codecs**: Rely on `CodecRegistry` auto-registration from registry metadata; avoid manual codec duplication; prefer stable codec names (`codecs.rules.md`).
- **SPI**: Extend `VertxConfigurator` or `VerticleStartup` for custom Vert.x options/verticles; ensure JPMS exports are aligned if adding packages (`configuration.rules.md`, `verticles.rules.md`).
- **Nullness**: Adopt JSpecify annotations when editing APIs; keep CRTP-style fluent return types aligned.
- **Logging**: Use structured logging; avoid sensitive payloads on event bus logs.

## API Surface Sketches (contracts)
- `@VertxEventDefinition`: fields: `value` (address), `options` (`VertxEventOptions` with `localOnly:boolean`, `autobind:boolean`, `consumerCount:int`), optional type inference via annotated element.
- `VertxEventPublisher<T>`: methods `publish(T message)`, `publish(T message, DeliveryOptions opts)`, `send(T message) : Future<R>`, `send(T message, DeliveryOptions opts)`. Construction handled via Guice provider from registry type metadata.
- `VertxEventRegistry`: stores `address → definition`, `address → consumer class/method`, `address → publisher key/type`. Used by startup and Guice module to bind consumers/publishers.
- `CodecRegistry`: `createAndRegisterCodecsForAllEventTypes(Vertx vertx)`; provides codec names for publishers.
- Lifecycle services: `VertXPreStartup` (boots, scans, registers), `VertXModule` (binds/exposes), `VertXPostStartup` (teardown hook).

## Migration Notes (forward-only)
- Legacy standalone doc `README-DynamicCodecs.md` removed; codec guidance now lives in GUIDES and architecture docs.
- Existing README replaced by modular links; future edits should expand README with concise pointers only.
- When adding new event modules, prefer modular docs under `docs/` and update indexes (PACT/RULES/GUIDES/IMPLEMENTATION/architecture).

## Test Strategy Outline
- **Unit**: registry discovery, codec registry behavior (dedupe), VertxEventPublisher send/publish semantics (using Vert.x test context or mocks), CRTP fluent return consistency, JPMS module-info verification.
- **Integration**: start Vert.x via `VertXPreStartup`, bind Guice module, run end-to-end publish/consume (see existing tests in `src/test/java/com/guicedee/vertx` for patterns).
- **Negative paths**: invalid addresses (duplicates), codec registration conflicts, consumer exceptions.
- **CI**: GitHub Actions build/test matrix with Java 25; cache Maven; fail on test/lint violations.

## Acceptance Criteria (Stage 2 scope)
- Guides map selected rules to concrete usage in this project (see GUIDES.md and this file).
- Architecture diagrams referenced from guides (docs/architecture/*) remain consistent with described flows.
- Codec and event-publish/consume flows are documented with contracts and sequence diagrams.
- Glossary precedence and CRTP enforcement noted in RULES/GLOSSARY and applied in guides.
- Migration path away from legacy docs documented.

## UI/UX Artifacts
- Not applicable (library-only); no UI flows required.
