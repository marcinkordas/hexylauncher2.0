# Settings & Edit Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `SettingsActivity` with a new `SettingsHubActivity` (action-tile grid) and add a live-preview Edit Mode overlay (`EditModeOverlay`) attached to `MainActivity`'s `hexGridContainer`, with a 5-button toolbar (Shape / Style / Order / More / Done) driving real-time grid customization via `SharedPreferences` change listeners.

**Architecture:** Two-surface design. Edit Mode is a `FrameLayout` overlay child of `hexGridContainer`, hosting a panel + toolbar; SharedPreferences writes auto-persist and a listener in `MainActivity` redraws the underlying `HexagonalGridView` live. Settings Hub is a fresh Activity replacing `SettingsActivity` — vertical scroll with a hero "Customize layout" tile and a 3-column grid of action tiles (Widgets, Manage Apps, Permissions, Default Launcher, Export, Import). Long-press on empty grid area enters Edit Mode; gear icon opens the Hub.

**Tech Stack:** Kotlin, Android SDK 26+, ViewBinding, Material 3 components (Slider, SwitchMaterial, MaterialButton, MaterialButtonToggleGroup, ChipGroup), SharedPreferences + `OnSharedPreferenceChangeListener`, `WindowInsetsCompat`, JUnit4 + Mockito for unit tests.

---

## File Structure

### New Files

| File | Responsibility |
|------|----------------|
| `ui/EditModeOverlay.kt` | FrameLayout overlay; hosts toolbar pill and panel swap logic |
| `ui/edit/ShapePanel.kt` | Shape controls — sliders + orientation chip |
| `ui/edit/StylePanel.kt` | Style controls — switches + chip groups |
| `ui/edit/OrderPanel.kt` | Order controls — sort/search chips + mic switch |
| `ui/SettingsHubActivity.kt` | Replaces SettingsActivity; hero tile + 6 action tiles |
| `res/layout/overlay_edit_mode.xml` | Root layout for EditModeOverlay |
| `res/layout/panel_shape.xml` | Shape panel view |
| `res/layout/panel_style.xml` | Style panel view |
| `res/layout/panel_order.xml` | Order panel view |
| `res/layout/activity_settings_hub.xml` | Settings Hub layout (ScrollView + hero + grid) |
| `res/drawable/tile_squircle_primary.xml` | Gradient squircle background for hero tile |
| `res/drawable/tile_squircle_secondary.xml` | Surface-variant squircle for action tiles |
| `res/drawable/hex_slot_filled.xml` | Vector hex motif, filled stroke, for hero tile |
| `res/drawable/hex_slot_outline.xml` | Vector hex motif, thin stroke, watermark use |
| `res/drawable/edit_toolbar_pill_bg.xml` | Pill background for edit mode toolbar |

### Modified Files

| File | Change |
|------|--------|
| `MainActivity.kt` | Add `enterEditMode()` / `exitEditMode()`; register/unregister prefs listener; route gear icon to `SettingsHubActivity`; wire `onEmptyAreaLongPress`; handle `EXTRA_ENTER_EDIT_MODE` intent extra |
| `ui/HexagonalGridView.kt` | Add `onEmptyAreaLongPress` callback; empty-area hit-test in long-press handler |
| `ui/DockView.kt` | Gear icon target: `SettingsHubActivity` instead of `SettingsActivity` |
| `AndroidManifest.xml` | Register `SettingsHubActivity`; remove `SettingsActivity` |
| `ui/WidgetManagementActivity.kt` | Add `show_widgets_during_search` SwitchMaterial |
| `ui/SettingsActivity.kt` | **Replaced** — delete; superseded by `SettingsHubActivity.kt` |
| `res/layout/activity_settings.xml` | **Replaced** — delete; superseded by `activity_settings_hub.xml` |

---

## Chunk 1: Drawables, Theme Tokens, and Visual Primitives

### Task 1: Squircle tile drawables

**Files:**
- Create: `app/src/main/res/drawable/tile_squircle_secondary.xml`
- Create: `app/src/main/res/drawable/tile_squircle_primary.xml`

- [ ] **Step 1: Create `tile_squircle_secondary.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Secondary tile background — action tiles in Settings Hub and inactive toolbar buttons -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="?attr/colorSurfaceVariant" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorOutline" />
</shape>
```

- [ ] **Step 2: Create `tile_squircle_primary.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Primary tile background — hero tile and active toolbar button -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="20dp" />
    <gradient
        android:angle="135"
        android:startColor="?attr/colorPrimary"
        android:endColor="?attr/colorPrimaryContainer" />
</shape>
```

- [ ] **Step 3: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/tile_squircle_secondary.xml
git add app/src/main/res/drawable/tile_squircle_primary.xml
git commit -m "feat(edit-mode): add squircle tile drawables (primary gradient, secondary surface-variant)"
```

---

### Task 2: Hex-slot vector drawables

**Files:**
- Create: `app/src/main/res/drawable/hex_slot_filled.xml`
- Create: `app/src/main/res/drawable/hex_slot_outline.xml`

> Path derivation: pointy-top hexagon, viewBox 100×100, R=46. Vertices: top=(50,4), top-right=(89.83,27), bottom-right=(89.83,73), bottom=(50,96), bottom-left=(10.17,73), top-left=(10.17,27). Formula: `(cx±R·√3/2, cy±R/2)` with cx=50, cy=50, R=46. √3/2≈0.866 → R·√3/2≈39.83.

- [ ] **Step 1: Create `hex_slot_filled.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Regular pointy-top hexagon, stroke-only. Used on hero tile and Shape toolbar button.
     Sized so the hexagon is ~55% of a 100×100 viewport (R=46, 4dp padding for stroke). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="55dp"
    android:height="55dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <path
        android:pathData="M50,4 L89.83,27 L89.83,73 L50,96 L10.17,73 L10.17,27 Z"
        android:strokeColor="?attr/colorOnPrimary"
        android:strokeWidth="2"
        android:fillColor="@android:color/transparent" />
</vector>
```

- [ ] **Step 2: Create `hex_slot_outline.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Same path, thinner stroke. Used as watermark on secondary tiles. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="55dp"
    android:height="55dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <path
        android:pathData="M50,4 L89.83,27 L89.83,73 L50,96 L10.17,73 L10.17,27 Z"
        android:strokeColor="?attr/colorPrimary"
        android:strokeWidth="1"
        android:fillColor="@android:color/transparent" />
</vector>
```

- [ ] **Step 3: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/hex_slot_filled.xml
git add app/src/main/res/drawable/hex_slot_outline.xml
git commit -m "feat(edit-mode): add hex-slot vector drawables (filled + outline variants)"
```

---

### Task 3: Toolbar pill background

**Files:**
- Create: `app/src/main/res/drawable/edit_toolbar_pill_bg.xml`

- [ ] **Step 1: Create `edit_toolbar_pill_bg.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Floating pill background for the Edit Mode toolbar.
     28dp radius creates a fully-rounded pill at standard pill heights. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="28dp" />
    <solid android:color="?attr/colorSurfaceVariant" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorOutline" />
</shape>
```

