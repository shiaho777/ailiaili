# ailiaili repository instructions

- Keep source code free of new comments.
- Use `jvmShared` for shared Android and desktop behavior. Put platform APIs behind the existing `expect`/`actual` boundaries. Keep network code independent of the data layer.
- Follow the [development guide](static/doc/development.md) for shared playback, UI, and request behavior.
- Build Android with `./gradlew :androidApp:assembleDebug`. Run shared JVM tests and compile desktop with `./gradlew verifyJvm`, the same entry point used by [CI](.github/workflows/ci.yml).
- Run the affected app when changing playback, platform APIs, or interaction. Report which commands and platforms were actually verified.
- Derive version numbers from `gradle.properties`. Keep durable user information in the paired READMEs and version changes in the release notes draft until release. Track temporary plans and review notes in the task, Issue, or PR.
- Preserve unrelated worktree changes. Follow the [contribution workflow](README.md#contribute-and-get-help) when delivery is requested. Keep local credentials, signing keys, and raw session or diagnostic data out of commits.
