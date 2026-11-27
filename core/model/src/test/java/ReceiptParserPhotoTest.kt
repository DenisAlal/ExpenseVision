import com.denalal.expensevision.core.model.CategoryType
import com.denalal.expensevision.core.model.parser.ReceiptParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test


class ReceiptParserPhotoTest {

    @Test
    fun `scrambled layout - itog value is far from marker`() {
        val raw = """
            Пятёрочка
            ИТОГО
            94.00
            94.00
            188.00
            ПОДЫТОГ:
            188.00
            ИТОГ:
            28.68
            Сумма НАС 18%
            29.06.17 13:18
        """.trimIndent()

        val draft = ReceiptParser().parse(raw)

        assertEquals(18800L, draft.sum)
        assertEquals("Пятёрочка", draft.storeName)
        assertEquals(LocalDate.of(2017, 6, 29), draft.date)
        assertEquals(CategoryType.GROCERY, draft.category)
    }

    @Test
    fun `itog on next line, poditog bigger due to ocr misread`() {
        val raw = """
            Пятерочка
            ПОДЫТОГ:
            198.00
            ИТОГ:
            188.00
            C:
            28.68
            Сумма НДC 18%
            29.06.17 13:18
        """.trimIndent()

        val draft = ReceiptParser().parse(raw)

        assertEquals(18800L, draft.sum)
        assertEquals("Пятерочка", draft.storeName)
    }
}
