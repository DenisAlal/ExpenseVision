package com.denalal.expensevision.core.model

import java.time.LocalDate

data class ReceiptModel(
    val id: Long = 0,
    val date: LocalDate,
    val sum: Long,
    val storeName: String?,
    val category: CategoryType = CategoryType.OTHER,
)