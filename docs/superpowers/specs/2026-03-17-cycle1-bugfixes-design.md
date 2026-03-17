# HexGrid Launcher — Cycle 1: Bugfixes Design Spec

## Overview

Three bugs that break core launcher UX: stale app list after install/uninstall, missing PWA shortcut support, and filtered icons landing off-screen.

---

## 1. Reactive App List Refresh

### Problem

`AppRepository.cachedApps` never invalidates. No `BroadcastReceiver` for package changes. No `LauncherApps.Callback` for Samsung Modes / work profile availability changes. The launcher only reloads apps in `onResume()`, and even then returns stale cache.

### Solution: Dual listener architecture

`LauncherApps.Callback` is the **primary** mechanism. `BroadcastReceiver` is a **secondary** safety net.

Both are registered in `onCreate()` and unregistered in `onDestroy()` — this ensures events are caught even while the launcher is paused (user is in Play Store installing/uninstalling).

**A. PackageChangeBroadcastReceiver** (install/uninstall/update — secondary)
- Registered in `onCreate()`, unregistered in `onDestroy()`
- Listens for: `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED`
- Must check `intent.getBooleanExtra(EXTRA_REPLACING, false)` to avoid triple-reload on updates:
  - `ACTION_PACKAGE_REMOVED` with `EXTRA_REPLACING=true` → ignore (update in progress)
  - `ACTION_PACKAGE_ADDED` with `EXTRA_REPLACING=true` → treat as update (single reload)
  - `ACTION_PACKAGE_REMOVED` with `EXTRA_REPLACING=false` → real uninstall
  - `ACTION_PACKAGE_ADDED` with `EXTRA_REPLACING=false` → real install
- On event: `AppRepository.invalidateCache()` → `viewModel.reloadApps()`

**B. LauncherApps.Callback** (Samsung Modes / work profiles / availability — primary)
- Registered in `onCreate()`, unregistered in `onDestroy()`
- Key callbacks:
  - `onPackagesUnavailable(packageNames)` → `viewModel.markUnavailable(packages)` — immediately filters grid
  - `onPackagesAvailable(packageNames)` → `viewModel.markAvailable(packages)` — restores apps when mode changes
  - `onPackageAdded/Removed/Changed()` → `repo.invalidateCache()` + `viewModel.reloadApps()`
  - `onShortcutsChanged()` → `repo.invalidateCache()` + `viewModel.reloadApps()` (needed for PWA pin flow in section 2)

### Changes per file

**AppRepository.kt:**
- Add `fun invalidateCache()` that sets `cachedApps = null`

**LauncherViewModel.kt:**
- New `_unavailableApps: MutableSet<String>`
- New `_currentQuery: String = ""` — tracks active search query so reloads don't clobber filter state
- `updateAppList()` filters by: `_hiddenApps` + `_unavailableApps` + `_currentQuery` (all three always applied)
- `filterApps(query)` stores query in `_currentQuery` before filtering
- New `reloadApps()` method — invalidates cache, reloads, and re-applies `_currentQuery` filter
- New methods: `markUnavailable(packages: Array<String>)`, `markAvailable(packages: Array<String>)` — both re-apply `_currentQuery` after updating the set

**MainActivity.kt:**
- Register `PackageChangeBroadcastReceiver` in `onCreate()` / unregister in `onDestroy()`
- Register `LauncherApps.Callback` in `onCreate()` / unregister in `onDestroy()`
- Both trigger appropriate ViewModel methods
- Expose `AppRepository.invalidateCache()` to callbacks (via ViewModel accessor or direct reference)

### Data flow

```
System Event (install/uninstall/mode change)
    │
    ├─ BroadcastReceiver (secondary)
    │   └─ (check EXTRA_REPLACING) ──→ repo.invalidateCache() → viewModel.reloadApps()
    │
    └─ LauncherApps.Callback (primary)
        ├─ onPackagesUnavailable ──→ viewModel.markUnavailable(pkgs)
        ├─ onPackagesAvailable ──→ viewModel.markAvailable(pkgs)
        ├─ onPackageAdded/Removed/Changed ──→ repo.invalidateCache() → viewModel.reloadApps()
        └─ onShortcutsChanged ──→ repo.invalidateCache() → viewModel.reloadApps()
```

Note: `reloadApps()` always re-applies `_currentQuery`, so active search filter is preserved across reloads.

---

## 2. PWA Shortcut Pinning Support

### Problem

Chrome's "Add to Home Screen" calls `ShortcutManager.requestPinShortcut()`. The system looks for a default launcher that handles `ACTION_CONFIRM_PIN_SHORTCUT`. HexGrid Launcher doesn't declare this intent filter, so pin requests are silently dropped.

**Prerequisite:** `PinShortcutActivity` only receives intents when HexGrid is set as the default launcher. This is an inherent Android limitation — all launchers work this way.

### Solution: PinShortcutActivity

New minimal Activity that shows a confirmation dialog for pin requests.

