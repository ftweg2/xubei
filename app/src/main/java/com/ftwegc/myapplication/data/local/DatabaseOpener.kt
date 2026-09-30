package com.ftwegc.myapplication.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Opens the app database. One case is handled instead of crashing: a database written by an
 * earlier development build whose tables differ although the version number is the same (Room's
 * identity hash does not match). Such a file cannot be read by this build, so it is renamed and
 * kept in the app's private database folder (never deleted), and a new empty database is created.
 *
 * Everything else (a newer database after a downgrade, a missing migration, a damaged file) is left
 * to Room: the database is returned unopened and Room fails exactly as it always did on first use.
 * The settings (DataStore) are separate files and are never touched here.
 */
class DatabaseOpener(
    private val context: Context,
    private val name: String = AppDatabase.NAME,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val build: (Context, String) -> AppDatabase = { c, n -> AppDatabase.build(c, n) },
) {

    /** @property keptAs file name the incompatible database was renamed to; `null` normally. */
    class Opened(val database: AppDatabase, val keptAs: String?)

    fun open(): Opened {
        val database = build(context, name)
        val error = try {
            // Opening runs Room's checks (version, migrations, identity hash) right here.
            database.openHelper.writableDatabase
            return Opened(database, null)
        } catch (e: Exception) {
            e
        }
        val mismatch = IdentityMismatch.parse(error.message)
        if (mismatch == null) {
            Log.w(TAG, "Database could not be opened; left to Room", error)
            return Opened(database, null)
        }
        database.close()
        val file = context.getDatabasePath(name)
        if (!storedIdentity(file).let { it != null && it.version == AppDatabase.VERSION && it.hash == mismatch.found }) {
            // Not what Room reported: do not touch the file.
            Log.w(TAG, "Identity mismatch reported but not confirmed on disk; left to Room", error)
            return Opened(build(context, name), null)
        }
        val keptAs = keepAside(file) ?: return Opened(build(context, name), null)
        Log.w(TAG, "Incompatible database of an earlier build kept as $keptAs; starting empty")
        val fresh = build(context, name)
        fresh.openHelper.writableDatabase
        return Opened(fresh, keptAs)
    }

    private class StoredIdentity(val version: Int, val hash: String?)

    /** Version and Room identity hash stored in [file], read without Room; `null` when unreadable. */
    private fun storedIdentity(file: File): StoredIdentity? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val hash = db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            StoredIdentity(db.version, hash)
        }
    } catch (e: SQLiteException) {
        Log.w(TAG, "Could not read the stored identity", e)
        null
    }

    /**
     * Renames the database and its journal files to `<name>-incompatible-<time>.db…` in the same
     * folder. Returns the new name of the main file, or `null` when it could not be renamed.
     */
    private fun keepAside(file: File): String? {
        val dir = file.parentFile ?: return null
        val stamp = LocalDateTime.now(clock).format(STAMP)
        val base = name.removeSuffix(".db")
        var target = File(dir, "$base-incompatible-$stamp.db")
        var n = 2
        while (SUFFIXES.any { File(target.path + it).exists() }) target = File(dir, "$base-incompatible-$stamp-${n++}.db")
        if (!file.renameTo(target)) {
            Log.w(TAG, "Could not rename $file")
            return null
        }
        for (suffix in SUFFIXES.drop(1)) {
            val companion = File(file.path + suffix)
            if (companion.exists() && !companion.renameTo(File(target.path + suffix))) Log.w(TAG, "Could not rename $companion")
        }
        return target.name
    }

    private companion object {
        const val TAG = "DatabaseOpener"
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** The database and the files SQLite keeps next to it. */
        val SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    }
}

/**
 * Room's "identity hash does not match" failure, recognized by its message (Room 2.8:
 * "Room cannot verify the data integrity. … Expected identity hash: X, found: Y").
 */
data class IdentityMismatch(val expected: String, val found: String) {
    companion object {
        private val PATTERN = Regex("""Room cannot verify the data integrity\..*Expected identity hash: (\w+), found: (\w+)""", RegexOption.DOT_MATCHES_ALL)

        /** The mismatch described by [message], or `null` for any other failure. */
        fun parse(message: String?): IdentityMismatch? {
            val match = PATTERN.find(message ?: return null) ?: return null
            return IdentityMismatch(match.groupValues[1], match.groupValues[2])
        }
    }
}

/**
 * Remembers, across restarts, that an incompatible database was kept aside and the user has not
 * been told yet (a small file in the app's no-backup folder holding the backup's name).
 */
class LegacyDatabaseNotice(private val file: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILE_NAME))

    /** Name of the kept database the user has not been told about, or `null`. */
    fun pending(): String? = try {
        file.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
    } catch (e: java.io.IOException) {
        Log.w(TAG, "Could not read the notice", e)
        null
    }

    fun record(keptAs: String) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(keptAs)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Could not record the notice", e)
        }
    }

    /** The user has seen it: never shown again. */
    fun dismiss() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "legacy_database_notice"
        const val TAG = "LegacyDatabaseNotice"
    }
}
