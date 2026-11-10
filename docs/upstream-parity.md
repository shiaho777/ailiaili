# 上游对齐记录（BiliDesk v1.6.4）

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
| v1.5.3 | 竖屏副屏 / 窄窗口适配：初始窗口按工作区夹取、最小尺寸使用逻辑点、播放器窄宽度收起次要控制项 | `desktopApp/Main.kt` / `Player.kt` |
| v1.5.4 | 倍速弹层：预设 + 自定义 0.25–4.0x；长按快进目标取 max(2x, 当前速率) | `Player.kt` |
| v1.5.4 | 关注直播看板默认收起，标题行常驻 | `Dynamic.kt` |
| v1.5.4 | 桌面主窗口尺寸记忆（只记常规尺寸，最大化/全屏不覆盖） | `desktopApp/Main.kt` |
| v1.6.0 | 屏蔽升级：精确 UP / 标题规则持久化，卡片右键一键屏蔽，覆盖首页、搜索、分区、收藏、历史、动态流、关注直播、稍后再看、个人空间与相关推荐 | `ContentFilter.kt` / `Components.kt` / `ListRows.kt` |
| v1.6.0 | 首页屏蔽面板显示规则标签，可单独撤销；列表被筛空时自动补下一页 | `Home.kt` / `FilterAutoLoad` |
| v1.6.0 | 评论昵称改为次要色、加大昵称与正文间距、楼中楼回复之间加分隔线 | `Player.kt` |
| v1.6.2 | 线路选择默认「跟随服务端」，老配置一次性迁移，之后手动选择仍保留 | `Stores.kt` / `Settings.kt` |
| v1.6.2 | seek 后短暂停顿不再被误判成网络缓冲，避免「正在缓冲」反复闪 | `Player.kt` |
| v1.6.3 | 弹幕半透明圆角胶囊底衬，默认开；设置页与播放器工具栏双入口，泳道同步加高 | `DanmakuOverlay.kt` / `DanmakuRuntime.kt` / `Settings.kt` |
| v1.6.3 | 动态左栏按「最近更新」稳定排序并显示相对时间；未读改为头像右下角数字角标 + 左侧红点 | `Dynamic.kt` / `Parse.kt` |
| v1.6.3 | 设置页重新分组：赞助片段置顶，播放分画质/网络线路，弹幕分显示与过滤/外观 | `Settings.kt` |
| v1.6.4 | 播放进度本机记录与自动续播（已具备，按 bvid+cid 记录，5 秒节流落盘） | `PlayMemory.kt` / `Player.kt` |
| v1.6.4 | 搜索新增「用户」分类，支持 UP 主结果、粉丝/投稿数、认证与主页跳转 | `Search.kt` / `SearchDecode.kt` |
| v1.6.4 | 桌面主窗口位置记忆，与尺寸一起保存；位置不在任何屏幕内时回退居中 | `desktopApp/Main.kt` |
| v1.6.4 | 拖动进度条时浮出目标时间气泡（拖动预览已有） | `PlayerTransport.kt` |
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
| v1.5.4 播放器闪白 / 原生层 WM_ERASEBKGND | WPF 宿主窗口专属，Compose 播放层没有这套原生窗口路径 |
| v1.6.0 多任务下载进度中心 | 本项目只有播放页单任务缓存，没有并发下载队列 |
| v1.6.1 评论详情页排序 / v1.6.3 评论悬浮工具列 | 本项目评论是列表内联展开，没有独立详情页 |
| v1.6.3 删除弹幕关键词屏蔽 | 保留该功能作为高级选项；智能屏蔽仍默认开启 |
| v1.6.4 音质档位（Hi-Res / 杜比） | 需要播放接口返回多音轨并做音轨切换，当前只取默认音轨 |
| v1.6.4 进度条悬停时间气泡 | 当前只在拖动时显示预览气泡，桌面悬停待补 |
| v1.6.5 全局平滑滚轮 / 滚轮速度 | Compose 没有 WPF 那种全局 ScrollViewer 钩子，需要逐列表接管滚轮，暂缓 |
| 账号密码登录 + 极验 | 依赖 WebView2，四平台没有同一套实现 |
| 短视频本地下载并合流 MP4 | 需要跨平台 MP4 muxer；当前用 durl 单流覆盖大部分 seek 场景 |
| 自定义下载路径 | Android 需要 SAF 持久授权，跨平台交互不一致 |
| 动态单列卡 / 追番 Tab | 属于更大的产品功能，建议单独排期 |

## 许可

上游与本项目均为 GPL-3.0。借鉴行为/接口/交互没有问题；如果后续翻译或移植
具体代码，请在本文件和 `README.md` 的致谢章节补充项目、版本、文件和许可证。
