package com.ftwegc.myapplication.ui.debug

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.GlassSettings
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.charts.BarChart
import com.ftwegc.myapplication.ui.charts.BarDatum
import com.ftwegc.myapplication.ui.charts.CalendarHeatmap
import com.ftwegc.myapplication.ui.charts.ConcentricRings
import com.ftwegc.myapplication.ui.charts.DonutChart
import com.ftwegc.myapplication.ui.charts.DonutSlice
import com.ftwegc.myapplication.ui.charts.ForecastLineChart
import com.ftwegc.myapplication.ui.charts.HBarRow
import com.ftwegc.myapplication.ui.charts.HorizontalBars
import com.ftwegc.myapplication.ui.charts.LiquidRing
import com.ftwegc.myapplication.ui.charts.ProgressRing
import com.ftwegc.myapplication.ui.charts.RingSeries
import com.ftwegc.myapplication.ui.charts.SemiGauge
import com.ftwegc.myapplication.ui.charts.SmoothAreaChart
import com.ftwegc.myapplication.ui.charts.Sparkline
import com.ftwegc.myapplication.ui.charts.TimelineChart
import com.ftwegc.myapplication.ui.charts.TimelineEvent
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.GlassTier
import com.ftwegc.myapplication.ui.glass.currentGlass
import java.time.LocalDate
import java.time.YearMonth

private enum class GalleryPage(val label: Int) { Charts(R.string.gallery_charts), Icons(R.string.gallery_icons), Glass(R.string.gallery_glass) }

/*
 * Sample names, amounts and their "示例：…" descriptions below are sample data (like
 * ui/preview/SampleData.kt), not interface text; titles and labels come from strings.xml.
 */

