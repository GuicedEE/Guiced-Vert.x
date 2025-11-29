# Project RULES — Guiced-Vert.x

Scope: Host project consuming Rules Repository at `rules/` (submodule). Use these rules for all future changes; do not place project docs inside `rules/`.

## Selected Stacks & Enforced Choices
- Language/Build: Java 25 LTS (`rules/generative/language/java/java-25.rules.md`), Maven (`rules/generative/language/java/build-tooling.md`).
- Backend Reactive: Vert.x 5 (`rules/generative/backend/vertx/README.md`), GuicedEE Client (`rules/generative/backend/guicedee/README.md` and functions under `rules/generative/backend/guicedee/functions/guiced-vertx-rules.md` as applicable).
- Library topic rules: Guiced Vert.x bridge (`rules/generative/backend/guicedee/vertx/README.md`).
- Fluent API Strategy: CRTP (`rules/generative/backend/fluent-api/crtp.rules.md`); avoid Builder/Lombok @Builder.
- Logging: `rules/generative/backend/logging/README.md`.
- JSpecify: `rules/generative/backend/jspecify/README.md`.
- CI/CD: GitHub Actions (`rules/generative/platform/ci-cd/README.md`, provider `rules/generative/platform/ci-cd/providers/github-actions.md`).
- Architecture guidance: `rules/generative/architecture/README.md`.
- Platform Security/Secrets: `rules/generative/platform/secrets-config/README.md` for env/config handling.

## Behavioral & Process Rules
- Follow Forward-Only Change Policy and Document Modularity Policy (see `rules/RULES.md` sections 4–6).
- Documentation-first, stage-gated workflow: no code changes before docs/guides/plans are approved per stage gates.
- Topic-first glossary precedence: defer to topic glossaries listed in GLOSSARY.md.
- JPMS alignment: keep `module-info.java` consistent with dependencies and service providers.
- No host-specific docs inside `rules/`; host artifacts live at repo root or under `docs/`.

## Traceability
- PACT.md ↔ this RULES.md ↔ GUIDES.md ↔ IMPLEMENTATION.md ↔ GLOSSARY.md ↔ docs/architecture/*. Link updates must stay in sync in the same change set.
- CI/Env: reference `.env.example` (host) and CI workflows (GitHub Actions) from IMPLEMENTATION.md and GUIDES.md when added.
