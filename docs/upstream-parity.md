# 上游对齐记录（BiliDesk v1.5.3）

本文件记录 ailiaili 从 [ABING1111/BiliDesk](https://github.com/ABING1111/BiliDesk)
借鉴的行为、接口和交互。代码全部用 Kotlin / Compose 重写，不是逐行移植。

## 已对齐

| 上游版本 | 能力 | 本仓库实现位置 |
|---|---|---|
| v1.3.2 | 游客 UA 从 Chrome/126 升到 131，修复游客态 `/view` 412 | `core/model/.../Models.kt` |
| v1.3.2 | 720P 及以下优先走 durl 合流单流，改善桌面 VLC 远距离 seek | `BiliClient.playUrl` / `decodeDurl` |
| v1.3.2 | `dm_img_str` / `dm_cover_img_str` 进程内固定，避免长驻进程被判定设备特征不稳定 | `BiliClient` 字段 |
| v1.5.0 | 游客搜索风控降级：裸端点 → 换 buvid 重试 | `BiliClient.search` / `rotateBuvid` |
| v1.4.0 | 评论图片显示、@ 提及高亮、自定义表情内联 | `Comment` / `parseComment` / `CommentText.kt` |
| v1.4.0 | 动态页「关注直播」看板 | `BiliClient.followingLive` / `Dynamic.kt` |
| v1.5.3 | 关注直播改用官方 PC 直播页 `GetWebList` 接口，一次拿回全部在播主播；回到动态页和 60 秒轮询都会刷新 | `BiliClient.followingLive` / `DynamicViewModel.onEnter` |
| v1.5.3 | 动态页左栏 UP 未读角标：本地记录上次看到的 bvid，点开该 UP 清零，首次运行只建基准 | `DynamicSeenStore` / `Dynamic.kt` |
| v1.5.3 | 竖屏副屏 / 窄窗口适配：初始窗口按工作区夹取、最小尺寸按 dp 换算、播放器窄宽度收起次要控制项 | `desktopApp/Main.kt` / `Player.kt` |
| v1.4.1 / v1.4.2 | 图片预览支持缩放、拖动、双击 1x/2x | `AvatarPreview.kt` |
| v1.4.2 | 视频卡封面长按 / 右键预览 | `Components.kt` |
| v1.5.1 | 自动 / 手动网络缓冲策略，按 CDN 实测带宽挑选预读深度 | `BufferPolicy.kt` / `Settings.kt` |
| v1.5.1 | 卡顿按 CDN host 归因，后续媒体自动加深预读 | `CdnPicker.reportStall` / `Player.kt` |
| v1.5.1 | 长视频 seek 看门狗：超时重发、必要时用 `:start-time` 重建 | `PlayerEngine.jvm.kt` / `SeekWatchdog.kt` |
| v1.5.1 | 评论输入框可展开写长评 | `Player.kt` |

## 暂不跟

| 上游能力 | 原因 |
|---|---|
| .NET 10 / WPF 云母 / 亚克力 / Inno 安装器 | Windows WPF 专属，Compose Multiplatform 不适用 |
| 账号密码登录 + 极验 | 依赖 WebView2，四平台没有同一套实现 |
| 短视频本地下载并合流 MP4 | 需要跨平台 MP4 muxer；当前用 durl 单流覆盖大部分 seek 场景 |
| 自定义下载路径 | Android 需要 SAF 持久授权，跨平台交互不一致 |
| 动态单列卡 / 追番 Tab | 属于更大的产品功能，建议单独排期 |

## 许可

上游与本项目均为 GPL-3.0。借鉴行为/接口/交互没有问题；如果后续翻译或移植
具体代码，请在本文件和 `README.md` 的致谢章节补充项目、版本、文件和许可证。
