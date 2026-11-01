package io.github.eslamasabry.oc.ui.words

import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiReferenceAlreadyAdded
import io.github.eslamasabry.oc.ui.words.res.e7SettingsStorageSummary__queued
import io.github.eslamasabry.oc.ui.words.res.teamProjectGoalStatus__milestones
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The converted catalogue read back by CMP itself (desktop resources, the system locale as the app language):
 * the evidence for plan 06 §13.4 [U] "CMP picks zero, one, two, few, many and other for 0, 1, 2, 3, 11, 100 in ar".
 */
class WordsTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun lang(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    @Test
    fun `B-L10N-010 Arabic plurals pick zero, one, two, few, many and other for 0, 1, 2, 3, 11 and 100`() = runTest {
        lang("ar")
        val q = Res.plurals.e7SettingsStorageSummary__queued
        assertEquals("لا طلبات في قائمة الانتظار", getPluralString(q, 0, 0))
        assertEquals("طلب واحد في قائمة الانتظار", getPluralString(q, 1, 1))
        assertEquals("طلبان في قائمة الانتظار", getPluralString(q, 2, 2))
        assertEquals("3 طلبات في قائمة الانتظار", getPluralString(q, 3, 3))
        assertEquals("11 طلبًا في قائمة الانتظار", getPluralString(q, 11, 11))
        assertEquals("100 طلب في قائمة الانتظار", getPluralString(q, 100, 100))
    }

    @Test
    fun `B-L10N-010 a translated message reads in its language, a missing one reads English`() = runTest {
        lang("ar")
        assertEquals("‎@x موجود بالفعل في الطلب", getString(Res.string.chatUiReferenceAlreadyAdded, "x"))
        // Arabic writes this count as a number, so it has no piece: CMP falls back to English, not to another language.
        assertEquals("2 milestones", getPluralString(Res.plurals.teamProjectGoalStatus__milestones, 2, 2))
        lang("ja")
        assertEquals("@x is already in the prompt", getString(Res.string.chatUiReferenceAlreadyAdded, "x"))
    }

    @Test
    fun `B-L10N-010 hand-converted selects pick their branch, any other value reads other`() = runTest {
        lang("en")
        assertEquals("Idle · 45%", getString(HandWords.runningPhoneCpu("idle"), "45"))
        assertEquals("Very busy · 45%", getString(HandWords.runningPhoneCpu("overloaded"), "45"))
        assertEquals("Ada, reviewer, working", getString(HandWords.kitAgentLabel("yes"), "Ada", "reviewer", "working"))
        assertEquals("Ada, working", getString(HandWords.kitAgentLabel("no"), "Ada", "reviewer", "working"))
        lang("ar")
        assertEquals("خامل · 45%", getString(HandWords.runningPhoneCpu("idle"), "45"))
    }

    @Test
    fun `B-L10N-010 hand-converted split plurals compose the sentence`() = runTest {
        lang("en")
        assertEquals("a.xlsx attached as text · 1 sheet, 3 rows", HandWords.chatAttachmentSheetAttached("a.xlsx", 1, 3))
        assertEquals("Spec approved today · 1 milestone · 2 repos", HandWords.teamProjectGoalStatus("today", 1, 2))
        lang("ar")
        assertEquals("أُرفق a.xlsx كنص · ورقتان، 3 صفوف", HandWords.chatAttachmentSheetAttached("a.xlsx", 2, 3))
        assertEquals(
            "اعتُمدت المواصفات منذ يومين · عدد المراحل الرئيسية: 2 · عدد المستودعات: 3",
            HandWords.teamProjectGoalStatus("يومين", 2, 3),
        )
        lang("zh")
        assertEquals("2 个里程碑中共 5 个任务", HandWords.teamProjectBoardSummary(5, 2))
    }
}