- [ ] **Step 2: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/edit_toolbar_pill_bg.xml
git commit -m "feat(edit-mode): add toolbar pill background drawable"
```

---

## Chunk 2: Settings Hub Activity (Replaces SettingsActivity)

### Task 4: Delete SettingsActivity and create SettingsHubActivity layout

**Files:**
- Delete: `app/src/main/java/com/hexgrid/launcher/ui/SettingsActivity.kt`
- Delete: `app/src/main/res/layout/activity_settings.xml`
- Create: `app/src/main/res/layout/activity_settings_hub.xml`

- [ ] **Step 1: Delete old files**

```bash
git rm app/src/main/java/com/hexgrid/launcher/ui/SettingsActivity.kt
git rm app/src/main/res/layout/activity_settings.xml
```

- [ ] **Step 2: Create `activity_settings_hub.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Hero tile: "Customize layout" → enters Edit Mode -->
        <FrameLayout
            android:id="@+id/tileHero"
            android:layout_width="match_parent"
            android:layout_height="160dp"
            android:background="@drawable/tile_squircle_primary"
            android:clickable="true"
            android:focusable="true"
            android:contentDescription="Customize layout"
            android:layout_marginBottom="16dp">

            <ImageView
                android:layout_width="55dp"
                android:layout_height="55dp"
                android:layout_gravity="center"
                android:src="@drawable/hex_slot_filled"
                android:contentDescription="@null"
                android:importantForAccessibility="no" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="bottom|center_horizontal"
                android:layout_marginBottom="16dp"
                android:text="Customize layout"
                android:textColor="?attr/colorOnPrimary"
                android:textAppearance="?attr/textAppearanceTitleMedium" />
        </FrameLayout>

        <!-- 3-column action tile grid -->
        <GridLayout
            android:id="@+id/tileGrid"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:columnCount="3"
            android:useDefaultMargins="false">

            <!-- Tile 1: Widgets -->
            <FrameLayout
                android:id="@+id/tileWidgets"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Widgets"
                android:padding="12dp">

                <ImageView
                    android:layout_width="28dp"
                    android:layout_height="28dp"
                    android:layout_gravity="center_horizontal|top"
                    android:src="@drawable/hex_slot_outline"
                    android:importantForAccessibility="no" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Widgets"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

            <!-- Tile 2: Manage Apps -->
            <FrameLayout
                android:id="@+id/tileManageApps"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Manage Apps"
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Manage Apps"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

            <!-- Tile 3: Permissions (badge dot optional) -->
            <FrameLayout
                android:id="@+id/tilePermissions"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Permissions"
                android:padding="12dp">

                <View
                    android:id="@+id/badgePermissions"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:layout_margin="4dp"
                    android:background="?attr/colorError"
                    android:importantForAccessibility="no"
                    android:visibility="gone" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Permissions"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

            <!-- Tile 4: Default Launcher (badge dot optional) -->
            <FrameLayout
                android:id="@+id/tileDefaultLauncher"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Default Launcher"
                android:padding="12dp">

                <View
                    android:id="@+id/badgeDefaultLauncher"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:layout_margin="4dp"
                    android:background="?attr/colorError"
                    android:importantForAccessibility="no"
                    android:visibility="gone" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Default Launcher"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

            <!-- Tile 5: Export -->
            <FrameLayout
                android:id="@+id/tileExport"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Export settings"
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Export"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

            <!-- Tile 6: Import -->
            <FrameLayout
                android:id="@+id/tileImport"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:layout_margin="4dp"
                android:minHeight="88dp"
                android:background="@drawable/tile_squircle_secondary"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="Import settings"
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|center_horizontal"
                    android:text="Import"
                    android:textAppearance="?attr/textAppearanceLabelSmall" />
            </FrameLayout>

        </GridLayout>

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: Compile check**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: compilation error for missing `SettingsActivity` references — resolved in Task 5.

---

### Task 5: Create SettingsHubActivity.kt and update manifest + DockView

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/ui/SettingsHubActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/DockView.kt`
- Modify: `app/src/main/java/com/hexgrid/launcher/MainActivity.kt` (gear icon reference)

- [ ] **Step 1: Create `SettingsHubActivity.kt`**

```kotlin
package com.hexgrid.launcher.ui

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hexgrid.launcher.MainActivity
import com.hexgrid.launcher.databinding.ActivitySettingsHubBinding
import com.hexgrid.launcher.util.SettingsExporter

class SettingsHubActivity : AppCompatActivity() {

    companion object {
        // Caller uses this extra to signal that Edit Mode should open on resume.
        // Re-exported here so callers in this file use the canonical name.
        const val EXTRA_ENTER_EDIT_MODE = MainActivity.EXTRA_ENTER_EDIT_MODE
        private const val REQUEST_CODE_DEFAULT_LAUNCHER = 1001
    }

    private lateinit var binding: ActivitySettingsHubBinding

