package com.ftwegc.myapplication.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Komari-specific `vps_configs` updates only touch their own columns. */
@RunWith(AndroidJUnit4::class)
class VpsTrafficDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun linked(): Long {
        val id = db.subscriptionDao().upsert(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 1, currency = "¥", cycle = BillingCycle.MONTHLY)
                .toEntity(1, 1),
        )
        db.vpsDao().upsert(
            VpsConfig(subscriptionId = id, komariUuid = "n1", lockedFields = LockedField.PRICE.mask, resetDay = 7).toEntity(id),
        )
        return id
    }

    @Test
    fun sampleAndErrorUpdates_keepUserSettings() = runBlocking {
        val id = linked()
        val dao = db.vpsDao()
        assertEquals(1, dao.updateTrafficSample(id, "n1", 10, 20, 30, 40, 50))
        var row = dao.getBySubscriptionId(id)!!
        assertEquals(10L, row.lastTotalUp)
        assertEquals(20L, row.lastTotalDown)
        assertEquals(30L, row.lastUptimeSec)
        assertEquals(40L, row.lastReportedAt)
        assertEquals(50L, row.lastFetchAt)
        assertNull(row.lastError)
        assertEquals(LockedField.PRICE.mask, row.lockedFields)
        assertEquals(7, row.resetDay)

        assertEquals(1, dao.updateFetchError(id, "n1", 60, "OFFLINE"))
        row = dao.getBySubscriptionId(id)!!
        assertEquals("OFFLINE", row.lastError)
        assertEquals(60L, row.lastFetchAt)
        assertEquals("counters kept", 10L, row.lastTotalUp)

        assertEquals("a different uuid (relinked meanwhile) is not written", 0, dao.updateTrafficSample(id, "other", 1, 1, 1, 1, 1))

        assertEquals(1, dao.setKomariMissing(id, true))
        assertTrue(dao.getBySubscriptionId(id)!!.komariMissing)
    }
}
