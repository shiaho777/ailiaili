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

- Keep buffer policy in `BufferPolicy`. Desktop maps the policy to VLC's `:network-caching`. Android maps the same policy to Media3's buffer durations. Verify changes when opening and switching media.
- Publish desktop frames from the VLC callback through `FramePump`. The Compose renderer draws published images. Keep images open while the UI can still draw them.
- Keep desktop seek recovery in the playback engine. It tracks the target, retries the seek, and can reopen network media at the target. Verify both resume playback and seeking after playback ends.
- Render Bilibili JSON subtitles and danmaku through the shared Compose overlays. Read their timing from the playback clock. Keep per-frame danmaku movement in drawing so it avoids whole-screen recomposition.
- Reuse `BiliRepository` in screens and the existing request policies in `BiliClient`. Use request coalescing and short caches for eligible reads. Keep writes out of automatic retry policies and keep playback URLs out of read caches. Keep account-dependent cache entries scoped to the account.
- Keep playback state and gestures in `Player.kt`, transport controls in `PlayerTransport.kt`, and comment UI in `PlayerComments.kt`. Subtitle and danmaku overlays have their own files.

## Shared screens and components

| Need | Use |
| --- | --- |
| A row of statistics | `MetricFlow` and `MetricChip`, with compact chips in lists and regular chips in detail headers. |
| A numeric profile counter | `MetricStat`. |
| Secondary information on a card | `MetaRow`. |
| A short status such as Live, quality, or unread count | `BiliBadge`. Use `Dark` and `Solid` over thumbnails. |
| A filter or suggested action | `BiliFilterChip` or `BiliAssistChip`. |
| A media interaction | `BiliActionPill` or `BiliActionItem`, depending on the screen. |
| A grouped surface or section title | `BiliSurfaceCard` or `BiliSectionHeader`. |
| Loading, empty, and error states | `LoadBox` or the shared state components. |

- Use `MetricTone` for semantic colors and `LocalWindowForm` for window-width behavior. Let `MetricFlow` wrap on narrow screens.
- Use `cdnThumb` for thumbnails and avatars. Use the original image for full-size previews. Keep filtering in shared derived state where available.
- Register F5 with `RegisterPageRefresh` from the composed screen. Navigation can retain the screen's ViewModel after the screen has left composition.
- Reserve press animation for interactive elements. Keep loading animations scoped to loading states. Check list scrolling on the affected platform when changing shared layout or input code.

Use the [build commands](../../README.md#build-from-source) and the checks in [CI](../../.github/workflows/ci.yml). JVM tests cover pure decisions and parsing. Run the relevant app for changes to input, playback, and platform behavior.