    // Export: user picks a destination file; we write JSON to it.
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportSettingsToUri(it) }
    }

    // Import: user picks a JSON file; we read and apply it, then recreate.
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importSettingsFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }

        setupTiles()
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate badge conditions each time the Hub comes to foreground.
        updateBadges()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupTiles() {
        // Hero tile: enter Edit Mode in MainActivity.
        // FLAG_ACTIVITY_CLEAR_TOP brings the existing singleTask MainActivity to front
        // and finishes SettingsHubActivity so back-press from Edit Mode returns to launcher home.
        binding.tileHero.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_ENTER_EDIT_MODE, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        // Tile 1: Widgets
        binding.tileWidgets.setOnClickListener {
            startActivity(Intent(this, WidgetManagementActivity::class.java))
        }

        // Tile 2: Manage Apps (app visibility)
        binding.tileManageApps.setOnClickListener {
            startActivity(Intent(this, AppVisibilityActivity::class.java))
        }

        // Tile 3: Permissions — notification listener access
        binding.tilePermissions.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        // Tile 4: Default Launcher — use RoleManager on API 29+, fallback to settings
        binding.tileDefaultLauncher.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_DEFAULT_LAUNCHER)
                }
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        // Tile 5: Export settings to a JSON file chosen by the user
        binding.tileExport.setOnClickListener {
            exportLauncher.launch(SettingsExporter.getSuggestedFilename())
        }

        // Tile 6: Import settings from a JSON file chosen by the user
        binding.tileImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun updateBadges() {
        // Permissions badge: show if notification listener access is not granted
        val notifGranted = isNotificationListenerEnabled()
        binding.badgePermissions.visibility = if (notifGranted) View.GONE else View.VISIBLE
        binding.tilePermissions.contentDescription =
            if (notifGranted) "Permissions" else "Permissions — action needed"

        // Default Launcher badge: show if this app is not the current default launcher
        val isDefault = isDefaultLauncher()
        binding.badgeDefaultLauncher.visibility = if (isDefault) View.GONE else View.VISIBLE
        binding.tileDefaultLauncher.contentDescription =
            if (isDefault) "Default Launcher" else "Default Launcher — action needed"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, "com.hexgrid.launcher.service.NotificationListener")
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun exportSettingsToUri(uri: Uri) {
        try {
            val json = SettingsExporter.exportToJson(this)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            SettingsExporter.importFromJson(this, json).fold(
                onSuccess = { count ->
                    Toast.makeText(this, "Imported $count settings", Toast.LENGTH_SHORT).show()
                    recreate()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
```

- [ ] **Step 2: Update `AndroidManifest.xml` — swap SettingsActivity for SettingsHubActivity**

Find the block:
```xml
        <activity
            android:name=".ui.SettingsActivity"
```
Replace with:
```xml
        <activity
            android:name=".ui.SettingsHubActivity"
            android:exported="false"
            android:label="Settings" />
```

- [ ] **Step 3: Add `EXTRA_ENTER_EDIT_MODE` companion constant to `MainActivity.kt`**

In `MainActivity.kt` companion object, add alongside `EXTRA_PLACEMENT_WIDGET_ID`:
```kotlin
const val EXTRA_ENTER_EDIT_MODE = "extra_enter_edit_mode"
```

- [ ] **Step 4: Update `DockView.kt` — gear icon targets SettingsHubActivity**

In `DockView.kt`, the `onSettingsClick` callback is wired in `MainActivity.setupDock()`. No change needed in DockView itself — the callback is set externally. Update `MainActivity.setupDock()`: find the line:
```kotlin
dock.onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
```
Replace with:
```kotlin
dock.onSettingsClick = { startActivity(Intent(this, SettingsHubActivity::class.java)) }
```

Also replace the two `SettingsActivity::class.java` references in `setupGrid()` (the `onAppClick` block that handles `app.packageName == packageName`):
```kotlin
// Old:
startActivity(Intent(this, SettingsActivity::class.java))
// New:
startActivity(Intent(this, SettingsHubActivity::class.java))
```

Update the import in `MainActivity.kt`: remove `import com.hexgrid.launcher.ui.SettingsActivity`, add `import com.hexgrid.launcher.ui.SettingsHubActivity`.

- [ ] **Step 5: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/SettingsHubActivity.kt
git add app/src/main/res/layout/activity_settings_hub.xml
git add app/src/main/AndroidManifest.xml
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt
git commit -m "feat(settings-hub): replace SettingsActivity with SettingsHubActivity (hero tile + 6-tile grid)"
```

---

### Task 6: Add show_widgets_during_search to WidgetManagementActivity

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/WidgetManagementActivity.kt`
- Modify: `app/src/main/res/layout/activity_widget_management.xml`

- [ ] **Step 1: Add a SwitchMaterial to `activity_widget_management.xml`**

Near the bottom of the layout, before `</LinearLayout>` or equivalent root close, add:
```xml
<com.google.android.material.switchmaterial.SwitchMaterial
    android:id="@+id/switchShowWidgetsDuringSearch"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:text="Show widgets during search" />
```

- [ ] **Step 2: Wire the switch in `WidgetManagementActivity.kt`**

In `onCreate()`, after existing setup:
```kotlin
binding.switchShowWidgetsDuringSearch.isChecked =
    SettingsManager.getShowWidgetsDuringSearch(this)
binding.switchShowWidgetsDuringSearch.setOnCheckedChangeListener { _, isChecked ->
    SettingsManager.setShowWidgetsDuringSearch(this, isChecked)
}
```

Also add import at top of file: `import com.hexgrid.launcher.util.SettingsManager`

- [ ] **Step 3: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/WidgetManagementActivity.kt
git add app/src/main/res/layout/activity_widget_management.xml
git commit -m "feat(settings-hub): add show_widgets_during_search toggle to WidgetManagementActivity"
```

---

## Chunk 3: Long-Press Empty-Area Gesture

### Task 7: Add onEmptyAreaLongPress callback to HexagonalGridView

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt`
- Create: `app/src/test/java/com/hexgrid/launcher/ui/EmptyAreaLongPressTest.kt`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/hexgrid/launcher/ui/EmptyAreaLongPressTest.kt`:

```kotlin
package com.hexgrid.launcher.ui

import com.hexgrid.launcher.domain.HexCoordinate
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test for the empty-area long-press routing logic extracted from HexagonalGridView.
 * Verifies that the callback fires only for unoccupied, non-app, non-widget coordinates.
 */
class EmptyAreaLongPressTest {

    /**
     * Pure function extracted from HexagonalGridView.GestureListener.onLongPress logic.
     * Returns: "app" | "widget" | "empty"
     */
    private fun classify(
        coord: HexCoordinate,
        appCoords: Set<HexCoordinate>,
        widgetCoords: Set<HexCoordinate>
    ): String = when {
        coord in appCoords    -> "app"
        coord in widgetCoords -> "widget"
        else                  -> "empty"
    }

    @Test
    fun `app coordinate triggers app path`() {
        val coord = HexCoordinate(1, 0)
        val result = classify(coord, setOf(coord), emptySet())
        assertEquals("app", result)
    }

    @Test
    fun `widget coordinate triggers widget no-op path`() {
        val coord = HexCoordinate(2, 1)
        val result = classify(coord, emptySet(), setOf(coord))
        assertEquals("widget", result)
    }

    @Test
    fun `unoccupied coordinate triggers empty-area callback`() {
        val coord = HexCoordinate(5, 3)
        val result = classify(coord, setOf(HexCoordinate(0, 0)), setOf(HexCoordinate(1, 1)))
        assertEquals("empty", result)
    }

    @Test
    fun `app takes priority over widget when same coord (should not happen but safe)`() {
        val coord = HexCoordinate(0, 0)
        val result = classify(coord, setOf(coord), setOf(coord))
        assertEquals("app", result)
    }

    @Test
    fun `origin coordinate is empty when no apps or widgets placed`() {
        val result = classify(HexCoordinate(0, 0), emptySet(), emptySet())
        assertEquals("empty", result)
    }
}
```

- [ ] **Step 2: Run test — expect PASS (pure logic, no Android context)**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew test --tests "com.hexgrid.launcher.ui.EmptyAreaLongPressTest" 2>&1 | tail -20
```

Expected: 5 tests PASS. (If `HexCoordinate` has a non-default constructor, adjust the instantiation to match the actual class.)

- [ ] **Step 3: Add `onEmptyAreaLongPress` callback field to `HexagonalGridView.kt`**

After the existing `var onScrollChanged` field (around line 99), add:

```kotlin
// Edit Mode entry point — fires when user long-presses a hex cell that has no app or widget.
// The HexCoordinate parameter is passed for future "add widget here" placement use.
var onEmptyAreaLongPress: ((HexCoordinate) -> Unit)? = null
```

- [ ] **Step 4: Update `GestureListener.onLongPress` in `HexagonalGridView.kt`**

The existing `onLongPress` (around line 555) only handles the app path. Replace the full method body:

```kotlin
override fun onLongPress(e: MotionEvent) {
    // Do not fire long-press while placement mode is active.
    if (isPlacementMode) return

    val index = findAppIndexAt(e.x, e.y)
    if (index >= 0 && index < apps.size) {
        val app = apps[index]
        if (!AppSorter.isPlaceholder(app)) {
            // App cell: fire the existing context menu callback (unchanged).
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onAppLongClickListener?.invoke(app, e.x, e.y)
        }
        return
    }

    // No app found at the tap point. Determine the hex coordinate under the finger.
    val centerX = width / 2f + offsetX
    val centerY = height / 2f + offsetY
    val coord = calculator.pixelToHex(e.x - offsetX, e.y - offsetY, width / 2f, height / 2f)

    // Widget cells: explicit no-op so widget views handle their own long-press internally.
    if (coord in occupiedCells) return

    // Empty cell: invoke callback for Edit Mode entry.
    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    onEmptyAreaLongPress?.invoke(coord)
}
```

> **Implementer note:** `calculator.pixelToHex` signature — check `HexGridCalculator.kt` for exact parameter order. The existing `findAppIndexAt` already computes pixel→hex internally, so use whichever helper is already available. If `pixelToHex` is not a public method, inline the coordinate computation or make it internal.

- [ ] **Step 5: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Wire stub in `MainActivity.kt` — add `enterEditMode()` stub**

In `MainActivity.kt`, add a private function (will be replaced in Chunk 5):

```kotlin
// Stub — replaced in Chunk 5 with real EditModeOverlay logic.
fun enterEditMode() {
    android.util.Log.d("HexGrid", "enterEditMode() called — stub")
    android.widget.Toast.makeText(this, "Edit Mode (coming soon)", android.widget.Toast.LENGTH_SHORT).show()
}
```

In `setupGrid()`, after `binding.hexGrid.setOnAppLongClick { ... }`, add:

```kotlin
binding.hexGrid.onEmptyAreaLongPress = { _ -> enterEditMode() }
```

- [ ] **Step 7: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt
git add app/src/test/java/com/hexgrid/launcher/ui/EmptyAreaLongPressTest.kt
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt
git commit -m "feat(edit-mode): add onEmptyAreaLongPress callback + empty-area hit-test in HexagonalGridView"
```

---

## Chunk 4: Live-Preview SharedPreferences Listener Wiring

### Task 8: Register prefs listener in MainActivity and implement refresh helpers

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/MainActivity.kt`

- [ ] **Step 1: Add private fields to `MainActivity`**

After the existing `private var widgetFadeAnimator` field, add:

```kotlin
// Edit Mode state — null when overlay is not attached.
// Declared as View? so Chunk 4 compiles before EditModeOverlay class exists.
// Replace with EditModeOverlay? in Chunk 5.
private var editModeOverlay: View? = null

// Dark-theme recreate is deferred while Edit Mode is active to avoid
// destroying the overlay mid-session. Flushed in exitEditMode().
private var pendingDarkThemeRecreate: Boolean = false

// SharedPreferences change listener — drives live grid preview.
// Registered in onCreate(), unregistered in onDestroy().
private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    when (key) {
        "hex_radius", "icon_size_multiplier", "icon_padding",
        "outline_width", "corner_radius", "show_outline",
        "show_labels", "show_notification_glow",
        "unified_bucket_colors", "tile_transparency" -> {
            binding.hexGrid.invalidate()
        }
        "dock_transparency" -> {
            getCurrentDock().invalidate()
        }
        "dark_theme" -> {
            // Defer recreate() until Edit Mode exits — recreate() during edit would
            // destroy the overlay and eject the user mid-session.
            if (editModeOverlay == null) recreate()
            else pendingDarkThemeRecreate = true
        }
        "hex_orientation" -> recomputeGridAndInvalidate()
        "sort_order"      -> reloadAppsAndInvalidate()
        "search_position" -> repositionSearchBar()
        "search_with_mic" -> updateSearchMicButton()
        "shortcut_icon_shape" -> {
            SettingsManager.setIconCacheDirty(this, true)
            binding.hexGrid.invalidate()
        }
        "dim_status_bar"  -> applyStatusBarDim()
    }
}
```

Add required import: `import android.content.SharedPreferences`

- [ ] **Step 2: Register listener in `onCreate()` and unregister in `onDestroy()`**

In `onCreate()`, after `binding = ActivityMainBinding.inflate(layoutInflater)` and `setContentView(binding.root)`, add:

```kotlin
// Register live-preview prefs listener. Must use the same SharedPreferences instance
// that SettingsManager uses (PreferenceManager.getDefaultSharedPreferences).
androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    .registerOnSharedPreferenceChangeListener(prefsListener)
```

In `onDestroy()`, add before `super.onDestroy()`:

```kotlin
androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    .unregisterOnSharedPreferenceChangeListener(prefsListener)
```

- [ ] **Step 3: Implement refresh helpers**

Add the following private functions to `MainActivity.kt`:

```kotlin
/** Reloads hex grid calculator settings (radius, orientation) and invalidates. */
private fun recomputeGridAndInvalidate() {
    binding.hexGrid.refreshSettings()
}

/** Invalidates the app list then redraws — used when sort order changes. */
private fun reloadAppsAndInvalidate() {
    viewModel.reloadApps()
}

/** Shows/hides/repositions the search dock based on current search_position setting. */
private fun repositionSearchBar() {
    setupDock()
}

/** Toggles the mic button in the dock based on search_with_mic setting. */
private fun updateSearchMicButton() {
    getCurrentDock().refreshSettings()
}

/**
 * Applies dim_status_bar setting via WindowInsetsController.
 * The Style panel label "(applied on exit)" is a UX hint — this function
 * actually applies immediately, but the effect is hidden behind the overlay.
 */
private fun applyStatusBarDim() {
    val controller = androidx.core.view.WindowCompat
        .getInsetsController(window, binding.root)
    val dim = SettingsManager.getDimStatusBar(this)
    controller.isAppearanceLightStatusBars = !dim
}
```

Add required imports:
```kotlin
import androidx.preference.PreferenceManager
import androidx.core.view.WindowCompat
```

- [ ] **Step 4: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt
git commit -m "feat(edit-mode): register SharedPreferences live-preview listener in MainActivity"
```

---

## Chunk 5: EditModeOverlay + Mode Panels + 5-Button Toolbar

### Task 9: Panel layouts

**Files:**
- Create: `app/src/main/res/layout/panel_shape.xml`
- Create: `app/src/main/res/layout/panel_style.xml`
- Create: `app/src/main/res/layout/panel_order.xml`

- [ ] **Step 1: Create `panel_shape.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Shape panel: hex size, icon appearance, orientation -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/tile_squircle_secondary"
    android:orientation="vertical"
    android:padding="16dp"
    android:elevation="4dp">

    <!-- Hex Radius: 50–150 dp -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Hex radius"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderHexRadius"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="50"
        android:valueTo="150"
        android:stepSize="1"
        android:contentDescription="Hex radius" />

    <!-- Icon size: 0.5–1.5× -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Icon size"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderIconSize"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0.5"
        android:valueTo="1.5"
        android:stepSize="0.05"
        android:contentDescription="Icon size" />

    <!-- Icon padding: 0–20 dp -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Icon padding"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderIconPadding"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0"
        android:valueTo="20"
        android:stepSize="1"
        android:contentDescription="Icon padding" />

    <!-- Outline width: 0.5–4 dp -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Outline width"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderOutlineWidth"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0.5"
        android:valueTo="4.0"
        android:stepSize="0.5"
        android:contentDescription="Outline width" />

    <!-- Corner radius: 0–20 dp -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Corner radius"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderCornerRadius"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0"
        android:valueTo="20"
        android:stepSize="1"
        android:contentDescription="Corner radius" />

    <!-- Hex orientation: Pointy / Flat toggle -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Hex orientation"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.button.MaterialButtonToggleGroup
        android:id="@+id/toggleHexOrientation"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        app:singleSelection="true"
        app:selectionRequired="true"
        xmlns:app="http://schemas.android.com/apk/res-auto">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnOrientationPointy"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Pointy" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnOrientationFlat"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Flat" />
    </com.google.android.material.button.MaterialButtonToggleGroup>

</LinearLayout>
```

- [ ] **Step 2: Create `panel_style.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Style panel: visual toggles, theme, transparency, icon shape -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/tile_squircle_secondary"
    android:orientation="vertical"
    android:padding="16dp"
    android:elevation="4dp">

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchShowOutline"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Show outline" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchShowLabels"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Show labels" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchNotificationGlow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Notification glow" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchDarkTheme"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Dark theme (applied on exit)" />

    <!-- Tile transparency: 0–100 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Tile transparency"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderTileTransparency"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0"
        android:valueTo="100"
        android:stepSize="1"
        android:contentDescription="Tile transparency" />

    <!-- Dock transparency: 0–100 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Dock transparency"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.slider.Slider
        android:id="@+id/sliderDockTransparency"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:valueFrom="0"
        android:valueTo="100"
        android:stepSize="1"
        android:contentDescription="Dock transparency" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchUnifiedBucketColors"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Unified bucket colors" />

    <!-- Shortcut icon shape chip group -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Shortcut icon shape"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.button.MaterialButtonToggleGroup
        android:id="@+id/toggleShortcutIconShape"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        app:singleSelection="true"
        app:selectionRequired="true">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShapeSquare"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Square" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShapeSquircle"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Squircle" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShapeCircle"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Circle" />
    </com.google.android.material.button.MaterialButtonToggleGroup>

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchDimStatusBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Dim status bar (applied on exit)" />

</LinearLayout>
```

- [ ] **Step 3: Create `panel_order.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Order panel: sort order, search position, mic toggle -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/tile_squircle_secondary"
    android:orientation="vertical"
    android:padding="16dp"
    android:elevation="4dp">

    <!-- Sort order chips -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Sort order"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.button.MaterialButtonToggleGroup
        android:id="@+id/toggleSortOrder"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        app:singleSelection="true"
        app:selectionRequired="true">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSortName"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Name" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSortFrequency"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Frequency" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSortTime"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Time" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSortNotifications"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Notifications" />
    </com.google.android.material.button.MaterialButtonToggleGroup>

    <!-- Search position chips -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="Search bar"
        android:textAppearance="?attr/textAppearanceLabelMedium" />
    <com.google.android.material.button.MaterialButtonToggleGroup
        android:id="@+id/toggleSearchPosition"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        app:singleSelection="true"
        app:selectionRequired="true">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSearchNone"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="None" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSearchTop"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Top" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSearchBottom"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Bottom" />
    </com.google.android.material.button.MaterialButtonToggleGroup>

    <!-- Voice search -->
    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchVoiceSearch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="Voice search" />

</LinearLayout>
```

- [ ] **Step 4: Create `overlay_edit_mode.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Root layout for EditModeOverlay. Fills hexGridContainer (MATCH_PARENT × MATCH_PARENT).
     Panel sits above the toolbar; toolbar is centered at the bottom with inset margin. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Active panel container: sits above toolbar, horizontal margin for breathing room -->
    <FrameLayout
        android:id="@+id/panelContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp"
        android:layout_marginBottom="96dp" />

    <!-- Toolbar pill: 5 squircle buttons -->
    <LinearLayout
        android:id="@+id/toolbarPill"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="16dp"
        android:background="@drawable/edit_toolbar_pill_bg"
        android:elevation="4dp"
        android:orientation="horizontal"
        android:padding="8dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnShape"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/tile_squircle_secondary"
            android:contentDescription="Shape"
            android:text="⬡"
            android:textSize="20sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnStyle"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/tile_squircle_secondary"
            android:contentDescription="Style"
            android:text="🎨"
            android:textSize="20sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnOrder"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/tile_squircle_secondary"
            android:contentDescription="Order"
            android:text="↕"
            android:textSize="20sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnMore"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/tile_squircle_secondary"
            android:contentDescription="More"
            android:text="⋯"
            android:textSize="20sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnDone"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:background="@drawable/tile_squircle_secondary"
            android:contentDescription="Done"
            android:text="✓"
            android:textSize="20sp" />

    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 5: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/panel_shape.xml
git add app/src/main/res/layout/panel_style.xml
git add app/src/main/res/layout/panel_order.xml
git add app/src/main/res/layout/overlay_edit_mode.xml
git commit -m "feat(edit-mode): add panel and overlay layouts (shape/style/order panels + toolbar pill)"
```

---

### Task 10: Panel Kotlin classes

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/ui/edit/ShapePanel.kt`
- Create: `app/src/main/java/com/hexgrid/launcher/ui/edit/StylePanel.kt`
- Create: `app/src/main/java/com/hexgrid/launcher/ui/edit/OrderPanel.kt`

- [ ] **Step 1: Create `ShapePanel.kt`**

```kotlin
package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.slider.Slider
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelShapeBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Shape panel: sliders for hex radius, icon size/padding, outline width, corner radius,
 * and a Pointy/Flat orientation chip group.
 *
 * Each control reads its initial value from SettingsManager on [attach] and writes
 * immediately on change — the SharedPreferences listener in MainActivity propagates
 * the change to the live grid view.
 */
class ShapePanel(private val context: Context) {

    private var _binding: PanelShapeBinding? = null
    val view: View get() = _binding!!.root

    fun attach(parent: ViewGroup) {
        val binding = PanelShapeBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelShapeBinding) {
        // Hex Radius
        binding.sliderHexRadius.value = SettingsManager.getHexRadius(context)
        binding.sliderHexRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setHexRadius(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderHexRadius,
                    sliderDelegate("Hex radius: ${value.toInt()}dp"))
            }
        }
        ViewCompat.setAccessibilityDelegate(binding.sliderHexRadius,
            sliderDelegate("Hex radius: ${SettingsManager.getHexRadius(context).toInt()}dp"))

        // Icon Size Multiplier
        binding.sliderIconSize.value = SettingsManager.getIconSizeMultiplier(context)
        binding.sliderIconSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setIconSizeMultiplier(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderIconSize,
                    sliderDelegate("Icon size: ${(value * 100).toInt()}%"))
            }
        }

        // Icon Padding
        binding.sliderIconPadding.value = SettingsManager.getIconPadding(context)
        binding.sliderIconPadding.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setIconPadding(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderIconPadding,
                    sliderDelegate("Icon padding: ${value.toInt()}dp"))
            }
        }

        // Outline Width
        binding.sliderOutlineWidth.value = SettingsManager.getOutlineWidth(context)
        binding.sliderOutlineWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setOutlineWidth(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderOutlineWidth,
                    sliderDelegate("Outline width: ${value}dp"))
            }
        }

        // Corner Radius
        binding.sliderCornerRadius.value = SettingsManager.getCornerRadius(context)
        binding.sliderCornerRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setCornerRadius(context, value)
                ViewCompat.setAccessibilityDelegate(binding.sliderCornerRadius,
                    sliderDelegate("Corner radius: ${value.toInt()}dp"))
            }
        }

        // Hex Orientation toggle
        val orientation = SettingsManager.getHexOrientation(context)
        when (orientation) {
            SettingsManager.HexOrientation.POINTY_TOP ->
                binding.toggleHexOrientation.check(R.id.btnOrientationPointy)
            SettingsManager.HexOrientation.FLAT_TOP ->
                binding.toggleHexOrientation.check(R.id.btnOrientationFlat)
        }
        binding.toggleHexOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newOrientation = when (checkedId) {
                R.id.btnOrientationPointy -> SettingsManager.HexOrientation.POINTY_TOP
                else                      -> SettingsManager.HexOrientation.FLAT_TOP
            }
            SettingsManager.setHexOrientation(context, newOrientation)
        }
    }

    /**
     * Returns an AccessibilityDelegate that overrides contentDescription for the slider.
     * Used to include the current value + unit so TalkBack reads it aloud.
     */
    private fun sliderDelegate(description: String) =
        object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
            }
        }
}
```

- [ ] **Step 2: Create `StylePanel.kt`**

```kotlin
package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelStyleBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Style panel: appearance toggles (outline, labels, glow, dark theme),
 * transparency sliders, unified colors switch, icon shape chips, dim status bar.
 *
 * Dark theme writes to SharedPreferences immediately. The prefs listener in
 * MainActivity defers recreate() until Edit Mode exits to avoid destroying the overlay.
 * The "(applied on exit)" label in the layout informs the user of this behaviour.
 */
