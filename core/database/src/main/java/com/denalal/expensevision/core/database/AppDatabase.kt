package com.denalal.expensevision.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.denalal.expensevision.core.database.dao.ReceiptDao
import com.denalal.expensevision.core.database.entity.ReceiptDBModel

@Database(entities = [ReceiptDBModel::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
}