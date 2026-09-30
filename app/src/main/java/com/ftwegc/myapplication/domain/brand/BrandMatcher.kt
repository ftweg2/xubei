package com.ftwegc.myapplication.domain.brand

import java.text.Normalizer
import java.util.Locale

/**
 * A service with a built-in icon.
 *
 * @property id stable key, also stored as a user's icon choice (`subscriptions.icon_key`).
 * @property aliases names that point to it. Long aliases match anywhere in a subscription name
 *   ("ChatGPT Plus" contains "chatgpt"); aliases of [SHORT_ALIAS] characters or fewer only match a
 *   whole word ("GPT-4" → "gpt", but "Cloudflare" never matches "cf").
 */
data class Brand(val id: String, val title: String, val aliases: List<String>) {
    companion object {
        const val SHORT_ALIAS = 3
    }
}

/**
 * Finds the service a subscription name refers to. Pure and lenient: case, spaces, punctuation
 * and full-width characters are ignored; Chinese names and common abbreviations are aliases.
 */
object BrandMatcher {

    /** Every brand with a built-in icon (icons in `ui/brand/BrandGlyphs.kt`). */
    val brands: List<Brand> = listOf(
        // AI
        Brand("openai", "ChatGPT", listOf("chatgpt", "openai", "gpt", "chat gpt", "sora", "dall-e", "dalle")),
        Brand("claude", "Claude", listOf("claude", "claude code")),
        Brand("anthropic", "Anthropic", listOf("anthropic")),
        Brand("gemini", "Gemini", listOf("gemini", "google gemini", "bard", "google ai pro", "google ai ultra", "谷歌 gemini")),
        Brand("copilot", "GitHub Copilot", listOf("copilot", "github copilot")),
        Brand("cursor", "Cursor", listOf("cursor")),
        Brand("midjourney", "Midjourney", listOf("midjourney", "mj")),
        Brand("perplexity", "Perplexity", listOf("perplexity", "pplx")),
        Brand("grok", "Grok", listOf("grok", "supergrok")),
        Brand("xai", "xAI", listOf("xai", "x.ai")),
        Brand("deepseek", "DeepSeek", listOf("deepseek", "深度求索")),
        Brand("kimi", "Kimi", listOf("kimi", "moonshot", "月之暗面")),
        Brand("doubao", "豆包", listOf("doubao", "豆包")),
        Brand("qwen", "通义千问", listOf("qwen", "tongyi", "通义", "千问")),
        Brand("kiro", "Kiro", listOf("kiro")),
        Brand("windsurf", "Windsurf", listOf("windsurf", "codeium")),
        Brand("poe", "Poe", listOf("poe")),
        Brand("suno", "Suno", listOf("suno")),
        // VPS and clouds
        Brand("oracle", "Oracle Cloud", listOf("oracle", "oci", "甲骨文", "oracle cloud")),
        Brand("aws", "AWS", listOf("aws", "amazon web services", "lightsail", "ec2", "亚马逊云", "亚马逊 aws")),
        Brand("vultr", "Vultr", listOf("vultr")),
        Brand("digitalocean", "DigitalOcean", listOf("digitalocean", "digital ocean")),
        Brand("hetzner", "Hetzner", listOf("hetzner")),
        Brand("alibabacloud", "阿里云", listOf("阿里云", "aliyun", "alibaba cloud", "alicloud", "alibabacloud")),
        Brand("tencentcloud", "腾讯云", listOf("腾讯云", "tencent cloud", "tencentcloud", "qcloud")),
        Brand("cloudflare", "Cloudflare", listOf("cloudflare")),
        // Everything else
        Brand("icloud", "iCloud", listOf("icloud", "icloud+")),
        Brand("apple", "Apple", listOf("apple", "apple one", "apple music", "apple tv", "苹果")),
        Brand("spotify", "Spotify", listOf("spotify")),
        Brand("netflix", "Netflix", listOf("netflix", "奈飞", "网飞")),
        Brand("youtube", "YouTube", listOf("youtube", "youtube premium", "油管")),
        Brand("telegram", "Telegram", listOf("telegram", "电报", "tg premium")),
        Brand("onepassword", "1Password", listOf("1password", "1 password")),
        Brand(
            "jetbrains", "JetBrains",
            listOf(
                "jetbrains", "intellij", "idea ultimate", "pycharm", "webstorm", "goland", "clion", "rider",
                "datagrip", "phpstorm", "rubymine", "rustrover",
            ),
        ),
        Brand("notion", "Notion", listOf("notion")),
    )

    private val byId: Map<String, Brand> = brands.associateBy { it.id }

    /** Alias → brand, longest alias first (so "icloud" beats "apple" in "Apple iCloud"). */
    private val index: List<Pair<String, Brand>> = brands
        .flatMap { brand -> brand.aliases.map { normalize(it) to brand } }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { it.first.length }

    fun byId(id: String?): Brand? = id?.let(byId::get)

    /** The brand [name] refers to, or `null`. */
    fun match(name: String): Brand? {
        val compact = normalize(name)
        if (compact.isEmpty()) return null
        val words = words(name)
        for ((alias, brand) in index) {
            val found = if (alias.length <= Brand.SHORT_ALIAS && alias.all { it.isAsciiLetterOrDigit() }) {
                alias in words
            } else {
                compact.contains(alias)
            }
            if (found) return brand
        }
        return null
    }

    /**
     * Lower case, full-width folded to ASCII (NFKC), everything but letters, digits and CJK
     * characters removed ("ChatGPT Plus" → "chatgptplus", "ＧＰＴ－４" → "gpt4").
     */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    /** The ASCII words of [text] ("GPT-4 Turbo" → gpt, 4, turbo), for short aliases. */
    fun words(text: String): Set<String> =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .toSet()

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'
}

/**
 * The letter mark used when no icon matches: the first letter (upper case) or the first Chinese
 * character of the name, on a color that always comes out the same for the same name.
 */
object Monogram {

    /** Number of built-in letter mark colors (see `ui/brand/BrandPalette`). */
    const val COLOR_COUNT: Int = 10

    /** "洛杉矶 9929" → "洛", "vultr tokyo" → "V", "  #1 box" → "1", "" → "?". */
    fun letterOf(name: String): String {
        val text = Normalizer.normalize(name, Normalizer.Form.NFKC).trim()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) {
                return String(Character.toChars(cp)).uppercase(Locale.ROOT)
            }
            i += Character.charCount(cp)
        }
        return "?"
    }

    /** Stable color slot 0 until [COLOR_COUNT] for [name] (FNV-1a of the normalized name). */
    fun colorIndex(name: String, colorCount: Int = COLOR_COUNT): Int {
        val key = BrandMatcher.normalize(name).ifEmpty { name.trim() }
        var hash = FNV_OFFSET
        for (byte in key.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= FNV_PRIME
        }
        return Math.floorMod(hash, colorCount)
    }

    private const val FNV_OFFSET = 0x811C9DC5.toInt()
    private const val FNV_PRIME = 0x01000193
}
