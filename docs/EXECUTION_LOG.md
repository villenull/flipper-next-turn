# Execution log

## M0 — 2026-09-09
- Read AGENTS.md, START_HERE.md, all ten numbered specifications, constants, reference codec and all golden vectors. Inspected approved concept.
- Repository contains handoff only; `.git` is an inaccessible/empty managed mount, not a usable Git checkout. Preserve supplied files.
- `python3 -m unittest discover -s protocol -v`: exit 0, 27 tests passed.
- Available: GCC 16.2.1, Clang 22.1.8, Python 3.14.7. No Java, Gradle, Kotlin, CMake, Android SDK/ADB or ARM compiler on PATH; no matching cached SDK found in initial scan.
- Sandboxed `git ls-remote` failed DNS (exit 128). Authorized network retry succeeded (exit 0): official tag 1.4.3 resolves to 8622f1a2b83d8f4918dd5fa3f43de963f6d6f819.
- Downloading pinned official source/submodules into `.cache/flipper-firmware`. No device writes authorized or performed.
- Current milestone: M0/M1. Next: inspect exact source/export semantics, bootstrap project-local build tools, compile real custom GATT FAP and Android handshake.

## Validation policy
Build and host results do not establish physical pairing, bond restoration, Apple Music metadata or locked-screen volume behavior. Those remain NOT_RUN until measured on authorized devices.

## M1 — compile gate
- Firmware clone/submodules completed, exit 0. All 13 initial required BT/GATT symbols exported (+).
- `./fbt --version` downloaded the official local toolchain, exit 0; SCons 4.7.0, bundled Python 3.11.
- First `./fbt fap_now_playing`: exit 2 (missing vendor packet header and signed MIN argument). Fixed the include and size type, without disabling warnings.
- Second `./fbt fap_now_playing`: exit 0, API 87.1 up to date, APPCHK passed. Artifact: `.cache/flipper-firmware/build/f7-firmware-D/.extapps/now_playing.fap`. This is the transport milestone, not the final product.
- Downloaded checksum-verified Temurin 17.0.16+8, Gradle 8.11.1. Android SDK download/use license authorization requested through execution approval and granted; platform 36 revision 2 and build-tools 35.0.0 downloaded and checked against Google's repository SHA-1. No device writes.
- Source confirms HAL does not free profile instance after stop; custom stop owns free. Avoided BT status callback to reduce teardown races; service dispatcher handles disconnect. HAL profile reinitialization locks core2, then calls stop/unregister before BLE stack teardown.

## M2–M4 — implementation and hardening in progress
- C/Kotlin production codecs independently match all 16 committed vectors; randomized comparison passes 616 valid and 619 malformed frames (`python3 scripts/compare_codecs.py`, exit 0).
- C host sanitizer tests pass every split, every one-bit corruption, 200k noise bytes, timeout, clock wrap/freeze, input press/short dedup and repeat bounds. LSan excluded for ptrace limitation only.
- Android debug APK and test-player APK compiled; first lint failed on API 27 notification-access API with minSdk 26. Added API 26 Settings.Secure component check. Five initial core tests passed, expanded tests being added.
- Subsequent resource build found an apostrophe escaping error in extracted strings; fixing it (not a toolchain blocker).
- Native Canvas production renderer compiled with pinned firmware u8g2 fonts in a host adapter; rendered seven 128x64 states. Inspected approved and maximum-text screenshots. Corrected reversed previous/next triangles. Images are simulations, not device captures.
- FAP including domain/input/renderer built again with APPCHK success. Latest source changes need restaging and rebuild.
- No /dev/kvm, /dev/bus/usb, or ttyACM devices visible in initial read-only check. No emulator/device test passed or claimed.
- Current milestone: M4/M5. Next: finish callback/cleanup review, strengthen domain/transport tests, rerun lint/builds, finish reproducible scripts/hardware harness and release packaging. No final release bundle yet.

