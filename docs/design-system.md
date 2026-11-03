# ailiaili 设计系统

目标：让所有“数据展示”长得像同一套产品，而不是每个页面各写一版 Text。

底层只有一套 Compose 组件，Android / Windows / macOS / Linux 共用。

## 原则

1. **一个数据，一个语义**：播放、弹幕、点赞、投币、收藏、在线、日期都有固定语义色，不在业务页面里硬编码。
2. **展示与交互分离**：`MetricChip` 默认是纯展示；需要点击时传 `onClick`，自动获得按压动效和语义角色。
3. **响应式优先**：多个统计用 `MetricFlow`（FlowRow），窄屏自动换行，不用手写 if/else。
4. **轻量**：统计胶囊只做 `Surface + Row + Icon + 2 Text`，不引入额外布局层；列表里默认用 `Compact`。
5. **动效克制**：只给可点击元素加 `biliPressScale`，纯展示不加动画；页面切换继续用 `MaterialTheme.motionScheme`。

## 组件

### MetricChip / MetricFlow

用于“图标 + 数值 + 标签”的横向统计。

```kotlin
MetricFlow(
    metrics = listOf(
        MetricSpec(Icons.Filled.PlayArrow, "104.9万", "播放", MetricTone.Play),
        MetricSpec(Icons.AutoMirrored.Filled.Comment, "384", "弹幕", MetricTone.Danmaku),
        MetricSpec(Icons.Filled.ThumbUp, "3.8万", "点赞", MetricTone.Like),
    ),
)
```

- `MetricChipSize.Regular`：详情页、卡片头部。
- `MetricChipSize.Compact`：视频卡、个人资料、空间页等密集区域。
- `onMetricClick`：整组可点击时使用；单个可点击直接给 `MetricChip` 传 `onClick`。
- `MetricTone`：`Play` / `Danmaku` / `Like` / `Coin` / `Favorite` / `Online` / `Date` / `Accent` / `Neutral`。

### MetricStat

用于“大数字 + 标签”的纵向统计，例如空间页粉丝/关注/投稿、我的页动态/关注/粉丝。

```kotlin
Row {
    MetricStat("1.2万", "粉丝", Modifier.weight(1f))
    MetricStat("32", "关注", Modifier.weight(1f))
    MetricStat("108", "投稿", Modifier.weight(1f))
}
```

传 `onClick` 会自动加按压动效。

### MetaRow

用于视频卡底部的轻量元信息，只有小图标 + 文本，没有胶囊背景。

```kotlin
MetaRow(
    items = listOf(
        MetaSpec(Icons.Filled.PlayArrow, "33.1万", MetricTone.Play),
        MetaSpec(Icons.AutoMirrored.Filled.Comment, "492", MetricTone.Danmaku),
    ),
    maxItems = 2,
)
```

`maxItems` 默认 2，避免窄卡片溢出。

### BiliBadge

用于短状态，不承载“数值 + 单位”的统计：直播中、清晰度、未读数、等级、私密、更新至第 N 话。

```kotlin
BiliBadge("直播中", tone = BiliBadgeTone.Accent, style = BiliBadgeStyle.Solid)
BiliBadge("LV6", tone = BiliBadgeTone.Accent, style = BiliBadgeStyle.Soft)
BiliBadge("1080P", tone = BiliBadgeTone.Neutral, style = BiliBadgeStyle.Outline)
```

- `BiliBadgeTone`：`Accent` / `Neutral` / `Dark` / `Success` / `Warning`
- `BiliBadgeStyle`：`Solid` / `Soft` / `Outline`
- `BiliBadgeSize`：`Small` / `Medium`
- 叠在封面上的时长、清晰度用 `Dark + Solid`。

### BiliActionPill

用于详情页/播放器的操作按钮：点赞、投币、收藏、稍后、分享、缓存。

```kotlin
BiliActionPill(
    icon = Icons.Filled.ThumbUp,
    label = "点赞 3.8万",
    active = liked,
    accent = Color(0xFFFF6B81),
    onClick = { toggleLike() },
)
```

- 选中态：`accent` 16% 浅底 + accent 文字 + accent 细边框
- 未选中：`surfaceContainerHigh` + `onSurface`
- 统一 12dp 圆角、44dp 最小高度、按压动效

