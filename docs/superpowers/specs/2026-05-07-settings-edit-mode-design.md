# HexGrid Launcher — Settings & Edit Mode Design Spec

Redesigns the Settings screen as an action-tile **Settings Hub** and adds a live-preview **Edit Mode** overlay for real-time grid customization. Edit Mode is a `FrameLayout`-based overlay on top of the existing `hexGridContainer`; Settings Hub replaces `SettingsActivity` entirely. SharedPreferences change listeners drive immediate visual feedback with no extra architecture.

---

## 1. Architecture

### Two surfaces

| Surface | Class | Entry | Exit |
|---|---|---|---|
| Edit Mode overlay | `EditModeOverlay` (FrameLayout) | Long-press empty grid / Hub hero tile | Done button / More button / back-press |
| Settings Hub | `SettingsHubActivity` | Gear icon in dock | Android back |

### Overlay position in view hierarchy

```
FrameLayout (id: hexGridContainer)
  ├─ HexagonalGridView          ← live grid, always present
  ├─ AppWidgetHostView × N      ← widgets (Cycle 2)
  └─ EditModeOverlay            ← added/removed dynamically by MainActivity
       ├─ active panel view (ShapePanel / StylePanel / OrderPanel)
       └─ toolbar pill (5 squircle buttons)
```

`EditModeOverlay` is attached to `hexGridContainer` via `addView()` in `enterEditMode()` and detached in `exitEditMode()`. It fills the container (`MATCH_PARENT` × `MATCH_PARENT`).

### Data flow

```
User changes a slider/toggle in a panel
  → SettingsManager.setXxx() writes SharedPreferences
  → OnSharedPreferenceChangeListener in MainActivity fires
  → MainActivity reads new value, calls refresh (invalidate / alpha / recreate)
  → HexagonalGridView redraws — live preview visible behind overlay
```

Settings Hub actions (Widgets, Manage Apps, etc.) launch existing Activities directly — no new intermediary layer.

---

## 2. Edit Mode UX

### Entry

- **Long-press on empty grid area**: `HexagonalGridView.onEmptyAreaLongPress` callback fires when the long-pressed coordinate maps to an unoccupied hex cell. `MainActivity` wires this callback to `enterEditMode()`. See §6 for hit-test spec.
- **Settings Hub hero tile "Customize layout"**: `SettingsHubActivity` starts `MainActivity` with intent extra `EXTRA_ENTER_EDIT_MODE = true`; `MainActivity.onNewIntent()` and `onResume()` both check for the extra and call `enterEditMode()`.

### Toolbar

Centered floating pill. Bottom edge anchored via `WindowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + 16dp`. On gesture-nav devices, additionally floor the offset at `getInsets(Type.tappableElement()).bottom + 8dp` so the pill never enters the OS gesture zone (bottom ~32dp). Read insets in `onApplyWindowInsets` and translate the pill's bottom margin accordingly. Background: `edit_toolbar_pill_bg.xml` (rounded rectangle, 28dp radius, surface-variant fill, 1dp stroke at outline color, 4dp elevation shadow).

| Position | Label | Icon |
|---|---|---|
| 1 | Shape | hex outline icon |
| 2 | Style | palette icon |
| 3 | Order | sort icon |
| 4 | More | dots-vertical icon |
| 5 | Done | checkmark icon |

Buttons are squircle-body, 56dp × 56dp minimum touch target, spaced 8dp apart inside the pill. Active mode button: filled primary-blue background. Inactive: transparent.

### Mode panels

One panel at a time slides up from just above the toolbar. Switching modes cross-fades the panel (150 ms alpha). Panel background: same squircle surface-variant card, 20dp radius, 4dp elevation.

**Shape panel** (`panel_shape.xml` / `ShapePanel.kt`):

| Control | Type | Key |
|---|---|---|
| Hex radius | Slider (50–150 dp) | `hex_radius` |
| Icon size | Slider (0.5–1.5×) | `icon_size_multiplier` |
| Icon padding | Slider (0–20 dp) | `icon_padding` |
| Outline width | Slider (0.5–4 dp) | `outline_width` |
| Corner radius | Slider (0–20 dp) | `corner_radius` |
| Hex orientation | Toggle chip (Pointy / Flat) | `hex_orientation` |

