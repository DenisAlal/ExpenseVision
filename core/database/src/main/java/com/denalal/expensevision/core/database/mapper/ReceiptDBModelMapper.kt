package com.denalal.expensevision.core.database.mapper

import com.denalal.expensevision.core.database.entity.ReceiptDBModel
import com.denalal.expensevision.core.model.CategoryType
import com.denalal.expensevision.core.model.ReceiptModel
import java.time.LocalDate

fun ReceiptDBModel.toDomain(): ReceiptModel = ReceiptModel(
    id = id,
    date = LocalDate.ofEpochDay(date),
    sum = sum,
    storeName = storeName,
    category = parseCategory(category)
)

fun ReceiptModel.toDB(): ReceiptDBModel = ReceiptDBModel(
    id = id,
    date = date.toEpochDay(),
    sum = sum,
    storeName = storeName,
    category = category.name
)

private fun parseCategory(raw: String): CategoryType =
    runCatching { CategoryType.valueOf(raw) }.getOrDefault(CategoryType.OTHER)