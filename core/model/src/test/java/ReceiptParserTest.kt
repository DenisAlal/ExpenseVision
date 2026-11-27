import com.denalal.expensevision.core.model.CategoryType
import com.denalal.expensevision.core.model.parser.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    @Test
    fun `parses date total and store`() {
        val raw = """
            ПЯТЁРОЧКА
            ИНН 123456
            01.12.2026 12:34
            Молоко 89,90
            ИТОГО 1234,56
        """.trimIndent()

        val draft = ReceiptParser().parse(raw)   // метод, который ты напишешь

        assertEquals(LocalDate.of(2026, 12, 1), draft.date)
        assertEquals(123456L, draft.sum)
        assertEquals("ПЯТЁРОЧКА", draft.storeName)
        assertEquals(CategoryType.GROCERY, draft.category)
    }

    @Test
    fun `missing fields are null`() {
        val draft = ReceiptParser().parse("просто текст без даты и суммы")
        assertEquals(null, draft.date)
        assertEquals(null, draft.sum)
    }
}