class StylePanel(private val context: Context) {

    private var _binding: PanelStyleBinding? = null
    val view: View get() = _binding!!.root

    fun attach(parent: ViewGroup) {
        val binding = PanelStyleBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelStyleBinding) {
        binding.switchShowOutline.isChecked = SettingsManager.getShowOutline(context)
        binding.switchShowOutline.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowOutline(context, v)
        }

        binding.switchShowLabels.isChecked = SettingsManager.getShowLabels(context)
        binding.switchShowLabels.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowLabels(context, v)
        }

        binding.switchNotificationGlow.isChecked = SettingsManager.getShowNotificationGlow(context)
        binding.switchNotificationGlow.setOnCheckedChangeListener { _, v ->
            SettingsManager.setShowNotificationGlow(context, v)
        }

        // Dark theme: immediate prefs write; MainActivity defers recreate() until exit.
        binding.switchDarkTheme.isChecked = SettingsManager.getDarkTheme(context)
        binding.switchDarkTheme.setOnCheckedChangeListener { _, v ->
            SettingsManager.setDarkTheme(context, v)
        }

        // Tile transparency
        binding.sliderTileTransparency.value = SettingsManager.getTileTransparency(context).toFloat()
        binding.sliderTileTransparency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setTileTransparency(context, value.toInt())
                ViewCompat.setAccessibilityDelegate(binding.sliderTileTransparency,
                    sliderDelegate("Tile transparency: ${value.toInt()}%"))
            }
        }

        // Dock transparency
        binding.sliderDockTransparency.value = SettingsManager.getDockTransparency(context).toFloat()
        binding.sliderDockTransparency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                SettingsManager.setDockTransparency(context, value.toInt())
                ViewCompat.setAccessibilityDelegate(binding.sliderDockTransparency,
                    sliderDelegate("Dock transparency: ${value.toInt()}%"))
            }
        }

        binding.switchUnifiedBucketColors.isChecked = SettingsManager.getUnifiedBucketColors(context)
        binding.switchUnifiedBucketColors.setOnCheckedChangeListener { _, v ->
            SettingsManager.setUnifiedBucketColors(context, v)
        }

        // Shortcut icon shape
        when (SettingsManager.getShortcutIconShape(context)) {
            SettingsManager.ShortcutIconShape.SQUARE    ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeSquare)
            SettingsManager.ShortcutIconShape.SQUIRCLE  ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeSquircle)
            SettingsManager.ShortcutIconShape.CIRCLE    ->
                binding.toggleShortcutIconShape.check(R.id.btnShapeCircle)
        }
        binding.toggleShortcutIconShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val shape = when (checkedId) {
                R.id.btnShapeSquare   -> SettingsManager.ShortcutIconShape.SQUARE
                R.id.btnShapeCircle   -> SettingsManager.ShortcutIconShape.CIRCLE
                else                  -> SettingsManager.ShortcutIconShape.SQUIRCLE
            }
            if (shape != SettingsManager.getShortcutIconShape(context)) {
                SettingsManager.setShortcutIconShape(context, shape)
                SettingsManager.setIconCacheDirty(context, true)
            }
        }

        binding.switchDimStatusBar.isChecked = SettingsManager.getDimStatusBar(context)
        binding.switchDimStatusBar.setOnCheckedChangeListener { _, v ->
            SettingsManager.setDimStatusBar(context, v)
        }
    }

    private fun sliderDelegate(description: String) =
        object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = description
            }
        }
}
```

- [ ] **Step 3: Create `OrderPanel.kt`**

```kotlin
package com.hexgrid.launcher.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.PanelOrderBinding
import com.hexgrid.launcher.util.SettingsManager

