package com.denalal.expensevision.core.model.parser

import com.denalal.expensevision.core.model.ReceiptDraftModel
import com.denalal.expensevision.core.model.categorizer.ReceiptCategorizer
import java.time.LocalDate

private val dateDmy4 = Regex("""\b(\d{2})[.\-/](\d{2})[.\-/](\d{4})\b""")
private val dateIso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
private val dateDmy2 = Regex("""\b(\d{2})[.\-/](\d{2})[.\-/](\d{2})\b""")

class ReceiptParser {
    private val categorizer = ReceiptCategorizer()

    fun parse(rawText: String): ReceiptDraftModel {
        val lines = rawText.lines()
        return ReceiptDraftModel(
            date = extractDate(rawText),
            sum = extractTotal(rawText),
            storeName = extractStore(lines),
            category = categorizer.categorize(rawText),
            rawText = rawText,
        )
    }

    private fun extractDate(rawText: String): LocalDate? {
        dateDmy4.find(rawText)?.let { m ->
            val (d, mo, y) = m.destructured
            runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
                .getOrNull()?.let { return it }
        }

        dateIso.find(rawText)?.let { m ->
            val (y, mo, d) = m.destructured
            runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
                .getOrNull()?.let { return it }
        }

        dateDmy2.find(rawText)?.let { m ->
            val (d, mo, y) = m.destructured
            runCatching { LocalDate.of(2000 + y.toInt(), mo.toInt(), d.toInt()) }
                .getOrNull()?.let { return it }
        }
        return null
    }

    private fun extractTotal(rawText: String): Long? {
        val number = Regex("""\d[\d\s\u00A0]*[.,]\d{2}""")
        val lines = rawText.lines()

        // Все денежные значения на чеке (в копейках)
        val amounts = lines.flatMap { line ->
            number.findAll(line).mapNotNull { toKopecks(it.value) }.toList()
        }.filter { it in 1..10_000_000_00L }
        if (amounts.isEmpty()) return null
        val maxAmount = amounts.max()

        val markerAny = Regex("""(?i)(к\s*оплате|итог|всего)""")
        val markerWithColon = Regex("""(?i)(к\s*оплате|итог|всего)\s*:""")
        val skip = listOf("сумма", "ндс", "без скидки", "скидка", "наличн", "сдача", "бонус")

        for ((index, line) in lines.withIndex()) {
            val l = line.lowercase()
            if (skip.any { it in l }) continue

            // 1) Явный маркер + число в той же строке — доверяем
            val sameLine = number.find(line)?.value?.let { toKopecks(it) }
            if (sameLine != null && markerAny.containsMatchIn(l)) return sameLine

            // 2) Маркер с двоеточием — число может быть на следующей непустой строке.
            //    Берём, только если оно «правдоподобно» (не НДС/мелочь):
            //    не меньше ~75% от максимума.
            if (markerWithColon.containsMatchIn(l)) {
                val nextLine = lines.drop(index + 1)
                    .firstOrNull { it.isNotBlank() }
                    ?.let { number.find(it)?.value }
                    ?.let { toKopecks(it) }
                if (nextLine != null && nextLine * 4 >= maxAmount * 3) return nextLine
            }
        }

        // Fallback: самое большое денежное значение (итог обычно максимальный)
        return maxAmount
    }

    private fun extractStore(lines: List<String>): String? {
        val skip = listOf("кассовый", "чек", "инн", "итог", "сумма", "смена", "кассир", "фд", "фн")

        return lines.map { it.trim() }
            .firstOrNull { l ->
                l.isNotBlank() &&
                        skip.none { it in l.lowercase() } &&
                        !l.matches(Regex("""\d+"""))
            }
    }

    private fun toKopecks(raw: String): Long? =
        raw.replace(Regex("""[\s\u00A0]"""), "")
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?.multiply(java.math.BigDecimal(100))
            ?.toLong()
}