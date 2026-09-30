# 开源库选型结论（2026-09-28）

监工已做决定。实现时按这份文档执行，不要自行更换方案。遇到编译不过的情况按“退路”一节处理，并在汇报里写明。

## 一、决定

| 事项 | 决定 |
|---|---|
| 玻璃效果库 | `io.github.kyant0:backdrop:1.0.6`，同时显式声明 `io.github.kyant0:shapes:1.2.0` |
| 图表 | 全部自己用 Compose Canvas 画，不引入图表库 |
| 底部导航 | 改写 Kyant 示例里的 `LiquidBottomTabs`，自己加滚动收缩和独立圆钮 |
| 服务图标 | 以 simple-icons 为主，缺的从 lobe-icons 和 dashboard-icons 补 |
| 图标转换方式 | 把 SVG 的路径字符串存成常量，运行时用 `PathParser` 生成 `ImageVector` |

## 二、依赖条目

写进 `gradle/libs.versions.toml`：

```toml
[versions]
kyantBackdrop = "1.0.6"
kyantShapes = "1.2.0"

[libraries]
kyant-backdrop = { module = "io.github.kyant0:backdrop", version.ref = "kyantBackdrop" }
kyant-shapes   = { module = "io.github.kyant0:shapes",   version.ref = "kyantShapes" }
```

**版本绝对不能升级。** backdrop 2.x 需要 Kotlin 2.3 或 2.4 和 Compose 1.11 或 1.12。shapes 1.2.1 需要 Kotlin 2.4.10。本项目是 Kotlin 2.2.10 和 Compose 1.10.x。在版本旁边写注释说明原因。

backdrop 的 POM 里 shapes 是 runtime 作用域，所以 shapes 必须显式声明，否则编译时找不到 `com.kyant.shapes.*`。

引入后 kotlin-stdlib 会被抬到 2.3.10。Kotlin 2.2 编译器能读 2.3 的元数据，预期只会有版本不一致的警告。

## 三、退路

按顺序尝试：

1. backdrop 1.0.6 编译不过：换 backdrop 1.0.2。它依赖 stdlib 2.2.21 和 Compose 1.10.0，不依赖 shapes。缺点是没有 1.0.4 修复的绘制不更新问题。
2. 库完全不能用：自己用 `RenderEffect` 加 `RuntimeShader` 实现模糊、折射和高光。
3. 不要用升级 Kotlin 或 Compose 的办法解决。

## 四、backdrop 1.0.6 用法

文档：https://kyant.gitbook.io/backdrop 。源码在仓库的 `android` 分支。

### 基本结构

```kotlin
Box(Modifier.fillMaxSize()) {
    val bg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(bg)      // 先画底色，避免透明像素
        drawContent()
    }
    // 被玻璃透过的内容
    Content(Modifier.fillMaxSize().layerBackdrop(backdrop))

    // 玻璃组件
    Box(
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(32.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), depthEffect = true)
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.12f)) }
            )
    )
}
```

需要的导入：`com.kyant.backdrop.drawBackdrop`、`com.kyant.backdrop.backdrops.layerBackdrop`、`com.kyant.backdrop.backdrops.rememberLayerBackdrop`、`com.kyant.backdrop.effects.blur`、`com.kyant.backdrop.effects.lens`、`com.kyant.backdrop.effects.vibrancy`。

### drawBackdrop 的参数

`backdrop`、`shape`、`effects`、`highlight`、`shadow`、`innerShadow`、`layerBlock`、`exportedBackdrop`、`onDrawBehind`、`onDrawBackdrop`、`onDrawSurface`、`onDrawFront`。

### 效果

必须按这个顺序调用：颜色滤镜，模糊，透镜。

- 颜色滤镜：`colorFilter`、`opacity`、`colorControls(brightness, contrast, saturation)`、`vibrancy()`、`exposureAdjustment`、`gammaAdjustment`。
- 模糊：`blur(radius, edgeTreatment)`。
- 折射和色散：`lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)`。
- 高光：`Highlight.Default`、`Highlight.Ambient`、`Highlight.Plain`。
- 着色：库没有单独的接口。在 `onDrawSurface` 里画颜色。

### 各系统版本的实际行为

