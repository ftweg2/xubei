package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.entity.SubscriptionEntity
import com.ftwegc.myapplication.data.local.entity.SubscriptionWithVpsRow
import com.ftwegc.myapplication.data.local.entity.VpsConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory tables shared by the fake DAOs, with the cascade and uniqueness rules of the schema. */
class FakeTables {
    val subscriptions = MutableStateFlow<Map<Long, SubscriptionEntity>>(emptyMap())
    val vps = MutableStateFlow<Map<Long, VpsConfigEntity>>(emptyMap())
    private var nextId = 1L

    fun newId(): Long = nextId++

    fun row(id: Long): SubscriptionWithVpsRow? =
        subscriptions.value[id]?.let { SubscriptionWithVpsRow(it, vps.value[id]) }
}

class FakeSubscriptionDao(private val t: FakeTables) : SubscriptionDao {

    override fun observeAll(): Flow<List<SubscriptionWithVpsRow>> =
        combine(t.subscriptions, t.vps) { subs, vps ->
            subs.values.sortedBy { it.id }.map { SubscriptionWithVpsRow(it, vps[it.id]) }
        }

    override fun observeById(id: Long): Flow<SubscriptionWithVpsRow?> =
        combine(t.subscriptions, t.vps) { _, _ -> t.row(id) }

    override suspend fun getById(id: Long): SubscriptionWithVpsRow? = t.row(id)

    override suspend fun getEntity(id: Long): SubscriptionEntity? = t.subscriptions.value[id]

    override suspend fun upsert(entity: SubscriptionEntity): Long {
        if (entity.id != 0L && t.subscriptions.value.containsKey(entity.id)) {
            t.subscriptions.update { it + (entity.id to entity) }
            return -1
        }
        val id = if (entity.id == 0L) t.newId() else entity.id
        t.subscriptions.update { it + (id to entity.copy(id = id)) }
        return id
    }

    override suspend fun delete(id: Long): Int {
        val existed = t.subscriptions.value.containsKey(id)
        t.subscriptions.update { it - id }
        t.vps.update { it - id } // ON DELETE CASCADE
        return if (existed) 1 else 0
    }

    override suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long): Int =
        modify(id) { it.copy(archived = archived, updatedAt = updatedAt) }

    override suspend fun updateDue(id: Long, nextDueDay: Long?, anchorDay: Int?, updatedAt: Long): Int =
        modify(id) { it.copy(nextDueDay = nextDueDay, anchorDay = anchorDay, updatedAt = updatedAt) }

    override suspend fun getDueBetween(startDay: Long, endDay: Long): List<SubscriptionEntity> =
        t.subscriptions.value.values
            .filter { !it.archived && !it.isPermanent && it.nextDueDay != null && it.nextDueDay in startDay..endDay }
            .sortedWith(compareBy({ it.nextDueDay }, { it.id }))

    override suspend fun getAutoRenewExpired(todayDay: Long): List<SubscriptionEntity> =
        t.subscriptions.value.values
            .filter {
                !it.archived && it.autoRenew && !it.isPermanent && it.cycleUnit != "ONCE" &&
                    it.nextDueDay != null && it.nextDueDay < todayDay
            }
            .sortedWith(compareBy({ it.nextDueDay }, { it.id }))

    private fun modify(id: Long, change: (SubscriptionEntity) -> SubscriptionEntity): Int {
        val current = t.subscriptions.value[id] ?: return 0
        t.subscriptions.update { it + (id to change(current)) }
        return 1
    }
}

class FakeVpsDao(private val t: FakeTables) : VpsDao {

    override suspend fun insert(entity: VpsConfigEntity) {
        check(!t.vps.value.containsKey(entity.subscriptionId)) { "duplicate subscription_id" }
        checkUuid(entity)
        checkParent(entity)
        t.vps.update { it + (entity.subscriptionId to entity) }
    }

    override suspend fun update(entity: VpsConfigEntity): Int {
        if (!t.vps.value.containsKey(entity.subscriptionId)) return 0
        checkUuid(entity)
        t.vps.update { it + (entity.subscriptionId to entity) }
        return 1
    }

    override suspend fun upsert(entity: VpsConfigEntity) {
        checkUuid(entity)
        checkParent(entity)
        t.vps.update { it + (entity.subscriptionId to entity) }
    }

    override suspend fun getBySubscriptionId(subscriptionId: Long): VpsConfigEntity? = t.vps.value[subscriptionId]

    override fun observeBySubscriptionId(subscriptionId: Long): Flow<VpsConfigEntity?> =
        t.vps.map { it[subscriptionId] }

    override suspend fun getByKomariUuid(uuid: String): VpsConfigEntity? =
        t.vps.value.values.firstOrNull { it.komariUuid == uuid }

    override suspend fun getAll(): List<VpsConfigEntity> = t.vps.value.values.sortedBy { it.subscriptionId }

    override suspend fun getLinked(): List<VpsConfigEntity> = getAll().filter { it.komariUuid != null }

    override suspend fun deleteBySubscriptionId(subscriptionId: Long): Int {
        val existed = t.vps.value.containsKey(subscriptionId)
        t.vps.update { it - subscriptionId }
        return if (existed) 1 else 0
    }

    override suspend fun updateTrafficSample(
        subscriptionId: Long,
        uuid: String,
        totalUp: Long,
        totalDown: Long,
        uptimeSec: Long?,
        reportedAt: Long?,
        fetchAt: Long,
    ): Int = modify(subscriptionId, uuid) {
        it.copy(
            lastTotalUp = totalUp, lastTotalDown = totalDown, lastUptimeSec = uptimeSec,
            lastReportedAt = reportedAt, lastFetchAt = fetchAt, lastError = null,
        )
    }

    override suspend fun updateFetchError(subscriptionId: Long, uuid: String, fetchAt: Long, error: String): Int =
        modify(subscriptionId, uuid) { it.copy(lastFetchAt = fetchAt, lastError = error) }

    override suspend fun setKomariMissing(subscriptionId: Long, missing: Boolean): Int {
        val current = t.vps.value[subscriptionId] ?: return 0
        t.vps.update { it + (subscriptionId to current.copy(komariMissing = missing)) }
        return 1
    }

    private fun modify(subscriptionId: Long, uuid: String, change: (VpsConfigEntity) -> VpsConfigEntity): Int {
        val current = t.vps.value[subscriptionId]?.takeIf { it.komariUuid == uuid } ?: return 0
        t.vps.update { it + (subscriptionId to change(current)) }
        return 1
    }

    private fun checkUuid(entity: VpsConfigEntity) {
        val uuid = entity.komariUuid ?: return
        check(t.vps.value.values.none { it.komariUuid == uuid && it.subscriptionId != entity.subscriptionId }) {
            "UNIQUE constraint failed: vps_configs.komari_uuid"
        }
    }

    private fun checkParent(entity: VpsConfigEntity) {
        check(t.subscriptions.value.containsKey(entity.subscriptionId)) { "FOREIGN KEY constraint failed" }
    }
}

/** Runs the block directly and counts the calls. */
class DirectTransactionRunner : TransactionRunner {
    var calls = 0
        private set

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        calls++
        return block()
    }
}
