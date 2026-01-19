# Hexy Launcher - Gemini Flash Task List

> **Instructions**: Execute each task in order. After completing each task, run the verification step. Mark tasks ✅ when complete or ❌ if blocked.

---

## Phase 1: Project Setup

### Task 1.1: Create Gradle Root Files

**Files to create:**

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`

**Verification:**

```
✅ Files exist in HexyLauncher/
```

---

### Task 1.2: Create App Module Structure

**Directories to create:**

```
app/
├── src/main/java/com/hexy/launcher/
│   ├── data/
│   ├── domain/
│   ├── ui/
│   └── util/
└── src/main/res/
    ├── layout/
    ├── values/
    ├── menu/
    └── drawable/
```

**Files to create:**

- `app/build.gradle.kts`

**Verification:**

```
✅ Directory structure exists
✅ app/build.gradle.kts has correct dependencies (palette-ktx, lifecycle, coroutines)
```

---

## Phase 2: Domain Layer (Math & Logic)

### Task 2.1: Create HexCoordinate

**File:** `app/src/main/java/com/hexy/launcher/domain/HexCoordinate.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 4.2

**Verification:**

```kotlin
// Test in main():
val hex = HexCoordinate(2, -1)
assert(hex.ring == 2)
assert(hex.neighbors().size == 6)
✅ Ring calculation correct
✅ Neighbors generation correct
```

---

### Task 2.2: Create HexGridCalculator

**File:** `app/src/main/java/com/hexy/launcher/domain/HexGridCalculator.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 5.1

**Verification:**

```kotlin
// Test:
val calc = HexGridCalculator(80f)
val spiral = calc.generateSpiralCoordinates(2)
// Expected: Ring 0 = 1, Ring 1 = 6, Ring 2 = 12 → Total = 19
assert(spiral.size == 19)
✅ Spiral generates correct count
✅ hexToPixel returns valid PointF
```

---

### Task 2.3: Create AppSorter

**File:** `app/src/main/java/com/hexy/launcher/domain/AppSorter.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 6.2

**Verification:**

```
✅ Center app is most-used
✅ Positions 1-18 are most recently used
✅ Remaining apps grouped by color bucket
```

---

## Phase 3: Data Layer

### Task 3.1: Create AppInfo Data Class

**File:** `app/src/main/java/com/hexy/launcher/data/AppInfo.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 4.1

**Verification:**

```
✅ Contains: packageName, label, icon, dominantColor, colorBucket, usageCount, lastUsedTimestamp, isShortcut, shortcutId
```

---

### Task 3.2: Create ColorExtractor

**File:** `app/src/main/java/com/hexy/launcher/util/ColorExtractor.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 6.1

**Verification:**

```
✅ extractColor() returns Pair<Int, Int> (color, bucket 0-5)
✅ Grayscale colors → bucket 0
✅ Red hue → bucket 0, Green → bucket 2, Blue → bucket 4
```

---

### Task 3.3: Create UsageStatsHelper

**File:** `app/src/main/java/com/hexy/launcher/data/UsageStatsHelper.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 7.2

**Verification:**

```
✅ getUsageStats() returns Map<String, UsageStats>
✅ hasPermission() returns Boolean
```

---

### Task 3.4: Create AppRepository

**File:** `app/src/main/java/com/hexy/launcher/data/AppRepository.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 7.1

**Verification:**

```
✅ loadInstalledApps() returns List<AppInfo> with launcher apps
✅ loadInstalledApps() includes pinned shortcuts (PWAs)
✅ launchApp() correctly launches apps and shortcuts
```

---

## Phase 4: UI Layer

### Task 4.1: Create HexagonalGridView

**File:** `app/src/main/java/com/hexy/launcher/ui/HexagonalGridView.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 8.1

**Verification:**

```
✅ onDraw() renders hexagons with system icons (no clipping)
✅ onTouchEvent() handles tap → app click
✅ onTouchEvent() handles long press → context menu
✅ Scroll/pan works with gesture detector
```

---

### Task 4.2: Create LauncherViewModel

**File:** `app/src/main/java/com/hexy/launcher/ui/LauncherViewModel.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 9.1

**Verification:**

