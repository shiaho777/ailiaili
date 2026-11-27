<div align="center">
  <h1>ailiaili</h1>
  <img src="core/designsystem/src/commonMain/composeResources/drawable/app_icon.png" width="96" height="96" alt="The ailiaili wordmark over a mist-blue television on a cool white background">
  <p><a href="https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml"><img src="https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml/badge.svg?branch=main" alt="Build and test status for main"></a></p>
  <p>An unofficial Bilibili client for Android, Windows, macOS, and Linux.</p>
  <p><a href="#download">Download</a> · <a href="https://github.com/shiaho777/ailiaili/issues">Report a problem</a> · <a href="README.zh-CN.md">简体中文</a></p>
</div>

## Supported platforms

| Platform | Runtime and installation |
| --- | --- |
| Android | Android 8.0 (API 26) or later. Uses Media3. A debug APK is available from CI. |
| Windows | Build an MSI on Windows. |
| macOS | Build a DMG on macOS. |
| Linux | Build a DEB on Linux. |

Desktop builds use LibVLC. Install VLC for the same architecture as the app.

## Screenshots

<p align="center">
  <img src="static/screenshot/home.jpg" width="440" alt="Wide home layout with expanded navigation and a grid of video cards">
  <img src="static/screenshot/player.jpg" width="440" alt="Wide player layout showing video, floating playback controls, and details">
</p>

## Features

- Home opens in the video grid. Select the feed button in the home header to open the immersive video view. Navigation, search, and comments follow the app theme while playback actions overlay the video. Previews start muted. Swipe on mobile or use the mouse wheel and arrow keys on desktop. Select Grid or press Escape on desktop to return to the grid.
- Search, activity, your profile, and the full player share the feed’s navigation and controls. The bottom navigation and Settings tabs float over the content. Settings remembers each tab’s scroll position. Browse mode gives video covers more room, and reduced motion applies to page transitions. Offline downloads are accessible from your profile while signed out.
- The light theme uses cool white surfaces. The dark theme takes its neutral gray layers and pale blue accent from Chromium’s base palette. Blue marks primary actions, selection, and progress. Choose and preview a custom accent in Appearance settings. New installs follow the system appearance. A compact dock floats on phones, and wide windows use an expanded sidebar. Full-player controls float over the video, with a single-row transport on wide stages and a separate seek bar on narrow stages. Move the mouse on desktop or tap the video on mobile to reveal them.
- Filter the category directory, choose covers or a list in each category, and filter private messages to unread. History and favorites are accessible from your profile. Video and live history remember their own filters and scroll positions.
- Browse creator profiles, open individual posts from their profiles, and switch between popular and recent comments. Series details include a synopsis and an episode list that can play from the first episode. Search for videos, live rooms, series, and users.
- Search suggests matching recent and trending queries as you type, and lets you remove recent searches individually. Paste a Bilibili link into the search field to see what it opens, including the part and timestamp when available.
- Watch videos and live streams with danmaku. Use subtitles, quality and speed controls, resume playback, and optionally skip sponsored segments with SponsorBlock.
- Temporarily clear on-screen danmaku from the player menu while keeping subtitles. Mute from the volume control and restore the last audible level when unmuting.
- Pick up the last unfinished online video from the Home recommendations, or dismiss the card while keeping its saved position. In the player options, copy a link to the current part and timestamp. Paste it into search or use the Home link shortcut to open it.
- Sign in by QR code to access history, favorites, Watch Later, messages, and account interactions. Favorites offer cover and list views and remember your choice.
- Add individual parts or whole multi-part videos to the download queue. Pause, resume, or retry tasks from **Profile → Offline cache → Download tasks**. Android can keep downloads running with a system notification and wait for an unmetered connection. Resume unfinished tasks after restarting the app. Find completed videos by title or BV ID.
- Save available subtitles and danmaku with a download for offline playback. Fit, crop, or stretch the picture in the full player, and lock the fullscreen controls to avoid accidental input.
- Search settings by name or keyword. Choose a startup page, seek step, danmaku timing, and default download quality. Customize the theme and content filters. The interface and danmaku use MiSans by default, with system and imported fonts available in General settings. Desktop builds support keyboard shortcuts, a system tray where available, and a custom cache directory.
- Filter download tasks by status and retry failed downloads together. Settings search can jump directly to options such as seek step, subtitle size, and download folder.

<details>
<summary>Playback and platform notes</summary>

- Stream quality and subtitles depend on the video, account, and device. Bilibili often limits AI subtitles to signed-in users.
- Android can merge compatible offline tracks into an MP4. Its custom download directory in development builds is used for export, with a separate copy kept in the app cache. Desktop downloads retain separate audio and video tracks.
- Global media keys are Windows-only in development builds. In-app shortcuts also work on macOS and Linux.
- The sleep timer runs while the player page is open. Live notifications require the app to be running. The in-app proxy covers API, image, download, and live danmaku requests. Media streams use the playback engine's own connection.

</details>

See the [release notes draft](static/doc/release-notes.md) for changes still in development.

## Download

There are currently no release installers on [GitHub Releases](https://github.com/shiaho777/ailiaili/releases).

- **Android preview.** Sign in to GitHub, open a recent successful run for `main` in [GitHub Actions](https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml?query=branch%3Amain), and download `android-debug-apk` under **Artifacts**. After extracting the archive, find the APK in the Android build’s `debug` folder. The archive root includes `SHA256SUMS`. A CI preview may be older than this checkout. CI keeps artifacts for 14 days. This build uses a debug signing key.
- **Desktop or expired artifacts.** Build from source using the commands below. Package desktop installers on the target operating system.

## Build from source

Use JDK 21 to run Gradle, matching CI. Desktop builds also need a JDK 17 available to Gradle's toolchain and a local VLC installation for playback. For Android, configure the SDK path and install SDK Platform 37.0 and Build Tools 37.0.0.

On a fresh build, Gradle fetches MiSans Regular from Xiaomi and verifies its SHA-256 digest.

```sh
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDistributionForCurrentOS
```

The debug APK is in the `debug` subfolder of `androidApp/build/outputs/apk/`. Desktop packages are under `desktopApp/build/compose/binaries/`. On Windows, use `gradlew.bat`.

## Contribute and get help

Check the [issue tracker](https://github.com/shiaho777/ailiaili/issues) for existing reports. Include the app version, platform, and reproduction steps. In **Settings → About** (设置 → 关于), you can copy the version and optionally copy diagnostics for playback or network issues. Review diagnostics, logs, and screenshots for private information before posting.

For changes, use an existing issue when one covers the work, open a pull request against `main`, and include the checks you ran. A PR that fixes an issue can use `Fixes #123` to close it on merge. The [development guide](static/doc/development.md) covers the code layout and shared conventions. [CI](.github/workflows/ci.yml) defines JVM tests, desktop compilation, and the Android debug build.

Use `./gradlew verifyJvm` to run the same JVM tests and desktop compilation as CI.

## License and credits

Source code is licensed under [GPL-3.0](LICENSE). ailiaili is an unofficial, noncommercial project independent of Bilibili. Bilibili content and trademarks remain with their respective owners.

MiSans is provided by Xiaomi under the [MiSans Font License Agreement](https://hyperos.mi.com/font/en/download/).

Thanks to [BiliDesk](https://github.com/ABING1111/BiliDesk), Kotlin, Compose Multiplatform, Media3, LibVLC, OkHttp, Coil, and the contributors to these projects.
