import com.denalal.expensevision.core.model.CategoryType
import com.denalal.expensevision.core.model.categorizer.ReceiptCategorizer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptCategorizerTest {

    private val categorizer = ReceiptCategorizer()

    @Test
    fun `pyaterochka is grocery`() {
        assertEquals(CategoryType.GROCERY, categorizer.categorize("Пятерочка 1234"))
    }


    @Test
    fun `unknown text falls back to other`() {
        assertEquals(CategoryType.OTHER, categorizer.categorize("случайный текст"))
    }
}