package cz.craftplan.app

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkModelsTest {
    @Test
    fun bundledSdkExposesTextModelsBeforeInitialization() {
        assertTrue("cs" in InkModels.languages)
        assertTrue("en" in InkModels.languages)
        assertTrue(InkModels.languages.size > 100)
    }

    @Test
    fun applicationLocaleUsesBcp47WithLanguageFallback() {
        val available = setOf("cs", "en", "pt-BR", "pt-PT", "zh-Hant", "zh-Hans")
        assertEquals("cs", defaultInkLanguage(Locale.forLanguageTag("cs-CZ"), available))
        assertEquals("pt-PT", defaultInkLanguage(Locale.forLanguageTag("pt-PT"), available))
        assertEquals("zh-Hant", defaultInkLanguage(Locale.forLanguageTag("zh-Hant-TW"), available))
        assertEquals("en", defaultInkLanguage(Locale.forLanguageTag("de-DE"), available))
    }

    @Test
    fun downloadApprovalDoesNotPinAutomaticLanguage() {
        val store = mutableMapOf<String, Any>()
        val prefs = testInkPreferences(store)
        val cs = InkLanguageSettings(prefs, "cs")
        cs.approve(InkDownloadRequest("cs"))
        assertEquals(null, cs.manualLanguage)
        assertEquals("cs", cs.language)
        assertEquals("en", InkLanguageSettings(prefs, "en").language)
        cs.select("en")
        assertEquals(InkDownloadRequest("en", manualSelection = true), cs.downloadConfirmation)
        cs.approve(requireNotNull(cs.downloadConfirmation))
        assertEquals("en", InkLanguageSettings(prefs, "cs").language)
        cs.selectAutomatic()
        assertEquals("cs", InkLanguageSettings(prefs, "cs").language)
    }

    @Test
    fun missingModelOfferIsOncePerLanguageAndDoesNotPinIt() {
        val prefs = testInkPreferences(mutableMapOf())
        val settings = InkLanguageSettings(prefs, "cs")
        settings.requestModelForWriting()
        assertEquals(InkDownloadRequest("cs"), settings.downloadConfirmation)
        settings.downloadConfirmation = null
        settings.requestModelForWriting()
        assertEquals(null, settings.downloadConfirmation)
        assertEquals(null, settings.manualLanguage)
        assertTrue(settings.approved.isEmpty())
    }

    @Test
    fun existingModelCheckPreventsUnnecessaryDownloadOffer() {
        val prefs = testInkPreferences(mutableMapOf())
        InkLanguageSettings(prefs, "cs").approve(InkDownloadRequest("cs"))
        val ready = InkLanguageSettings(prefs, "cs")
        ready.requestModelForWriting()
        assertEquals(null, ready.downloadConfirmation)
        ready.modelChecked("cs", true)
        assertEquals("cs", ready.readyLanguage)
        assertEquals(null, ready.downloadConfirmation)
        val missing = InkLanguageSettings(prefs, "cs")
        missing.requestModelForWriting()
        missing.modelChecked("cs", false)
        assertEquals(InkDownloadRequest("cs"), missing.downloadConfirmation)
    }

    @Test
    fun excludesNonLanguageModelTags() {
        assertTrue(isTextInkLanguage("zh-Hant"))
        assertFalse(isTextInkLanguage("zxx-Zsym-x-autodraw"))
        assertFalse(isTextInkLanguage("und"))
    }
}

/** In-memory preference boundary; no Android device or SDK network operation. */
private fun testInkPreferences(store: MutableMap<String, Any>): SharedPreferences {
    val editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "putString", "putStringSet" -> { store[args[0] as String] = requireNotNull(args[1]); proxy }
            "remove" -> { store.remove(args[0] as String); proxy }
            "apply" -> null
            else -> error("Unexpected editor call: ${method.name}")
        }
    } as SharedPreferences.Editor
    return Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getString", "getStringSet" -> store[args[0] as String] ?: args[1]
            "edit" -> editor
            else -> error("Unexpected preferences call: ${method.name}")
        }
    } as SharedPreferences
}
