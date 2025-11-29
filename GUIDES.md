# GUIDES — Guiced-Vert.x

Purpose: Point to modular guides for this project’s selected stacks and how to apply them here. Follow topic indexes under `rules/`.

## Core References
- Guiced Vert.x topic rules — `rules/generative/backend/guicedee/vertx/README.md` (lifecycle, codecs, configurators, verticles, glossary)
- Vert.x 5 integration patterns — `rules/generative/backend/vertx/README.md` (event bus, codecs, deployment)
- GuicedEE Client functions — `rules/generative/backend/guicedee/README.md` and `rules/generative/backend/guicedee/functions/guiced-vertx-rules.md`
- Fluent API (CRTP) — `rules/generative/backend/fluent-api/crtp.rules.md`
- Java 25 + Maven — `rules/generative/language/java/java-25.rules.md`, `rules/generative/language/java/build-tooling.md`
- Logging — `rules/generative/backend/logging/README.md`
- JSpecify nullness — `rules/generative/backend/jspecify/README.md`
- CI/CD — `rules/generative/platform/ci-cd/README.md`, provider `rules/generative/platform/ci-cd/providers/github-actions.md`
- Secrets/config — `rules/generative/platform/secrets-config/README.md`
- Architecture — `rules/generative/architecture/README.md`

## How to Apply (current system)
- Bootstrapping: Follow lifecycle ordering in docs/architecture/sequence-startup.md and `rules/generative/backend/guicedee/vertx/lifecycle.rules.md`; bind Vert.x via `VertXPreStartup`/`VertXModule`, then deploy via `VertXPostStartup`.
- Event bus definitions: Annotate with `@VertxEventDefinition`/`@VertxEventOptions` per `event-definitions.rules.md`; keep addresses stable.
- Publishers: Inject `VertxEventPublisher<T>` via named bindings; align codec registration per `publishers.rules.md` and sequence-publish-consume.md.
- Codec management: Prefer automatic codec registration via `CodecRegistry` (see `codecs.rules.md`); avoid duplicate codec names and keep DTOs JSON-friendly.
- Configuration: Use configurator SPI (`configuration.rules.md`) instead of hard-coding Vert.x options; document env vars in README/IMPLEMENTATION.md.
- Testing: Leverage existing tests under `src/test/java/com/guicedee/vertx` as patterns; align with `testing.rules.md` and JUnit Jupiter per Java rules.

## Traceability
- See IMPLEMENTATION.md for where these guides map to code.
- See docs/architecture/README.md for diagrams referenced in guides.
- See docs/design-validation.md for Stage 2 mappings, API sketches, migration notes, test strategy, and acceptance criteria.
