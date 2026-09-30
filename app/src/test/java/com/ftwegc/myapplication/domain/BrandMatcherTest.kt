package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.brand.BrandMatcher
import com.ftwegc.myapplication.domain.brand.Monogram
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandMatcherTest {

    private fun id(name: String) = BrandMatcher.match(name)?.id

    @Test
    fun match_isCaseAndSpaceInsensitive_withAliases() {
        assertEquals("openai", id("ChatGPT Plus"))
        assertEquals("openai", id("chatgpt"))
        assertEquals("openai", id("GPT"))
        assertEquals("openai", id("GPT-4 Turbo"))
        assertEquals("openai", id("Chat GPT 会员"))
        assertEquals("claude", id("Claude Max"))
        assertEquals("claude", id("claude pro"))
        assertEquals("gemini", id("Google Gemini Advanced"))
        assertEquals("copilot", id("GitHub Copilot"))
        assertEquals("cursor", id("Cursor Pro"))
        assertEquals("perplexity", id("Perplexity Pro"))
        assertEquals("grok", id("SuperGrok"))
        assertEquals("xai", id("xAI API"))
        assertEquals("midjourney", id("Midjourney Basic"))
        assertEquals("windsurf", id("Codeium"))
    }

    @Test
    fun match_chineseNames() {
        assertEquals("oracle", id("甲骨文 法兰克福"))
        assertEquals("oracle", id("甲骨文-eu-92"))
        assertEquals("oracle", id("Oracle Cloud"))
        assertEquals("oracle", id("OCI Tokyo"))
        assertEquals("alibabacloud", id("阿里云 ECS"))
        assertEquals("tencentcloud", id("腾讯云轻量"))
        assertEquals("doubao", id("豆包"))
        assertEquals("qwen", id("通义千问"))
        assertEquals("qwen", id("通义"))
        assertEquals("kimi", id("Kimi 会员"))
        assertEquals("kimi", id("月之暗面"))
        assertEquals("deepseek", id("DeepSeek API"))
        assertEquals("netflix", id("奈飞"))
        assertEquals("aws", id("亚马逊云 Lightsail"))
    }

    @Test
    fun match_fullWidthAndPunctuation() {
        assertEquals("openai", id("ＣｈａｔＧＰＴ"))
        assertEquals("onepassword", id("1Password Families"))
        assertEquals("icloud", id("iCloud+ 200GB"))
        assertEquals("youtube", id("YouTube-Premium"))
    }

    @Test
    fun match_longestAliasWins() {
        assertEquals("icloud", id("Apple iCloud"))
        assertEquals("apple", id("Apple Music"))
        assertEquals("grok", id("xAI Grok"))
    }

    @Test
    fun match_shortAliasesOnlyAsWholeWords() {
        assertNull(id("Social Club")) // contains "oci"
        assertNull(id("Poetry 会员")) // contains "poe"
        assertNull(id("Gpt4all local")) // "gpt" inside a longer word
        assertEquals("aws", id("AWS 新加坡"))
        assertEquals("poe", id("Poe"))
        assertEquals("aws", id("EC2 us-east"))
    }

    @Test
    fun match_unknownNamesAndBlank() {
        assertNull(id("洛杉矶 9929"))
        assertNull(id("新西伯利亚"))
        assertNull(id("搬瓦工 CN2")) // no vector icon in the three libraries
        assertNull(id("RackNerd"))
        assertNull(id("RUVDS"))
        assertNull(id("JustHost"))
        assertNull(id(""))
        assertNull(id("   "))
    }

    @Test
    fun byId() {
        assertEquals("Claude", BrandMatcher.byId("claude")?.title)
        assertNull(BrandMatcher.byId("nope"))
        assertNull(BrandMatcher.byId(null))
    }

    @Test
    fun everyBrandHasAnIcon_andEveryIconAbrand() {
        val ids = BrandMatcher.brands.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull("glyph for $it", BrandCatalog.glyph(it)) }
        assertEquals(ids.size, BrandCatalog.brands.size)
        // The brands the design brief and oss-research.md ask for.
        listOf(
            "openai", "claude", "anthropic", "gemini", "copilot", "cursor", "midjourney", "perplexity", "grok", "xai",
            "deepseek", "kimi", "doubao", "qwen", "kiro", "windsurf", "poe", "suno", "oracle", "aws", "vultr",
            "digitalocean", "hetzner", "alibabacloud", "tencentcloud", "cloudflare", "icloud", "apple", "spotify",
            "netflix", "youtube", "telegram", "onepassword", "jetbrains", "notion",
        ).forEach { assertTrue(it, it in ids) }
    }

    // ------------------------------------------------------------ letter marks

    @Test
    fun monogram_letters() {
        assertEquals("洛", Monogram.letterOf("洛杉矶 9929"))
        assertEquals("V", Monogram.letterOf("vultr tokyo"))
        assertEquals("1", Monogram.letterOf("  #1 box"))
        assertEquals("伦", Monogram.letterOf("「伦敦」"))
        assertEquals("A", Monogram.letterOf("ａｂｃ"))
        assertEquals("?", Monogram.letterOf(""))
        assertEquals("?", Monogram.letterOf("—— · ——"))
    }

    @Test
    fun monogram_colorIsStableAndSpread() {
        val a = Monogram.colorIndex("洛杉矶 9929")
        assertEquals(a, Monogram.colorIndex("洛杉矶 9929"))
        // Case and spaces do not change the color.
        assertEquals(Monogram.colorIndex("My Box"), Monogram.colorIndex("mybox"))
        val names = (1..200).map { "node-$it" }
        val slots = names.map { Monogram.colorIndex(it) }
        assertTrue(slots.all { it in 0 until Monogram.COLOR_COUNT })
        // Every color is used across 200 names.
        assertEquals(Monogram.COLOR_COUNT, slots.toSet().size)
    }
}
