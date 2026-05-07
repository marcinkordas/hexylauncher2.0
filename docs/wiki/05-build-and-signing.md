# 05 — Build & Signing

How to build, sign, and produce a Play-uploadable AAB.

---

## Toolchain

- **Android Studio Ladybug** or newer (the project uses Gradle wrapper, so any recent IDE works).
- **JDK 17** — set in `compileOptions` and Kotlin `jvmTarget`.
- **Android SDK 34** (`compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`).
- Gradle wrapper bundled (`gradlew.bat`).

Verify locally:

```powershell
java -version          # expect 17
$env:ANDROID_HOME      # expect SDK path
.\gradlew --version
```

---

## Debug build

```powershell
cd "c:\Users\mckar\Documents\Projekty\HexGrid Launcher\hexylauncher2.0"
.\gradlew assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`. Install to a connected device with `adb install -r app-debug.apk`.

Debug builds use the auto-generated debug keystore (`~\.android\debug.keystore`). They are not Play-uploadable.

---

## Release signing — initial setup

1. **Generate the upload key** (one time, store offline):

   ```powershell
   keytool -genkey -v -keystore hexgrid-upload.jks -keyalg RSA -keysize 2048 -validity 25000 -alias hexgrid-upload
   ```

   Save the keystore file outside the repo. Save the keystore password and key password to a secure store (1Password / Infisical / encrypted USB).

2. **Create `keystore.properties`** in the project root (`hexylauncher2.0/keystore.properties`) — **gitignored**:

   ```properties
   storeFile=C:/path/to/hexgrid-upload.jks
   storePassword=...
   keyAlias=hexgrid-upload
   keyPassword=...
   ```

   Add to `.gitignore`:

   ```
   keystore.properties
   *.jks
   ```

3. **Wire it into `app/build.gradle.kts`** — add at the top of the file, before `android { ... }`:

   ```kotlin
   import java.util.Properties
   import java.io.FileInputStream

   val keystorePropertiesFile = rootProject.file("keystore.properties")
   val keystoreProperties = Properties().apply {
       if (keystorePropertiesFile.exists()) {
           load(FileInputStream(keystorePropertiesFile))
       }
   }
   ```

   Inside `android { ... }`, add:

   ```kotlin
   signingConfigs {
       create("release") {
           if (keystorePropertiesFile.exists()) {
               storeFile = file(keystoreProperties["storeFile"] as String)
               storePassword = keystoreProperties["storePassword"] as String
               keyAlias = keystoreProperties["keyAlias"] as String
               keyPassword = keystoreProperties["keyPassword"] as String
           }
       }
   }
   ```

   Then update `buildTypes.release`:

   ```kotlin
   buildTypes {
       release {
           isMinifyEnabled = true
           isShrinkResources = true
           proguardFiles(
               getDefaultProguardFile("proguard-android-optimize.txt"),
               "proguard-rules.pro"
           )
           signingConfig = signingConfigs.getByName("release")
       }
   }
   ```

4. **Add Play App Signing**: when uploading the first AAB, opt in. Google holds the app signing key; you keep the upload key. If the upload key is ever lost, Google can rotate it.

---

## Release build (AAB — for Play)

```powershell
.\gradlew bundleRelease
```

Output: `app\build\outputs\bundle\release\app-release.aab`.

This is what gets uploaded to Play Console.

---

## Release APK (for sideload distribution / GitHub Release)

```powershell
.\gradlew assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`.

Don't ship this APK to Play — Play wants the AAB. The APK is for direct download / sideload / F-Droid.

---

## ProGuard / R8 — known sensitive areas

When `isMinifyEnabled = true` is first switched on, validate that these don't get stripped:

- `MainActivity` inner subclasses: `packageChangeReceiver`, `installShortcutReceiver`, the `LauncherApps.Callback`. They are referenced via reflection only inside the framework — but they're declared as Kotlin object expressions inside an Activity, so they should survive. **Verify after the first release run.**
- `WidgetEntry` — currently serialized via hand-rolled JSON in `WidgetStore.entriesToJson()` / `parseEntries()` (no reflection), so should not need a keep rule.
- `LiveClockDrawable` — referenced from XML drawables; XML-referenced classes need keep rules. Add to `proguard-rules.pro` if it disappears: `-keep class com.hexgrid.launcher.ui.LiveClockDrawable { *; }`.
- `PreferenceFragmentCompat` subclasses (`SettingsFragment`) — already handled by androidx default keep rules.
- `NotificationListener` service — declared in manifest; should be kept by manifest scan.

If the release APK crashes immediately on `ClassNotFoundException`, add the missing class to `proguard-rules.pro` and rebuild.

---

## Versioning

In `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 1     // monotonically increasing integer for Play
    versionName = "1.0" // human-readable
}
```

Bump rules:

- Patch fix → `versionName "1.0.1"`, `versionCode 2`.
- Feature release → `versionName "1.1"`, `versionCode 10`.
- Major → `versionName "2.0"`, `versionCode 100`.

Play rejects an upload with the same `versionCode` as the last live build.

---

## Releasing to Play — flow

1. Build AAB: `.\gradlew bundleRelease`.
2. Play Console → app → **Production** → **Create new release**.
3. Upload `app-release.aab`.
4. Fill **Release notes** in `en-US`. Keep concise — what's new, what's fixed.
5. **Save → Review release → Start rollout to Production**. Choose 100% rollout, or staged (e.g. 20%).
6. First-time apps go through extended Play review (sometimes 3–7 days). Subsequent updates typically clear within 24 h.

---

## Internal testing track (recommended for v1.0)

Before pushing to Production, push to **Internal testing** first:

1. Play Console → **Testing → Internal testing → Create release**.
2. Upload the AAB.
3. Add testers by email or Google Group.
4. Each tester opts in via a one-time URL.
5. Validate on real hardware before promoting to Production via "Promote to Production" inside Play Console.

This catches Play-specific issues (signing config, manifest checks) without burning a public release.
