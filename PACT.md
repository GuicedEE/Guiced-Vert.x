# PACT — GuicedEE / Guiced-Vert.x

- **Project**: GuicedEE / Guiced-Vert.x (`com.guicedee:guiced-vertx`)
- **Scope**: Vert.x 5 integration for GuicedEE Client, providing event bus annotations, registry, lifecycle wiring, codec registration, and Guice module exposure (JPMS module `com.guicedee.vertx`).
- **License**: Apache-2.0 (see LICENSE)
- **Architecture**: Specification-Driven Design, Documentation-as-Code, forward-only changes.
- **Selected stacks**: Java 25 LTS, Maven, Vert.x 5, GuicedEE Client, Mutiny, Logging (log4j2), JSpecify, Fluent API strategy = CRTP (no Lombok @Builder), CI = GitHub Actions.

## Commitments
- Honor Rules Repository guidance via `rules/` submodule; host docs stay outside `rules/`.
- Close loops: PACT ↔ GLOSSARY ↔ RULES ↔ GUIDES ↔ IMPLEMENTATION ↔ architecture diagrams.
- Follow Forward-Only Change Policy (no legacy anchors/shims) and Document Modularity Policy.
- Treat existing README content as legacy; replace with modular docs linked here.

## Cross-References
- Rules index: RULES.md (host) → `rules/RULES.md`
- Guides: GUIDES.md (host) → selected topics under `rules/generative/**`
- Implementation notes: IMPLEMENTATION.md (host)
- Glossary (topic-first): GLOSSARY.md
- Topic rules index (Guiced Vert.x): `rules/generative/backend/guicedee/vertx/README.md` (glossary + lifecycle/configuration/publishers/codecs/verticles/testing)
- Architecture index: docs/architecture/README.md (C4, sequences, ERD)
- Prompt reference for future AI runs: docs/PROMPT_REFERENCE.md

## Evidence Snapshot (current repo)
- Core code under `src/main/java/com/guicedee/vertx` and `com/guicedee/vertx/spi`: event definitions/options, event registry, codec registry, lifecycle pre-startup/post-startup, Guice module bindings, publisher utilities, Verticle SPI, examples.
- Tests under `src/test/java/com/guicedee/vertx`: registry/publisher/consumer behaviors and JSON conversion.
- JPMS module descriptor: `src/main/java/module-info.java`.
- Maven build: `pom.xml` with GuicedEE BOMs, Vert.x core, jackson, Mutiny, Lombok (compile-time), JUnit Jupiter.

## Stage Status
- Stage 1 (Architecture & Foundations): completed in this change set (lifecycle/diagram updates).
- Stage 2 (Guides & Design Validation): completed in this change set (rules index + topic design).
- Stage 3 (Implementation Plan): completed in this change set (updated plan for rules modularization).
- Stage 4 (Implementation & Scaffolding): completed in this change set (docs/rules updates; blanketed approvals applied).
