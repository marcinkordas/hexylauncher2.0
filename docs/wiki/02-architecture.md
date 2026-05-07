# 02 — Architecture

Kotlin, View-based, MVVM-lite. Single application module `app/`. Package root `com.hexgrid.launcher`.

---

## Package layout

```
com.hexgrid.launcher
├── HexGridLauncherApp.kt          Application class
├── MainActivity.kt                Home screen — hosts grid + dock + widget container
├── data/
│   ├── AppInfo.kt                 Data class: pkg, label, icon, color, bucket, usage, shortcut flags
│   ├── AppRepository.kt           loadInstalledApps(), launchApp(), invalidateCache()
│   ├── UsageStatsHelper.kt        UsageStatsManager wrapper + permission check
│   └── UsageTracker.kt            In-app launch tracking (boosts usageCount/lastUsedTimestamp)
├── domain/
│   ├── HexCoordinate.kt           Axial (q,r) hex math; ring(), neighbors()
│   ├── HexGridCalculator.kt       Spiral generation, hex↔pixel conversion, occupiedCells exclusion
│   └── AppSorter.kt               Sort: most-used → center, recents → ring 1-2, color buckets → outer
├── service/
│   └── NotificationListener.kt    Per-app unread badges (NotificationListenerService)
├── ui/
│   ├── HexagonalGridView.kt       Custom View — onDraw renders hexagons, gestures pan/scroll/tap/long-press
│   ├── DockView.kt                Bottom dock — drag-reorder, drag-out-unpin, inline search, live clock
│   ├── LiveClockDrawable.kt       Procedural clock face for the Clock app icon
│   ├── LauncherViewModel.kt       _apps / _allApps LiveData, hidden + unavailable filters, _currentQuery
│   ├── PinShortcutActivity.kt     Dialog for ACTION_CONFIRM_PIN_SHORTCUT (PWAs / Chrome)
│   ├── SettingsActivity.kt        Preference screen wrapper
│   ├── SettingsFragment.kt        PreferenceFragmentCompat
│   ├── AppVisibilityActivity.kt   Hidden-apps manager
│   └── WidgetManagementActivity.kt  List + add + remove placed widgets
├── util/
│   ├── ColorExtractor.kt          Palette → dominant color → 0-5 color bucket (gray/red/yellow/green/cyan/blue/violet)
│   ├── PositionTracker.kt         Persists per-app pinned positions (when user reorders)
│   ├── SettingsManager.kt         SharedPreferences keys + getters/setters
│   └── SettingsExporter.kt        JSON export/import of all settings + hidden apps + widgets
└── widget/
    ├── WidgetEntry.kt             Data class: widgetId, appWidgetId, centerHexQ/R, widthPx/heightPx
    ├── WidgetHost.kt              AppWidgetHost wrapper, hardcoded host ID 1024
    ├── WidgetStore.kt             SharedPreferences JSON persistence
    └── WidgetManager.kt           Orchestrates add/remove/move/resize, scroll sync, occupiedCells()
```

---

## Data flow

### App list lifecycle

```
MainActivity.onCreate()
  └─ register: PackageChangeBroadcastReceiver  (secondary)
  └─ register: LauncherApps.Callback           (primary — Samsung Modes / work profile)
  └─ viewModel.loadApps()
       └─ AppRepository.loadInstalledApps()
            ├─ PackageManager.queryIntentActivities(MAIN+LAUNCHER)
            └─ ShortcutManager.getShortcuts(FLAG_MATCH_PINNED)   ← PWAs land here
       └─ AppSorter.sortApps(visible, context, occupiedCells)
       └─ _apps.value = sorted

System event (install/uninstall/mode change/PWA pin)
  └─ Receiver or Callback fires
       └─ viewModel.reloadApps()  → repo.invalidateCache() + loadApps()
       └─ _currentQuery preserved across reloads
```

### Search filter

```
DockView search field text changes
  └─ MainActivity's onSearchTextChanged callback
       └─ viewModel.filterApps(query)
            └─ _currentQuery = query
            └─ updateAppList(allInstalledApps)
                 ├─ filter by label.contains(query, ignoreCase)
                 ├─ subtract _hiddenApps + _unavailableApps
                 └─ AppSorter.sortApps(...)
       └─ _apps observer fires
            └─ binding.hexGrid.setApps(apps, centerOnChange = currentQuery.isNotBlank())
                 └─ if centerOnChange: animateToOrigin (no radial pop-in)
                 └─ else: startRadialAnimation
```

### Widget lifecycle

```
MainActivity.onCreate()
  └─ widgetHost = WidgetHost(context, hostId = 1024)
  └─ widgetStore = WidgetStore(context)
  └─ widgetManager = WidgetManager(context, host, store, hexGridContainer, ...)
  └─ widgetManager.restoreWidgets()
       └─ for each entry in store: createHostView, attach to FrameLayout, sync scroll

MainActivity.onStart() → widgetManager.startListening()  (AppWidgetHost active)
MainActivity.onStop()  → widgetManager.stopListening()

User flow (Settings → Manage Widgets → Add):
  WidgetManagementActivity
    └─ allocateAppWidgetId
    └─ ACTION_APPWIDGET_PICK launcher
    └─ bindAppWidgetIdIfAllowed (or ACTION_APPWIDGET_BIND fallback)
    └─ returns to MainActivity with EXTRA_PLACEMENT_WIDGET_ID
    └─ HexagonalGridView placement mode → user taps target hex
    └─ widgetManager.confirmPlacement(appWidgetId, centerHex)
         └─ store.add(entry) + create AppWidgetHostView + attach
         └─ HexagonalGridView occupiedCells() updated → spiral re-routes icons
```

---

## Key invariants

- `AppRepository.cachedApps` is invalidated on any package/shortcut/availability change. Cycle 1 enforces this via dual listeners.
- `_currentQuery` is reapplied after every reload so search state survives install/uninstall.
- Widget views are siblings of `HexagonalGridView` inside `hexGridContainer` (FrameLayout) — they scroll in sync via `widgetManager.syncScroll(offsetX, offsetY)`.
- `AppWidgetHost` host ID is **hardcoded to 1024**. Don't change — orphaned `appWidgetId`s become unrecoverable if the host ID drifts.
- `HexGridCalculator.generateSpiralCoordinates(maxRings, occupiedCells)` excludes widget-occupied cells so app icons flow around widgets.

---

## Tests (current)

Unit tests (JVM, JUnit4 + Mockito + Robolectric):

- `domain/AppSorterTest.kt` (legacy `com.hexy.launcher` path)
- `domain/HexCoordinateTest.kt` (legacy path)
- `domain/HexGridCalculatorOccupiedTest.kt` — Cycle 2
- `ui/AppFilterLogicTest.kt` — Cycle 1
- `widget/WidgetStoreTest.kt` — Cycle 2
- `util/SettingsExporterTest.kt` (legacy path)
- `PackageChangeBroadcastReceiverTest.kt` — Cycle 1

Instrumented tests: scaffolding present in `app/src/androidTest/`, dependencies in build.gradle, **no test classes written yet**. Marked manual in TASK_LIST Phase 7.