/**
 * Order panel: sort order chip group, search position chip group, voice search switch.
 */
class OrderPanel(private val context: Context) {

    private var _binding: PanelOrderBinding? = null
    val view: View get() = _binding!!.root

    fun attach(parent: ViewGroup) {
        val binding = PanelOrderBinding.inflate(LayoutInflater.from(context), parent, true)
        _binding = binding
        setup(binding)
    }

    fun detach(parent: ViewGroup) {
        _binding?.root?.let { parent.removeView(it) }
        _binding = null
    }

    private fun setup(binding: PanelOrderBinding) {
        // Sort order
        when (SettingsManager.getSortOrder(context)) {
            SettingsManager.SortOrder.NAME              ->
                binding.toggleSortOrder.check(R.id.btnSortName)
            SettingsManager.SortOrder.USAGE_FREQUENCY   ->
                binding.toggleSortOrder.check(R.id.btnSortFrequency)
            SettingsManager.SortOrder.USAGE_TIME        ->
                binding.toggleSortOrder.check(R.id.btnSortTime)
            SettingsManager.SortOrder.NOTIFICATION_COUNT ->
                binding.toggleSortOrder.check(R.id.btnSortNotifications)
        }
        binding.toggleSortOrder.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val order = when (checkedId) {
                R.id.btnSortName          -> SettingsManager.SortOrder.NAME
                R.id.btnSortFrequency     -> SettingsManager.SortOrder.USAGE_FREQUENCY
                R.id.btnSortTime          -> SettingsManager.SortOrder.USAGE_TIME
                else                      -> SettingsManager.SortOrder.NOTIFICATION_COUNT
            }
            SettingsManager.setSortOrder(context, order)
        }