**Style panel** (`panel_style.xml` / `StylePanel.kt`):

| Control | Type | Key |
|---|---|---|
| Show outline | Switch | `show_outline` |
| Show labels | Switch | `show_labels` |
| Notification glow | Switch | `show_notification_glow` |
| Dark theme | Switch (deferred — see below) | `dark_theme` |
| Tile transparency | Slider (0–100) | `tile_transparency` |
| Dock transparency | Slider (0–100) | `dock_transparency` |
| Unified bucket colors | Switch | `unified_bucket_colors` |
| Shortcut icon shape | Radio chip (Square / Squircle / Circle) | `shortcut_icon_shape` |
| Dim status bar | Switch — label "Dim status bar (applied on exit)" | `dim_status_bar` |

> Note: `show_widgets_during_search` exists in `SettingsManager` but is **not** assigned to this panel — see §10.6 for placement decision (recommended: behind the Widgets tile in Settings Hub).

**Order panel** (`panel_order.xml` / `OrderPanel.kt`):

| Control | Type | Key |
|---|---|---|
| Sort order | Radio chip (Name / Frequency / Time / Notifications) | `sort_order` |
| Search position | Radio chip (None / Top / Bottom) | `search_position` |
| Voice search | Switch | `search_with_mic` |

### Exit paths

| Trigger | Behavior |
|---|---|
| **Done** button | `exitEditMode()` — removes overlay, no navigation |
| **More** button | `exitEditMode()` then starts `SettingsHubActivity` via Intent |
| Back gesture / `OnBackPressedCallback` | `exitEditMode()` if overlay is attached |
| Tap outside panel and toolbar | No dismiss — user must use Done or back; prevents accidental exits while adjusting sliders |

**Persist semantics (no rollback)**: every control interaction writes to SharedPreferences immediately and is reflected in the live preview. There is no "Cancel" path — Done, More, and back-press are all "commit and exit". A user who wants to revert must re-enter Edit Mode and re-adjust. Document this clearly in any future user-facing onboarding; do not surprise users with hidden auto-save semantics.

### Long-press on icon while overlay is attached

Icon long-press in the underlying grid is suppressed (the overlay intercepts touches — see §10). To prevent silent failure of an ingrained gesture, `EditModeOverlay` detects long-press over the area where an icon would be hit and responds with: (a) a `HapticFeedbackConstants.LONG_PRESS` haptic tick, and (b) a transient bottom-anchored chip ("Exit edit mode to manage apps · Done") that auto-dismisses after 2 seconds. The chip is a `MaterialButton` with text and a checkmark trailing icon; tapping it triggers `exitEditMode()`. This affordance is the only feedback for blocked icon interactions in Edit Mode.

### Empty/error state for panels

If a mode panel fails to inflate (extreme low-memory edge case), the toolbar remains visible and the panel area shows a single-line `TextView`: "Couldn't load controls. Tap Done to exit." This prevents a stuck overlay with no recovery path.

### Animations

- **Toolbar entry**: translate Y from +80dp to 0, alpha 0→1, 200 ms ease-out. Exit: reverse.
- **Panel entry/exit**: cross-fade 150 ms when switching modes. Slide-up on first open (translate Y from +40dp, 150 ms).
- **Interruptibility**: animations must be interruptible. `EditModeOverlay` holds a single `currentPanelAnimator: Animator?` reference; before starting any new panel transition, call `currentPanelAnimator?.cancel()`. Same rule for the toolbar entry/exit animator. Rapid mode switching must not stack ghost panels on screen.
- No further animation work in this spec.

### Accessibility

