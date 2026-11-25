<div align="center">
  <h1>ailiaili</h1>
  <img src="desktopApp/src/main/resources/icon.png" width="96" height="96" alt="ailiaili 应用图标">
  <p><a href="https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml"><img src="https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml/badge.svg?branch=main" alt="main 分支的构建和测试状态"></a></p>
  <p>支持 Android、Windows、macOS 和 Linux 的第三方哔哩哔哩客户端。</p>
  <p><a href="#下载">下载</a> · <a href="https://github.com/shiaho777/ailiaili/issues">反馈问题</a> · <a href="README.md">English</a></p>
</div>

## 支持平台

| 平台 | 运行条件与安装方式 |
| --- | --- |
| Android | Android 8.0（API 26）及以上，使用 Media3。可从 CI 下载调试 APK。 |
| Windows | 在 Windows 上构建 MSI。 |
| macOS | 在 macOS 上构建 DMG。 |
| Linux | 在 Linux 上构建 DEB。 |

桌面端使用 LibVLC，需安装与应用架构一致的 VLC。

## 截图

<p align="center">
  <img src="static/screenshot/home.jpg" width="440" alt="桌面首页的推荐内容与视频分区">
  <img src="static/screenshot/player.jpg" width="440" alt="桌面播放器的画面、控制栏与视频详情">
</p>

## 功能

- 浏览推荐、排行、分区、UP 主空间和动态，搜索视频、直播、番剧与用户。
- 播放视频和直播，支持弹幕、字幕、画质与倍速调节、续播，也可启用 SponsorBlock 跳过赞助片段。
- 扫码登录后可同步历史、收藏、稍后再看、私信及互动。支持离线缓存。
- 可自定义主题、字体与内容屏蔽规则。桌面支持快捷键、系统托盘（取决于平台）和自定义缓存目录。

<details>
<summary>播放与平台限制</summary>

- 画质与字幕取决于视频、账号和设备。B 站通常只向登录用户提供 AI 字幕。
- Android 将下载内容保存在应用缓存中，可将兼容的离线音视频轨道合成 MP4。桌面下载保留独立的音视频轨道。

</details>

开发中的变更见[发行说明草稿](static/doc/release-notes.md)。

## 下载

[GitHub Releases](https://github.com/shiaho777/ailiaili/releases) 目前还没有发布安装包。

- **Android 预览版。** 登录 GitHub，在 [GitHub Actions](https://github.com/shiaho777/ailiaili/actions/workflows/ci.yml?query=branch%3Amain) 里打开近期运行成功的 `main` 构建，在 **Artifacts** 下载 `android-debug-apk`。解压后可在 Android 构建的 `debug` 子目录找到 APK，压缩包根目录有 `SHA256SUMS`。CI 预览的版本可能早于当前源码。CI 产物保留 14 天，APK 使用调试签名。
- **桌面端或 CI 产物已过期。** 可按下文从源码构建。桌面安装包需在对应系统上打包。

## 从源码构建

Gradle 使用与 CI 一致的 JDK 21。桌面构建还需为 Gradle 工具链提供 JDK 17，运行时需安装 VLC。构建 Android 前，请配置 SDK 路径并安装 SDK Platform 37.0 和 Build Tools 37.0.0。

```sh
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Android 调试 APK 位于 `androidApp/build/outputs/apk/` 下的 `debug` 子目录，桌面安装包位于 `desktopApp/build/compose/binaries/`。Windows 使用 `gradlew.bat`。

## 贡献与反馈

请先在 [Issues](https://github.com/shiaho777/ailiaili/issues) 查找已有反馈。报告问题时请附上版本、平台和复现步骤，并在上传日志或截图前清理隐私信息。

贡献改动时，请复用已有的相关 Issue，向 `main` 提交 PR，并说明实际执行过的验证。修复对应 Issue 时可用 `Fixes #123` 在合并时关闭它。[开发指南](static/doc/development.md) 记录代码结构与共用约定，[CI 配置](.github/workflows/ci.yml) 包含 JVM 测试、桌面编译和 Android 调试构建。

## 许可与致谢

源码采用 [GPL-3.0](LICENSE) 许可证。ailiaili 是非官方、非商业的独立项目，与哔哩哔哩官方无关。哔哩哔哩内容和商标归各自权利人所有。

感谢 [BiliDesk](https://github.com/ABING1111/BiliDesk)、Kotlin、Compose Multiplatform、Media3、LibVLC、OkHttp、Coil 及这些项目的贡献者。