        // Search position
        when (SettingsManager.getSearchPosition(context)) {
            SettingsManager.SearchPosition.NONE   ->
                binding.toggleSearchPosition.check(R.id.btnSearchNone)
            SettingsManager.SearchPosition.TOP    ->
                binding.toggleSearchPosition.check(R.id.btnSearchTop)
            SettingsManager.SearchPosition.BOTTOM ->
                binding.toggleSearchPosition.check(R.id.btnSearchBottom)
        }
        binding.toggleSearchPosition.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val pos = when (checkedId) {
                R.id.btnSearchTop    -> SettingsManager.SearchPosition.TOP
                R.id.btnSearchBottom -> SettingsManager.SearchPosition.BOTTOM
                else                 -> SettingsManager.SearchPosition.NONE
            }
            SettingsManager.setSearchPosition(context, pos)
        }

        // Voice search
        binding.switchVoiceSearch.isChecked = SettingsManager.getSearchWithMic(context)
        binding.switchVoiceSearch.setOnCheckedChangeListener { _, v ->
            SettingsManager.setSearchWithMic(context, v)
        }
    }
}
```

- [ ] **Step 4: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/edit/
git commit -m "feat(edit-mode): add ShapePanel, StylePanel, OrderPanel kotlin classes"
```

---

### Task 11: EditModeOverlay.kt + enterEditMode / exitEditMode in MainActivity

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/ui/EditModeOverlay.kt`
- Modify: `app/src/main/java/com/hexgrid/launcher/MainActivity.kt`
- Create: `app/src/test/java/com/hexgrid/launcher/ui/EditModeOverlayInterceptTest.kt`

- [ ] **Step 1: Write the failing touch-intercept unit test**

Create `app/src/test/java/com/hexgrid/launcher/ui/EditModeInterceptTest.kt`:

```kotlin
package com.hexgrid.launcher.ui

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the bounding-rect logic used by EditModeOverlay.onInterceptTouchEvent.
 * Pure Kotlin — no Android context required.
 */
class EditModeInterceptTest {

    /**
     * Mirrors the intercept decision inside EditModeOverlay:
     * - Inside panel or toolbar rect → return false (let child views handle it)
     * - Outside both rects          → return true (consume: no stray grid interaction)
     */
    private fun shouldIntercept(x: Float, y: Float, panelRect: Rect, toolbarRect: Rect): Boolean {
        return !panelRect.contains(x.toInt(), y.toInt()) &&
               !toolbarRect.contains(x.toInt(), y.toInt())
    }

    private val panel   = Rect(50, 400, 350, 600)
    private val toolbar = Rect(80, 620, 320, 680)

    @Test
    fun `touch inside panel is NOT intercepted`() {
        assertFalse(shouldIntercept(200f, 500f, panel, toolbar))
    }

    @Test
    fun `touch inside toolbar is NOT intercepted`() {
        assertFalse(shouldIntercept(200f, 650f, panel, toolbar))
    }

    @Test
    fun `touch outside both rects IS intercepted`() {
        assertTrue(shouldIntercept(10f, 10f, panel, toolbar))
    }

    @Test
    fun `touch on panel edge is NOT intercepted`() {
        assertFalse(shouldIntercept(50f, 400f, panel, toolbar))
    }

    @Test
    fun `touch between panel and toolbar IS intercepted`() {
        assertTrue(shouldIntercept(200f, 610f, panel, toolbar))
    }
}
```

- [ ] **Step 2: Run test — expect PASS**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew test --tests "com.hexgrid.launcher.ui.EditModeInterceptTest" 2>&1 | tail -20
```

Expected: 5 tests PASS

- [ ] **Step 3: Create `EditModeOverlay.kt`**