**Flow:**
1. System sends `Intent` with `LauncherApps.EXTRA_PIN_ITEM_REQUEST` to `PinShortcutActivity`
2. `onCreate()` extracts `PinItemRequest` from intent extras
3. Shows dialog with shortcut name and icon: "Add [name] to HexGrid?"
4. User taps "Add" → `pinItemRequest.accept()` → system registers pinned shortcut
5. `LauncherApps.Callback.onShortcutsChanged()` fires (registered in section 1) → `reloadApps()` finds new shortcut via `FLAG_MATCH_PINNED`
6. User taps "Cancel" → Activity finishes, shortcut not pinned

**New file: `ui/PinShortcutActivity.kt`**

**AndroidManifest.xml addition:**
```xml
<activity
    android:name=".ui.PinShortcutActivity"
    android:theme="@style/Theme.HexGridLauncher.Dialog"
    android:exported="true">
    <intent-filter>
        <action android:name="android.content.pm.action.CONFIRM_PIN_SHORTCUT"/>
    </intent-filter>
</activity>
```

**Theme.HexGridLauncher.Dialog definition (themes.xml):**
```xml
<style name="Theme.HexGridLauncher.Dialog" parent="Theme.MaterialComponents.DayNight.Dialog">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:backgroundDimEnabled">true</item>
</style>
```

**Integration notes:**
- `AppRepository.loadInstalledApps()` already queries `FLAG_MATCH_PINNED` — zero changes in repo
- After `accept()`, `onShortcutsChanged()` callback (section 1) triggers reload automatically
- No dependency on BroadcastReceiver for this flow

**Edge cases:**
- User cancels → `accept()` not called, shortcut not added
- Missing `PinItemRequest` in intent → Activity finishes gracefully (no crash)
- API < 26 → not applicable (minSdk = 26)
- Not default launcher → system won't route intent here (expected behavior)

---

## 3. Center Grid on Search Filter

### Problem

In `HexagonalGridView.setApps()` — when `filterApps()` reduces the list, `AppSorter` generates a new spiral centered at (0,0), but `offsetX`/`offsetY` remain at the old scroll position. User sees empty screen and must manually find filtered icons.

### Solution: Conditional animateToOrigin() on filter

**HexagonalGridView.kt:**
- Add `centerOnChange` parameter to `setApps()`:
```kotlin
fun setApps(appList: List<AppInfo>, centerOnChange: Boolean = false) {
    if (apps != appList) {
        apps = appList
        hexPositions = calculator.generateSpiralCoordinates(...)
        updateScrollBounds()
        if (centerOnChange) {
            // Skip radial animation during search — user already sees the grid,
            // just smoothly scroll to show filtered results at center
            animateToOrigin()
        } else {
            startRadialAnimation()
        }
    }
}
```

When `centerOnChange = true`, we skip `startRadialAnimation()` (pop-in effect) and only do `animateToOrigin()`. This avoids visual conflict between two concurrent animations and feels cleaner — the user is actively searching, so hexagons popping in ring-by-ring would be distracting.

**MainActivity.kt:**
- Track filtering state explicitly instead of relying on `isInSearchMode()` timing:
```kotlin
// In setupDock():
dock.onSearchTextChanged = { query ->
    viewModel.filterApps(query)
}

// In setupGrid():
viewModel.apps.observe(this) { apps ->
    val isFiltering = viewModel.currentQuery.isNotBlank()
    binding.hexGrid.setApps(apps, centerOnChange = isFiltering)
}
```

This avoids the timing issue where `exitSearchMode()` sets `isSearchMode = false` before the text-clear callback fires. Instead, we check the ViewModel's `_currentQuery` directly — empty query = not filtering.

**Behavior:**
- User types in search → `filterApps("ch")` → `centerOnChange = true` → smooth scroll to center, no pop-in
- User clears search text (but still in search mode) → `filterApps("")` → `centerOnChange = false` (query is blank) → radial animation with full list, preserves scroll
- Normal reload (onResume, install/uninstall) → `centerOnChange = false` → preserves scroll position
- User exits search mode → `exitSearchMode()` clears text → same as "clears search text" above

---

## Files changed summary

| File | Changes |
|------|---------|
| `AppRepository.kt` | Add `invalidateCache()` method |
| `LauncherViewModel.kt` | Add `_unavailableApps`, `_currentQuery`, `reloadApps()`, `markUnavailable()`, `markAvailable()`, expose `currentQuery`, update `filterApps()` and `updateAppList()` |
| `MainActivity.kt` | Register BroadcastReceiver + LauncherApps.Callback in `onCreate()`/`onDestroy()`, update apps observer to use `viewModel.currentQuery` |
| `HexagonalGridView.kt` | Add `centerOnChange` param to `setApps()`, skip radial animation when centering |
| `PinShortcutActivity.kt` | **New file** — pin shortcut confirmation dialog |
| `AndroidManifest.xml` | Add PinShortcutActivity with intent filter |
| `themes.xml` | Add `Theme.HexGridLauncher.Dialog` style |

## Scope boundaries

- No widget support (Cycle 2)
- No UI style refresh (Cycle 3)
- No new settings UI — all changes are internal/automatic
- No tests in this cycle (separate cycle later)
