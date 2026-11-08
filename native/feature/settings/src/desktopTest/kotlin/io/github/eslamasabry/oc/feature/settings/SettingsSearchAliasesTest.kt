package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearch
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.ui.aliasKeys
import io.github.eslamasabry.oc.feature.settings.ui.rowKey
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.teamUiHomeTitle
import org.jetbrains.compose.resources.getString

/**
 * B-165: Settings search finds the rows 2.1.4 finds, with the words Flutter's settings search gives them
 * (search_index/hub_rows.dart and settings_search_catalog.dart), read from the real `:ui:words` in each language.
 */
class SettingsSearchAliasesTest {
    private val saved = Locale.getDefault()

    @AfterTest fun restore() = Locale.setDefault(saved)

    private suspend fun entries(tag: String): List<SettingsSearchEntry> {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return SettingsRowId.entries.map { row ->
            SettingsSearchEntry(row, getString(rowKey(row)), aliasKeys(row).map { getString(it) }.joinToString(" "))
        }
    }

    private fun first(query: String, entries: List<SettingsSearchEntry>): SettingsRowId? =
        SettingsSearch.find(query, entries).firstOrNull()

    @Test
    fun `B-165 Text size, Voice typing and Always allowed actions find their rows in English`() = runTest {
        val en = entries("en")
        assertEquals(SettingsRowId.Appearance, first("Text size", en))
        assertEquals(SettingsRowId.Voice, first("Voice typing", en))
        assertEquals(SettingsRowId.Permissions, first("Always allowed actions", en))
        assertEquals(SettingsRowId.Appearance, first("glow", en))
    }

    @Test
    fun `B-165 the same rows are found in Arabic and Spanish`() = runTest {
        val ar = entries("ar")
        assertEquals(SettingsRowId.Appearance, first("حجم النص", ar))
        assertEquals(SettingsRowId.Voice, first("الكتابة الصوتية", ar))
        assertEquals(SettingsRowId.Permissions, first("الإجراءات المسموح بها دائمًا", ar))
        // Flutter keeps the English words searchable in either locale.
        assertEquals(SettingsRowId.Appearance, first("text size", ar))
        val es = entries("es")
        assertEquals(SettingsRowId.Appearance, first("Tamaño del texto", es))
        assertEquals(SettingsRowId.Appearance, first("text size", es))
        assertEquals(SettingsRowId.Voice, first("voice typing", es))
    }

    @Test
    fun `B-SET-260 searching for team finds AI Team in English and Arabic, not the phone's agents`() = runTest {
        val en = entries("en")
        assertEquals(SettingsRowId.AiTeam, first("team", en))
        assertEquals(SettingsRowId.AiTeam, first("AI Team", en))
        assertEquals(SettingsRowId.AiTeam, first("orchestration", en))
        val ar = entries("ar")
        assertEquals(SettingsRowId.AiTeam, first(getString(Res.string.teamUiHomeTitle), ar))
        assertEquals(SettingsRowId.AiTeam, first("team", ar))
    }
}
