package com.ftwegc.myapplication.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards the database against schema changes without a new version (installed copies would crash
 * on start: Room refuses a database whose identity hash does not match its version).
 *
 * [RELEASED] holds the identity hash of every released schema version. When a table changes, KSP
 * rewrites the exported schema of the current version, its hash no longer matches, and this test
 * tells what to do. See README.md, "改表结构的流程".
 */
class SchemaVersionTest {

    /** Released schema versions and their identity hashes (app/schemas/…AppDatabase/<version>.json). Never edit a recorded value. */
    private val RELEASED: Map<Int, String> = mapOf(
        1 to "97fcafdc3bbb05830ef9bbdede01ea71",
    )

    private val schemaDir: File = listOf(
        File("schemas/com.ftwegc.myapplication.data.local.AppDatabase"),
        File("app/schemas/com.ftwegc.myapplication.data.local.AppDatabase"),
    ).firstOrNull { it.isDirectory } ?: error("app/schemas/com.ftwegc.myapplication.data.local.AppDatabase not found")

    private class Exported(val version: Int, val identityHash: String)

    private fun exported(version: Int): Exported? {
        val file = File(schemaDir, "$version.json")
        if (!file.isFile) return null
        val database = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
        return Exported(database["version"]!!.jsonPrimitive.int, database["identityHash"]!!.jsonPrimitive.content)
    }

    private fun exportedVersions(): List<Int> =
        schemaDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    @Test
    fun schemaChange_requiresNewVersion() {
        val version = AppDatabase.VERSION
        val current = exported(version) ?: throw AssertionError(
            "app/schemas/…/AppDatabase/$version.json 不存在。先构建一次（KSP 会导出当前版本的表结构），再运行测试。",
        )
        assertEquals("$version.json 里的版本号和 AppDatabase.VERSION 不一致", version, current.version)
        val recorded = RELEASED[version] ?: throw AssertionError(
            "数据库版本已经是 $version，但这个测试还没有记录它的 identityHash。\n" +
                "确认 AppDatabase.MIGRATIONS 里已经有 Migration(${version - 1}, $version) 并且写了迁移测试后，" +
                "把 `$version to \"${current.identityHash}\"` 加进 SchemaVersionTest.RELEASED。",
        )
        if (current.identityHash != recorded) {
            fail(
                "表结构变了，但数据库版本号没变（都是 $version）。已安装的旧版打开会闪退。\n" +
                    "记录的 identityHash：$recorded\n" +
                    "现在导出的 identityHash：${current.identityHash}\n" +
                    "请按 README.md「改表结构的流程」处理：\n" +
                    "  1. 先把 app/schemas/…/AppDatabase/$version.json 恢复成已发布的版本（从备份或上一个版本取回）；\n" +
                    "  2. 把 AppDatabase.VERSION 改成 ${version + 1}，在 AppDatabase.MIGRATIONS 里加上 Migration($version, ${version + 1})；\n" +
                    "  3. 重新构建导出 ${version + 1}.json，再把 `${version + 1} to \"<新的 identityHash>\"` 记进 RELEASED。\n" +
                    "不要改动 RELEASED 里已有的值。",
            )
        }
    }

    @Test
    fun releasedSchemas_neverChange() {
        for ((version, hash) in RELEASED) {
            val file = exported(version)
            if (file == null) {
                fail("已发布版本 $version 的表结构文件 $version.json 不见了。它是写迁移和迁移测试的依据，必须保留。")
                return
            }
            assertEquals("已发布版本 $version 的表结构文件 $version.json 被改动了。已发布的表结构不能再改，请恢复它，改动放到新版本里。", hash, file.identityHash)
        }
    }

    @Test
    fun noSchemaNewerThanVersion() {
        val newest = exportedVersions().maxOrNull()
        assertEquals(
            "app/schemas 里有比 AppDatabase.VERSION（${AppDatabase.VERSION}）更新的表结构文件：版本号不能往回改。",
            AppDatabase.VERSION,
            newest,
        )
    }

    @Test
    fun everyVersionStep_hasMigration() {
        for (from in 1 until AppDatabase.VERSION) {
            assertTrue(
                "缺少从 $from 到 ${from + 1} 的迁移：在 AppDatabase.MIGRATIONS 里加上 Migration($from, ${from + 1})。",
                AppDatabase.MIGRATIONS.any { it.startVersion == from && it.endVersion == from + 1 },
            )
        }
    }

    @Test
    fun identityMismatch_isRecognizedFromRoomsMessage() {
        // Room 2.8's message (BaseRoomConnectionManager.checkIdentity).
        val message = "Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the " +
            "version number. You can simply fix this by increasing the version number. Expected identity hash: " +
            "97fcafdc3bbb05830ef9bbdede01ea71, found: 1b2c3d4e5f60718293a4b5c6d7e8f901"
        assertEquals(IdentityMismatch("97fcafdc3bbb05830ef9bbdede01ea71", "1b2c3d4e5f60718293a4b5c6d7e8f901"), IdentityMismatch.parse(message))
        // Other failures are left to Room.
        assertNull(
            IdentityMismatch.parse(
                "A migration from 2 to 1 was required but not found. Please provide the necessary Migration path via " +
                    "RoomDatabase.Builder.addMigration(...) or allow for destructive migrations via one of the " +
                    "RoomDatabase.Builder.fallbackToDestructiveMigration* functions.",
            ),
        )
        assertNull(IdentityMismatch.parse("Pre-packaged database has an invalid schema: subscriptions"))
        assertNull(IdentityMismatch.parse(null))
    }

    @Test
    fun legacyNotice_isShownUntilDismissed() {
        val file = File.createTempFile("legacy_notice", null).apply { delete() }
        try {
            val notice = LegacyDatabaseNotice(file)
            assertNull(notice.pending())
            notice.record("vluer-incompatible-20260929-101500.db")
            // A restart before the user saw it: still pending.
            assertEquals("vluer-incompatible-20260929-101500.db", LegacyDatabaseNotice(file).pending())
            notice.dismiss()
            assertNull(LegacyDatabaseNotice(file).pending())
        } finally {
            file.delete()
        }
    }
}
