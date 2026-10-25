package com.slothshot.app

import com.slothshot.app.ai.QwenVisionEngine
import com.slothshot.app.model.ActionType
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenVisionEngineTest {

    private val engine = QwenVisionEngine()

    @Test
    fun testParseCleanQwenJson() {
        val jsonOutput = """
            {
              "title": "Код Сбербанк Онлайн",
              "summary": "Одноразовый проверочный пароль для входа в приложение.",
              "category": "TEMPORARY_UTILITY",
              "retention": "EPHEMERAL_PURGE",
              "key_values": {
                "Код": "583921",
                "Банк": "Сбер"
              },
              "tags": ["сбер", "код", "2fa"],
              "action_type": "COPY",
              "action_label": "Скопировать код",
              "action_payload": "583921",
              "raw_content": "Сбербанк: Пароль для входа 583921. Никому не говорите!"
            }
        """.trimIndent()

        val parsed = engine.parseQwenJsonOutput(jsonOutput)
        assertNotNull(parsed)
        assertEquals(ContentCategory.TEMPORARY_UTILITY, parsed?.category)
        assertEquals(RetentionClass.EPHEMERAL_PURGE, parsed?.retention)
        assertEquals("Код Сбербанк Онлайн", parsed?.title)
        assertEquals("583921", parsed?.actionPayload)
        assertEquals(ActionType.COPY, parsed?.actionType)
        assertEquals("583921", parsed?.keyValues?.get("Код"))
    }

    @Test
    fun testParseMarkdownWrappedJson() {
        val markdownOutput = """
            Вот результат анализа скриншота:
            ```json
            {
              "title": "Чек Кофейня 450 ₽",
              "summary": "Чек за кофе и десерт в кофейне.",
              "category": "TRANSACTIONAL_RECEIPT",
              "retention": "KEEP_ARCHIVE",
              "key_values": {
                "Итого": "450 ₽"
              },
              "tags": ["чек", "кофе"],
              "action_type": "SHARE",
              "action_label": "Поделиться чеком",
              "action_payload": "450 ₽",
              "raw_content": "Кофейня\nКапучино 250р\nКруассан 200р\nИтого: 450р"
            }
            ```
        """.trimIndent()

        val parsed = engine.parseQwenJsonOutput(markdownOutput)
        assertNotNull(parsed)
        assertEquals(ContentCategory.TRANSACTIONAL_RECEIPT, parsed?.category)
        assertEquals(RetentionClass.KEEP_ARCHIVE, parsed?.retention)
        assertEquals("Чек Кофейня 450 ₽", parsed?.title)
        assertEquals(ActionType.SHARE, parsed?.actionType)
        assertTrue(parsed?.summary?.contains("кофе") == true)
    }

    @Test
    fun testParseInvalidJsonGracefulFallback() {
        val broken = "This is not valid json at all { unclosed"
        val parsed = engine.parseQwenJsonOutput(broken)
        assertTrue(parsed == null)
    }
}
