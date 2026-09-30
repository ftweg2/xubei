package com.ftwegc.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.ftwegc.myapplication.data.local.dao.ReminderLogDao
import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.TrafficDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.entity.ReminderLogEntity
import com.ftwegc.myapplication.data.local.entity.SubscriptionEntity
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import com.ftwegc.myapplication.data.local.entity.VpsConfigEntity

/**
 * Local database.
 *
 * Version 1 (app/schemas/…/1.json) is the released baseline. From here on every change of the
 * tables (a column, an index, a table, a type) must raise [VERSION] and add a [Migration] to
 * [MIGRATIONS]; `SchemaVersionTest` fails when the exported schema changes but the version does
 * not. The steps are in README.md ("改表结构的流程").
 */
@Database(
    entities = [
        SubscriptionEntity::class,
        VpsConfigEntity::class,
        TrafficPeriodEntity::class,
        TrafficSnapshotEntity::class,
        ReminderLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun vpsDao(): VpsDao
    abstract fun trafficDao(): TrafficDao
    abstract fun reminderLogDao(): ReminderLogDao

    companion object {
        /** The database file. Never rename it: installed copies would lose their data. */
        const val NAME: String = "vluer.db"

        /** Schema version; raise it with every table change (see the class comment). */
        const val VERSION: Int = 1

        /** One migration per version step (1 → 2, 2 → 3, …). None yet: 1 is the first release. */
        val MIGRATIONS: Array<Migration> = arrayOf()

        /**
         * The on-disk app database, not opened yet. Starts empty: no sample data is ever inserted.
         * The app opens it through [DatabaseOpener], which keeps a database of an incompatible
         * development build aside instead of crashing.
         */
        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
