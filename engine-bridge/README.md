# Game-engine Main Room clock bridge (v0.4, proof-of-concept)

## Confirmed from user's FNaF1 Android APK (private, not included here)

- `res/raw/application.ccn`: source frame `06-Main Room` is **index 5** (0-based).
- `classes.dex`: class `Application.CRunApp` has public field `int currentFrame`.
- `CRunApp.startTheFrame()` loads a new frame, sets `currentFrame`, constructs `RunLoop.CRun`, and invokes `CRun.initRunLoop()`.
- `CRun.initRunLoop()` invokes `CRun.f_InitLoop()`. `f_InitLoop()` invokes `System.currentTimeMillis()` and initializes `rhTimerOld`, `rhTimer`, `rhLoopCount`. Then `initRunLoop()` sets `rhTimerOld` again before returning.
- `Runtime.MMFRuntime.inst` is a **public static** field of an Android Activity class, so it can send a targeted broadcast.

Therefore the in-process insertion point **immediately after `CRun.initRunLoop()I` completes, guarded by `currentFrame == 5`**, is a far stronger frame-start reference than recognizing a 12 AM image or timing a READY tap.

## Exact signalling protocol

Insert `MainRoomStart.smali` into the **user-owned** game DEX and inject:

```smali
invoke-static {p0}, Lsovereign/bridge/MainRoomStart;->emit(LApplication/CRunApp;)V
```

immediately **after** the `initRunLoop()I` invocation's `move-result` (do not break the DEX invoke/move-result adjacency).

The helper sends an explicit broadcast addressed to `dev.sovereign.fnaf`, action:

`dev.sovereign.fnaf.INTERNAL_MAIN_ROOM_START`

with: `frame_index=5`, `engine_wall_ms=CRun.rhTimerOld`, and **`epoch_elapsed_ns=SystemClock.elapsedRealtimeNanos()` sampled inside the game process**.

The v0.4 Android controller includes `GameFrameBridgeReceiver`. It checks the transmitted frame index and epoch, rejects overly late signals, and calls `SovereignService.runSynchronizedWave(epoch)`, subtracting the already elapsed time from every scheduled absolute offset.

This **does not** set the epoch to the time the receiver sees the message. Transmission delay does not shift all scheduled events.

## Local patch outline (DO NOT upload the game's copyrighted APK to this public repo)

The owner's original Play-installation is a **split APK**: `base.apk` plus configuration splits; all installed splits must be signed consistently if modifying the game.

1. Extract `classes.dex` from the owner's `base.apk` **locally**.
2. Disassemble with a compatible `baksmali` version. Example: `java -jar baksmali.jar disassemble classes.dex -o smali-base`.
3. `python3 engine-bridge/patch_smali.py smali-base` — this adds the helper and instruments `Application/CRunApp.smali`. It refuses to patch if the expected method signature is not found exactly once.
4. Reassemble with `java -jar smali.jar assemble smali-base -o classes-patched.dex`.
5. Replace only `classes.dex` in **a local copy** of `base.apk`. Do not publish or commit any of the original APK bytes.
6. Re-sign the modified base and all owned split APKs using the same local Android signing key. Install the compatible bundle. Backup the original installation/data before any signature-change removal.
7. Enable the controller's accessibility service and arm execution. Launch 4/20 in the modified game and look for a log beginning `[ENGINE]` showing the `Main Room` signal. There should be **no** need to press volume+ or share a screen.

### What remains unverified

The companion's v0.4 APK **builds successfully**, but the patched game itself has **not** yet been assembled/installed/tested. This integration requires in-process instrumentation; an ordinary separate non-root Android app cannot directly read `currentFrame` or `rhTimer` from the original app. The game's build contains Play protection (`libpairipcore.so`), so re-signing may fail installation or run-time integrity checks; no modified-game success is claimed.

Start notifications align to the **frame initialization boundary**. If runtime freezes/pauses or its event timers' phase differs, further instrumentation should sample the game's own `CRun.rhTimer` and event loop, rather than assuming a perfect external wall clock over the entire 540-second night.
