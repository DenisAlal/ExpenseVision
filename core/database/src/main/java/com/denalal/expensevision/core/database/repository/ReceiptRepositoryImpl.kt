package com.denalal.expensevision.core.database.repository

import com.denalal.expensevision.core.database.dao.ReceiptDao
import com.denalal.expensevision.core.database.mapper.toDB
import com.denalal.expensevision.core.database.mapper.toDomain
import com.denalal.expensevision.core.model.ReceiptModel
import com.denalal.expensevision.core.model.repository.ReceiptRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReceiptRepositoryImpl @Inject constructor(
    private val dao: ReceiptDao
) : ReceiptRepository {
    override suspend fun save(receipt: ReceiptModel): Long = dao.insert(receipt.toDB())

    override fun observeAll(): Flow<List<ReceiptModel>> = dao.observeAll().map { items ->
        items.map { it.toDomain() }
    }

}