```
✅ apps LiveData emits sorted list
✅ hideApp() removes app and refreshes
✅ launchApp() calls repository
```

---

### Task 4.3: Create MainActivity

**File:** `app/src/main/java/com/hexy/launcher/MainActivity.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 9.2

**Verification:**

```
✅ Observes ViewModel apps
✅ Shows context menu on long press
✅ Requests usage stats permission if needed
```

---

### Task 4.4: Create HexyLauncherApp

**File:** `app/src/main/java/com/hexy/launcher/HexyLauncherApp.kt`

**Content:** See DEVELOPMENT_SPEC.md Section 11.1

**Verification:**

```
✅ Extends Application
```

---

## Phase 5: Resources

### Task 5.1: Create Layout Files

**Files:**

- `app/src/main/res/layout/activity_main.xml`

**Content:** See DEVELOPMENT_SPEC.md Section 10.1

**Verification:**

```
✅ Contains HexagonalGridView with id hexGrid
✅ Contains optional search bar
```

---

### Task 5.2: Create Menu Files

**Files:**

- `app/src/main/res/menu/app_context_menu.xml`

**Content:** See DEVELOPMENT_SPEC.md Section 10.2

**Verification:**

```
✅ Contains action_hide and action_uninstall items
```

---

### Task 5.3: Create Drawable Files

**Files:**

- `app/src/main/res/drawable/search_bg.xml`

**Content:** See DEVELOPMENT_SPEC.md Section 10.3

**Verification:**

```
✅ Rounded rectangle shape with semi-transparent dark color
```

---

### Task 5.4: Create Value Resources

**Files:**

- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`

**Content:** See DEVELOPMENT_SPEC.md Sections 10.4-10.6

**Verification:**

```
✅ Theme uses NoActionBar with transparent status bar
✅ app_name string is "Hexy Launcher"
```

---

## Phase 6: Manifest & Application

### Task 6.1: Create AndroidManifest.xml

**File:** `app/src/main/AndroidManifest.xml`

**Content:** See DEVELOPMENT_SPEC.md Section 11

**Verification:**

```
✅ Has QUERY_ALL_PACKAGES permission
✅ Has PACKAGE_USAGE_STATS permission
✅ MainActivity has HOME + DEFAULT + LAUNCHER categories
✅ MainActivity launchMode is singleTask
```

---

## Phase 7: Final Integration Tests

### Test 7.1: Build Verification

```bash
./gradlew assembleDebug
```

**Expected:** BUILD SUCCESSFUL

---

### Test 7.2: Runtime Checks (User in Android Studio)

| Feature            | Test Steps                           | Expected Result                           |
| ------------------ | ------------------------------------ | ----------------------------------------- |
| **App Display**    | Install, set as launcher, press Home | Apps appear in hex grid                   |
| **Icon Rendering** | Visual check                         | System icons displayed as-is, not clipped |
| **Center App**     | Check center hex                     | Most-used app in center                   |
| **Recent Apps**    | Check rings 1-2                      | 18 most recently used apps                |
| **Color Sorting**  | Check outer rings                    | Similar colors grouped together           |
| **Tap to Launch**  | Tap any app                          | App opens                                 |
| **Long Press**     | Long press any app                   | Context menu appears                      |
| **Hide App**       | Long press → Hide                    | App disappears from grid                  |
| **Uninstall**      | Long press → Uninstall               | System uninstall dialog                   |
| **Scroll/Pan**     | Drag on grid                         | Grid moves smoothly                       |
| **PWA Support**    | Install PWA, check grid              | PWA shortcut appears                      |
| **Permission**     | First run                            | Usage stats permission requested          |

---

## Progress Tracker

| Phase                  | Status |
| ---------------------- | ------ |
| Phase 1: Project Setup | ✅     |
| Phase 2: Domain Layer  | ✅     |
| Phase 3: Data Layer    | ✅     |
| Phase 4: UI Layer      | ✅     |
| Phase 5: Resources     | ✅     |
| Phase 6: Manifest      | ✅     |
| Phase 7: Testing       | ⬜     |

**Legend:** ⬜ Not Started | 🟡 In Progress | ✅ Complete | ❌ Blocked
