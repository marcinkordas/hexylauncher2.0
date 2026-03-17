# HexGrid Launcher — Cycle 2: Widget Support Design Spec

## Overview

Embed Android app widgets in the hexagonal grid. Widgets replace clusters of hexagons, scroll with the grid, and are free-form resizable with snap-to-hex boundaries.

---

## 1. Architecture

### Key constraint: `HexagonalGridView` extends `View`, not `ViewGroup`

`AppWidgetHostView` is a `View` that must be attached to a `ViewGroup`. Since `HexagonalGridView` extends `View`, it cannot host children directly. The solution: wrap both in a `FrameLayout` container in the activity layout.

```
FrameLayout (id: hexGridContainer)
  ├─ HexagonalGridView (draws hex cells + app icons via onDraw)
  └─ AppWidgetHostView × N (positioned absolutely, scrolled in sync)
```

`MainActivity` manages widget child views in the `FrameLayout`. `HexagonalGridView` exposes its scroll offset so widget views can be positioned in sync. Widget views are translated by the same `offsetX`/`offsetY` as the grid.

### Components

**`WidgetHost`** — wraps `AppWidgetHost` with a **hardcoded host ID** (constant `1024`). A hardcoded ID avoids orphaned widgets if SharedPreferences are cleared. Allocates widget IDs, creates `AppWidgetHostView` instances.

- `startListening()` called in `MainActivity.onStart()`
- `stopListening()` called in `MainActivity.onStop()`

This differs from the broadcast receivers (registered in `onCreate`/`onDestroy`) because `AppWidgetHost` requires active listening only while the UI is visible.

**`WidgetStore`** — persistence via SharedPreferences. Widget entries stored as a JSON string under `KEY_WIDGETS`. Each entry:
```json
{
  "widgetId": 1,
  "appWidgetId": 42,
  "centerHexQ": 2,
  "centerHexR": -1,
  "widthPx": 400,
  "heightPx": 200
}
```
Note: `SettingsExporter` stores this as a raw string key (add to `STRING_KEYS` list).

**`WidgetManager`** — orchestrator. Handles add/remove/resize/move flows. Bridges `WidgetHost`, `WidgetStore`, and `MainActivity` (for widget view management).

### Data flow

```
Add widget:
  User triggers add → WidgetManager.requestAdd()
    → AppWidgetHost.allocateAppWidgetId()
    → system widget picker (ACTION_APPWIDGET_PICK) via ActivityResultLauncher
    → bind result (bindAppWidgetIdIfAllowed / ACTION_APPWIDGET_BIND)
    → enter placement mode (grid highlights valid areas)
    → user taps hex cell → WidgetStore.save(entry)
    → MainActivity adds AppWidgetHostView to hexGridContainer FrameLayout

On startup:
  MainActivity.onCreate()
    → WidgetStore.loadAll()
    → for each entry: WidgetHost.createView(appWidgetId)
    → add each to hexGridContainer, positioned at hex coords
    → invalid entries (getAppWidgetInfo() returns null) cleaned up
```

### Manifest changes

- Add `<uses-permission android:name="android.permission.BIND_APPWIDGET" />` — required for `bindAppWidgetIdIfAllowed()`. Granted to the default launcher via `ROLE_HOME`.
- Register `WidgetManagementActivity`

---

## 2. Widget Placement & Resizing

### Adding a widget

1. User triggers add via one of three entry points:
   - **Settings → "Manage Widgets" → "Add Widget"** (must-have, ships first)
   - Long-press empty grid area → "Add Widget" (secondary, deferred)
   - Long-press hex cell → context menu → "Replace with Widget" (secondary, deferred)
2. `WidgetManager` calls `AppWidgetHost.allocateAppWidgetId()`
3. Launches system widget picker via `ActivityResultLauncher` registered in `WidgetManagementActivity` (for Settings flow) or `MainActivity` (for long-press flows)
4. On result, binds with `AppWidgetManager.bindAppWidgetIdIfAllowed()`. If denied, launches `ACTION_APPWIDGET_BIND` via another `ActivityResultLauncher`.
5. Widget gets initial size from `AppWidgetProviderInfo.minWidth × minHeight`
6. Grid enters **placement mode**:
   - Visual indicator: semi-transparent widget preview follows the user's finger/tap position
   - Valid hex positions highlighted (positions where the widget fits without overlapping existing widgets)
   - User taps to confirm position
   - Back button / tap outside grid: cancels placement, deallocates widget ID
7. `AppWidgetHostView` added to `hexGridContainer` FrameLayout

### Free-form resize with snap

- Long-press on widget → enters resize mode (drag handles appear on edges/corners)
- Dragging resizes the widget. Snaps to hex radius increments (`hexRadius` px steps).
- Minimum size enforced: `AppWidgetProviderInfo.minWidth × minHeight`
- Maximum size enforced: `AppWidgetProviderInfo.maxResizeWidth × maxResizeHeight` (falls back to screen dimensions if unspecified)
- On release: `WidgetStore` saves new dimensions, calls `updateAppWidgetSize(Bundle, List<SizeF>)` (API 31+) with fallback to the four-int overload on older API levels

### Moving a widget

- Long-press on widget → drag the widget body (not resize handles) → reposition
- Snaps to hex center positions on release
- `WidgetStore` updates `centerHexQ`/`centerHexR`

### Hex cell occupation

