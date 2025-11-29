# PROMPT_REFERENCE — Guiced-Vert.x

Use this file to seed future AI prompts. Load alongside RULES.md, PACT.md, GUIDES.md, IMPLEMENTATION.md, and docs/architecture/*.

## Selected Stacks & Policies
- Java 25 LTS; Maven build.
- Vert.x 5; GuicedEE Client; Mutiny.
- Fluent API: CRTP (no Builder).
- Logging (log4j2); JSpecify.
- CI: GitHub Actions (planned).
- Forward-only + Document Modularity enforced.
- Rules location for this library: rules/generative/backend/guicedee/vertx (topic index + modular rules).
- Stage gates: documentation-first; approvals waived for this run (blanket approval noted in PROMPT library inputs).

## Glossary Sources
- Topic-first: see GLOSSARY.md for links to Java 25, CRTP, Vert.x, GuicedEE, Logging, JSpecify, CI.
- Library topic glossary lives in rules/generative/backend/guicedee/vertx/GLOSSARY.md (authoritative for this bridge).

## Architecture Artifacts
- docs/architecture/README.md (index)
- C4: ./c4-context.md, ./c4-container.md, ./c4-component-runtime.md
- Sequences: ./sequence-startup.md, ./sequence-publish-consume.md
- ERD: ./erd-event-model.md

## Cross-Links
- PACT.md ↔ RULES.md ↔ GUIDES.md ↔ IMPLEMENTATION.md ↔ GLOSSARY.md ↔ docs/architecture/*
- Rules repository: `rules/` submodule; host docs stay outside it.
- Topic rules index (service/framework) — rules/generative/backend/guicedee/vertx/README.md