### BiliLoadingState / BiliEmptyState / BiliErrorState

统一空态、加载态、错误态。

```kotlin
BiliLoadingState(label = "加载中…")
BiliEmptyState(title = "还没有内容", message = "换个关键词试试")
BiliErrorState(message = "网络请求失败", onRetry = vm::reload)
```

`LoadBox` 已经默认使用这套状态组件，业务页优先复用 `LoadBox`。

### BiliSurfaceCard

统一卡片容器：`surfaceContainerLow` + 16dp 圆角 + 细边框 + 14dp 内边距。

```kotlin
BiliSurfaceCard {
    Text("标题", style = MaterialTheme.typography.titleLarge)
    MetricFlow(metrics)
}
```

需要不同圆角/颜色时传 `shape` / `color` / `border`，不要在页面里重复写 `Surface`。

### BiliSectionHeader

带右侧动作的区块标题，自动处理点击、触感反馈和按压动效。

```kotlin
BiliSectionHeader("最近动态", "全部") {
    vm.select(SpaceSection.Dynamic)
}
```

### biliPressScale

```kotlin
val interaction = remember { MutableInteractionSource() }
Modifier
    .clickable(interactionSource = interaction, indication = LocalIndication.current) { ... }
    .biliPressScale(interaction)
```

只给可点击元素用；纯展示不要加。

## 语义色

| Tone | 用途 | 颜色来源 |
|---|---|---|
| `Play` / `Accent` | 播放、主强调 | `colorScheme.primary` |
| `Danmaku` | 弹幕 | `#4FC3F7` |
| `Like` | 点赞 | `#FF6B81` |
| `Coin` | 投币 | `#FFB74D` |
| `Favorite` | 收藏 | `#4DD0E1` |
| `Online` | 在线/在看 | `#81C784` |
| `Date` / `Neutral` | 日期、次要信息 | `colorScheme.onSurfaceVariant` |

不要在 feature 里直接写这些十六进制；需要新语义时在 `MetricTone` 里加一项。

## 响应式

- 多统计一律 `MetricFlow`，不要 `Row + horizontalScroll`。
- 窄屏 / 卡片内部用 `MetricChipSize.Compact`。
- 详情页、宽屏头部用 `MetricChipSize.Regular`。
- 需要按窗口宽度调整时读 `LocalWindowForm.current.widthClass`，不要在组件里自己量窗口。

## 动效

- 按压：`biliPressScale`，spring，`StiffnessMediumLow`，`DampingRatioNoBouncy`，默认 0.97。
- 页面切换：`MaterialTheme.motionScheme`（`defaultSpatialSpec` / `fastSpatialSpec` / `defaultEffectsSpec`）。
- 不要给长列表里的每个卡片加 `animateContentSize`；列表滚动优先。

## 性能约定

- `MetricSpec` 列表在重组频繁的地方用 `remember(...)` 缓存。
- 统计组件不要嵌套 `BoxWithConstraints`；响应式交给外层或 `MetricFlow`。
- 视频卡、列表项用 `Compact`，详情头部用 `Regular`。
- 纯展示组件不创建 `MutableInteractionSource`；只有传 `onClick` 时才创建。

## 迁移清单

- [x] 播放页详情头部：标题卡片 + 统计胶囊
- [x] 视频卡底部元信息：小图标 + 数值
- [x] 空间页：粉丝/关注/投稿 + 区块标题 + 合集/收藏夹数量
- [x] 我的页：动态/关注/粉丝 + 硬币/经验胶囊 + 等级标
- [x] 搜索番剧海报卡：统一卡片 + 角标 + 更新信息
- [x] 动态页直播卡：直播标 + 在线人数
- [x] 消息列表未读数
- [x] 历史记录作者/时长
- [x] 离线缓存清晰度
- [x] 设置页卡片容器
- [x] 统一空态/加载态/错误态
- [x] 播放器操作按钮统一成 ActionPill
- [x] 设置页缓存摘要：已下载数量 + 封面缓存
- [x] 搜索热搜：排名角标 + 火焰图标
- [x] 空间页动态时间 / 合集数量 / 收藏夹数量
- [ ] 搜索历史 chip 的进一步结构化
- [ ] 动态页用户筛选列表
