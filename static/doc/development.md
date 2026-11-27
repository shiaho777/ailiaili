# Development guide

[Return to the project home](../../README.md)

## Code layout

| Module | Responsibility |
| --- | --- |
| `androidApp`, `desktopApp` | Platform entry points, application setup, and packaging. |
| `app` | Shared application shell, navigation, theme assembly, and update prompts. |
| `feature:*` | Screen state, UI, and interactions. |
| `core:designsystem` | Shared Compose components, theming, page refresh, filters, and input behavior. |
| `core:data` | `AppGraph`, `BiliRepository`, session and settings storage, and offline downloads. |
| `core:network` | Bilibili API calls and decoding, request policy, and CDN selection. |
| `core:model` | Shared data models and pure rules for playback, links, and display. |
| `core:player` | Playback and buffer policy, implemented by Media3 on Android and LibVLC on desktop. |

Put shared JVM UI and behavior in `jvmShared`. Keep platform implementations in `androidMain` and `jvmMain`, and pure JVM tests in `jvmTest`. Add platform capabilities behind the existing `expect`/`actual` contracts. Keep `core:network` independent of `core:data` and keep imports from feature modules out of core.

## Playback and network

- Keep buffer policy in `BufferPolicy`. Desktop maps the policy to VLC's `:network-caching`. Android applies buffer changes when Media3 next prepares media and shares the allocator across configurations.
- Publish desktop frames from the VLC callback through `FramePump`. The Compose renderer draws published images. Keep images open while the UI can still draw them.
- Preserve seek confirmation before storing progress or advancing the queue. Desktop confirms within 1.5 seconds of the target, retries once, and can rebuild network media at the target. Treat a premature end as a recovery case.
- Render Bilibili JSON subtitles and danmaku through the shared Compose overlays. Read their timing from the playback clock. Keep per-frame danmaku movement in drawing so it avoids whole-screen recomposition.
- Reuse `BiliRepository` in screens and the existing request policies in `BiliClient`. Use request coalescing and short caches for eligible reads. Keep writes out of automatic retry policies and keep playback URLs out of read caches. Keep account-dependent cache entries scoped to the account.
- Home previews share `PlayerEngine` across platforms. Keep one active preview for the settled page, release it when leaving the feed, and let the full player own platform media controls. Keep playback URLs in the existing repository flow.
- Keep playback state and gestures in `Player.kt`, transport controls in `PlayerTransport.kt`, and comment UI in `PlayerComments.kt`. Subtitle and danmaku overlays have their own files.
- Run offline work through `DownloadQueue` so leaving a page does not cancel the task. Identify each task by BV ID and CID. Stage tracks before publishing cache metadata, and validate resumed HTTP ranges before appending bytes.
- Route Android network changes and notification actions through the shared queue. Start the download foreground service from the visible activity. Store offline subtitles and danmaku alongside the completed media and read them through the same playback overlays as online content.

## Shared screens and components

| Need | Use |
| --- | --- |
| A row of statistics | `MetricFlow` and `MetricChip`, with compact chips in lists and regular chips in detail headers. |
| A numeric profile counter | `MetricStat`. |
| Secondary information on a card | `MetaRow`. |
| A short status such as Live, quality, or unread count | `BiliBadge`. Use `Dark` and `Solid` over thumbnails. |
| A filter or suggested action | `BiliFilterChip` or `BiliAssistChip`. |
| A media interaction | `BiliActionPill` or `BiliActionItem`, depending on the screen. |
| A grouped surface or section title | `AliStagePanel` or `BiliSectionHeader`. |
| Loading, empty, and error states | `LoadBox` or the shared state components. |

Use `BiliPaginationFooter` for list pagination that can fail after content is visible. Retain the loaded items and retry the same page. Keep the search field draft separate from the submitted query used for pagination and refresh.

- Use the shared Ali stage header, tabs, and seek bar for the feed, search, activity, profile, and player. Keep playback state outside the shared controls.
- Use `AliMaterial` for panels, navigation, popups, and playback controls. Reserve shadows for floating elements and use the ambient background on page canvases. Use `BiliSearchEntry` and `BiliHeaderAction` for discovery controls. Cache size-dependent gradients and markers in the draw phase. Keep scrolling lists and video chrome free of backdrop blur and frame snapshots.
- Use `aliScreenTransition` for tab and route changes and give each animated page an opaque background. Keep tab scroll state in a saveable state holder. Use shared press feedback on navigation and preserve keyboard focus indicators. Respect `LocalAliReducedMotion`.

- Use `MetricTone` for semantic colors and `LocalWindowForm` for window-width behavior. Let `MetricFlow` wrap on narrow screens.
- Use `cdnThumb` for thumbnails and avatars. Use the original image for full-size previews. Keep filtering in shared derived state where available.
- Register F5 with `RegisterPageRefresh` from the composed screen. Navigation can retain the screen's ViewModel after the screen has left composition.
- Reserve press animation for interactive elements. Keep loading animations scoped to loading states. Check list scrolling on the affected platform when changing shared layout or input code.

Use the [build commands](../../README.md#build-from-source) and the checks in [CI](../../.github/workflows/ci.yml). Run `./gradlew verifyJvm` for JVM tests and desktop compilation. Run `python3 .github/scripts/check_docs.py` to check local documentation links. Run the relevant app for changes to input, playback, and platform behavior.