## M5 — complete primary software verification
- `./scripts/test_all.sh`: exit 0. 27 reference tests; 9 Kotlin core tests; 3 Android-module tests; C ASan/UBSan; seven renderer/pixel checks; 616 valid + 619 malformed cross-language cases; APKs, FAP, 77-import audit, APK signature/permissions and FAP API audit; validation stamp all PASS.
- Final lint reports no issues. Explicit backup exclusion resources and narrow parameter-guarded permission-string annotation resolved the final warnings. No lint baseline introduced.
- `./scripts/bootstrap.sh --accept-android-sdk-license`: exit 0 using pinned cached archives; `doctor.sh`: exit 0.
- Read-only ADB inventory: SHIELD Android 5.1/API22, Apple Music absent; unsupported for minSdk26. No device writes. No Flipper product found in accessible USB inventory; /dev/kvm unavailable.
- Clean-source check started in `/tmp/fnp-clean-m1o34dmm` with empty app/host build outputs and shared downloaded dependency cache. Ran official `./fbt -c fap_now_playing` (exit 0) first, deleting only generated FAP/dependency build outputs. Clean source pipeline in progress.
- Current milestone: M6. Next: collect clean-source result, finalize reports/matrix, run package_release.py and inspect all hashes/source archive exclusions. Do not alter tested runtime source unless a failure requires it.
- Fresh-source pipeline: exit 0; 130 Android tasks executed; host tests, full FAP compilation, 77-export audit, artifact audit and stamp passed. Source digest matched; unsigned release APK and FAP byte-identical. Debug APKs differ between clean/incremental builds; comparison recorded without claiming identical hashes.
- All seven renderer states visually inspected at nearest-neighbor 6x; native PNGs retained unchanged.

