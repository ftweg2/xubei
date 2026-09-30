package com.ftwegc.myapplication.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ftwegc.myapplication.data.repo.RoomTransactionRunner
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Opening the app database over a file left by an earlier development build: a different table
 * layout under the same version number (Room: identity hash mismatch) is kept aside and the app
 * starts empty; every other failure is left to Room. Uses its own database name, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseOpenerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "opener-test-${UUID.randomUUID()}.db"
    private val base = name.removeSuffix(".db")
    private val clock = Clock.fixed(Instant.parse("2026-09-29T02:15:30Z"), ZoneId.of("Asia/Shanghai"))
    private val opened = mutableListOf<AppDatabase>()

    private val dir: File get() = context.getDatabasePath(name).parentFile!!

    private fun ourFiles(): List<File> = dir.listFiles().orEmpty().filter { it.name.startsWith(base) }

    @Before
    fun setUp() {
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        ourFiles().forEach { it.delete() }
    }

    private fun open(): DatabaseOpener.Opened = DatabaseOpener(context, name, clock).open().also { opened += it.database }

    /** A database the way an early build left it: other columns, version 1, another identity hash, one demo row. */
    private fun writeEarlyBuildDatabase(version: Int = 1, hash: String = "0123456789abcdef0123456789abcdef") {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE subscriptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, monthly_price REAL)")
            db.execSQL("INSERT INTO subscriptions (name, monthly_price) VALUES ('演示订阅', 12.5)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$hash')")
            db.version = version
        }
    }

    private fun demoRowsIn(file: File): List<String> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM subscriptions", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }

    private fun repository(db: AppDatabase) =
        SubscriptionRepository(db.subscriptionDao(), db.vpsDao(), RoomTransactionRunner(db), Clock.systemDefaultZone(), db.trafficDao())

    @Test
    fun incompatibleEarlyBuild_isKeptAside_andTheAppStartsEmpty() = runBlocking {
        writeEarlyBuildDatabase()

        val result = open()

        // Kept, renamed, in the app's private database folder, with its data.
        val keptAs = assertNotNullAndGet(result.keptAs)
        assertEquals("$base-incompatible-20260929-101530.db", keptAs)
        val kept = File(dir, keptAs)
        assertTrue(kept.exists())
        assertEquals(listOf("演示订阅"), demoRowsIn(kept))

        // The new database works and is empty.
        val repo = repository(result.database)
        assertTrue(repo.observeAll().first().isEmpty())
        repo.save(Subscription(name = "新订阅", category = Category.AI, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY), null)
        assertEquals(listOf("新订阅"), repo.observeAll().first().map { it.subscription.name })

        // The next start opens the new database normally: nothing is kept aside again.
        result.database.close()
        val again = open()
        assertNull(again.keptAs)
        assertEquals(listOf("新订阅"), repository(again.database).observeAll().first().map { it.subscription.name })
        assertEquals(1, ourFiles().count { it.name.contains("-incompatible-") && it.name.endsWith(".db") })
    }

    @Test
    fun currentDatabase_opensWithItsData() = runBlocking {
        val first = open()
        assertNull(first.keptAs)
        repository(first.database).save(
            Subscription(name = "保留", category = Category.OTHER, priceMinor = 500, currency = "CNY", cycle = BillingCycle.MONTHLY),
            null,
        )
        first.database.close()

        val second = open()
        assertNull(second.keptAs)
        assertEquals(listOf("保留"), repository(second.database).observeAll().first().map { it.subscription.name })
        assertFalse(ourFiles().any { it.name.contains("-incompatible-") })
    }

    @Test
    fun newerVersion_isLeftToRoom_andTheFileIsNotTouched() {
        // A downgrade (the file says version 99): not an identity mismatch, Room's default failure.
        writeEarlyBuildDatabase(version = 99)
        val result = open()
        assertNull(result.keptAs)
        try {
            result.database.openHelper.writableDatabase
            fail("Room opened a database of a newer version")
        } catch (e: IllegalStateException) {
            assertNull(IdentityMismatch.parse(e.message))
        }
        result.database.close()
        assertFalse(ourFiles().any { it.name.contains("-incompatible-") })
        assertEquals(listOf("演示订阅"), demoRowsIn(context.getDatabasePath(name)))
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
