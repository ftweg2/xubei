# 第三方代码与图标说明

续杯是个人自用的 app。下面列出其中拷贝、改写或引用的开源代码和图标，以及它们的许可证。

## 一、依赖库

| 库 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| `io.github.kyant0:backdrop`（[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)） | 1.0.6 | Apache License 2.0，Copyright 2025 Kyant | 玻璃的背景模糊、折射和图层采样 |
| `io.github.kyant0:shapes`（[Kyant0/Shapes](https://github.com/Kyant0/Shapes)） | 1.2.0 | Apache License 2.0，Copyright Kyant | backdrop 的运行时依赖 |

两个库的版本不能升级，原因写在 `gradle/libs.versions.toml` 的注释里。

## 二、拷贝或改写的源码

来源：[Kyant0/AndroidLiquidGlass 1.0.6 的 catalog 示例](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog)，Apache License 2.0，Copyright 2025 Kyant。每个文件开头保留了许可证声明，并写明了改动。

| 本项目文件 | 来源文件 | 改动 |
|---|---|---|
| `app/src/main/java/com/ftwegc/myapplication/ui/navigation/LiquidTabs.kt` | `components/LiquidBottomTabs.kt`、`components/LiquidBottomTab.kt` | 改用项目自己的玻璃（含低版本降级和“降低透明效果”）；液滴采样导航栏导出的图层；液滴画在文字下面，从选中项拖动；移动时横向拉伸；滚动收缩；无障碍语义 |
| `app/src/main/java/com/ftwegc/myapplication/ui/glass/DampedDragAnimation.kt` | `utils/DampedDragAnimation.kt` | 横纵分开的按压缩放；`snapTo`（系统关闭动画时）；点选时的移动曲线；用 Compose 帧时钟代替 `awaitFrame` |
| `app/src/main/java/com/ftwegc/myapplication/ui/glass/DragGestureInspector.kt` | `utils/DragGestureInspector.kt` | 只改了包名 |
| `app/src/main/java/com/ftwegc/myapplication/ui/glass/LiquidToggle.kt` | `components/LiquidToggle.kt` | 改名 `GlassSwitch`，由 `checked` 控制（整行可点）；续杯的配色（青色）代替 iOS 绿；通过 `LocalGlassSource` 采样外层玻璃（弹层导出的图层），无模糊档位退回普通滑块；系统关闭动画时直接跳到位；胶囊用 `RoundedCornerShape` |
| `app/src/main/java/com/ftwegc/myapplication/ui/glass/LiquidSlider.kt` | `components/LiquidSlider.kt` | 改名 `GlassSlider`，加步进和取值范围、无障碍语义；续杯的配色；采样外层玻璃，无模糊档位退回普通滑块；系统关闭动画时直接跳到位 |

Apache License 2.0 全文：<https://www.apache.org/licenses/LICENSE-2.0>

## 三、服务图标

图标的矢量路径从各图标库的官方发布地址下载，由脚本原样写入 `app/src/main/java/com/ftwegc/myapplication/ui/brand/BrandGlyphs.kt`，没有手工改动路径。品牌色也取自图标库自己的数据。

### Simple Icons 16.33.0（CC0 1.0）

来源：`https://cdn.jsdelivr.net/npm/simple-icons@16.33.0/icons/<标识>.svg`，<https://github.com/simple-icons/simple-icons>

`claude`、`anthropic`、`googlegemini`、`githubcopilot`、`cursor`、`perplexity`、`deepseek`、`kimi`、`qwen`、`windsurf`、`poe`、`suno`、`vultr`、`digitalocean`、`hetzner`、`alibabacloud`、`cloudflare`、`icloud`、`apple`、`spotify`、`netflix`、`youtube`、`telegram`、`1password`、`jetbrains`、`notion`

### Lobe Icons（`@lobehub/icons-static-svg` 1.95.1，MIT License，Copyright (c) 2023 LobeHub）

来源：`https://cdn.jsdelivr.net/npm/@lobehub/icons-static-svg@1.95.1/icons/<名称>.svg`，<https://github.com/lobehub/lobe-icons>

`openai`、`midjourney`、`xai`、`grok`、`aws`、`tencentcloud`、`doubao`、`kiro`（均为单色版本；豆包和 Kiro 的品牌色取自同名的 `-color` 版本）

MIT License 要求保留的声明：

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.

### Dashboard Icons（homarr-labs，Apache License 2.0）

来源：`https://raw.githubusercontent.com/homarr-labs/dashboard-icons/main/svg/oracle.svg`，<https://github.com/homarr-labs/dashboard-icons>

`oracle`（描边样式，按原样用描边绘制）

### 没有收录的品牌

搬瓦工（BandwagonHost）、RUVDS、JustHost 在上面三个图标库里都找不到；RackNerd 只在 Dashboard Icons 里有 PNG 位图，没有矢量。这几个按规则不自己画，显示为字母标记。

## 四、商标说明

图标库的许可证只覆盖图标文件本身，不覆盖商标权。上面所有服务的名称、标志和商标归各自的品牌所有者所有。续杯仅供个人使用，图标只用来识别订阅对应的服务，不代表与这些品牌有任何关联或获得其认可。不要把这些图标用在公开发布的版本或宣传材料里。

## 五、非第三方内容

续杯的标志（环里的一只玻璃杯）、导航图标和界面样式照 `docs/mockups/` 里的效果图绘制，是本项目自己的设计。以下也是本项目自己编写的，没有拷贝第三方代码：玻璃对话框、玻璃日期选择器、玻璃提示条、分组列表、环形加载、杯子下拉刷新指示器、启动画面和测试连接的注满动画、成功对勾动画、统计页的全部图表和计算（现金流与摊销两种口径的思路参考了 renewlet，只借鉴功能设计，没有拷贝代码）。

## 六、这份文件在 app 里的位置

构建时这份文件会原样复制进 app 的资源，“设置 → 关于续杯 → 开源许可”页面显示的就是它的内容。
