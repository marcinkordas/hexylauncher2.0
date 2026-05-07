# 06 — References

Every authoritative source the wiki summarizes from. If something here ever conflicts with the wiki, the source wins — update the wiki.

---

## In-repo specs and plans

Cycle 1 — bugfixes (PWA pinning, reactive package changes, search center):

- Spec: [`docs/superpowers/specs/2026-03-17-cycle1-bugfixes-design.md`](../superpowers/specs/2026-03-17-cycle1-bugfixes-design.md)
- Plan: [`docs/superpowers/plans/2026-03-17-cycle1-bugfixes.md`](../superpowers/plans/2026-03-17-cycle1-bugfixes.md)

Cycle 2 — widget support (`AppWidgetHost`, FrameLayout overlay, move/resize):

- Spec: [`docs/superpowers/specs/2026-03-17-cycle2-widgets-design.md`](../superpowers/specs/2026-03-17-cycle2-widgets-design.md)
- Plan: [`docs/superpowers/plans/2026-03-17-cycle2-widgets.md`](../superpowers/plans/2026-03-17-cycle2-widgets.md)

---

## In-repo top-level docs

- [`README.md`](../../README.md) — public-facing project README. Has placeholder URLs (`yourusername`, `yourname`) to clean up before publishing.
- [`TASK_LIST.md`](../../TASK_LIST.md) — original Gemini-Flash phased build list. Phases 1–6 ✅, Phase 7 (runtime/UI tests) still ⬜.
- [`DEVELOPMENT_SPEC.md`](../../DEVELOPMENT_SPEC.md) — original design spec referenced from TASK_LIST. Authoritative for the original module-by-module class designs.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — contribution guide.
- [`LICENSE`](../../LICENSE) — GPLv3.

---

## Out-of-repo: Gemini Antigravity brain

Path: `C:\Users\mckar\.gemini\antigravity\brain\53a75261-bdcb-4ef0-b779-bafe4622008a\`

Surviving artifacts from the previous AI-assisted development cycle. Useful for: logo concept art, the original phased implementation plan, and the older walkthrough.

Notable files:

| File | What it is |
|------|------------|
| `ai_design_prompts.md` | Prompts used to generate UI mockups + logo concepts |
| `implementation_plan.md` | Earlier phased plan (superseded by `TASK_LIST.md` and `docs/superpowers/`) |
| `task.md` | Per-task brief (early scaffolding cycle) |
| `walkthrough.md` | Walkthrough of the v1 build (early state) |
| `logo_concept_material_*.png` | Material You styled launcher icon concept |
| `logo_concept_neon_*.png` | Neon styled launcher icon concept |
| `ui_mockup_dark_glass_*.png` | Dark glass UI mockup |
| `ui_mockup_light_material_*.png` | Light Material UI mockup |
| `media__*.jpg` | Misc reference imagery |

These are **not** authoritative for current code state — they're snapshots from the early Gemini-driven phase. Use them as inspiration / asset source only.

---

## Source-of-truth files (current code)

For the current architecture, read the source directly:

| Concern | File |
|---------|------|
| Activity orchestration | [`MainActivity.kt`](../../app/src/main/java/com/hexgrid/launcher/MainActivity.kt) |
| Grid drawing + gestures | [`HexagonalGridView.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt) |
| Dock + search + live clock | [`DockView.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/DockView.kt) |
| App list state | [`LauncherViewModel.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/LauncherViewModel.kt) |
| App enumeration + launch | [`AppRepository.kt`](../../app/src/main/java/com/hexgrid/launcher/data/AppRepository.kt) |
| Color bucket extraction | [`ColorExtractor.kt`](../../app/src/main/java/com/hexgrid/launcher/util/ColorExtractor.kt) |
| Hex math | [`HexCoordinate.kt`](../../app/src/main/java/com/hexgrid/launcher/domain/HexCoordinate.kt), [`HexGridCalculator.kt`](../../app/src/main/java/com/hexgrid/launcher/domain/HexGridCalculator.kt) |
| Sort order | [`AppSorter.kt`](../../app/src/main/java/com/hexgrid/launcher/domain/AppSorter.kt) |
| PWA pinning | [`PinShortcutActivity.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/PinShortcutActivity.kt) |
| Widgets | [`widget/`](../../app/src/main/java/com/hexgrid/launcher/widget/) |
| Settings | [`SettingsActivity.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/SettingsActivity.kt), [`SettingsFragment.kt`](../../app/src/main/java/com/hexgrid/launcher/ui/SettingsFragment.kt), [`SettingsManager.kt`](../../app/src/main/java/com/hexgrid/launcher/util/SettingsManager.kt), [`SettingsExporter.kt`](../../app/src/main/java/com/hexgrid/launcher/util/SettingsExporter.kt) |
| Manifest | [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) |
| Build config | [`app/build.gradle.kts`](../../app/build.gradle.kts), [`build.gradle.kts`](../../build.gradle.kts) |

---

## External references

- Android `LauncherApps` API: <https://developer.android.com/reference/android/content/pm/LauncherApps>
- `ShortcutManager.requestPinShortcut`: <https://developer.android.com/reference/android/content/pm/ShortcutManager#requestPinShortcut>
- `AppWidgetHost` widget hosting guide: <https://developer.android.com/develop/ui/views/appwidgets/host>
- Play Console — App Signing: <https://support.google.com/googleplay/android-developer/answer/9842756>
- Play Console — Data safety: <https://support.google.com/googleplay/android-developer/answer/10787469>
- ROLE_HOME: <https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME>

---

## Memory entries (project-scoped)

Stored at `C:\Users\mckar\.claude\projects\c--Users-mckar-Documents-Projekty-HexGrid-Launcher\memory\`:

- `project_hexgrid_overview.md` — overall project status snapshot.
- `feedback_token_usage.md` — warn before high/fast token usage.
- `feedback_no_svg_gen.md` — don't auto-generate SVG logos.

Update memory when long-lived facts change. The wiki is for humans; memory is for the next Claude Code session.
