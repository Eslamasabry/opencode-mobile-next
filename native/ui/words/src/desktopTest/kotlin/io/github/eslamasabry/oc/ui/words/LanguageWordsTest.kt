package io.github.eslamasabry.oc.ui.words

import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.e7LibraryOpenCodeCouldNotPrepareThisWorktree
import io.github.eslamasabry.oc.ui.words.res.e7LibraryOpenCodeDidNotSwitchLocations
import io.github.eslamasabry.oc.ui.words.res.e7LibraryOpenCodeIsReconnectingTryAgain
import io.github.eslamasabry.oc.ui.words.res.e7LibraryWaitForOpenCodeToFinishPreparingThis
import io.github.eslamasabry.oc.ui.words.res.e7ModelUiCost
import io.github.eslamasabry.oc.ui.words.res.kitComposerSend
import io.github.eslamasabry.oc.ui.words.res.worktreesCreateHelper
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The language rules of `:ui:words`: which languages ship, how the phone picks one, and how words and prices read. */
class LanguageWordsTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() {
        Locale.setDefault(saved)
        Numbers.pinned = null
    }

    private fun lang(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    // ---- B-L10N-008 -------------------------------------------------------------------------------------------------

    @Test
    fun `B-L10N-008 the shipped languages, the saved-choice set and the picker agree with the catalogue folders`() {
        val folders = NativeTree.words.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }
            .map { if (it.name == "values") AppLanguages.ENGLISH else it.name.removePrefix("values-") }.toSet()
        assertEquals(setOf("en", "ar", "es", "ja", "pt", "ru", "zh"), folders)
        assertEquals(folders, AppLanguages.shipped)
        assertEquals(folders, AppLanguages.picker.toSet())
        assertEquals(AppLanguages.picker.size, AppLanguages.picker.distinct().size)
        assertEquals("en", AppLanguages.picker.first())
        assertTrue(AppLanguages.isShipped("ar"))
        assertFalse(AppLanguages.isShipped("de"), "an unshipped language is never saved as a choice")
        assertFalse(AppLanguages.isShipped(null))
    }

    @Test
    fun `B-L10N-008 each catalogue speaks the language its folder names`() = runTest {
        lang("en")
        val english = getString(Res.string.kitComposerSend)
        for (code in AppLanguages.picker - "en") {
            lang(code)
            assertNotEquals(english, getString(Res.string.kitComposerSend), code)
        }
        lang("ja")
        assertEquals("送信", getString(Res.string.kitComposerSend))
        lang("es")
        assertEquals("Nueva conversación", getString(Res.string.chatsHomeNewChat))
        lang("ar")
        assertEquals("إرسال", getString(Res.string.kitComposerSend))
        lang("zh")
        assertEquals("发送", getString(Res.string.kitComposerSend))
        lang("ru")
        assertEquals("Отправить", getString(Res.string.kitComposerSend))
        lang("pt")
        assertEquals("Nova conversa", getString(Res.string.chatsHomeNewChat))
    }

    // ---- B-L10N-009 -------------------------------------------------------------------------------------------------

    @Test
    fun `B-L10N-009 a language the app does not ship reads English, never Arabic`() {
        assertEquals("en", AppLanguages.resolve(listOf("de-DE")))
        assertEquals("en", AppLanguages.resolve(listOf("ko", "fr")))
        assertEquals("en", AppLanguages.resolve(emptyList()))
    }

    @Test
    fun `B-L10N-009 each shipped language is found by its code, region or script`() {
        assertEquals("ja", AppLanguages.resolve(listOf("ja-JP")))
        assertEquals("es", AppLanguages.resolve(listOf("es-MX")))
        assertEquals("ru", AppLanguages.resolve(listOf("ru-RU")))
        assertEquals("ar", AppLanguages.resolve(listOf("ar-EG")))
        assertEquals("pt", AppLanguages.resolve(listOf("pt-BR")))
        assertEquals("pt", AppLanguages.resolve(listOf("pt-PT")))
        assertEquals("zh", AppLanguages.resolve(listOf("zh-Hans-CN")))
        assertEquals("zh", AppLanguages.resolve(listOf("zh-Hant-TW")), "Traditional readers get Chinese, not English")
        assertEquals("zh", AppLanguages.resolve(listOf("zh_Hant_TW")), "Java's own spelling")
        assertEquals("en", AppLanguages.resolve(listOf("en-GB")))
    }

    @Test
    fun `B-L10N-009 the next language in the device list is tried before English`() {
        assertEquals("ru", AppLanguages.resolve(listOf("de", "ru")))
        assertEquals("ar", AppLanguages.resolve(listOf("de-DE", "fr-FR", "ar-EG", "ja")))
    }

    // ---- B-L10N-012 -------------------------------------------------------------------------------------------------

    private val arabicIndic = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫')

    @Test
    fun `B-L10N-012 English prices and counts are unchanged`() {
        val latin = NumberSymbols.Latin
        assertEquals("$0.043", LocaleFormat.usd("en", 0.0426, decimals = 3, symbols = latin))
        assertEquals("$12.50", LocaleFormat.usd("en", 12.5, symbols = latin))
        assertEquals("980", LocaleFormat.compactCount(980, latin))
        assertEquals("12.4k", LocaleFormat.compactCount(12400, latin))
        assertEquals("1.2M", LocaleFormat.compactCount(1_200_000, latin))
        assertEquals("1,234,567", LocaleFormat.count(1_234_567, latin))
    }

    @Test
    fun `B-L10N-012 Arabic puts the dollar sign after the amount, with the phone's digits`() {
        val latin = NumberSymbols.Latin
        val cost = LocaleFormat.usd("ar", 0.0426, decimals = 3, symbols = latin)
        assertFalse(cost.startsWith("$"), cost)
        assertTrue(cost.contains("0.043"), cost)
        assertTrue(cost.trimEnd().endsWith("$"), cost)
        assertNotEquals(LocaleFormat.usd("en", 3.0, symbols = latin), LocaleFormat.usd("ar", 3.0, symbols = latin))
        assertTrue(LocaleFormat.usd("ar-EG", 3.0, symbols = latin).trimEnd().endsWith("$"), "a region keeps the rule")
        // A phone that writes Arabic-Indic digits.
        assertTrue(LocaleFormat.count(1234, arabicIndic).contains('١'))
        assertTrue(LocaleFormat.compactCount(12400, arabicIndic).contains('١'))
        assertTrue(LocaleFormat.usd("ar", 1234.5, symbols = arabicIndic).contains("١٬٢٣٤٫٥٠"))
    }

    @Test
    fun `B-L10N-012 the model price label follows the app language`() = runTest {
        val latin = NumberSymbols.Latin
        val (enIn, enOut) = LocaleFormat.modelPrices("en", 3.0, 15.0, latin)!!
        lang("en")
        val en = getString(Res.string.e7ModelUiCost, enIn, enOut)
        assertTrue(en.contains("$3.00"), en)
        assertTrue(en.contains("$15.00"), en)
        val (arIn, arOut) = LocaleFormat.modelPrices("ar", 3.0, 15.0, latin)!!
        lang("ar")
        val ar = getString(Res.string.e7ModelUiCost, arIn, arOut)
        assertFalse(ar.contains("$3.00"), ar)
        assertTrue(ar.contains("3.00"), ar)
        assertTrue(ar.contains("مليون"), ar)
        assertEquals("$0.250", LocaleFormat.modelPrices("en", 0.25, 0.0, latin)!!.first, "three places below a dollar")
        assertEquals(null, LocaleFormat.modelPrices("en", 0.0, 0.0, latin), "no price, no label")
    }

    // ---- B-L10N-004 -------------------------------------------------------------------------------------------------

    @Test
    fun `B-L10N-004 untitled placeholders read in the app language while real titles are kept as-is`() = runTest {
        lang("ar")
        assertEquals("محادثة جديدة", sessionTitleWords(SessionTitleShown.of(null, placeholder = false)))
        assertEquals(
            "محادثة جديدة",
            sessionTitleWords(SessionTitleShown.of("New session - 2026-09-10T00:00:00Z", true, fallback = "جلسة بلا عنوان")),
        )
        assertEquals("Investigate checkout", sessionTitleWords(SessionTitleShown.of("Investigate checkout", false)))
        assertEquals("جلسة بلا عنوان", sessionTitleWords(SessionTitleShown.of(null, false, fallback = "جلسة بلا عنوان")))
        lang("en")
        assertEquals("New conversation", sessionTitleWords(SessionTitleShown.of("  ", placeholder = false)))
        assertEquals("Investigate checkout", sessionTitleWords(SessionTitleShown.of("Investigate checkout", false)))
    }

    @Test
    fun `B-L10N-004 relative times follow the app language`() = runTest {
        Numbers.pinned = NumberSymbols.Latin
        val minute = 60_000L
        lang("ar")
        assertEquals("الآن", relativeAgeWords(RelativeAge.of(20_000)))
        assertEquals("قبل 5 د", relativeAgeWords(RelativeAge.of(5 * minute)))
        assertEquals("قبل 3 س", relativeAgeWords(RelativeAge.of(180 * minute)))
        assertEquals("أمس", relativeAgeWords(RelativeAge.of(30 * 60 * minute)))
        assertEquals("قبل 2 ي", relativeAgeWords(RelativeAge.of(2 * 24 * 60 * minute)))
        lang("en")
        assertEquals("Just now", relativeAgeWords(RelativeAge.of(-5_000)), "a clock skew reads Just now")
        assertEquals("5 min ago", relativeAgeWords(RelativeAge.of(5 * minute)))
        assertEquals("3h ago", relativeAgeWords(RelativeAge.of(180 * minute)))
        assertEquals("Yesterday", relativeAgeWords(RelativeAge.of(25 * 60 * minute)))
        assertEquals("2d ago", relativeAgeWords(RelativeAge.of(2 * 24 * 60 * minute)))
        Numbers.pinned = arabicIndic
        lang("ar")
        assertEquals("قبل ٥ د", relativeAgeWords(RelativeAge.of(5 * minute)), "the count uses the phone's digits")
    }

    // ---- B-L10N-015 -------------------------------------------------------------------------------------------------

    @Test
    fun `B-L10N-015 worktree helper and error copy never names OpenCode`() = runTest {
        lang("en")
        val copy = listOf(
            Res.string.worktreesCreateHelper,
            Res.string.e7LibraryOpenCodeCouldNotPrepareThisWorktree,
            Res.string.e7LibraryWaitForOpenCodeToFinishPreparingThis,
            Res.string.e7LibraryOpenCodeIsReconnectingTryAgain,
            Res.string.e7LibraryOpenCodeDidNotSwitchLocations,
        ).map { getString(it) }
        for (text in copy) {
            assertTrue(text.isNotBlank())
            assertFalse(text.contains("OpenCode"), text)
        }
        // And every worktree message in every language, read from the catalogues themselves.
        val worktree = Regex("""<string name="((?:worktrees|e7Library)[A-Za-z]*Worktree[A-Za-z]*|worktrees[A-Za-z]+)">([^<]*)<""")
        val found = NativeTree.words.walkTopDown().filter { it.isFile && it.extension == "xml" }
            .flatMap { file -> worktree.findAll(file.readText()).map { "${file.parentFile.name}/${it.groupValues[1]}" to it.groupValues[2] } }
            .toList()
        assertTrue(found.size > 20, "the worktree copy was found")
        assertEquals(emptyList(), found.filter { it.second.contains("OpenCode") }.map { it.first })
    }
}