## M6 — packaged
- `python3 scripts/package_release.py`: exit 0; APKs/FAP/source ZIP/native and 6x simulated screenshots/install/compatibility/build/hardware reports/evidence/manifest/SHA256SUMS produced under dist/.
- Independent source ZIP audit: 101 files, actual wrapper and BLE source present, no .cache/.git/.agents/.codex/build trees or *.keys/*.jks/*.keystore. Manifest audit: all 45 listed file sizes/hashes matched on first package pass. Subsequent documentation/evidence additions are rehashed by final packaging.
- UUID byte-order check: actual profile service initializer equals canonical UUID bytes reversed; RX/TX byte12 substitutions match both protocol characteristic UUIDs. PASS; evidence/uuid-byte-order.json.
- All seven native renderer states inspected; no bitmap mock substitutes the production view.
- Remaining external blockers: supported API26+ Android/Apple Music phone and physical Flipper for radio/volume/bond-restoration/endurance tests; API37 environment for audio hardening; no /dev/kvm for accelerated emulator. Attached API22 SHIELD was read only, never installed/changed.
- Current milestone: delivery complete for implemented/buildable software and local package. Device-validation gate remains explicitly open. Resume future device work from HARDWARE_VALIDATION.md and acceptance_matrix.csv; do not restart architecture or claim physical PASS without observations.

## Final teardown correction
- Final review found owner clearing reset/disconnect flags before worker join could allow a late worker snapshot to re-enable readiness. Corrected: STOPPING is set before invalidation; error/disconnect flags remain asserted through join; model apply rechecks STOPPING under its lock; snapshot ACK cannot enable readiness during stop/reset/disconnect; flags clear only after worker quiescence. Re-running software pipeline and fresh FAP compilation; earlier package is superseded until new validation/packaging.
- Final `./scripts/test_all.sh`: exit 0 after all teardown corrections; staged source compared byte-for-byte to production source. 27 reference tests, C ASan/UBSan, seven render checks, 12 Kotlin/module tests (unchanged and up-to-date), lint, 616/619 differential codec cases, 77 exported imports, artifact audits and validation stamp passed.
- Clean FAP target removed/rebuilt (exit 0). First independent artifact audit lacked sourced JAVA_HOME (java not found, exit 127); rerun uses `source scripts/env.sh`. Initial copy/compare used the SDK working directory (exit 1); corrected to project working directory and both succeeded. Firmware metadata fetch under sandbox failed; rerun with approved network access. No runtime change was required for these environment/command corrections.
- Final clean FAP compile/audit/stamp: exit 0 with network and environment configured. Latest source digest matches fresh copy; final FAP and unsigned release APK byte-identical. Clean FAP log and stamp refreshed. Teardown flags stay asserted through worker join, readiness is invalidated after join, and controls reject STOPPING.
- Current milestone: final software verified; packaging refreshed below. Physical acceptance remains explicitly unverified.

## Nowo Playing naming and GitHub release
- User authorized a new GitHub repository for APK distribution and chose Nowo Playing. Updating visible branding; stable IDs, protocol, keys and filenames unchanged. Rebuild/test/package before publishing a public preview under villenull/nowo-playing.
- User corrected both interim names as typos: final display name **Now Playing**, repository **villenull/flipper-now-playing**. No interim repository/release was created. Revalidating final naming after the in-progress build completes.
- Created public repository https://github.com/villenull/flipper-now-playing with `gh repo create`: exit 0. No interim typo repository was created. Final-name `./scripts/test_all.sh`: exit 0; full portable checks, APK/FAP builds and audits passed. Running a final incremental Android build to ensure all corrected display-name inputs are current before publishing. Git publication uses an isolated checkout because the supplied workspace metadata is managed; no global Git identity change.
- Final incremental Android build: exit 0, all changed-name build inputs current (128 tasks up-to-date, 2 executed); APK/FAP audit and source stamp passed. Publishing preview v1.0.0-preview.1 with original signing identity and explicit hardware NOT_RUN status.
- Publication complete: isolated checkout `/tmp/now-playing-publish`, source commit `084bfa9`, pushed main (exit 0). `gh release create v1.0.0-preview.1 --prerelease`: exit 0; 6 release assets uploaded. Direct unauthenticated APK download via curl succeeded and `cmp` matched the local APK byte-for-byte (exit 0). GitHub Actions was still in progress at delivery; local software validation passed. Release URL: https://github.com/villenull/flipper-now-playing/releases/tag/v1.0.0-preview.1 .

## Authorized USB installation — 2026-09-10
- User connected Flipper and explicitly requested installation; resolved Linux serial access after user applied per-device ACL. Read-only device_info: Momentum mntm-012, commit e1784e74, f7, API 87.1. Official 1.4.3 runtime remains the supported build baseline; matching Momentum API is not a runtime-validation claim.
- Official storage Python library copied only `/ext/apps/Bluetooth/now_playing.fap`, previously absent, then read back all 22,980 bytes. Byte equality and SHA-256 `12ef06bb2e8d41f136b74505b70fe896421bf5e59e8325085f5fcbffdb4a3102` verified; exit 0. Evidence: build/evidence/device-install.json. Firmware, bonds and settings unchanged. App launch/pairing not run.

## First physical startup failure and fix
- User launched installed preview: Check SD / Bluetooth. Source trace confirms `storage_common_stat(EXT_PATH(""))` reaches FatFs `f_stat` on root, which returns FR_INVALID_NAME for NS_NONAME. Replaced root stat with exported `storage_sd_status`; retained app-directory write probe and BLE guard. Distinct startup messages now identify SD, app storage, BLE stack or profile failure. No firmware/bond change. USB access awaiting renewed user ACL after reconnection.
- Physical diagnosis: CLI `storage stat /ext/` returned invalid name/path; app-data absent. Normal `input send back long` exited app (loader confirmed no app). Saved previous FAP locally, installed fix and verified full readback; reopened with loader. App running; own app-data directory now exists. Build/export audit, host ASan/UBSan + render checks, artifact audit and stamp all exit 0. Awaiting user screen observation; no BLE success claimed.

- User confirmed corrected app displays **Waiting for phone**. Startup screen PASS on Momentum mntm-012; Android BLE pairing and media controls still pending.

## 1.1 artwork implementation milestone
- User authorized local/GitHub/Flipper and APK update. Added negotiated application-v2 ARTWORK, independent C/Kotlin/reference validators and six new frozen fixtures while retaining all sixteen v1 fixtures. Added 45×45 Bayer conversion, bounded bitmap/content-URI worker, stale callback rejection, coalesced low-priority art, model identity guard, 5s/12px-sec/1.5s scrolling and art/text renderer.
- Initial pixel test incorrectly included footer glyph top row (y56) in the empty gutter assertion; corrected gutter to y46..55 after inspecting real native render. Eleven native renders and C ASan/UBSan now pass. Full Android/codec pipeline in progress.
- Read-only ADB inventory now shows an API28 AOSP emulator (two aliases), no helper installed. No physical Android handset is visible over ADB. Prior unavailable-emulator statements describe the initial build environment.
- Added actual Android reader instrumentation without dependencies; bitmap/crop/no-art/no-HTTP/stale callbacks. Late review found BitmapFactory default sample size could be zero; moved sample calculation into pure tested Artwork.decodeSampleSize (starts at 1) and added deterministic mid-gray dithering test. Test-only player now supplies optional bitmap metadata.
- A running build script was edited to include assembleDebugAndroidTest; Bash resumed at a shifted file offset and exited 127 after successful Gradle compilation. Restarted the full pipeline with all source/scripts stable. No test failure was hidden or suppressed.
- Previous GitHub Actions run for original preview completed successfully; new-release CI has not yet been dispatched.
- Final full 1.1 pipeline exit0: 30 reference tests, 17 Kotlin/module unit tests, C ASan/UBSan, eleven renderer/pixel states, 722 valid/731 malformed differential frames, no lint issues, all APKs/FAP built, 76 exports and artifact audit passed. Actual API28 emulator installs and custom ArtworkReader instrumentation also exit0, five checks PASS. Source/scripts frozen since final run.
- Installed 1.1 on connected Momentum Flipper after normal long-Back exit. Full 23,580-byte readback matches SHA256 bbfaa8a68718c1846cfc607f9e112e7cacee8ff332dab4d41994a8e7446fcfab; reopened and loader reports Now Playing running. APK certificate SHA256 matches original84af0fed14f74dc3d177870628d375ce0577364150036348c72ee9e736c25251. No firmware/default bonds changed. Next: package/publish1.1 and verify downloadable APK; actual phone artwork pending user upgrade.
- User steered release naming to v0.1 and requested catalog-style README/icon/renders. Prior release-create attempt failed422 because abbreviated target SHA was not accepted; no1.1 release exists. Will create v0.1 against the final full commit SHA. Generated four orange renderer previews + four native originals and app-icon SVG, with direct APK/FAP links and catalog-style description/usage/controls/changelog/credits. Packaging metadata only changed to release0.1; app binaries remain tested build1.1/code2.
- Completed user-requested GitHub v0.1 presentation and publication. Commit22fa75912add87d96fabd2864824467da96de69a; `gh release create v0.1 --target main --prerelease` exit0 with six assets. Unauthenticated APK and FAP downloads both byte-match local files (curl+cmp exit0). README viewed in GitHub browser: icon/links/4-image orange gallery/sections present, gallery visually verified. Repo description/topics updated. Latest GitHub Actions runs still in progress at delivery; local full pipeline and Android artwork instrumentation passed. Connected Flipper already runs installed/readback-verified updated FAP. User phone APK upgrade and real artwork acceptance remain next.

- User requested a single orange version of the previously approved concept instead of the four-image README gallery. Generated an edit from that exact reference, visually inspected it, and replaced the gallery with one concept image. Kept native renderer evidence linked and clearly distinguished from the concept. Documentation/assets only; application binaries and device installation are unchanged.

## Advertising fix (FAP 1.2) — 2026-09-11
- Live triage with user: helper showed Bluetooth/notification true but `Find` never listed the Flipper; `Selected device: none`, diagnostics header-only (service never armed). nRF Connect saw other BLE devices but no `NP`/Flipper entry even centimeters away.
- USB CLI confirmed FAP 1.1 (23,580 B) installed and `loader info` reporting Now Playing running, Flipper BT on, yet zero advert packets. Compared `np_ble_profile.c` gap config against official 1.4.3 `gap.c`/`hid_profile.c`: legacy advert payload is 31 bytes, our 128-bit service UUID occupies 18, leaving 10 for the name field (<= 8 chars). Custom Flipper name `Betfoyet` plus the `Flipper→NP` rewrite that never fires put the payload at/over the limit; `aci_gap_set_discoverable` failures surface only in disabled logs, so the screen kept showing `Waiting for phone` with no error.
- Fix: fixed short advert name `NP %02X%02X` from the offset MAC tail (7 chars, 30/31 bytes total, independent of Flipper name); after `furi_hal_bt_start_advertising`, poll exported `furi_hal_bt_is_active()` for 2 s and show `Advertising failed` instead of a false ready screen. FAP version bumped to (1,2). Verified `furi_hal_bt_is_active` is `+` in pinned api_symbols.csv.
- Removed duplicate install: `/ext/apps/Bluetooth` held both `now_playing.fap` and `now_playing-fw1.4.3-api87.1.fap` (both 23,580 B); deleted the versioned copy via `storage remove`, leaving one Now Playing plus untouched user apps.
- Rebuilt against pinned 1.4.3 (`8622f1a2`, api_symbols hash match): `./fbt fap_now_playing` exit 0, API 87.1 up to date; `audit_fap_imports.py` PASS (73 imports exported); 30 Python reference tests OK; `test_host.py` C/render checks PASS.
- Deployed `dist/now_playing-fw1.4.3-api87.1.fap` (23,416 B, md5 `2e300a3b8f206086ef5a11ef63192eab`, device md5 match) via CLI `write_chunk` after `loader close` refused (`has to be closed manually` — by design, profile restore on normal exit). Old v1.1 instance still resident in RAM; user must long-Back exit and reopen to run 1.2. Awaiting user screen + nRF + helper retest.

## Advertising fix corrected (FAP 1.2, second build) — 2026-09-11
- Deck `bluetoothctl` scan and phone nRF both saw no Flipper packets despite v1.2 (`NP E180`, 7-char name) running with `Waiting for phone`. Captured Flipper logs over USB during exit/reopen: `[BleGap] set_discoverable failed 146` (0x92 `BLE_STATUS_INVALID_PARAMS`, confirmed in `ble_defs.h`) plus `set_non_discoverable failed 12` (nothing ever advertising). The 60 s `INITIAL_ADV_TIMEOUT` low-power re-submit reproduced the failure live.
- Root cause corrected: `aci_gap_set_discoverable` doc (`ble_gap_aci.h`) states the stack auto-adds Flags + TX Power, so our name + 128-bit UUID blocks must fit **25** bytes, not 31. `NP E180` used 27 — still rejected. Stock profiles fit because a 16-bit UUID block is only 4 bytes.
- New advert name `NP%02X` (last MAC byte, e.g. `NP80`, 4 chars → 6 + 18 = 24 ≤ 25). Rebuilt (23,404 B, md5 `1b410b6f25d7648d0a9f1b80436675c7`), audit PASS (73 imports), redeployed via CLI `write_chunk` with device md5 match. Old instance resident until user exits/reopens. Verifying via Deck BLE scan next.

## Advertising confirmed on air — 2026-09-11
- Deck `bluetoothctl` sees `80:E1:26:76:65:42` (NP MAC byte-reversed) at RSSI -20 with `UUID: 8f9a1000-6d8a-4f1b-a6e6-3b4c5d6e7f80`, complete local name `NP80`, TxPower 0. Phone-side `Find` should now match the service-UUID filter. Handing back to user for helper select/Start/pairing and Apple Music metadata check.

## First successful phone↔Flipper link — 2026-09-11
- After the 25-byte advert fix, Deck BLE scan confirmed `NP80` (MAC 42:65:76:26:E1:80, service UUID `8f9a1000-…`) at RSSI -20. Phone nRF found the `8f9a…` device; helper `Find` listed it (`Now Playing 65:42`, reversed OTA byte order); user selected it and Started.
- First bond hit `Pairing rejected` (missed/mismatched numeric-comparison confirm); after forgetting the half-bond and confirming the 6-digit code on both sides (phone popup/shade + Flipper center button), link came up. User reports working.

## Nav Turns v0.1 built (FAP compiled, APK staged unbuilt) — 2026-09-11
- Implemented per user confirmation (Google Maps source, standalone APK): `protocol/nav_constants.json`, `nav_reference_codec.py`, 14 frozen `nav_golden_vectors.json`, `test_nav_reference_codec.py` (3 tests), `test_nav_parse.py` parser spec (9 cases, all green with the full 42-test protocol suite).
- Flipper `nav_turns` FAP (model/protocol/view/profile/app, v0.1): host C tests PASS under ASan/UBSan (vectors, splits, every-bit corruption, model, input, scroll); `./fbt fap_nav_turns` exit 0, API 87.1; 71 imports all exported; `dist/nav_turns-fw1.4.3-api87.1.fap` 16,868 B md5 `0f8fe17a42362411019c273c871781cc`. Install to `/ext/apps/Bluetooth/nav_turns.fap` pending — Flipper was unplugged before deploy.
- Android `navcore` (protocol/parser/domain + `NavTest`) and `navapp` (listener, repository, BLE central, foreground service, setup activity) written complete and registered in `settings.gradle.kts`, but **not compiled**: no JDK/Kotlin/Android SDK in this environment and the SDK license + downloads were not authorized. Needs `./scripts/bootstrap.py --accept-android-sdk-license`, `./scripts/build_android.sh`-equivalent for nav modules, then on-device pairing test with live Maps navigation.

## Nav Turns APK compiled, lint clean, existing app unbroken — 2026-09-11
- User authorized full downloads: `bootstrap.py --accept-android-sdk-license` exit 0 (JDK/Gradle/SDK verified by SHA-256; flipper HEAD re-verified).
- Fixed two self-made defects found by the compiler/tests: `NavParse.parse` missing `sub` default; Kotlin reroute test asserting turn 3 instead of 0.
- `./gradlew :navcore:test :navapp:assembleDebug :navapp:lintDebug :app:assembleDebug :core:test` BUILD SUCCESSFUL. navcore unit tests pass (protocol vectors + 9 parser cases); navapp lint 0 errors (24 SetTextI18n programmatic-UI warnings, same accepted pattern as bridge); existing `:app` + `:core` still build/test with the new modules included.
- Added `ic_nav` arrow icon + backup/data-extraction rules mirrored from bridge (cleared MissingApplicationIcon/DataExtractionRules warnings).
- `dist/nav-turns-debug.apk` (vCode 1/vName 0.1, min 26/target 36, no INTERNET permission). FAP install still pending — Flipper unplugged; remaining: USB install + live Maps drive test + helper pairing.

## Flipper Navigator GitHub publication — 2026-09-11
- User named the project **Flipper Navigator** and asked for a phone-downloadable GitHub home. Created public `villenull/flipper-navigator` via `gh repo create` (exit 0) from isolated checkout `/tmp/nav-publish` (source only: dist/.cache/build excluded, matching house convention); commit `35cc885`, pushed main (exit 0). Root README rewritten for the navigator (install, pairing lessons, layout, sibling link); no global git identity change.
- `gh release create v0.1 --target main --prerelease` exit 0 with `nav-turns-debug.apk` + `nav_turns-fw1.4.3-api87.1.fap`. Unauthenticated curl downloads byte-match local files (cmp exit 0). Release: https://github.com/villenull/flipper-navigator/releases/tag/v0.1
- Nav FAP on-device install still pending (Flipper present but serial ACL lapsed again; udev rule never landed).

## Nav Turns FAP installed on device — 2026-09-11
- After user re-applied the serial ACL, installed `/ext/apps/Bluetooth/nav_turns.fap` (16,868 B) via CLI `write_chunk` (17 chunks; the pre-remove `file/dir not exist` was the expected fresh-install case). Device md5 `0f8fe17a42362411019c273c871781cc` matches local. `now_playing.fap` (23,404 B v1.2) and all user apps untouched. Next: user opens Nav Turns, air check for `NV…` advert, APK install from GitHub, live Maps drive test.
