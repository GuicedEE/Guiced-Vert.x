# Architecture Index — Guiced-Vert.x

- C4 Context — ./c4-context.md
- C4 Container — ./c4-container.md
- C4 Component (runtime integration) — ./c4-component-runtime.md
- Sequences:
  - Startup lifecycle — ./sequence-startup.md
  - Publish/consume flow — ./sequence-publish-consume.md
- ERD — ./erd-event-model.md

## Scope & Architecture Overview
- Purpose: bridge GuicedEE Client lifecycle with Vert.x 5, exposing a singleton Vertx binding, codec registry, and event publisher/consumer wiring via CRTP-aligned APIs.
- Design pillars: Specification-Driven Design, Documentation-as-Code, forward-only docs with topic-first glossary, and CRTP fluent APIs (no Builder/Lombok).
- Lifecycle: `VertXPreStartup` boots Vert.x, scans @VertxEventDefinition, registers codecs, installs Guice bindings; `VertXPostStartup` handles verticle startup/configurators and static `VertX` access.
- Extension: configurator SPI (resolver/event bus/metrics/fs) and verticle startup hooks allow host apps to adjust runtime options without editing core wiring.

## Dependency & Integration Map
- Vert.x runtime (event bus, codecs, verticles) — external dependency bootstrapped by `VertXPreStartup` and exposed via `VertXModule`.
- GuicedEE Client lifecycle — invokes pre/post startup hooks, triggers verticle deployment, and sets the static `VertX` accessor.
- Logging (log4j2) — used across lifecycle/components for startup, registry, and publish/consume diagnostics.
- Jackson (json-representation) — used in codec registration and event payload conversion.
- Tests use JUnit Jupiter; validation references docs/PROMPT_REFERENCE.md for required stack selection.

## Threat Model (summary)
- Trust boundaries: Guice injector vs. Vert.x event bus. Event bus payloads must be validated/typed (codecs) to avoid injection/serialization issues.
- Lifecycle hooks run with application privileges; configuration must come from trusted sources (env/system props) and vetted configurators.
- Avoid duplicate codec registration to prevent message handling inconsistencies; enforce deterministic codec names/keys.
- Logging should avoid sensitive payloads where events may contain user data; rely on structured logging rather than raw bodies.

Sources are text-based Mermaid. Update this index with any new diagrams and keep docs in sync with rules/generative/backend/guicedee/vertx.
