package com.ftwegc.myapplication.data.repo

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** [TransactionRunner] backed by a Room database transaction. */
class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = database.withTransaction { block() }
}
