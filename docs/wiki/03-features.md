# 03 — Features

Catalog of what's working, by area. Use this as the truth source when writing the Play Store description.

---

## Grid

- Hexagonal layout with axial (q,r) coordinates and a spiral coordinate generator.
- **Smart placement**: most-used app at center (ring 0); 18 most recently used apps fill rings 1–2; remaining apps grouped by **dominant icon color** in outer rings.
- 6-bucket color sort: gray → red → yellow → green → cyan → blue → violet (bucket 0–5; grayscale folds to bucket 0).
- Pan and scroll with bounds clamping.
- Radial pop-in animation on cold load; smooth `animateToOrigin()` recenters during search.
- Long-press context menu: **Hide**, **Uninstall**, **App info**.
- Tap-to-launch dispatches via `LauncherApps.startMainActivity()` (launches both APKs and pinned shortcuts/PWAs).

## Dock

- Customizable bottom dock — drag-to-reorder, drag-out-to-unpin.
- Scrollable when dock items exceed screen width.
- Inline animated **search field**: slides in over the dock; live label-substring filter (case-insensitive).
- **Live clock icon** for the system clock app — `LiveClockDrawable` renders hands procedurally and ticks each minute.
- Configurable position (top/bottom) and transparency via Settings.

## App handling

- Reactive list — registers both:
  - `PackageChangeBroadcastReceiver` (install / uninstall / replace, with `EXTRA_REPLACING` handling to avoid triple-reload on updates).
  - `LauncherApps.Callback` (Samsung Modes, work profile availability, package and shortcut changes).
- **Hide apps** — persisted in SharedPreferences; managed via `AppVisibilityActivity`.
- **PWA / "Add to Home Screen"** — `PinShortcutActivity` handles `ACTION_CONFIRM_PIN_SHORTCUT` from Chrome and other sources, so Chrome PWAs and arbitrary pinned shortcuts land on the grid.
- **Notification badges** — `NotificationListener` service tracks unread counts per package and surfaces them on icons.
- Per-app pinned positions persisted via `PositionTracker` (when the user manually rearranges).

## Widgets (Cycle 2)

- Add widgets via **Settings → Manage Widgets → Add Widget** (system widget picker).
- Widgets render as `AppWidgetHostView` siblings inside `hexGridContainer` and scroll in sync with the grid.
- **Move** — long-press, drag widget body, snaps to nearest hex center on release.
- **Resize** — long-press, drag from a corner zone; snaps to hex-radius increments; honors `minResizeWidth`/`maxResizeWidth` from `AppWidgetProviderInfo`.
- **Persistence** — widget entries stored as JSON under `KEY_WIDGETS` in SharedPreferences; restored on every cold start.
- **Cleanup** — uninstalled widget providers (where `getAppWidgetInfo()` returns null) are pruned automatically on next startup.
- **Occupancy routing** — `HexGridCalculator.generateSpiralCoordinates()` skips widget-occupied cells so app icons flow around widgets without overlap.
- **Search behavior** — toggle in Settings: hide widgets while a search query is active (default), or keep them visible.

## Theming

- Material You dynamic colors on Android 12+ via `DynamicColors.applyToActivitiesIfAvailable()`.
- Adjustable transparency for grid background, dock, and search overlay.
- Custom `Theme.HexGridLauncher` (NoActionBar, transparent status bar) and `Theme.HexGridLauncher.Dialog` for `PinShortcutActivity`.

## Settings

- Adjustable grid radius / hex size.
- Dock position, transparency.
- Hidden apps manager.
- **Settings export / import** — full JSON snapshot via `SettingsExporter` (includes hidden apps and widget JSON; usage stats not included).
- "Show widgets during search" toggle.
- "Set as default launcher" entry point.

## Default launcher

- `MainActivity` declares `category.HOME + DEFAULT + LAUNCHER` with `launchMode="singleTask"` and `stateNotNeeded="true"` (rotation safe).
- Standard ROLE_HOME flow on Android 10+.

---

## What's intentionally **not** in v1

| Feature | Reason |
|---------|--------|
| Compose migration | View-based works; would slow ship |
| Icon packs | Material You + adaptive icons are enough for 1.0 |
| Cloud sync of settings | JSON export/import covers it |
| Cross-device widget restore | `appWidgetId` is device-local — Android limitation |
| Long-press grid → "Add Widget" | Settings entry ships first; deferred to v1.x |
| Translations | Strings exist; locales come post-1.0 |
