<img src="docs/media/nav-icon.svg" width="72" height="72" align="left" alt="Flipper Next Turn icon">

# Flipper Next Turn

**Your next turn, on your Flipper.** Live Google Maps maneuvers — road name, turn arrow, and distance — on the pocket display. Companion music display included via the shared helper.

**Bluetooth · Navigation + Media** &nbsp; | &nbsp; **Release v1.0** &nbsp; | &nbsp; **Flipper Zero + Android 8.0+**

### Download & install

**[⬇ Download the Android app](https://github.com/villenull/flipper-next-turn/releases/download/v1.0/flipper-now-debug.apk)** — one **Flipper Now** helper for both companions. Open this link on your phone, download, and tap to install.

**[⬇ Download the Now Turning Flipper app (.fap)](https://github.com/villenull/flipper-next-turn/releases/download/v0.2/nav_turns-fw1.4.3-api87.1.fap)** · [All release files](https://github.com/villenull/flipper-next-turn/releases/tag/v1.0) · [Checksums](https://github.com/villenull/flipper-next-turn/releases/download/v0.2/SHA256SUMS)

> Built for official Flipper firmware **1.4.3 / API 87.1**. Now Playing is device-validated; the live Maps drive test is still open.

## Screen preview

![Flipper Next Turn orange concept render with turn arrow on the left and maneuver details on the right](docs/media/nav-next-turn.png)

Simulated layout preview following the production renderer geometry. The actual 128×64 display is coarser; see the [native 128×64 example](docs/media/nav-next-turn-native.png).

The helper app is deliberately minimal: a permission box, one **PAIR FLIPPER** button, and a status box per companion (grey empty, amber working, green paired). Everything else lives behind the gear icon.

## What does Flipper Next Turn do?

Flipper Next Turn turns your Flipper Zero into a handlebar-style navigator. The Flipper Now helper reads the Google Maps navigation notification and sends each maneuver over a dedicated Bluetooth connection. The same helper also drives the **Now Playing** music display.

- **Turn arrow** for 14 maneuver types: left/right, slight/sharp, U-turn, roundabout, exit, merge, keep, ferry, arrival.
- **Road name and instruction** beside the arrow, scrolling when long.
- **Big live distance** counting down to the maneuver.
- **Rerouting and arrival states** straight from Maps.
- **Now Playing**: album artwork, track details, progress, and physical-button controls from Apple Music or another player.

Google Maps, Apple Music, and the official Flipper Android app stay unchanged. Separate external FAPs plus one helper APK; no firmware fork.

## How to use

1. **Install the Android app.** Download the APK above on your phone and tap it. If Android prompts, allow installation from your browser. It upgrades any previous helper in place.
2. **Enable everything (one tap).** Tap **Enable services**, approve the Bluetooth prompts, and enable notification access for **Flipper Now** in the system screen it opens. Sideloaded apps may need the system App info → `⋮` → **Allow restricted settings** first, or Android blocks the toggle.
3. **Copy the Flipper apps.** With the companion closed, use qFlipper to copy the FAPs: `SD Card/apps/Bluetooth/nav_turns.fap` for Now Turning, `now_playing.fap` for Now Playing. No firmware flashing is involved.
4. **Open one companion on the Flipper.** **Apps → Bluetooth → Now Turning** (or Now Playing). Disconnect any active management connection in the official Flipper Android app. Only one companion runs at a time.
5. **Pair (one tap).** Tap **PAIR FLIPPER** — the helper scans for both companion signals, recognizes which app is open, saves it and starts. Confirm the matching pairing code on both screens (the phone popup sometimes hides in the notification shade — pull it down).
6. **Navigate or play.** Start driving directions in Google Maps, or music in your player. Each update appears once the link is ready.

If a system Bluetooth entry for the Flipper goes stale after failed attempts, **Forget** it in phone settings and pair again.

## Controls

**Now Turning**

| Flipper button | Action |
|---|---|
| OK | Re-request the current maneuver |
| Hold Back | Exit and restore the default Bluetooth profile |

Only the current maneuver is available from the Maps feed; Up/Down/Left/Right are reserved.

**Now Playing**

| Flipper button | Action |
|---|---|
| Up / Down | Volume up / down; hold to repeat |
| Left / Right | Previous / next track |
| OK | Play / pause |
| Hold Back | Exit and restore the default Bluetooth profile |

## Privacy & requirements

- Android **8.0 / API 26 or newer**; Bluetooth and user-enabled notification access.
- Flipper Zero with an SD card and **official firmware 1.4.3 / API 87.1**.
- No Internet permission, backend, accounts, root, analytics, or history databases. Only Google Maps navigation notifications and the selected media session are read.
- Each companion uses its own Bluetooth identity (`NP…` / `NT…` adverts) and bond storage.

## Validation status

The local build passes **NAV/1 golden vectors**, **Maps parser cases**, **FNP/1 reference and cross-language suites** (722 valid + 731 malformed frames across C, Kotlin and reference), **C ASan/UBSan**, Kotlin unit tests, and Android lint with no errors or warnings. Both FAPs import only officially exported API symbols.

**Device evidence:** Now Playing pairs and exchanges metadata/controls with a real phone; the Now Turning advert (`NT…`, service UUID `04b78bcf-…`) is confirmed on air by an independent BLE scan. **Still open:** the live Maps drive test and nav pairing completion.

Protocol details: `protocol/nav_constants.json` (NAV/1), `protocol/constants.json` (FNP/1).

## Changelog

### v1.0

- Minimal centered UI: permission box plus half-width companion boxes (grey/amber/green); settings behind the gear icon.
- Lowercase `now` wordmark launcher icon.

### v0.9 – v0.7

- One-tap setup (ENABLE EVERYTHING, auto-pick PAIR), single PAIR button scanning both signals, dark Flipper-style UI, unified Flipper Now helper for both companions.

### v0.2 – v0.1

- Initial NAV/1 previews: wire protocol, 14-arrow renderer, Maps scraper, dedicated BLE service, short advert fix from the Now Playing radio investigation.

## Build from source

```sh
./scripts/bootstrap.py --accept-android-sdk-license
source scripts/env.sh && cd android && ./gradlew :navcore:test :nowapp:assembleDebug
(cd .cache/flipper-firmware && ./fbt fap_nav_turns fap_now_playing)
./scripts/test_all.sh
```

Read the Android SDK license before supplying its acceptance flag. Toolchains are pinned in [`.toolchains.lock.json`](.toolchains.lock.json). Builds never flash firmware or install onto a device.

## Credits & references

- [Official Flipper firmware](https://github.com/flipperdevices/flipperzero-firmware): exported API, BLE infrastructure, Canvas and native fonts.
- [Android notification-listener APIs](https://developer.android.com/reference/android/service/notification/NotificationListenerService): navigation notification access.
- Sibling project [flipper-now-playing](https://github.com/villenull/flipper-now-playing): the Now Playing lineage, shared architecture, and radio lessons.

The catalog icon and orange preview are generated by [`docs/media/render_nav.py`](docs/media/render_nav.py) following the production renderer geometry; they are simulated previews, not device captures.

[GPL-3.0 license](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Report an issue](https://github.com/villenull/flipper-next-turn/issues)
