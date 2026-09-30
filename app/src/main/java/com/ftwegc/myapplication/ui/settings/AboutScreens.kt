package com.ftwegc.myapplication.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.brand.LogoRing
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.brand.VluerSpinner
import com.ftwegc.myapplication.ui.glass.GlassDivider
import com.ftwegc.myapplication.ui.glass.GlassGroup
import com.ftwegc.myapplication.ui.glass.GlassPage
import com.ftwegc.myapplication.ui.glass.GlassRow
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.preview.GlassPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "关于": the 续杯 mark, the version, the licenses and what the app does with data. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    AboutContent(version, onBack, onOpenLicenses)
}

@Composable
private fun AboutContent(version: String, onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val env = currentGlass()
    GlassPage(title = stringResource(R.string.about_title), onBack = onBack, spacing = 18.dp) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    // A soft halo of the ring colors behind the mark.
                    Canvas(Modifier.size(132.dp)) {
                        drawCircle(
                            Brush.radialGradient(
                                0f to LogoRing[0].copy(alpha = if (env.dark) 0.34f else 0.26f),
                                0.55f to LogoRing[2].copy(alpha = 0.12f),
                                1f to Color.Transparent,
                            ),
                        )
                    }
                    VluerLogo(size = 104.dp)
                }
                Text(
                    stringResource(R.string.app_name),
                    color = env.ink.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                )
                Text(stringResource(R.string.about_version, version), color = env.ink.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Text(
                    stringResource(R.string.about_tagline),
                    color = env.ink.primary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        GlassGroup(null) {
            GlassRow(
                title = stringResource(R.string.about_licenses),
                subtitle = stringResource(R.string.about_licenses_desc),
                icon = Icons.Rounded.Description,
                iconTint = Color(0xFF6366F1),
                onClick = onOpenLicenses,
            )
            GlassDivider()
            GlassRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_desc),
                icon = Icons.Rounded.Shield,
                iconTint = Color(0xFF22C55E),
            )
            GlassDivider()
            GlassRow(
                title = stringResource(R.string.about_personal),
                subtitle = stringResource(R.string.about_personal_desc),
                icon = Icons.Rounded.PersonOutline,
                iconTint = Color(0xFFF59E0B),
            )
        }
    }
}

// ------------------------------------------------------------------ Licenses

/** A block of THIRD_PARTY_NOTICES.md. */
@Immutable
sealed interface NoticeBlock {
    data class Heading(val text: String, val level: Int) : NoticeBlock
    data class Paragraph(val text: String) : NoticeBlock
    data class Quote(val text: String) : NoticeBlock
    data class Bullet(val text: String) : NoticeBlock

    /** A table row: header cell names with this row's values. */
    data class TableRow(val cells: List<Pair<String, String>>) : NoticeBlock
}

/** A section of the notices: its `##` title and blocks. */
@Immutable
data class NoticeSection(val title: String?, val blocks: List<NoticeBlock>)

/**
 * A small Markdown reader for THIRD_PARTY_NOTICES.md (pure, unit tested): `#` headings split the
 * file into sections, tables become rows of "header: value", quotes, bullets and paragraphs stay;
 * links show their text with the address, code and emphasis marks are dropped.
 */
object NoticesMarkdown {

    fun parse(markdown: String): List<NoticeSection> {
        val sections = mutableListOf<NoticeSection>()
        var title: String? = null
        var blocks = mutableListOf<NoticeBlock>()
        var header: List<String>? = null
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += NoticeBlock.Paragraph(inline(paragraph.toString().trim()))
            paragraph.clear()
        }

        fun flushSection() {
            flushParagraph()
            if (title != null || blocks.isNotEmpty()) sections += NoticeSection(title, blocks)
            blocks = mutableListOf()
        }

        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flushSection()
                    title = inline(line.removePrefix("## ").trim())
                    header = null
                }
                line.startsWith("# ") -> {
                    flushSection()
                    title = null
                    blocks += NoticeBlock.Heading(inline(line.removePrefix("# ").trim()), 1)
                    header = null
                }
                line.startsWith("### ") -> {
                    flushParagraph()
                    blocks += NoticeBlock.Heading(inline(line.removePrefix("### ").trim()), 3)
                    header = null
                }
                line.startsWith("|") -> {
                    flushParagraph()
                    val cells = line.trim('|').split('|').map { inline(it.trim()) }
                    when {
                        cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } } -> Unit
                        header == null -> header = cells
                        else -> blocks += NoticeBlock.TableRow(header!!.zip(cells))
                    }
                }
                line.startsWith("> ") -> {
                    flushParagraph()
                    blocks += NoticeBlock.Quote(inline(line.removePrefix("> ").trim()))
                }
                line.startsWith("- ") -> {
                    flushParagraph()
                    blocks += NoticeBlock.Bullet(inline(line.removePrefix("- ").trim()))
                }
                line.isEmpty() -> {
                    flushParagraph()
                    header = null
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flushSection()
        return sections
    }

    private val Link = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val Angle = Regex("<(https?://[^>]+)>")

    /** Links as "text（address）", code and emphasis marks removed. */
    fun inline(text: String): String = text
        .replace(Link) { m -> if (m.groupValues[1] == m.groupValues[2]) m.groupValues[1] else "${m.groupValues[1]}（${m.groupValues[2]}）" }
        .replace(Angle) { it.groupValues[1] }
        .replace("**", "")
        .replace("`", "")
}

/** "开源许可": THIRD_PARTY_NOTICES.md from the app's assets, one glass panel per section. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections by produceState<List<NoticeSection>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() } }
                .map(NoticesMarkdown::parse)
                .getOrDefault(emptyList())
        }
    }
    LicensesContent(sections, onBack)
}

private const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.md"

@Composable
private fun LicensesContent(sections: List<NoticeSection>?, onBack: () -> Unit) {
    val env = currentGlass()
    GlassPage(
        title = stringResource(R.string.about_licenses),
        subtitle = stringResource(R.string.about_licenses_desc),
        onBack = onBack,
        spacing = 14.dp,
    ) {
        when {
            sections == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VluerSpinner(size = 32.dp) }
            sections.isEmpty() -> Text(stringResource(R.string.about_licenses_missing), color = env.ink.secondary, fontSize = 13.sp)
            else -> sections.forEach { section ->
                if (section.title == null) {
                    section.blocks.forEach { NoticeBlockView(it) }
                } else {
                    GlassGroup(section.title) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            section.blocks.forEach { NoticeBlockView(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBlockView(block: NoticeBlock) {
    val env = currentGlass()
    when (block) {
        is NoticeBlock.Heading -> Text(
            block.text,
            color = env.ink.primary,
            fontSize = if (block.level == 1) 15.sp else 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        is NoticeBlock.Paragraph -> Text(block.text, color = env.ink.primary, fontSize = 13.sp, lineHeight = 19.sp)
        is NoticeBlock.Quote -> Row {
            Box(
                Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .padding(vertical = 2.dp),
            ) {
                Canvas(Modifier.size(3.dp, 40.dp)) { drawRect(LogoRing[1].copy(alpha = 0.6f)) }
            }
            Spacer(Modifier.width(10.dp))
            Text(block.text, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic)
        }
        is NoticeBlock.Bullet -> Row {
            Text("•", color = env.ink.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            Text(block.text, color = env.ink.primary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        is NoticeBlock.TableRow -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.cells.forEachIndexed { i, (name, value) ->
                if (i == 0) {
                    Text(value, color = env.ink.primary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                } else if (value.isNotBlank()) {
                    Text("$name：$value", color = env.ink.secondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AboutPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) { AboutContent("1.0", {}, {}) }
}