这是读源码确认的，库不会自动降级。

| 系统版本 | 行为 | 需要自己做的 |
|---|---|---|
| Android 13 及以上 | 全部效果生效 | 无 |
| Android 12 | 有模糊和颜色滤镜，`lens` 不生效 | 无 |
| Android 11 及以下 | 没有模糊，玻璃区域原样画出背景 | 用 `Build.VERSION.SDK_INT` 判断，在 `onDrawSurface` 里画更不透明的底色 |

### 必须避开的坑

1. **玻璃叠玻璃会崩溃。** 不能在已经 `drawBackdrop` 的容器上再套 `layerBackdrop` 给子玻璃用，渲染线程会崩。要用 `exportedBackdrop = rememberLayerBackdrop()`。底部弹层、玻璃块里的小玻璃都属于这种情况。
2. **`lens` 对形状有限制。** 只支持 `CornerBasedShape` 或 kyant 的圆角矩形，其他形状在 Android 13 及以上会抛异常。统一用 `RoundedCornerShape`。
3. **按压缩放写在 `layerBlock` 里。** 写在外层 `graphicsLayer` 上会连背景一起缩放。
4. **内存泄漏。** 1.0.6 里有一个布局坐标泄漏的问题，到 2.0 才修复。页面频繁进出时要留意。

## 五、性能规则

来自库作者的说明和 KeiOS 的真机实测。

- 列表里的玻璃块用轻量玻璃：`vibrancy()` 加小半径 `blur`，不加 `lens`。
- `lens` 和色散只给悬浮元素用：底部导航、顶栏、加号按钮、弹层。
- 模糊半径上限约 11dp。
- 完全滚出屏幕的玻璃跳过绘制。
- 形状用普通 `RoundedCornerShape`。连续曲率圆角会走软件路径，每帧上传纹理的次数会从约 5 次升到约 18 次。
- 启动时预热着色器，避免第一次展开时卡顿。
- 滚动和转场期间可以冻结背景重录。这个要自己实现。
- 降低背景采集分辨率没有用，不要做。

## 六、可以直接拷贝改用的源码

拷贝时保留许可证和版权声明，改动处注明。在项目根目录建 `THIRD_PARTY_NOTICES.md` 列出来源。

### Kyant 示例组件（Apache-2.0）

`https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog/`

- `components/LiquidBottomTabs.kt`、`LiquidBottomTab.kt`：可拖动的玻璃液滴导航。
- `components/LiquidButton.kt`、`LiquidToggle.kt`、`LiquidSlider.kt`。
- `utils/InteractiveHighlight.kt`：按压高光。
- `utils/DampedDragAnimation.kt`。
- `destinations/` 下有对话框、渐进模糊、自适应亮度玻璃、滚动列表等示例。

### KeiOS（Apache-2.0）

`https://github.com/hosizoraru/KeiOS` 的 `ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/`

它用的是 backdrop 2.0.1，接口和 1.0.6 有差异，拷贝后要适配。

- `glass/UiPerformanceBudget.kt`、`CullWhenFullyClipped.kt`、`GlassEffectRuntime.kt`。
- `sheet/LiquidGlassBottomSheet.kt`、`dialog/LiquidGlassDialog.kt`。
- `chrome/LiquidGlassBottomBar.kt`。
- `docs/planning/` 下的性能复盘文档。

### Yanami（MIT）

`https://github.com/icylian/Yanami`，Komari 的 Android 客户端。

- `app/src/main/java/com/sekusarisu/yanami/ui/screen/nodelist/NodeCardIndicators.kt`：Canvas 画的圆环。
- `NodeDetailCharts.kt`：带渐变填充的折线。它用的是 Vico，我们只参考视觉，不引入 Vico。

### 只能看思路、不能拷贝的（GPL-3.0）

Convx、Square、Wallos。可以参考的思路：滚动时冻结背景重录、启动时预热着色器、低版本改用半透明底色。

## 七、服务图标

### 来源

