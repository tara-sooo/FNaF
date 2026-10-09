# Sovereign First Wave — FNaF1 4/20 Android test

Android-only first-wave input prototype for the user's copy of FNaF1. This repository is used to build a debug APK with GitHub Actions.

**Scope:** Nine timed touch gestures over the opening ~4.8 seconds (CAM4B selection, Foxy camera refresh, left/right door closure). It is **not** a full-night auto-clear bot, and the tap locations are still configurable test values.

## Build

Open **Actions → Build Android debug APK** (or look for the automatic run after push). Download the **SovereignFirstWave-debug-apk** artifact and extract **app-debug.apk**.

The first workflow run unpacks the original Java Android Studio sources from the temporary source bundle, builds the APK, and publishes the source as a workflow artifact for inspection.

## Run

Install the debug APK, enable its Accessibility Service, configure screen bounds/touch targets, arm it, switch to FNaF1, and press Volume+ to run. Press Volume− to cancel.

The software does not include the proprietary game APK or its resources. The controller start time is not automatically synchronized to the actual game clock. This is a first-wave gesture test, not a proven strategy execution.
