# ailiaili 架构

一套 Kotlin / Compose Multiplatform 代码覆盖 Android、Windows、macOS、Linux。
本文记录分层约定与关键机制的决策依据；改代码前先对照这里，不引入反向依赖。

## 模块分层与依赖方向

```
androidApp / desktopApp        平台入口（Activity / Compose Window、托盘、单实例）
        │
       app                     共享外壳：导航栈（Dest）、主题装配、免责声明、更新提示
        │
   feature:*                   页面：home dynamic search region space mine library
        │                            messages settings login player
        │
core:designsystem              设计系统 + 全站共享机制（ContentFilter、PageRefresh、
        │                            QuickActions、图片加载、滚轮、骨架）
core:data                      仓库（BiliRepository）、本地存储、离线缓存、AppGraph
        │
core:network                   BiliClient、签名、CDN 选择、请求策略（NetPolicy）
        │
core:model                     纯数据模型与纯函数（无平台依赖，最好测的一层）
core:player                    播放引擎 expect/actual（Media3 / LibVLC）+ 缓冲策略
```

规则：
- 依赖只能向下。`core:network` 不依赖 `core:data`（需要 TTL 缓存时用网络层自己的
  `ShortTtlCache`，不复用数据层 TtlCache）。
- `core:model` 保持纯函数：画质收敛、字幕定位、链接扫描、缓冲映射都在这里，全部有单测。
- 平台差异用 expect/actual 收口在最小接口上（`Platform.kt`、`PlayerEngine.kt`、
  `Hover.kt`、`Wheel.kt`），feature 层不写 if(Android)。

## 源集约定

| 源集 | 内容 |
|---|---|
| `jvmShared` | Android 与桌面共用的主体代码（绝大多数逻辑在这里） |
| `jvmHttp` | 共用 OkHttp 实现（两个平台同一套 HTTP 引擎） |
| `androidMain` | Media3、SAF/Photo Picker、MediaCodec 能力探测、系统 Typeface |
| `jvmMain` | LibVLC/vlcj、Skia 帧泵与字体、AWT 托盘/文件选择器 |
| `jvmTest` | 纯 JVM 单测（模型/解析/策略/滤波器） |

## 关键机制决策

### 播放
- **统一缓冲决策**：`BufferPolicy.resolve` 输出毫秒预读深度；桌面映射 VLC
  `:network-caching`，安卓经 `androidDurations` 映射 Media3 四档参数，由
  `SwitchableLoadControl` 委托在换片时应用（ExoPlayer 构造后不可换 LoadControl）。
- **桌面帧渲染**：VLC 回调线程拷贝 RV32 → 复用 Bitmap → 同线程做成 Skia Image；
  UI 只画现成 Image。双缓冲 + published/displayed 原子槽位，正在画的不写不关。
  行拷贝缓冲复用，非紧凑行距不再每帧分配。
- **seek 可靠性**：±1.5s 宽窗确认（容忍 VLC 回退爬回）→ 重发一次 → `:start-time`
  重建；已结束的 input 直接重建（setTime 对 ended 无效）。
- **字幕**：B 站字幕是外挂 JSON，不走 VLC/Media3 字幕管线；Compose 浮层按播放钟
  二分渲染，双端同一套。

### 网络
- **请求策略**（NetPolicy）：GET 瞬时失败指数退避重试；同 URL 单飞合并；
  WBI 密钥并发单飞 + 20h 主动刷新；只读短缓存（键含登录态、不含签名时间戳）。
  写操作与实时接口（播放地址/动态/评论/私信/搜索）绝不重试/缓存。
- **连接**：三个 HttpEngine 共享 OkHttp 连接池与调度器。
- **风控一致性**：免登录指纹与私信 dev_id 进程内稳定，不逐次随机。

### UI 状态
- **页面常驻**：外壳 `movableContentOf` 保状态切页；tab 页 ViewModel 存活但内容
  离开组合——所以 F5 刷新按「组合内注册」判定（RegisterPageRefresh），不按 VM 生命周期。
- **列表过滤**：`rememberFilteredVideos` / derivedStateOf 记忆化，滚动等无关重组
  不重算；ContentFilter 关键词分词是派生状态，不逐条视频重复 split。
- **弹幕**：位置每帧只改 draw，不触发布局/整页重组；文本测量缓存 256 条；
  关键词正则按原文缓存，分段追加不重复编译。
- **图片加载**：列表/头像等小图一律经 `cdnThumb` 取 hdslb 服务端缩图
  （显示尺寸 2x、10px 取整提高边缘缓存命中）；预览对话框才加载原图；
  GIF/已带 @ 参数/外域 URL 原样保留。新增图片加载点必须遵守此约定。
- **动画克制**：只给可点击元素按压动效；骨架只在加载中组合动画；主题切换纯颜色
  插值零快照。

### 文件组织
- 播放器按职责分文件：`Player.kt`（状态+舞台+手势）、`PlayerComments.kt`（评论区
  组件）、`PlayerTransport.kt`（控制栏）、`DanmakuOverlay/Runtime`（弹幕）、
  `SubtitleOverlay.kt`（字幕）、`CommentText.kt`（富文本）。
- 拆分只做纯移动（private → internal 可见性调整），逐行比对校验零逻辑改动。

## 测试策略

纯函数优先：模型层（画质/字幕/链接/时间）、网络层（解析/签名/重试/单飞/缓存）、
播放层（缓冲映射/seek 判定/帧尺寸）、设计系统（滚轮滤波）。
UI 行为靠双端编译 + 真机验收；不为 Compose UI 引入重型测试框架。