- Every toolbar squircle button has `android:contentDescription` set to its label ("Shape", "Style", "Order", "More", "Done"). Icons alone are not sufficient.
- Active toolbar button must have `selected = true` so TalkBack announces selection state.
- Each slider in a panel has a persistent text label (rendered as a `TextView` above the slider, not placeholder text). The slider's `contentDescription` is updated on every value change to include the unit (e.g., `"Hex radius: 80dp"`). Use `ViewCompat.setAccessibilityDelegate` or override `dispatchPopulateAccessibilityEvent`.
- D-pad / switch-control increment/decrement on sliders must work — verify standard `Slider`/`SeekBar` behavior is not overridden.
- Focus order through the toolbar pill follows visual left-to-right (no `importantForAccessibility="no"` on toolbar children).
- Hero tile gradient must verify `colorOnPrimary` text contrast ≥ 4.5:1 against the lightest gradient stop in both light and dark themes; pick a gradient mid-stop that maintains contrast or fall back to solid `colorPrimary` if the dark-theme gradient drops below threshold.
- Badge dot is decorative — it embeds its meaning into the parent tile's `contentDescription` (e.g., "Permissions — action needed"). Badge itself is `importantForAccessibility="no"`.

---

## 3. Settings Hub UX

`SettingsHubActivity` replaces `SettingsActivity`. Vertical `ScrollView` with two sections.

### Hero tile

Full-width, height ~160dp. Background: primary-blue gradient drawable. Hex slot icon (see §4) centered. Label: "Customize layout". Tapping launches `MainActivity` with `EXTRA_ENTER_EDIT_MODE = true` and flags `Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP` so the existing `MainActivity` instance is brought to front (avoids stacking a second `MainActivity` above `SettingsHubActivity`).

Back-stack rule: after entering Edit Mode via this tile, back-press exits Edit Mode and returns to the launcher home (not to the hub). `SettingsHubActivity` is finished by the `CLEAR_TOP` flag; verify in QA that the back-stack contains only `MainActivity` once Edit Mode is dismissed.

### Action tile grid

3-column `GridLayout` (or `RecyclerView` with `GridLayoutManager`), each tile 1:1 aspect ratio, minimum 88dp side, 8dp gaps.

| Tile | Label | Target | Badge condition |
|---|---|---|---|
| 1 | Widgets | `WidgetManagementActivity` | — |
| 2 | Manage Apps | `AppVisibilityActivity` | — |
| 3 | Permissions | System app settings intent | Notification access not granted |
| 4 | Default Launcher | System role-home settings intent | App is not currently default launcher |
| 5 | Export | `SettingsExporter.export()` inline | — |
| 6 | Import | `SettingsExporter.import()` inline | — |

Badge: small red dot (8dp) at top-right corner of tile, decorative only — it is not a separate tap target. The whole tile is the touch target; badge interaction equals tile tap. `SettingsHubActivity.onResume()` re-evaluates badge conditions.

Badge re-evaluation only on `onResume` may miss the edge case where the user grants notification access via the system settings while the activity remains technically resumed. Acceptable for v1. If users report stale badges, add a supplementary `BroadcastReceiver` (`ACTION_NOTIFICATION_LISTENER_SETTINGS_CHANGED`) in a follow-up cycle.

---

## 4. Visual Language

### Tile drawable

"Squircle" is the design vocabulary used throughout this spec to describe the rounded-tile body of all big buttons (toolbar buttons, action tiles, hero tile). For v1 the **implementation is a rounded rectangle** with 20dp corner radius — this is the standard Material 3 tile chrome and reads as squircle-adjacent at the sizes used here. A true superellipse squircle would require a custom `Drawable` subclass with a `Path` superellipse formula and is deferred as future-work; do not attempt it in this cycle.

`tile_squircle_primary.xml` (hero tile) and `tile_squircle_secondary.xml` (action tiles):

```xml
<!-- secondary variant — action tiles -->
<shape android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="?attr/colorSurfaceVariant" />
    <stroke android:width="1dp" android:color="?attr/colorOutline" />
</shape>
```

Primary variant: replace `solid` with a `gradient` from `colorPrimary` to `colorPrimaryContainer` (angle 135°).

### Hex-slot inner motif

