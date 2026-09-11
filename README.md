# Flipper Navigator — Google Maps turn-by-turn on Flipper Zero

A standalone Flipper Zero app that shows live navigation on the 128×64 screen:
**next road name, turn arrow, and distance to the turn**, fed by a small
Android helper that reads the Google Maps navigation notification. No root,
no firmware fork, no account, no Internet permission, no cloud.

> **Status: hardware-unverified preview.** The Flipper app builds clean
> against official firmware 1.4.3 / API 87.1 and the Android app builds
> clean with passing unit tests, but the live Maps drive test has not run
> yet. Expect rough edges; please report them.

## Download (v0.1)

- Android helper: `nav-turns-debug.apk` (installable dev build, v0.1)
- Flipper app: `nav_turns-fw1.4.3-api87.1.fap`

Get both from the
[releases page](https://github.com/villenull/flipper-navigator/releases/tag/v0.1).

## Install

**Phone** (Android 8+, min SDK 26): install the APK normally
(`adb install -r nav-turns-debug.apk` also works). Open **Nav Turns**:
grant Bluetooth access, enable notification access. Sideloaded apps may
need the system App info → `⋮` → **Allow restricted settings** first —
Android blocks notification access otherwise.

**Flipper** (official firmware 1.4.3): copy the `.fap` to the SD card at
`apps/Bluetooth/nav_turns.fap` (qFlipper drag-and-drop works), then open
**Apps → Bluetooth → Nav Turns**. Before using it, disconnect the official
Flipper app's active connection; it reconnects normally after you exit.

## Pair and drive

1. Flipper app open on `Waiting for route`. Phone Bluetooth on.
2. Helper → **Find Nav Turns devices** → tap the `NV…` entry → **Start**.
3. Confirm the 6-digit pairing code on **both** screens (the phone popup
   sometimes hides in the notification shade — pull it down).
4. Start navigation in Google Maps. The Flipper shows the next maneuver;
   distance counts down as you drive. **OK** on the Flipper re-requests a
   refresh; **long Back** exits and restores the normal Bluetooth profile.

Only Google Maps navigation notifications are read; nothing is stored,
uploaded, or logged beyond on-device redacted diagnostics.

## Source layout

- `flipper/nav_turns/` — the Flipper app (protocol, model, renderer,
  custom BLE GATT profile)
- `android/navcore/`, `android/navapp/` — protocol/parser core and the
  helper APK (notification scraper, BLE central, foreground service)
- `protocol/nav_*.json/.py` — NAV/1 constants, reference codec, frozen
  golden vectors, parser spec
- `flipper/host_tests/test_nav.c` — C vectors/splits/model/input tests

Wire protocol NAV/1 reuses the proven FNP1/v1 frame envelope on its own
BLE service (`04b78bcf-…`). See `protocol/nav_constants.json`.

Sibling project: [flipper-now-playing](https://github.com/villenull/flipper-now-playing)
shows Apple Music playback on the Flipper using the same architecture.

License: see `LICENSE`. Built against official Flipper firmware 1.4.3.