```kotlin
package com.hexgrid.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hexgrid.launcher.MainActivity
import com.hexgrid.launcher.databinding.OverlayEditModeBinding
import com.hexgrid.launcher.ui.edit.OrderPanel
import com.hexgrid.launcher.ui.edit.ShapePanel
import com.hexgrid.launcher.ui.edit.StylePanel

/**
 * Full-screen FrameLayout overlay attached to hexGridContainer during Edit Mode.
 *
 * Touch interception strategy (spec §10.3):
 * - onInterceptTouchEvent returns true ONLY for events outside both the panelContainer
 *   and toolbarPill bounding rects. Events inside those rects fall through normally
 *   so sliders, switches, and chip groups receive their drag/tap events.
 * - This means the underlying HexagonalGridView never receives touches while Edit Mode
 *   is active, except via its own long-press callback (which is blocked at the overlay).
 *
 * Panel switching (spec §2 animation):
 * - A single [currentPanelAnimator] reference is cancelled before each new transition
 *   to prevent ghost-panel stacking on rapid mode changes.
 *
 * Long-press-on-icon affordance (spec §2):
 * - A GestureDetector on the overlay detects long-press in the open area.
 *   It fires haptic feedback and shows a transient "Exit edit mode · Done" chip.
 */
class EditModeOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : android.widget.FrameLayout(context, attrs) {

    enum class Mode { SHAPE, STYLE, ORDER }

    private val binding: OverlayEditModeBinding =
        OverlayEditModeBinding.inflate(LayoutInflater.from(context), this, true)

    private val shapePanel = ShapePanel(context)
    private val stylePanel = StylePanel(context)
    private val orderPanel = OrderPanel(context)

    // Single animator reference — cancelled before each new panel transition.
    private var currentPanelAnimator: Animator? = null

    // Toolbar entry/exit animator — interruptible.
    private var toolbarAnimator: Animator? = null

    private var currentMode: Mode? = null

    // Bounding rects used for touch interception — updated in onLayout.
    private val panelRect   = Rect()
    private val toolbarRect = Rect()

    // Callback fired when Done or More is tapped. Wired in MainActivity.enterEditMode().
    var onDone:  (() -> Unit)? = null
    var onMore:  (() -> Unit)? = null

    // Long-press gesture detector on the overlay background (non-panel, non-toolbar areas).
    private val longPressDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Only fire if the long-press is NOT inside panel or toolbar.
                if (!panelRect.contains(e.x.toInt(), e.y.toInt()) &&
                    !toolbarRect.contains(e.x.toInt(), e.y.toInt())) {
                    showBlockedIconChip()
                }
            }
        })

    init {
        setupToolbarButtons()
        // Initial state: toolbar invisible (animated in by enterEditMode).
        binding.toolbarPill.alpha = 0f
        binding.toolbarPill.translationY = 80f.dpToPx()
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Animates the toolbar in and switches to the initial mode (Shape). */
    fun show(initialMode: Mode = Mode.SHAPE) {
        toolbarAnimator?.cancel()
        toolbarAnimator = binding.toolbarPill.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { setMode(initialMode) }
            .also { it.start() }
    }

    /** Animates the toolbar out; calls [onComplete] when done. */
    fun hide(onComplete: () -> Unit) {
        toolbarAnimator?.cancel()
        toolbarAnimator = binding.toolbarPill.animate()
            .alpha(0f)
            .translationY(80f.dpToPx())
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(onComplete)
            .also { it.start() }
    }

    /** Switches the active mode panel with a 150ms cross-fade. */
    fun setMode(mode: Mode) {
        if (mode == currentMode) return

        // Cancel any in-flight panel animator before starting a new transition.
        currentPanelAnimator?.cancel()

        val panelContainer = binding.panelContainer

        // Detach old panel.
        when (currentMode) {
            Mode.SHAPE -> shapePanel.detach(panelContainer)
            Mode.STYLE -> stylePanel.detach(panelContainer)
            Mode.ORDER -> orderPanel.detach(panelContainer)
            null       -> { /* first open — no previous panel */ }
        }

        currentMode = mode

        // Update toolbar button selected state.
        binding.btnShape.isSelected = (mode == Mode.SHAPE)
        binding.btnStyle.isSelected = (mode == Mode.STYLE)
        binding.btnOrder.isSelected = (mode == Mode.ORDER)

        // Apply primary bg to active button, secondary to others.
        listOf(binding.btnShape, binding.btnStyle, binding.btnOrder).forEach { btn ->
            btn.setBackgroundResource(
                if (btn.isSelected) com.hexgrid.launcher.R.drawable.tile_squircle_primary
                else                com.hexgrid.launcher.R.drawable.tile_squircle_secondary
            )
        }

        // Attach new panel at alpha 0, then animate to 1.
        val newPanel = when (mode) {
            Mode.SHAPE -> shapePanel
            Mode.STYLE -> stylePanel
            Mode.ORDER -> orderPanel
        }
        newPanel.attach(panelContainer)
        newPanel.view.alpha = 0f
        if (panelContainer.childCount == 1) {
            // First open: slide up from below.
            newPanel.view.translationY = 40f.dpToPx()
        }

        currentPanelAnimator = ObjectAnimator.ofFloat(newPanel.view, View.ALPHA, 0f, 1f).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    newPanel.view.animate().translationY(0f).setDuration(150).start()
                }
            })
            start()
        }
    }

    // ── Insets ─────────────────────────────────────────────────────────────────

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val systemBars = compat.getInsets(WindowInsetsCompat.Type.systemBars())
        val tappable   = compat.getInsets(WindowInsetsCompat.Type.tappableElement())

        // Toolbar bottom margin: system bars + 16dp, floored at tappable + 8dp (gesture nav).
        val marginDp16 = (16f * resources.displayMetrics.density).toInt()
        val marginDp8  = (8f  * resources.displayMetrics.density).toInt()
        val bottomMargin = maxOf(
            systemBars.bottom + marginDp16,
            tappable.bottom   + marginDp8
        )

        (binding.toolbarPill.layoutParams as LayoutParams).apply {
            bottomMargin = bottomMargin
        }
        binding.toolbarPill.requestLayout()

        // Panel container sits above toolbar: margin = toolbar height + 8dp.
        val toolbarHeightPx = (72f * resources.displayMetrics.density).toInt() // 56dp button + 8dp padding x2
        (binding.panelContainer.layoutParams as LayoutParams).apply {
            this.bottomMargin = bottomMargin + toolbarHeightPx + marginDp8
        }
        binding.panelContainer.requestLayout()

        return super.onApplyWindowInsets(insets)
    }

    // ── Touch interception ─────────────────────────────────────────────────────

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Update bounding rects after layout so intercept logic is accurate.
        val panelContainer = binding.panelContainer
        panelRect.set(
            panelContainer.left, panelContainer.top,
            panelContainer.right, panelContainer.bottom
        )
        val toolbar = binding.toolbarPill
        toolbarRect.set(toolbar.left, toolbar.top, toolbar.right, toolbar.bottom)
    }

    /**
     * Intercepts touches OUTSIDE the panel and toolbar to prevent accidental grid
     * interaction (spec §10.3). Returns false for touches inside either control surface
     * so child views receive drag events naturally.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        val insidePanel   = panelRect.contains(x, y)
        val insideToolbar = toolbarRect.contains(x, y)
        if (!insidePanel && !insideToolbar) {
            // Pass to long-press detector before consuming.
            longPressDetector.onTouchEvent(ev)
            return true // Consume — grid does not receive this event.
        }
        return false // Let child views handle slider drags, chip taps, etc.
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun setupToolbarButtons() {
        binding.btnShape.setOnClickListener { setMode(Mode.SHAPE) }
        binding.btnStyle.setOnClickListener { setMode(Mode.STYLE) }
        binding.btnOrder.setOnClickListener { setMode(Mode.ORDER) }
        binding.btnDone.setOnClickListener  { onDone?.invoke() }
        binding.btnMore.setOnClickListener  { onMore?.invoke() }

        // Content descriptions already set in XML; also mark More/Done as non-mode buttons.
        binding.btnMore.isSelected = false
        binding.btnDone.isSelected = false
    }

    /**
     * Shows the "blocked icon" chip for 2 seconds when the user long-presses where an
     * icon would be (spec §2 "Long-press on icon while overlay is attached").
     */
    private fun showBlockedIconChip() {
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        val chip = com.google.android.material.button.MaterialButton(context).apply {
            text = "Exit edit mode to manage apps · Done"
            setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
            setOnClickListener { onDone?.invoke() }
        }

        val params = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (toolbarRect.height() + (80f * resources.displayMetrics.density).toInt())
        }
        addView(chip, params)

        Handler(Looper.getMainLooper()).postDelayed({
            removeView(chip)
        }, 2000)
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}
```

- [ ] **Step 4: Implement `enterEditMode()` and `exitEditMode()` in `MainActivity.kt`**

Replace the stub `enterEditMode()` with the full implementation and add `exitEditMode()`. Also update `editModeOverlay` field type from `View?` to `EditModeOverlay?`.

```kotlin
// Replace the field declaration added in Chunk 4:
// OLD: private var editModeOverlay: View? = null
// NEW:
private var editModeOverlay: EditModeOverlay? = null

// Back handler callback — kept so we can unregister it on exit.
private var editModeBackCallback: androidx.activity.OnBackPressedCallback? = null

fun enterEditMode() {
    if (editModeOverlay != null) return // Already in Edit Mode.

    val overlay = EditModeOverlay(this)
    overlay.onDone = { exitEditMode() }
    overlay.onMore = {
        exitEditMode()
        startActivity(Intent(this, SettingsHubActivity::class.java))
    }

    val params = android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
    )
    binding.hexGridContainer.addView(overlay, params)
    editModeOverlay = overlay

    // Dispatch insets so the toolbar respects navigation bar height.
    ViewCompat.requestApplyInsets(overlay)

    overlay.show(EditModeOverlay.Mode.SHAPE)

    // Register back callback so back-press exits Edit Mode instead of popping the stack.
    val callback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            exitEditMode()
        }
    }
    onBackPressedDispatcher.addCallback(this, callback)
    editModeBackCallback = callback
}

fun exitEditMode() {
    val overlay = editModeOverlay ?: return
    overlay.hide {
        binding.hexGridContainer.removeView(overlay)
        editModeOverlay = null
    }
    editModeBackCallback?.remove()
    editModeBackCallback = null

    // Flush any pending dark-theme recreate that was deferred during Edit Mode.
    if (pendingDarkThemeRecreate) {
        pendingDarkThemeRecreate = false
        recreate()
    }
}
```