| 品牌 | 来源 | 标识 | 颜色 |
|---|---|---|---|
| Claude | simple-icons | `claude` | #D97757 |
| Anthropic | simple-icons | `anthropic` | #191919 |
| Google Gemini | simple-icons | `googlegemini` | #8E75B2 |
| GitHub Copilot | simple-icons | `githubcopilot` | #000000 |
| Cursor | simple-icons | `cursor` | #000000 |
| Perplexity | simple-icons | `perplexity` | #1FB8CD |
| DeepSeek | simple-icons | `deepseek` | #5786FE |
| Vultr | simple-icons | `vultr` | #007BFC |
| DigitalOcean | simple-icons | `digitalocean` | #0080FF |
| Hetzner | simple-icons | `hetzner` | #D50C2D |
| Alibaba Cloud | simple-icons | `alibabacloud` | #FF6A00 |
| Cloudflare | simple-icons | `cloudflare` | #F38020 |
| iCloud | simple-icons | `icloud` | #3693F3 |
| Apple | simple-icons | `apple` | #000000 |
| Spotify | simple-icons | `spotify` | #1ED760 |
| Netflix | simple-icons | `netflix` | #E50914 |
| YouTube | simple-icons | `youtube` | #FF0000 |
| Telegram | simple-icons | `telegram` | #26A5E4 |
| 1Password | simple-icons | `1password` | #145FE4 |
| JetBrains | simple-icons | `jetbrains` | #000000 |
| Notion | simple-icons | `notion` | #000000 |
| OpenAI / ChatGPT | lobe-icons | `openai.svg` | 单色 |
| Midjourney | lobe-icons | `midjourney.svg` | 单色 |
| xAI | lobe-icons | `xai.svg` | 单色 |
| Grok | lobe-icons | `grok.svg` | 单色 |
| AWS | lobe-icons | `aws.svg` | #FF9900 |
| Tencent Cloud | lobe-icons | `tencentcloud-color.svg` | #006EFF |
| Oracle | dashboard-icons | `oracle.svg` | #C74634 |

simple-icons 的数据：`https://cdn.jsdelivr.net/npm/simple-icons@16.33.0/icons/<标识>.svg`。
lobe-icons：npm 包 `@lobehub/icons-static-svg@1.95.1`。
dashboard-icons：`https://github.com/homarr-labs/dashboard-icons`。

OpenAI、AWS、Oracle 是应商标方要求从 simple-icons 移除的。Midjourney、xAI、Tencent Cloud 从未收录。

没列在表里的品牌（搬瓦工、RackNerd、RUVDS、JustHost、Kimi、豆包、通义等）先到这三个来源里找。找不到就用字母标记，不要自己画商标。

### 处理规则

- 黑色图标（#000000 或 #191919）放在深色玻璃上看不见。这类图标用白色绘制，底座用中性色。
- 彩色品牌：图标用白色，底座用品牌色做渐变。
- 名称匹配要宽松：不区分大小写，忽略空格，支持别名。例如 “ChatGPT Plus” “chatgpt” “GPT” 都匹配到 OpenAI，“甲骨文” “Oracle” “OCI” 都匹配到 Oracle。
- Dashboard-icons 的 Oracle 是描边样式，要用 stroke 属性画，或者先转成填充路径。

### 许可说明

图标库的许可不覆盖商标权。这个 app 仅供个人使用，图标只用来识别订阅对应的服务。不要把这些图标用在公开发布的版本或宣传材料里。在 `THIRD_PARTY_NOTICES.md` 里写明这一点。

## 八、从同类项目借鉴的功能

纳入本次重做：

- **现金流和摊销两种口径**（来自 renewlet）。统计页的每月柱状图可以切换：现金流是实际扣款落在哪个月，摊销是把年付季付平摊到每个月。
- **点柱子看当月明细**（来自 renewlet）。
- **关键指标卡**（来自 Wallos 和 Subscription-Management）：日均花费、最贵的订阅、相比上月的变化、累计已花。
- **列表卡片上的资源小环**（来自 Yanami）：VPS 方块上显示 CPU、内存、硬盘三个小环。
- **Komari 返回但之前没用的指标**（来自 Yanami）：负载、进程数、TCP 和 UDP 连接数、系统和内核、CPU 型号、虚拟化类型。放在详情页的“节点信息”面板里。

本次不做：预算功能、多成员、支付方式管理、桌面小组件。
