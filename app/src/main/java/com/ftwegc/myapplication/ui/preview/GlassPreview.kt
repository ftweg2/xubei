package com.ftwegc.myapplication.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.ui.background.AuroraBackground
import com.ftwegc.myapplication.ui.background.AuroraPalettes
import com.ftwegc.myapplication.ui.background.BackgroundMood
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.glass.GlassEnvironment
import com.ftwegc.myapplication.ui.glass.GlassTier
import com.ftwegc.myapplication.ui.glass.LocalGlass
import com.ftwegc.myapplication.ui.theme.VluerTheme

/**
 * Theme + the flowing background + a glass environment for `@PreviewLightDark` previews of glass
 * and charts. Motion is reduced (charts show their end state); glass is the tinted tier (the
 * preview renderer has no backdrop to blur).
 */
@Composable
fun GlassPreview(
    padding: Dp = 16.dp,
    tone: PageTone = PageTone.Home,
    mood: BackgroundMood = BackgroundMood.Calm,
    content: @Composable BoxScope.() -> Unit,
) {
    VluerTheme(reducedMotion = true) {
        val dark = VluerTheme.colors.isDark
        val palette = remember(tone, mood, dark) { AuroraPalettes.of(tone, mood, dark) }
        val env = remember(palette) { GlassEnvironment.of(palette, GlassTier.Tinted) }
        CompositionLocalProvider(LocalGlass provides env, LocalContentColor provides env.ink.primary) {
            Box {
                AuroraBackground(palette, animate = false, modifier = Modifier.matchParentSize())
                Box(Modifier.padding(padding), content = content)
            }
        }
    }
}
