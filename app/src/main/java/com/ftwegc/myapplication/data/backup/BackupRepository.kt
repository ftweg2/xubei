package com.ftwegc.myapplication.data.backup

import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.data.local.toEntity
import com.ftwegc.myapplication.data.repo.TransactionRunner
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * What an import would do.
 *
 * @property toImport entries that will be added.
 * @property duplicates entries skipped because the same subscription already exists.
 * @property invalid entries of the file that could not be read.
 */
data class ImportPlan(
    val toImport: List<SubscriptionWithVps>,
    val duplicates: Int,
    val invalid: Int,
)

/** Outcome of [BackupRepository.import]. */
data class ImportOutcome(
    val imported: Int,
    /** VPS entries whose Komari node is already linked here; imported without the link. */
    val linksDropped: Int,
)

/** Export to and import from the JSON file format of [BackupCodec]. */
class BackupRepository(
    private val subscriptionDao: SubscriptionDao,
    private val vpsDao: VpsDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {

    /** Every subscription (archived included) as export text, and how many there are. */
    suspend fun export(): Pair<String, Int> {
        val items = subscriptionDao.observeAll().first().map { it.toDomain() }
        return BackupCodec.write(items, clock.instant()) to items.size
    }

    /** Reads [text] and plans the import; duplicates of existing subscriptions are skipped. */
    suspend fun plan(text: String): Result<ImportPlan> {
        return when (val read = BackupCodec.read(text)) {
            BackupReadResult.NotBackup -> Result.failure(NotBackupException())
            is BackupReadResult.NewerVersion -> Result.failure(NewerVersionException(read.version))
            is BackupReadResult.Ok -> {
                val existing = subscriptionDao.observeAll().first().mapTo(HashSet()) { identity(it.subscription.toDomain()) }
                val seen = HashSet<Identity>()
                val fresh = read.items.filter { item ->
                    val id = identity(item.subscription)
                    id !in existing && seen.add(id)
                }
                Result.success(ImportPlan(fresh, read.items.size - fresh.size, read.invalidCount))
            }
        }
    }

    /** Adds every entry of [plan] in one transaction. */
    suspend fun import(plan: ImportPlan): ImportOutcome = transactions.inTransaction {
        val now = clock.millis()
        var dropped = 0
        for (item in plan.toImport) {
            val id = subscriptionDao.upsert(item.subscription.copy(id = 0).toEntity(createdAt = now, updatedAt = now))
            val vps = item.vps ?: continue
            val uuid = vps.komariUuid
            val taken = uuid != null && vpsDao.getByKomariUuid(uuid) != null
            if (taken) dropped++
            val config = vps.copy(
                subscriptionId = id,
                komariUuid = if (taken) null else uuid,
                komariName = if (taken) null else vps.komariName,
                komariGroup = if (taken) null else vps.komariGroup,
                komariRegion = if (taken) null else vps.komariRegion,
                lockedFields = if (taken) 0 else vps.lockedFields,
                appCountingSince = if (vps.countingMode == CountingMode.APP_ACCUMULATED) clock.instant() else null,
            )
            vpsDao.upsert(config.toEntity(id))
        }
        ImportOutcome(plan.toImport.size, dropped)
    }

    private data class Identity(val name: String, val category: String, val price: Long, val currency: String, val due: Long?)

    private fun identity(s: Subscription) =
        Identity(s.name.trim(), s.category.name, s.priceMinor, s.currency.trim().uppercase(), s.nextDue?.toEpochDay())

    class NotBackupException : IllegalArgumentException("not an export of this app")
    class NewerVersionException(val version: Int) : IllegalArgumentException("export version $version is newer")
}
