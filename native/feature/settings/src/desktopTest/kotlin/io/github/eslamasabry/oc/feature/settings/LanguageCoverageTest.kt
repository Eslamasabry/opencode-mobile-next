package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.LanguageCoverage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Flutter `test/revamp/shared_settings_1_test.dart` › language sheet (B-L10N-017). */
@OptIn(ExperimentalTestApi::class)
class LanguageCoverageTest {
    private val saved: Locale = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val words = File("../../ui/words/src/commonMain/composeResources")
    private val name = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""")

    /** The entries of one values folder; `name__form` plural-select helpers are English-only and do not count. */
    private fun keys(folder: String): Set<String> =
        (File(words, folder).listFiles { f -> f.extension == "xml" } ?: emptyArray())
            .flatMap { f -> name.findAll(f.readText()).map { it.groupValues[1] } }
            .filterNot { "__" in it }
            .toSet()

    @Test
    fun `B-L10N-017 every language share is the real coverage, within 3 points`() {
        val en = keys("values")
        assertTrue(en.size > 1000, "found the English words at ${words.absolutePath}")
        for ((language, shown) in LanguageCoverage.percent) {
            val translated = keys("values-${language.tag}").count { it in en }
            val percent = translated * 100 / en.size
            assertTrue(
                abs(percent - shown) <= 3,
                "${language.tag} covers $percent % ($translated of ${en.size}); set LanguageCoverage.percent[${language.name}] to $percent.",
            )
        }
        assertEquals(
            AppLanguage.entries.filter { it.tag != null && it != AppLanguage.English }.toSet(),
            LanguageCoverage.percent.keys,
            "every shipped language but English has its share",
        )
    }

    @Test
    fun `B-L10N-017 each partly translated language says its own share and the complete ones say nothing`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            lateinit var presenter: SettingsPresenter
            setContent {
                SettingsThemed {
                    val scope = rememberCoroutineScope()
                    val p = remember { SettingsPresenter(scope, MutableStateFlow(phoneFacts()), FakeChoices(), RecordingOpeners()).also { presenter = it } }
                    val state by p.state.collectAsState()
                    SettingsScreen(state, p)
                }
            }
            waitForIdle()
            presenter.onOpen(SettingsPage.Language); waitForIdle()
            // Each language shows its own name, and the partly translated ones their own share on the same row.
            val names =
                mapOf(
                    AppLanguage.System to "Use system language", AppLanguage.English to "English", AppLanguage.Arabic to "العربية",
                    AppLanguage.Spanish to "Español", AppLanguage.Japanese to "日本語", AppLanguage.Portuguese to "Português (Brasil)",
                    AppLanguage.Russian to "Русский", AppLanguage.Chinese to "简体中文",
                )
            for (language in AppLanguage.entries) {
                val row = onNode(hasText(names.getValue(language))).performScrollTo()
                when (val share = LanguageCoverage.partly(language)) {
                    null -> row.assert(!hasText("Partly translated", substring = true))
                    else -> row.assert(hasText("Partly translated ($share %)"))
                }
            }
        }
}