- `HexagonalGridView` maintains a `Set<HexCoordinate>` of cells occupied by widgets
- Occupied set is computed from each widget's center hex + size (bounding rectangle mapped to hex coordinates using `HexGridCalculator.pixelToHex()`)
- `HexGridCalculator.generateSpiralCoordinates()` accepts an optional `occupiedCells: Set<HexCoordinate>` parameter and skips those positions — app icons flow around widgets naturally
- When a widget is moved or resized, the occupied set is recomputed and `setApps()` is called to re-lay out icons

---

## 3. Settings & Search Behavior

### Manage Widgets screen

- New `WidgetManagementActivity` — accessed from Settings → "Manage Widgets" button
- Lists all placed widgets: widget name + provider icon + "Remove" button
- "Add Widget" button at the top → launches widget picker → returns to this activity → then opens `MainActivity` in placement mode (via intent extra)
- Removing a widget: calls `WidgetManager.remove(widgetId)` → `AppWidgetHost.deleteAppWidgetId()` → `WidgetStore.remove()` → widget view removed from `hexGridContainer`

### Show widgets during search (setting)

- New toggle: `SettingsManager.getShowWidgetsDuringSearch()` — default `false`
- `LauncherViewModel` exposes `currentQuery` (already exists). `MainActivity` observes it to control widget view alpha.
- When `false`: widget views animate alpha to 0 when `currentQuery.isNotBlank()`, animate back to 1 when search clears
- When `true`: widgets remain visible during search

### Widget persistence

- `WidgetStore` reads/writes `KEY_WIDGETS` in SharedPreferences as a JSON string
- `SettingsExporter` includes `KEY_WIDGETS` in its `STRING_KEYS` list for export/import. Cross-device import limitation: `appWidgetId` values are device-specific and won't survive — documented as known limitation.
- On `MainActivity.onCreate()`: load all entries, recreate `AppWidgetHostView` for each via `WidgetHost.createView()`
- Invalid entries (`AppWidgetManager.getAppWidgetInfo()` returns null) cleaned up automatically

---

## 4. Edge Cases

| Scenario | Behavior |
|----------|----------|
| Widget provider uninstalled | `getAppWidgetInfo()` returns null → remove from store + grid on next startup |
| Widget crashes | `AppWidgetHostView` shows Android's default error view |
| Screen rotation | `MainActivity` has `stateNotNeeded="true"` — activity recreated. `onCreate` restores widgets from `WidgetStore`. `AppWidgetHost` re-creates views. |
| Widget overlaps dock | Dock is a separate view layer; widgets scroll under it naturally |
| No widgets added | Zero overhead — no child views, empty occupied set, spiral generation unchanged |
| User cancels placement | Allocated `appWidgetId` is deallocated via `AppWidgetHost.deleteAppWidgetId()` |
| Placement overlaps existing widget | Occupied positions are highlighted as invalid; tap is rejected |
| Export/import settings | Widget JSON exported as string. Import on different device: widget IDs invalid → cleaned up on startup |

---

## 5. Files Changed Summary

| File | Changes |
|------|---------|
| `widget/WidgetHost.kt` | **New** — `AppWidgetHost` wrapper, hardcoded host ID 1024, ID allocation, view creation |
| `widget/WidgetStore.kt` | **New** — SharedPreferences JSON persistence for widget entries |
| `widget/WidgetManager.kt` | **New** — orchestrator for add/remove/resize/move flows |
| `ui/WidgetManagementActivity.kt` | **New** — Settings screen to list/add/remove widgets |
| `res/layout/activity_widget_management.xml` | **New** — widget list layout |
| `res/layout/activity_main.xml` | Wrap hex grid in `FrameLayout` (`hexGridContainer`) for widget overlay |
| `ui/HexagonalGridView.kt` | Expose `offsetX`/`offsetY` for widget sync; occupied hex set; placement mode overlay |
| `domain/HexGridCalculator.kt` | `generateSpiralCoordinates()` accepts optional `occupiedCells` exclusion set |
| `domain/AppSorter.kt` | Pass occupied cells through to `HexGridCalculator` |
| `MainActivity.kt` | `onStart()`/`onStop()` for widget host listening; widget view management in `hexGridContainer`; placement mode; search-fade logic |
| `ui/LauncherViewModel.kt` | Expose widget visibility bridge for search state (already exposes `currentQuery`) |
| `util/SettingsManager.kt` | Add `KEY_SHOW_WIDGETS_DURING_SEARCH`, `KEY_WIDGETS` |
| `util/SettingsExporter.kt` | Add `KEY_WIDGETS` to `STRING_KEYS` |
| `ui/SettingsActivity.kt` | Add "Manage Widgets" button, "Show widgets during search" toggle |
| `res/layout/activity_settings.xml` | Add widget button + toggle |
| `AndroidManifest.xml` | Add `BIND_APPWIDGET` permission; register `WidgetManagementActivity` |

## 6. Scope Boundaries

- **In scope**: Add/remove/resize/move widgets via Settings, persistence, search hide/show toggle, FrameLayout overlay architecture
- **Out of scope**: Widget configuration activities (handled by Android system), widget stacking/z-order, custom widget rendering
- **Deferred to secondary iteration**: Long-press grid entry points (A, C) — Settings entry point (B) ships first
- **Deferred to separate spec**: Gravity animation for filtered icons
