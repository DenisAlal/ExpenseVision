package com.denalal.expensevision.core.model.categorizer

import com.denalal.expensevision.core.model.CategoryType


class ReceiptCategorizer(
    private val keywords: Map<CategoryType, List<String>> = categoryKeywords,
) {
    private fun normalize(s: String): String =
        s.lowercase().replace('ё', 'е')

    fun categorize(text: String): CategoryType {
        val t = normalize(text)
        return keywords.entries.firstOrNull { (_, words) -> words.any { normalize(it) in t } }?.key
            ?: CategoryType.OTHER
    }
}