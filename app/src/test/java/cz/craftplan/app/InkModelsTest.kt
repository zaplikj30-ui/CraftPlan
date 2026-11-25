package cz.craftplan.app

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkModelsTest {
    @Test
    fun applicationLocaleUsesBcp47WithLanguageFallback() {
        val available = setOf("cs", "en", "pt-BR", "pt-PT", "zh-Hant", "zh-Hans")
        assertEquals("cs", defaultInkLanguage(Locale.forLanguageTag("cs-CZ"), available))
        assertEquals("pt-PT", defaultInkLanguage(Locale.forLanguageTag("pt-PT"), available))
        assertEquals("zh-Hant", defaultInkLanguage(Locale.forLanguageTag("zh-Hant-TW"), available))
        assertEquals("en", defaultInkLanguage(Locale.forLanguageTag("de-DE"), available))
    }

    @Test
    fun excludesNonLanguageModelTags() {
        assertTrue(isTextInkLanguage("zh-Hant"))
        assertFalse(isTextInkLanguage("zxx-Zsym-x-autodraw"))
        assertFalse(isTextInkLanguage("und"))
    }
}