`hex_slot_filled.xml` — used on hero tile and toolbar Shape button background. Regular hexagon path, pointy-top, stroke only (no fill). Size ≈ 55% of the tile body. Stroke width 2dp, color `colorOnPrimary` (hero) or `colorPrimary` (action tiles).

`hex_slot_outline.xml` — same path, thinner stroke (1dp), used as a watermark on secondary tiles.

SVG `android:pathData` for a regular pointy-top hexagon, circumradius R, centered at the viewBox center. Vertex coordinates: `(cx, cy−R), (cx+R·√3/2, cy−R/2), (cx+R·√3/2, cy+R/2), (cx, cy+R), (cx−R·√3/2, cy+R/2), (cx−R·√3/2, cy−R/2)`.

For a 100×100 viewBox with R=46 (4dp padding for stroke):
```
M50,4 L89.83,27 L89.83,73 L50,96 L10.17,73 L10.17,27 Z
```
(Implementer: recompute if R changes. Don't trust the literal coordinates without running the formula.) Scale to 55% of the containing tile side in the `<vector>` `viewportWidth`/`viewportHeight`.

### Size tokens

| Element | Size |
|---|---|
| Toolbar button (squircle) | 56 × 56dp minimum |
| Action tile min side | 88dp |
| Hero tile height | 160dp |
| Tile corner radius | 20dp |
| Toolbar pill corner radius | 28dp |
| Hex inner motif | ~55% of tile side |

### Color tokens (existing theme)

| Role | Attribute |
|---|---|
| Action tile fill | `?attr/colorSurfaceVariant` |
| Action tile stroke | `?attr/colorOutline` |
| Hero gradient start | `?attr/colorPrimary` |
| Hero gradient end | `?attr/colorPrimaryContainer` |
| Active toolbar button bg | `?attr/colorPrimary` |
| Active toolbar button icon/label | `?attr/colorOnPrimary` (verify ≥ 3:1 contrast on bg in both themes) |
| Panel background | `?attr/colorSurface` |
| Badge dot | `@color/design_default_color_error` or `?attr/colorError` |

---

## 5. Live Preview Technique

`MainActivity.onCreate()` registers an `OnSharedPreferenceChangeListener`. `MainActivity.onDestroy()` unregisters it. No new interface required — integrates with existing `SettingsManager` without API changes.

```kotlin
private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    when (key) {
        "hex_radius", "icon_size_multiplier", "icon_padding",
        "outline_width", "corner_radius", "show_outline",
        "show_labels", "show_notification_glow",
        "unified_bucket_colors", "tile_transparency" -> {
            binding.hexGrid.invalidate()
        }
        "dock_transparency" -> dockView.invalidate()
        "dark_theme" -> {
            // Defer recreate() until Edit Mode exits — recreate() during edit
            // would destroy the overlay and eject the user mid-session.
            if (editModeOverlay == null) recreate()
            else pendingDarkThemeRecreate = true
        }
        "hex_orientation" -> recomputeGridAndInvalidate()
        "sort_order" -> reloadAppsAndInvalidate()
        "search_position" -> repositionSearchBar()
        "search_with_mic" -> updateSearchMicButton()
        "shortcut_icon_shape" -> {
            SettingsManager.setIconCacheDirty(this, true)
            binding.hexGrid.invalidate()
        }
        "dim_status_bar" -> {
            // Not previewable behind the overlay; apply immediately,
            // but the Style panel label warns the user it's not visible until exit.
            applyStatusBarDim()
        }
    }
}

// In exitEditMode():
//     if (pendingDarkThemeRecreate) { pendingDarkThemeRecreate = false; recreate() }
```

### Key → effect table

| Key | Refresh call |
|---|---|
| `hex_radius` | `hexGrid.invalidate()` — grid redraws with new spiral |
| `icon_size_multiplier` | `hexGrid.invalidate()` |
| `icon_padding` | `hexGrid.invalidate()` |
| `outline_width` | `hexGrid.invalidate()` |
| `corner_radius` | `hexGrid.invalidate()` |
| `show_outline` | `hexGrid.invalidate()` |
| `show_labels` | `hexGrid.invalidate()` |
| `show_notification_glow` | `hexGrid.invalidate()` |
| `unified_bucket_colors` | `hexGrid.invalidate()` |
| `tile_transparency` | `hexGrid.invalidate()` |
| `dock_transparency` | `dockView.invalidate()` |
| `dark_theme` | `recreate()` (deferred to `exitEditMode()` if overlay attached) |
| `hex_orientation` | recompute spiral + `hexGrid.invalidate()` |
| `sort_order` | reload app list + `hexGrid.setApps()` + `invalidate()` |
| `search_position` | reposition/show/hide search bar |
| `search_with_mic` | toggle mic button visibility in search bar |
| `shortcut_icon_shape` | set `KEY_ICON_CACHE_DIRTY`, `hexGrid.invalidate()` |
| `dim_status_bar` | `WindowInsetsController` flags |

---

## 6. Long-press Empty-area Gesture

`HexagonalGridView` already routes long-press to the app context menu when a cell containing an app icon is hit. This section specifies the new behavior for empty cells.

### Contract

Add to `HexagonalGridView`:
```kotlin
var onEmptyAreaLongPress: ((HexCoordinate) -> Unit)? = null
```

### Hit-test logic (in `onLongPress` gesture handler)

```
coord = pixelToHex(event.x, event.y)
if coord is in apps list             → existing context menu (unchanged)
else if coord is in widget occupied  → no-op (widget handles its own long-press internally; this branch is explicit, not silent)
else                                  → onEmptyAreaLongPress?.invoke(coord)
```

Icons still get their existing context menu (Hide / Uninstall / Info). Only a long-press that resolves to an unoccupied, non-widget hex fires the new callback. This must be explicit: do not swallow icon long-press.

### Wiring in MainActivity

```kotlin
binding.hexGrid.onEmptyAreaLongPress = { _ -> enterEditMode() }
```

The `HexCoordinate` parameter is passed to the lambda but not used by the initial implementation — reserved for future "add widget here" placement mode.

---

## 7. Files Changed

| File | Status | Change |
|---|---|---|
| `ui/EditModeOverlay.kt` | New | FrameLayout overlay; hosts toolbar pill and panel swap logic |
| `ui/edit/ShapePanel.kt` | New | Shape controls — sliders + orientation chip |
| `ui/edit/StylePanel.kt` | New | Style controls — switches + chip groups |
| `ui/edit/OrderPanel.kt` | New | Order controls — sort/search chips + mic switch |
| `ui/SettingsHubActivity.kt` | New | Replaces SettingsActivity; hero tile + 6 action tiles |
| `res/layout/overlay_edit_mode.xml` | New | Root layout for EditModeOverlay |
| `res/layout/panel_shape.xml` | New | Shape panel view |
| `res/layout/panel_style.xml` | New | Style panel view |
| `res/layout/panel_order.xml` | New | Order panel view |
| `res/layout/activity_settings_hub.xml` | New | Settings Hub layout (ScrollView + hero + grid) |
| `res/drawable/tile_squircle_primary.xml` | New | Gradient squircle background for hero tile |
| `res/drawable/tile_squircle_secondary.xml` | New | Surface-variant squircle for action tiles |
| `res/drawable/hex_slot_filled.xml` | New | Vector hex motif, filled stroke, for hero tile |
| `res/drawable/hex_slot_outline.xml` | New | Vector hex motif, thin stroke, watermark use |
| `res/drawable/edit_toolbar_pill_bg.xml` | New | Pill background for edit mode toolbar |
| `MainActivity.kt` | Modified | Add `enterEditMode()` / `exitEditMode()`; register/unregister prefs listener; route gear icon to `SettingsHubActivity`; wire `onEmptyAreaLongPress`; handle `EXTRA_ENTER_EDIT_MODE` intent extra |
| `ui/HexagonalGridView.kt` | Modified | Add `onEmptyAreaLongPress` callback; empty-area hit-test in long-press handler |
| `ui/DockView.kt` | Modified | Gear icon target: `SettingsHubActivity` instead of `SettingsActivity` |
| `AndroidManifest.xml` | Modified | Register `SettingsHubActivity`; remove `SettingsActivity` |
| `ui/SettingsActivity.kt` | Replaced | Delete; superseded by `SettingsHubActivity.kt` |
| `res/layout/activity_settings.xml` | Replaced | Delete; superseded by `activity_settings_hub.xml` |

---

## 8. Migration Plan

Straight replacement — no coexistence period.

1. Delete `ui/SettingsActivity.kt` and `res/layout/activity_settings.xml`.
2. Create all New files listed in §7.
3. Apply Modified changes to `MainActivity.kt`, `HexagonalGridView.kt`, `DockView.kt`.
4. Update `AndroidManifest.xml` atomically: remove `SettingsActivity` registration, add `SettingsHubActivity`.
5. SharedPreferences keys are unchanged — user's saved settings survive the upgrade.
6. `SettingsExporter` export/import contract is unchanged; `SettingsHubActivity` calls the same `SettingsExporter` methods that `SettingsActivity` called.

No database migrations. No format changes. No feature flags needed.

---

## 9. Out of Scope

- Dock/taskbar redesign (separate spec, ships later).
- Compose migration.
- Tablet-specific landscape layouts.
- Animations beyond simple cross-fades and toolbar slide-in.
- New settings or new SharedPreferences keys.
- Per-locale string variations beyond English.
- Signing and release flow.
- Widget placement entry from Edit Mode (long-press empty area → "add widget here" path deferred from Cycle 2 spec remains deferred).

---

## 10. Open Questions for the Plan Stage

Six items. Resolve each before writing the plan; each has a recommended path.

1. **`hex_orientation` recreation vs. invalidate**. Does flipping orientation require `recreate()` or can `HexagonalGridView` accept a new orientation and recompute internally? **Spike**: call `recomputeGridAndInvalidate()` with the new orientation value before committing to `recreate()`. If the grid renders correctly, no `recreate()` is needed and this question is closed; if not, document the slight live-preview jank as accepted.

2. **`dim_status_bar` placement**. Currently in Style panel per §2 with a "(applied on exit)" label. Decision required *before plan stage*: keep in Style panel with the explicit label, OR move to Settings Hub as a small "Misc" tile. Recommendation: keep in Style with the label — it groups visually with the other appearance toggles and the label keeps users informed.

3. **Touch interception mechanism**. Goal: `EditModeOverlay` must intercept all touches that fall outside the toolbar and panel bounding rects so the underlying grid never receives them. **Implementation**: override `onInterceptTouchEvent` (NOT `onTouchEvent`) to return `true` only for events outside the panel and toolbar rects; return `false` for events inside, allowing slider drags and chip taps to reach their child views. Test path: drag a slider in a panel while a hex cell is behind it — the grid must not scroll or receive the event. Also: a touch on the empty grid background (not panel, not toolbar) is silently consumed by the overlay; no feedback. This is intentional — combined with §2's blocked-icon-long-press chip, the user has one clear feedback path.

4. **Icon long-press blocking feedback**. Resolved in §2 ("Long-press on icon while overlay is attached"): haptic + dismissible chip. This item is closed; retained for traceability.

5. **`EXTRA_ENTER_EDIT_MODE` intent handling**. `MainActivity` already declares `launchMode="singleTask"`. Verify that `onNewIntent()` is invoked when the hub hero tile fires the Intent with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`. Plan-stage check: write a manual QA case — open Hub → tap Customize → confirm `onNewIntent` fires with the extra and `enterEditMode()` is called; back-press exits Edit Mode and returns to launcher home, not to Hub.

6. **`show_widgets_during_search` placement**. Key exists in `SettingsManager` (Cycle 2 widget-specific). Decide: (a) add a single switch to the Style panel, (b) move into a dedicated "Widgets" sub-screen reachable from the Settings Hub Widgets tile, or (c) leave it accessible only via Settings export/import for power users. Recommendation: (b) — fewest user-facing surfaces, keeps the Style panel focused on visual defaults.