/**
 * Debug builds only (settings → 调试): every chart component with sample data, every built-in
 * service icon, and the glass switches (force the Android 12 / 11 fallbacks, reduce transparency)
 * for screenshots and checks on a new device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugGalleryScreen(
    glassSettings: GlassSettings,
    onSetReduceTransparency: (Boolean) -> Unit,
    onSetGlassTier: (GlassTier?) -> Unit,
    onBack: () -> Unit,
) {
    val env = currentGlass()
    var page by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSurface(
                    shape = GlassDefaults.CapsuleShape,
                    onClick = onBack,
                    onClickLabel = stringResource(R.string.action_back),
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = env.ink.primary, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.debug_gallery), color = env.ink.primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item(key = "tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GalleryPage.entries.forEachIndexed { i, p ->
                    GlassChip(stringResource(p.label), selected = page == i, onClick = { page = i })
                }
                Spacer(Modifier.weight(1f))
                GlassChip(stringResource(R.string.gallery_tier_now, tierLabel(env.tier)))
            }
        }
        when (GalleryPage.entries[page]) {
            GalleryPage.Charts -> chartItems()
            GalleryPage.Icons -> iconItems()
            GalleryPage.Glass -> item(key = "glass") { GlassSwitches(glassSettings, onSetReduceTransparency, onSetGlassTier) }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun tierLabel(tier: GlassTier): String = stringResource(
    when (tier) {
        GlassTier.Refractive -> R.string.gallery_tier_refractive
        GlassTier.Blurred -> R.string.gallery_tier_blurred
        GlassTier.Tinted -> R.string.gallery_tier_tinted
        GlassTier.Solid -> R.string.gallery_tier_solid
    },
)

@Composable
private fun GalleryBlock(title: String, content: @Composable () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GlassLabel(title)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.chartItems() {
    item(key = "c1") {
        GalleryBlock(stringResource(R.string.gallery_chart_1)) {
            SmoothAreaChart(
                values = listOf(0f, 20f, 20f, 36f, 36f, 60f, 60f, 82f, 120f, 148f, 150f, 176f, 196f),
                forecast = listOf(210f, 226f, 240f),
                contentDescription = "示例：支出节奏，到今天 196，月底预计 240",
            )
        }
    }
    item(key = "c2") {
        GalleryBlock(stringResource(R.string.gallery_chart_2)) {
            val c = chartColors()
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                ProgressRing(0.9f, "示例：本周期已过 90%，3 天后到期", colors = listOf(c.amber)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("3", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.home_due_after_unit), fontSize = 11.sp, color = c.inkSecondary)
                    }
                }
                ProgressRing(0.62f, "示例：流量已用 62%", colors = listOf(c.teal, c.blue)) {
                    Text("62%", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    item(key = "c3") {
        GalleryBlock(stringResource(R.string.gallery_chart_3)) {
            val c = chartColors()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConcentricRings(
                    listOf(RingSeries(0.94f, c.coral, "洛杉矶"), RingSeries(0.62f, c.amber, "法兰克福"), RingSeries(0.31f, c.teal, "新西伯利亚"), RingSeries(0.12f, c.blue, "伦敦")),
                    "示例：洛杉矶 94%，法兰克福 62%，新西伯利亚 31%，伦敦 12%",
                    size = 110.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    com.ftwegc.myapplication.ui.glass.LegendRow(c.coral, "洛杉矶", "94%", Modifier.width(150.dp))
                    com.ftwegc.myapplication.ui.glass.LegendRow(c.amber, "法兰克福", "62%", Modifier.width(150.dp))
                    com.ftwegc.myapplication.ui.glass.LegendRow(c.teal, "新西伯利亚", "31%", Modifier.width(150.dp))
                    com.ftwegc.myapplication.ui.glass.LegendRow(c.blue, "伦敦", "12%", Modifier.width(150.dp))
                }
            }
        }
    }
    item(key = "c4") {
        GalleryBlock(stringResource(R.string.gallery_chart_4)) {
            val c = chartColors()
            var sel by remember { mutableIntStateOf(0) }
            val slices = listOf(DonutSlice(212f, c.violet, "AI"), DonutSlice(139f, c.blue, "VPS"), DonutSlice(35f, c.teal, "其他"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DonutChart(slices, "示例：AI 55%，VPS 36%，其他 9%", selected = sel, onSelect = { if (it >= 0) sel = it }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("55%", "36%", "9%")[sel], fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(slices[sel].label, fontSize = 10.5.sp, color = c.inkSecondary)
                    }
                }
                Text(stringResource(R.string.gallery_donut_hint), color = c.inkSecondary, fontSize = 12.sp)
            }
        }
    }
    item(key = "c5") {
        GalleryBlock(stringResource(R.string.gallery_chart_5)) {
            val c = chartColors()
            var sel by remember { mutableIntStateOf(-1) }
            BarChart(
                bars = listOf(52f, 60f, 48f, 92f, 56f, 64f, 58f, 100f, 54f, 62f, 50f, 88f).mapIndexed { i, v -> BarDatum(v, future = i > 5, current = i == 5) },
                contentDescription = "示例：12 个月应付，本月 64",
                height = 96.dp,
                selected = sel,
                onSelect = { sel = it },
            )
            Spacer(Modifier.height(12.dp))
            BarChart(
                bars = List(14) { BarDatum(listOf(10f + (it * 7) % 23, 5f + (it * 3) % 11)) },
                contentDescription = "示例：每日上下行堆叠",
                stackColors = listOf(c.mint, c.violet),
            )
        }
    }
    item(key = "c6") {
        GalleryBlock(stringResource(R.string.gallery_chart_6)) {
            val c = chartColors()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sparkline(listOf(18f, 14f, 17f, 8f, 12f, 6f, 14f, 10f, 15f, 9f), "示例：上行 1.2 MB/s", Modifier.weight(1f), color = c.mint)
                Sparkline(listOf(10f, 16f, 6f, 12f, 4f, 14f, 8f, 18f, 9f, 13f), "示例：上行 8.6 MB/s", Modifier.weight(1f), color = c.coral)
                Sparkline(listOf(20f, 19f, 17f, 19f, 15f, 18f, 16f, 19f, 17f, 18f), "示例：上行 86 KB/s", Modifier.weight(1f), color = c.blue, fill = true)
            }
        }
    }
    item(key = "c7") {
        GalleryBlock(stringResource(R.string.gallery_chart_7)) {
            val c = chartColors()
            Row {
                SemiGauge(0.38f, "38%", "CPU", "示例：CPU 38%", Modifier.weight(1f), c.teal)
                SemiGauge(0.71f, "71%", "内存", "示例：内存 71%", Modifier.weight(1f), c.amber)
                SemiGauge(0.54f, "54%", "硬盘", "示例：硬盘 54%", Modifier.weight(1f), c.blue)
            }
        }
    }
    item(key = "c8") {
        GalleryBlock(stringResource(R.string.gallery_chart_8)) {
            val c = chartColors()
            val rows = listOf(
                HBarRow(1f, listOf(androidx.compose.ui.graphics.Color(0xFFE08A67), c.amber), "¥720", "Claude"),
                HBarRow(0.4f, listOf(androidx.compose.ui.graphics.Color(0xFF1FCF8B), c.mint), "¥144", "ChatGPT"),
                HBarRow(0.4f, listOf(androidx.compose.ui.graphics.Color(0xFF8B95A5), androidx.compose.ui.graphics.Color(0xFFCBD5E1)), "¥144", "Cursor"),
                HBarRow(0.22f, listOf(androidx.compose.ui.graphics.Color(0xFFFF7A90), androidx.compose.ui.graphics.Color(0xFFFDA4AF)), "¥43", "洛杉矶"),
            )
            val marks = listOf(MarkSource("Claude Max"), MarkSource("ChatGPT Plus"), MarkSource("Cursor Pro"), MarkSource("洛杉矶 9929", Category.VPS))
            HorizontalBars(rows, "示例：Claude ¥720，ChatGPT ¥144，Cursor ¥144，洛杉矶 ¥43") { i -> BrandMark(marks[i], size = MarkSize.Small) }
        }
    }
    item(key = "c9") {
        GalleryBlock(stringResource(R.string.gallery_chart_9)) {
            CalendarHeatmap(
                month = YearMonth.of(2026, 9),
                amounts = mapOf(1 to 72_000L, 7 to 14_400L, 12 to 12_900L, 15 to 36_000L, 21 to 2_100L, 24 to 39_000L, 28 to 2_100L),
                today = LocalDate.of(2026, 9, 28),
                contentDescription = "示例：9 月有 7 天缴费，最多一天 ¥720",
            )
        }
    }
    item(key = "c10") {
        GalleryBlock(stringResource(R.string.gallery_chart_10)) {
            val names = listOf("Claude Max", "ChatGPT Plus", "甲骨文 法兰克福", "Oracle 东京", "伦敦 VPS")
            TimelineChart(
                events = listOf(
                    TimelineEvent(0, 3, "10/1"), TimelineEvent(1, 9, "10/7"), TimelineEvent(2, 17, "10/15"),
                    TimelineEvent(3, 18, "10/16"), TimelineEvent(4, 26, "10/24"),
                ),
                windowDays = 30,
                contentDescription = "示例：未来 30 天 5 个到期",
            ) { e -> BrandMark(MarkSource(names[e.key as Int]), size = MarkSize.Small) }
        }
    }
    item(key = "c11") {
        GalleryBlock(stringResource(R.string.gallery_chart_11)) {
            val c = chartColors()
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                LiquidRing(0.94f, "94%", stringResource(R.string.gallery_used), "示例：流量已用 94%")
                LiquidRing(
                    0.36f, "36%", stringResource(R.string.gallery_used), "示例：流量已用 36%",
                    colors = com.ftwegc.myapplication.ui.charts.liquidColors(0.36f, c),
                    waveBack = c.mint.copy(alpha = 0.9f),
                )
            }
        }
    }
    item(key = "c12") {
        GalleryBlock(stringResource(R.string.gallery_chart_12)) {
            ForecastLineChart(
                actual = listOf(0f, 18f, 40f, 66f, 92f, 120f, 150f, 184f, 210f, 246f, 280f, 318f, 352f, 390f, 420f, 452f, 470f),
                totalDays = 30,
                limit = 500f,
                limitLabel = stringResource(R.string.gallery_limit_sample),
                startLabel = "9/1",
                todayLabel = stringResource(R.string.gallery_today),
                contentDescription = "示例：已用 470 GB，按当前速度将超出 500 GB 限额",
            )
        }
    }
    item(key = "c0") {
        GalleryBlock(stringResource(R.string.gallery_chart_empty)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmoothAreaChart(emptyList(), "无数据", Modifier.weight(1f))
                ProgressRing(null, "无数据", size = 86.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.iconItems() {
    item(key = "logo") {
        GalleryBlock(stringResource(R.string.gallery_logo)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                VluerLogo(size = 28.dp)
                VluerLogo(size = 44.dp)
                VluerLogo(size = 72.dp)
            }
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.ftwegc.myapplication.ui.brand.CupSpinner(size = 48.dp)
                com.ftwegc.myapplication.ui.brand.CupFill(size = 48.dp)
                com.ftwegc.myapplication.ui.brand.RingCheck(size = 28.dp)
                com.ftwegc.myapplication.ui.brand.VluerSpinner(size = 28.dp)
            }
            // The icon resources as Android draws them: the launcher icon (background and
            // foreground layers in a round and a rounded-square mask), the themed icon
            // (monochrome layer, tinted), the notification icon.
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherIconPreview(androidx.compose.foundation.shape.CircleShape)
                LauncherIconPreview(androidx.compose.foundation.shape.RoundedCornerShape(24))
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E3A36)),
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.ftwegc.myapplication.R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF7EF0DD)),
                        modifier = Modifier.requiredSize(96.dp),
                    )
                }
                Icon(
                    androidx.compose.ui.res.painterResource(com.ftwegc.myapplication.R.drawable.ic_stat_cup),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    item(key = "brands") {
        GalleryBlock(stringResource(R.string.gallery_brands, BrandCatalog.brands.size)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandCatalog.brands.forEach { brand ->
                    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BrandMark(MarkSource(brand.title, iconKey = brand.id))
                        Text(
                            brand.title,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = chartColors().inkSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
    item(key = "letters") {
        GalleryBlock(stringResource(R.string.gallery_letters)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    MarkSource("洛杉矶 9929", Category.VPS, region = "🇺🇸"),
                    MarkSource("新西伯利亚", Category.VPS, region = "🇷🇺"),
                    MarkSource("伦敦 VPS", Category.VPS, region = "🇬🇧"),
                    MarkSource("搬瓦工 CN2", Category.VPS, region = "🇺🇸"),
                    MarkSource("RackNerd 圣何塞", Category.VPS, region = "🇺🇸"),
                    MarkSource("RUVDS 莫斯科", Category.VPS, region = "🇷🇺"),
                    MarkSource("JustHost", Category.VPS, region = "🇷🇺"),
                    MarkSource("域名 xubei.app"),
                    MarkSource("健身房"),
                ).forEach { BrandMark(it) }
                BrandMark(MarkSource("甲骨文 法兰克福", Category.VPS, region = "🇩🇪"), size = MarkSize.Large)
            }
        }
    }
    item(key = "sizes") {
        GalleryBlock(stringResource(R.string.gallery_sizes)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkSize.entries.forEach { BrandMark(MarkSource("Claude Max"), size = it) }
                MarkSize.entries.forEach { BrandMark(MarkSource("甲骨文-eu-92", Category.VPS, region = "🇩🇪"), size = it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlassSwitches(settings: GlassSettings, onReduce: (Boolean) -> Unit, onTier: (GlassTier?) -> Unit) {
    val env = currentGlass()
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GalleryBlock(stringResource(R.string.gallery_tier_title)) {
            Text(stringResource(R.string.gallery_tier_desc, Build.VERSION.SDK_INT), color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                GlassChip(stringResource(R.string.gallery_tier_native), selected = settings.tierOverride == null, onClick = { onTier(null) })
                GlassChip(stringResource(R.string.gallery_tier_blurred), selected = settings.tierOverride == GlassTier.Blurred, onClick = { onTier(GlassTier.Blurred) })
                GlassChip(stringResource(R.string.gallery_tier_tinted), selected = settings.tierOverride == GlassTier.Tinted, onClick = { onTier(GlassTier.Tinted) })
            }
        }
        GalleryBlock(stringResource(R.string.gallery_reduce_title)) {
            Text(stringResource(R.string.gallery_reduce_desc), color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GlassChip(stringResource(R.string.gallery_off), selected = !settings.reduceTransparency, onClick = { onReduce(false) })
                GlassChip(stringResource(R.string.gallery_on), selected = settings.reduceTransparency, onClick = { onReduce(true) })
            }
        }
        GalleryBlock(stringResource(R.string.gallery_thickness_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassThickness.entries.forEach { t ->
                    GlassSurface(Modifier.weight(1f).height(72.dp), thickness = t, onClick = {}, contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when (t) {
                                    GlassThickness.Thin -> R.string.gallery_thin
                                    GlassThickness.Standard -> R.string.gallery_standard
                                    GlassThickness.Thick -> R.string.gallery_thick
                                },
                            ),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) {
                GlassSurface(Modifier.fillMaxWidth().height(64.dp), tint = chartColors().amber, onClick = {}, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.gallery_tinted_sample), fontSize = 12.sp)
                }
            }
        }
    }
}

/** An adaptive launcher icon: 108 dp layers, the middle 72 dp shown through [mask] (64 dp here). */
@Composable
private fun LauncherIconPreview(mask: androidx.compose.ui.graphics.Shape) {
    Box(Modifier.size(64.dp).clip(mask), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.ftwegc.myapplication.R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.requiredSize(96.dp),
        )
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.ftwegc.myapplication.R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(96.dp),
        )
    }
}
