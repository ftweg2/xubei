package com.ftwegc.myapplication.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoticesMarkdownTest {

    private val sample = """
        # 第三方代码与图标说明

        Vluer 是个人自用的 app。

        ## 一、依赖库

        | 库 | 版本 | 许可证 |
        |---|---|---|
        | `io.github.kyant0:backdrop`（[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)） | 1.0.6 | Apache License 2.0 |

        两个库的版本不能升级，
        原因写在注释里。

        ## 三、服务图标

        ### Simple Icons（CC0 1.0）

        > Permission is hereby granted, **free** of charge.

        - 搬瓦工
        全文：<https://www.apache.org/licenses/LICENSE-2.0>
    """.trimIndent()

    @Test
    fun sectionsTablesQuotesAndLinks() {
        val sections = NoticesMarkdown.parse(sample)
        assertEquals(3, sections.size)
        // The file title and intro come before the first section.
        assertEquals(null, sections[0].title)
        assertEquals(NoticeBlock.Heading("第三方代码与图标说明", 1), sections[0].blocks[0])
        assertEquals("一、依赖库", sections[1].title)
        val row = sections[1].blocks.filterIsInstance<NoticeBlock.TableRow>().single()
        assertEquals("库", row.cells[0].first)
        assertEquals("io.github.kyant0:backdrop（Kyant0/AndroidLiquidGlass（https://github.com/Kyant0/AndroidLiquidGlass））", row.cells[0].second)
        assertEquals("版本" to "1.0.6", row.cells[1])
        // Lines of one paragraph are joined.
        assertEquals(NoticeBlock.Paragraph("两个库的版本不能升级， 原因写在注释里。"), sections[1].blocks.last())
        val icons = sections[2].blocks
        assertEquals(NoticeBlock.Heading("Simple Icons（CC0 1.0）", 3), icons[0])
        assertEquals(NoticeBlock.Quote("Permission is hereby granted, free of charge."), icons[1])
        assertEquals(NoticeBlock.Bullet("搬瓦工"), icons[2])
        assertEquals(NoticeBlock.Paragraph("全文：https://www.apache.org/licenses/LICENSE-2.0"), icons[3])
    }

    @Test
    fun theProjectsNoticesFileParses() {
        // The file shipped in the app (copied into the assets at build time).
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "THIRD_PARTY_NOTICES.md") }
            .firstOrNull { it.exists() } ?: return
        val sections = NoticesMarkdown.parse(file.readText())
        assertTrue(sections.size >= 4)
        assertTrue(sections.any { s -> s.title?.contains("依赖库") == true })
        assertTrue(sections.flatMap { it.blocks }.none { it is NoticeBlock.Paragraph && it.text.contains("**") })
    }
}
