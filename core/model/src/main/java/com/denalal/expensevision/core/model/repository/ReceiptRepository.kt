package com.denalal.expensevision.core.model.repository

import com.denalal.expensevision.core.model.ReceiptModel
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    suspend fun save(receipt: ReceiptModel): Long
    fun observeAll(): Flow<List<ReceiptModel>>
}