Add required import: `import com.hexgrid.launcher.ui.EditModeOverlay`

- [ ] **Step 5: Handle `EXTRA_ENTER_EDIT_MODE` in `onCreate()` and `onNewIntent()`**

In `onCreate()`, after `handlePlacementIntent(intent)`, add:

```kotlin
// Enter Edit Mode if launched from the Settings Hub hero tile.
if (intent?.getBooleanExtra(EXTRA_ENTER_EDIT_MODE, false) == true) {
    intent.removeExtra(EXTRA_ENTER_EDIT_MODE)
    // Post to run after the layout pass so hexGridContainer has measured dimensions.
    binding.root.post { enterEditMode() }
}
```

In `onNewIntent()`, after the existing `handlePlacementIntent(it)` call, add:

```kotlin
if (it.getBooleanExtra(EXTRA_ENTER_EDIT_MODE, false) == true) {
    it.removeExtra(EXTRA_ENTER_EDIT_MODE)
    binding.root.post { enterEditMode() }
}
```

- [ ] **Step 6: Build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run all unit tests**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS (EmptyAreaLongPressTest × 5, EditModeInterceptTest × 5)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/EditModeOverlay.kt
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt
git add app/src/test/java/com/hexgrid/launcher/ui/EditModeInterceptTest.kt
git commit -m "feat(edit-mode): implement EditModeOverlay with toolbar, panel switching, touch interception, and enterEditMode/exitEditMode in MainActivity"
```

---

## Chunk 6: Manual QA + Final Smoke Test

### Task 12: QA checklist and version bump

**Files:**
- Modify: `app/build.gradle.kts` (versionCode + versionName)

No new code. Walk through all behaviors described in spec §2, §3, §6 to confirm they work end-to-end.

- [ ] **Step 1: Full debug build + install**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` and `app-debug.apk` generated. Install on a device (API 26+).

- [ ] **Step 2: QA — Edit Mode via long-press**
  - Set HexGrid as default launcher
  - Long-press on an empty grid cell (not an icon, not a widget)
  - Verify: haptic feedback fires, Edit Mode overlay appears with Shape panel open, toolbar shows 5 buttons, Shape button is highlighted with primary color

- [ ] **Step 3: QA — Shape panel controls**
  - Drag the Hex radius slider left/right → grid redraws live behind the overlay
  - Drag Icon size and Corner radius sliders → grid updates in real time
  - Tap Flat toggle → grid redraws with flat-top orientation; tap Pointy → switches back
  - TalkBack: slider should announce "Hex radius: Xdp" with each change

- [ ] **Step 4: QA — Style panel controls**
  - Tap Style button → panel switches with cross-fade (not instant, 150ms visible)
  - Toggle Show outline → outline appears/disappears in grid
  - Toggle Dark theme → nothing changes immediately; a note confirms "(applied on exit)"
  - Tap Done → overlay exits; dark theme flips (recreate fires)

- [ ] **Step 5: QA — Order panel controls**
  - Tap Order button → panel switches
  - Change Sort order chip → grid re-sorts live
  - Change Search bar → dock repositions

- [ ] **Step 6: QA — Exit paths**
  - Tap Done → overlay exits, grid is visible, no crash
  - Re-enter Edit Mode, tap More → overlay exits, SettingsHubActivity opens
  - Re-enter Edit Mode, press system back → overlay exits
  - Confirm: after any exit path, long-press on empty area can re-enter Edit Mode

- [ ] **Step 7: QA — Blocked icon long-press affordance**
  - Enter Edit Mode, long-press on a grid position where an icon is visible beneath the overlay
  - Verify: haptic fires, "Exit edit mode to manage apps · Done" chip appears at bottom
  - Chip auto-dismisses after ~2 seconds
  - Tap chip while visible → Edit Mode exits

- [ ] **Step 8: QA — Settings Hub**
  - Tap gear icon in dock → SettingsHubActivity opens with hero tile + 6 action tiles
  - Tap hero tile → MainActivity comes to front, Edit Mode opens
  - Back-press → Edit Mode exits, returns to launcher home (not to Hub)
  - Tap Widgets → WidgetManagementActivity opens; switch "Show widgets during search" persists
  - Tap Manage Apps → AppVisibilityActivity opens
  - Tap Permissions → system notification listener settings opens
  - Tap Default Launcher → system role picker opens (if not already default)
  - Tap Export → file picker opens; save JSON; verify file contains valid JSON
  - Tap Import → file picker opens; pick previously exported file; settings restore; Toast shown

- [ ] **Step 9: QA — Permission badges**
  - Revoke notification listener access → Permissions tile shows red badge dot
  - Grant notification access → re-open Hub → badge gone
  - Set a different app as default launcher → Default Launcher tile shows red badge dot

- [ ] **Step 10: Bump version in `app/build.gradle.kts`**

Find:
```kotlin
versionCode = 1
versionName = "1.0"
```
Replace with:
```kotlin
versionCode = 2
versionName = "1.1"
```

- [ ] **Step 11: Final full build verify**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump versionCode 1→2, versionName 1.0→1.1 (Settings & Edit Mode release)"
```

---

## Critical Implementer Notes

These items must be addressed during implementation — do not defer to a follow-up cycle.

1. **Touch interception uses `onInterceptTouchEvent`** (NOT `onTouchEvent`). The override in `EditModeOverlay` returns `true` only for events outside `panelRect` and `toolbarRect`, allowing sliders to receive drag events normally. Test by dragging a slider while a hex cell is visually behind it — the grid must not scroll.

2. **Animator interruptibility**: `currentPanelAnimator?.cancel()` is called before every new panel transition in `setMode()`. Rapid mode switching (tap Shape → Style → Order in quick succession) must not stack ghost panels.

3. **WindowInsetsCompat-aware toolbar**: `onApplyWindowInsets` computes `bottomMargin = max(systemBars.bottom + 16dp, tappable.bottom + 8dp)`. On gesture-nav devices the tappable inset covers ~32dp; the floor prevents the pill entering the OS gesture zone. Call `ViewCompat.requestApplyInsets(overlay)` in `enterEditMode()` after adding to container.

4. **Dark theme deferred recreate**: `prefsListener` checks `if (editModeOverlay == null) recreate() else pendingDarkThemeRecreate = true`. `exitEditMode()` flushes with `if (pendingDarkThemeRecreate) { pendingDarkThemeRecreate = false; recreate() }`. Both branches are shown in Task 8 and Task 11 code.

5. **Slider accessibility**: Each slider's `addOnChangeListener` updates a `ViewCompat.setAccessibilityDelegate` with the current value + unit string (e.g., `"Hex radius: 80dp"`). This covers the spec §2 accessibility requirement without overriding `Slider` behaviour.

6. **Hero tile back-stack**: Intent uses `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`. `MainActivity` has `launchMode="singleTask"` in the manifest (confirmed in source). This brings the existing instance to front and finishes `SettingsHubActivity`, so back from Edit Mode returns to launcher home, not to the Hub. QA step 8 verifies this.

7. **`hex_orientation` spike result**: `recomputeGridAndInvalidate()` calls `binding.hexGrid.refreshSettings()` which already recomputes the spiral and invalidates. No `recreate()` needed. Live-preview works for orientation — confirmed from `HexagonalGridView.refreshSettings()` source.

8. **`pixelToHex` availability**: `HexagonalGridView.onLongPress` uses `findAppIndexAt` internally; the pixel→hex conversion is done inside that helper. For the empty-area branch, the implementer should check `HexGridCalculator.kt` for a public `pixelToHex()` method; if absent, call `findAppIndexAt` with a dummy test or extract the coordinate from the existing `GestureListener` via the `apps`/`hexPositions` arrays. The unit test in Task 7 tests pure routing logic and is not affected by this lookup detail.

9. **Dim status bar key is `"dim_status_bar"`** (spec §2 note: Style panel label says "Dim status bar (applied on exit)"). The prefs listener handles this key and calls `applyStatusBarDim()` immediately — the label is a UX note that the effect is hidden behind the overlay, not that the prefs write is deferred.
