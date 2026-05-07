# Plaster — HexGrid Launcher Knowledge Base

> *Plaster* — Polish for *honeycomb*. The shape the launcher draws, and the way these notes are organized: small hex-shaped pages, each self-contained, linked together.

This is the working knowledge base for **HexGrid Launcher**: what's been built, what remains, and how to ship it free on Google Play.

It supersedes the scattered context across `TASK_LIST.md`, `DEVELOPMENT_SPEC.md`, `docs/superpowers/`, and the old Gemini brain dumps. Those are still authoritative for their narrow scope and are linked from [06-references.md](06-references.md).

---

## Index

| # | Page | What's inside |
|---|------|---------------|
| 01 | [Overview](01-overview.md) | What HexGrid is, where it came from, why it exists |
| 02 | [Architecture](02-architecture.md) | Code map, package layout, key classes, data flow |
| 03 | [Features](03-features.md) | Catalog of everything currently working |
| 04 | [Release Checklist](04-release-checklist.md) | Punch list to ship 1.0 free on Google Play |
| 05 | [Build & Signing](05-build-and-signing.md) | How to build debug/release + keystore + Play Store assets |
| 06 | [References](06-references.md) | Pointers to specs, plans, Gemini brain context, source files |

---

## TL;DR — what's left to ship

Functional code is complete. Cycle 1 (PWA/package-change/search-center bugfixes) and Cycle 2 (widget support) are merged into source. A debug APK builds.

Before Google Play release:

1. **Release signing keystore** — none configured, `signingConfig` block missing in `app/build.gradle.kts`.
2. **Final logo/icon assets** — user produces SVGs manually (memory: don't auto-generate).
3. **Privacy policy + Data Safety form** — Play Console requires both.
4. **Store listing assets** — screenshots, feature graphic, short/full description, content rating questionnaire.
5. **Real device QA** — TASK_LIST.md Phase 7 runtime checks, plus widget add/move/resize.
6. **README cleanup** — replace `yourusername`/`yourname` placeholders.

Full breakdown: [04-release-checklist.md](04-release-checklist.md).

---

## How to use this wiki

- Pages are short and bookmarkable — open one at a time.
- Updates: edit in-place. There's no separate changelog inside the wiki — git history is the changelog.
- Cross-links: prefer `[label](relative-path.md#anchor)` over absolute paths so the wiki is portable.
- New page: copy any existing page, keep the front matter rule (`# Title` then a one-line subtitle), and add it to the index above.
