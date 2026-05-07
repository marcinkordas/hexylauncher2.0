# 01 — Overview

A modern Android launcher that lays out apps on a **hexagonal grid** sorted by dominant icon color, with a customizable dock, inline search, optional Material You theming, and (now) embedded app widgets.

---

## Origin

- Spiritual successor to **SwiftKey Hexy Launcher**, an experimental project from SwiftKey/Microsoft's labs that was discontinued.
- Original codebase started under the working title *Hexy Launcher* in `com.hexy.launcher`. It has been **rebranded to HexGrid Launcher / `com.hexgrid.launcher`** for legal compliance and to clear the way for an open-source GPLv3 release.
- Built from scratch in Kotlin + View-based UI (not Compose). Targets API 26+.
- Previous AI-assisted development used **Gemini Antigravity**; current work uses Claude Code's superpowers workflow (specs in `docs/superpowers/specs/`, plans in `docs/superpowers/plans/`).

---

## Goals (v1.0 ship target)

| Goal | Status |
|------|--------|
| Hexagonal app grid with color-bucket sorting | Done |
| Tap to launch, long-press menu (hide / uninstall / info) | Done |
| Most-used app pinned at center, recents in inner rings | Done |
| Customizable dock (drag-reorder, drag-out-to-unpin, scrollable) | Done |
| Inline animated search inside the dock | Done |
| Live-clock app icon | Done |
| Settings (transparency, grid size, dock position, hide apps, export/import) | Done |
| Material You dynamic theming (Android 12+) | Done |
| Reactive app list (install/uninstall/Samsung Modes/work profile) | Done — Cycle 1 |
| PWA "Add to Home Screen" support via `ACTION_CONFIRM_PIN_SHORTCUT` | Done — Cycle 1 |
| Search-filtered icons stay on screen | Done — Cycle 1 |
| Embedded app widgets — add/remove/move/resize, scroll in sync | Done — Cycle 2 |
| Notification badges on app icons | Done (NotificationListener service) |
| Set as default launcher (HOME intent filter) | Done |
| Unit tests for filter logic, widget store, hex grid math, app sorter | Done |
| Release-signed APK / AAB | **Not done** |
| Google Play listing assets | **Not done** |
| Privacy policy + Data Safety form | **Not done** |
| UI / instrumented tests for search, dock, widgets | Pending — TASK_LIST Phase 7 |

---

## Non-goals (explicitly deferred)

- Compose migration (View system is fine for v1).
- Theme/icon-pack support beyond Material You.
- Cloud sync of settings (export/import JSON is the v1 mechanism).
- Stack/group widgets, custom widget chrome.
- Cross-device widget restore (`appWidgetId` is device-local — documented limitation in Cycle 2 spec).
- Internationalization beyond English (string resources exist; translations come post-1.0).

---

## Distribution model

- **Free** on Google Play (no in-app purchases, no ads).
- **GPLv3** source on GitHub.
- Optional support links in README: Buy Me a Coffee / GitHub Sponsors / Patreon (placeholders to fill).
- Independent project — explicit disclaimer that it is not affiliated with SwiftKey or Microsoft (already in README).
