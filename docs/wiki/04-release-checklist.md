# 04 — Release Checklist

The punch list to ship **HexGrid Launcher 1.0** free on Google Play. Group A is the critical path. Groups B–D can move in parallel.

---

## A. Build & signing (blocks everything else)

- [ ] **Generate upload keystore** — `keytool -genkey -v -keystore hexgrid-upload.jks -keyalg RSA -keysize 2048 -validity 25000 -alias hexgrid-upload`. Store offline (1Password / encrypted USB / Infisical secret note).
- [ ] **Add `signingConfigs.release`** to `app/build.gradle.kts`, sourcing alias/passwords from environment variables or a gitignored `keystore.properties`. Do **not** commit secrets.
- [ ] **Wire `buildTypes.release.signingConfig = signingConfigs["release"]`**.
- [ ] **Enable `isMinifyEnabled = true`** for release; iterate on ProGuard rules until release APK runs end-to-end (currently `false` — release path is untested).
- [ ] **Verify ProGuard keeps**: `LauncherApps.Callback` subclasses, `BroadcastReceiver` subclasses, `WidgetEntry` (used by JSON reflection? — confirm; it's hand-rolled JSON in `WidgetStore`, so likely no keep needed), `PreferenceFragmentCompat` subclasses, `LiveClockDrawable`.
- [ ] **Build release AAB**: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- [ ] **Smoke test the signed release APK** on a physical device — install, set as default, launch ten apps, search, add a widget, reboot. The release configuration has never been validated end-to-end.

---

## B. Branding assets

User produces SVG/vector assets manually — do **not** auto-generate (memory rule).

- [ ] **App icon** — adaptive icon foreground + background; 432×432 dp safe zone. Replace `res/drawable/ic_launcher` and `res/mipmap-anydpi-v26/ic_launcher.xml` foreground/background. Source PNGs in `C:\Users\mckar\.gemini\antigravity\brain\53a75261-bdcb-4ef0-b779-bafe4622008a\` (`logo_concept_material_*.png`, `logo_concept_neon_*.png`).
- [ ] **Play Store icon** — 512×512 PNG, 32-bit, no alpha, ≤1 MB.
- [ ] **Feature graphic** — 1024×500 PNG/JPG.
- [ ] **Screenshots** — minimum 2, recommended 4–8. Phone: 16:9 or 9:16, between 320 px and 3840 px on the long side. Capture: empty grid, populated grid, search active, dock + reorder, widget placed, settings.
- [ ] **(Optional) Promo video** — YouTube URL, ≤30 s.

Existing concept art / mockups in the Gemini brain folder is fine for inspiration, not for shipping.

---

## C. Play Console listing

- [ ] **Create Google Play developer account** ($25 one-time).
- [ ] **Create the app** — package `com.hexgrid.launcher`, default language `en-US`, app type *App*, free.
- [ ] **App name** — `HexGrid Launcher` (≤30 chars).
- [ ] **Short description** — ≤80 chars. Example: *Hexagonal grid launcher with color-sorted apps, dock, and widgets. GPLv3.*
- [ ] **Full description** — ≤4000 chars. Pull from `01-overview.md` Goals + `03-features.md`. Lead with: hex layout, color sort, dock, widgets, Material You, free + open source.
- [ ] **App category** — Personalization.
- [ ] **Tags** — Launcher, Productivity, Customization.
- [ ] **Contact email** — fill in.
- [ ] **Privacy policy URL** — required (see Group D).
- [ ] **Content rating questionnaire** — Personalization launcher with no user-generated content → "Everyone".
- [ ] **Data safety form** — see Group D.
- [ ] **Target audience** — 13+ (no kids-targeted features).
- [ ] **Ads** — declare *No*.
- [ ] **In-app purchases** — *No*.
- [ ] **Government app / news app** — *No*.

---

## D. Compliance / data declarations

- [ ] **Privacy policy** — host on GitHub Pages or a static page. Must declare:
  - **No data collection or transmission.** Settings, hidden-apps list, widget config, usage stats are stored on-device only.
  - `PACKAGE_USAGE_STATS` is used locally to compute "most used" centering — never transmitted.
  - `BIND_NOTIFICATION_LISTENER_SERVICE` reads notification metadata locally for badge counts — no content is logged or sent.
  - Crash reports: declare yes/no — currently no crash reporter is integrated, so declare **no**.
- [ ] **Data safety form** in Play Console: select "No data collected" / "No data shared". This must match the privacy policy verbatim.
- [ ] **Permissions justification** — Play may flag `QUERY_ALL_PACKAGES`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `PACKAGE_USAGE_STATS`. Be ready to explain: launcher needs them to enumerate, badge, and rank user apps. There is a "Permissions Declaration Form" inside Play Console for this.
- [ ] **License visibility** — `LICENSE` (GPLv3) lives at repo root; surface it in Settings → "About" if not already.
- [ ] **Source code link** in store listing — once GitHub repo is public.

---

## E. README + GitHub repo cleanup

- [ ] Replace `yourusername` and `yourname` placeholders in `README.md` (GitHub URLs, sponsor links).
- [ ] Add real screenshots to `README.md` (`Screenshots` section currently says *Coming soon*).
- [ ] Add Play Store badge once published.
- [ ] Consider F-Droid submission (separate process, can come after Play).
- [ ] Verify `LICENSE` is GPLv3 and that all source files have appropriate headers (or none — GPLv3 doesn't require per-file headers).
- [ ] Tag a `v1.0.0` release on GitHub once the AAB is uploaded.

---

## F. Manual QA on a real device

Run TASK_LIST.md Phase 7 plus the Cycle 1 + Cycle 2 additions:

- [ ] Apps appear in hex grid; system icons render unclipped.
- [ ] Most-used app at center; 18 recents in rings 1–2.
- [ ] Color sort visible in outer rings.
- [ ] Tap launches; long-press shows context menu with Hide / Uninstall.
- [ ] Hidden app disappears from grid; reappears in `AppVisibilityActivity`.
- [ ] Pan / scroll smooth.
- [ ] **Install an app while launcher is in background → grid refreshes when returned to foreground or sooner.**
- [ ] **Uninstall an app while launcher is in foreground → grid refreshes immediately.**
- [ ] **Samsung Mode / Work profile toggle (if accessible) → restricted apps hide live.**
- [ ] **Chrome → Add to Home Screen → confirmation dialog appears → app pinned.**
- [ ] **Search "ch" → grid recenters; clear search → full grid restored at original scroll.**
- [ ] **Settings → Manage Widgets → Add → place clock widget → widget visible, scrolls with grid.**
- [ ] **Long-press widget → drag → release at new hex → position persists across reboot.**
- [ ] **Long-press widget → drag corner → release → resize persists across reboot.**
- [ ] **Settings → Export → settings JSON saved; Import on a fresh install restores hidden apps + widgets (widgetIds will reset, expected).**
- [ ] **Reboot phone with launcher set as default → grid + widgets restore.**
- [ ] Live clock icon ticks; updates after time-zone change.
- [ ] Notification badge appears on app with unread notification; clears when read.

---

## G. Post-release (nice-to-have, not blockers)

- [ ] F-Droid submission.
- [ ] Crash reporting (Sentry / Firebase Crashlytics — but check GPLv3 compatibility and update privacy policy if added).
- [ ] Translations (i18n) — start with PL, DE, ES.
- [ ] Compose migration (long-term).
- [ ] Long-press empty area → Add Widget shortcut (Cycle 2 deferred entry point).
