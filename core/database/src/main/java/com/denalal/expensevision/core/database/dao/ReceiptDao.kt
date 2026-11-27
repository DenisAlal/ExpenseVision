package com.denalal.expensevision.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.denalal.expensevision.core.database.entity.ReceiptDBModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insert(receipt: ReceiptDBModel): Long

    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun observeAll(): Flow<List<ReceiptDBModel>>
}