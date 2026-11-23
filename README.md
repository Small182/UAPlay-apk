# UAPlay — Android / Android TV

UAPlay Android preview, based on the UAPlay project for Nintendo Switch. This repository is **separate** from [UAPlay-nx](https://github.com/Small182/UAPlay-nx) and the PS5 port.

## Status — 0.1.1 alpha (test build)

The first-run screen lets you choose **Phone / tablet** (touch) or **Television** (remote control). The selection is saved and can be changed later in Settings. TV launcher support and D-pad navigation are present.

**Video test:** Open the built-in **Big Buck Bunny** sample, or supply your own direct HTTPS MP4/HLS link. The preview uses Android's built-in `VideoView`, not libmpv. Codec/stream support depends on the device.

**Not yet implemented:** the Switch app's full catalog, providers, IPTV, downloads, custom SDL2 UI and libmpv backend. This is an installable Android TV *test application*, not the full UAPlay port.

## Install

Download the APK from [GitHub Actions](https://github.com/Small182/UAPlay-apk/actions) after a successful **Build Android APK** workflow. The workflow also attempts to publish an APK under `apk/UAPlay-Android-TV-test.apk` for convenient direct download. APK is debug-signed; no root needed.

On Android TV, install using a file manager, USB transfer, or ADB after allowing installs from unknown apps. On first launch, select **Television**.

## TV remote

- D-pad: move focus
- OK: select; in player, pause/resume
- Back: previous screen; in player, close video
- Left / right in player: ±10-second seek
- Up / down: show player controls

## Build locally

Requires JDK 17, Android SDK Platform 35, and Gradle 8.11.1 (Android Gradle Plugin 8.9.2). From repository root run `gradle :app:assembleDebug` or open the project in Android Studio.

## Development policy

Keep the Android phone and Android TV builds in **one APK** with a first-run choice and editable Settings mode. Do not make changes to other UAPlay repositories through this repository. Publish GitHub Releases only when explicitly requested.

Original Switch project: https://github.com/Small182/UAPlay-nx
