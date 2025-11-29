# GLOSSARY — Guiced-Vert.x (Topic-First)

Glossary precedence policy:
- Topic glossaries override root definitions within their scope.
- This host glossary is an index/aggregator; avoid duplication except for enforced Prompt Language Alignment or project-only terms.
- Consult topic glossaries first, then host terms. Link back when terms are borrowed.

## Topic Glossaries (selected stacks)
- Guiced Vert.x Bridge — `rules/generative/backend/guicedee/vertx/GLOSSARY.md`
- Java 25 LTS — `rules/generative/language/java/GLOSSARY.md`
- Fluent API (CRTP) — `rules/generative/backend/fluent-api/GLOSSARY.md` (use CRTP, avoid Builder)
- Vert.x 5 — `rules/generative/backend/vertx/README.md#glossary` (no dedicated file; use topic anchors)
- GuicedEE Client — `rules/generative/backend/guicedee/GLOSSARY.md`
- Logging — `rules/generative/backend/logging/README.md` (terminology inline)
- JSpecify — `rules/generative/backend/jspecify/GLOSSARY.md`
- CI/CD (GitHub Actions) — `rules/generative/platform/ci-cd/providers/github-actions.md` (naming)

## Host-Specific Terms
- **VertxEventDefinition**: Annotation describing an event bus address and options discovered at startup for consumer/publisher binding.
- **VertxEventRegistry**: Runtime registry that collects annotated consumer/publisher definitions, used for Guice bindings and codec setup.
- **VertxEventPublisher**: Generic publisher wrapper that resolves codecs and sends/publishes messages on Vert.x event bus.
- **CodecRegistry**: Utility to create/register codecs per message type to avoid duplicate Vert.x codec registration.
- **VertXPreStartup**: GuicedEE lifecycle hook that boots Vert.x, scans event definitions, registers codecs/consumers.
- **VertXModule**: Guice `PrivateModule` exposing Vert.x, publishers, and consumer bindings discovered at runtime.
- **CRTP fluent APIs**: Prefer chainable methods returning `(T)this`; avoid Lombok `@Builder`.

## Interpretation Guidance
- When a term conflicts, defer to the most specific topic glossary above.
- Use CRTP-aligned naming for fluent APIs; avoid introducing Builder semantics.
- Treat “Vert.x event” terminology consistently with Vert.x 5 rules and GuicedEE function guides.
