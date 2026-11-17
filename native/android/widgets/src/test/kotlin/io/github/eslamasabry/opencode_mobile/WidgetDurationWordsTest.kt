package io.github.eslamasabry.opencode_mobile

import android.app.Application
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetDurationWordsTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        Numbers.pinned = null
        RuntimeEnvironment.setQualifiers("+en")
        java.util.Locale.setDefault(java.util.Locale.US)
    }

    private fun withLocale(tag: String, block: () -> Unit) {
        val q = if (tag == "id") "+in" else "+$tag"
        RuntimeEnvironment.setQualifiers(q)
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
        block()
    }

    @Test
    fun `B-L10N-023 widgets format pluralized Arabic, Russian and Japanese durations without English leaks`() {
        val now = 1_700_000_000_000L
        Numbers.pinned = NumberSymbols.Latin

        withLocale("ar") {
            assertEquals("منذ 5 دقائق", WidgetDurationWords.ago(app, now, now - 5 * 60_000L))
            assertEquals("منذ دقيقتين", WidgetDurationWords.ago(app, now, now - 2 * 60_000L))
            assertEquals("منذ ساعتين", WidgetDurationWords.ago(app, now, now - 2 * 3_600_000L))
            assertEquals("15 دقيقة", WidgetDurationWords.since(app, now, now - 15 * 60_000L))
            assertEquals("يومان", WidgetDurationWords.spanSince(app, now, now - 2 * 86_400_000L))
        }

        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
        withLocale("ar") {
            assertEquals("منذ ٥ دقائق", WidgetDurationWords.ago(app, now, now - 5 * 60_000L))
        }

        Numbers.pinned = NumberSymbols.Latin
        withLocale("ja") {
            assertEquals("5分前", WidgetDurationWords.ago(app, now, now - 5 * 60_000L))
            assertEquals("2時間前", WidgetDurationWords.ago(app, now, now - 2 * 3_600_000L))
            assertEquals("2日", WidgetDurationWords.spanSince(app, now, now - 2 * 86_400_000L))
            assertEquals("5分", WidgetDurationWords.compactDuration(app, 5 * 60_000L))
        }

        withLocale("ru") {
            assertEquals("5 минут назад", WidgetDurationWords.ago(app, now, now - 5 * 60_000L))
            assertEquals("2 минуты назад", WidgetDurationWords.ago(app, now, now - 2 * 60_000L))
            assertEquals("2 часа назад", WidgetDurationWords.ago(app, now, now - 2 * 3_600_000L))
        }

        withLocale("es") {
            assertEquals("5 min", WidgetDurationWords.compactDuration(app, 5 * 60_000L))
        }

        withLocale("id") {
            assertEquals("2 jam", WidgetDurationWords.compactDuration(app, 2 * 3_600_000L))
        }
    }

    @Test
    fun `B-L10N-024 usage widget sub-minute countdowns use 1-minute floor instead of now in all 10 languages`() {
        Numbers.pinned = NumberSymbols.Latin
        val now = 1_700_000_000_000L
        val expectedResets = mapOf(
            "en" to "Resets in 1m",
            "ar" to "يتجدّد خلال دقيقة واحدة",
            "es" to "Se restablece en 1 min",
            "fr" to "Réinitialisation dans 1 min",
            "id" to "Direset dalam 1 mnt",
            "pt" to "Renova em 1min",
            "ru" to "Сброс через 1 мин",
            "vi" to "Đặt lại sau 1 phút",
            "zh" to "1分后重置",
            "ja" to "1分後にリセット",
        )
        for ((loc, wantReset) in expectedResets) {
            withLocale(loc) {
                val active = UsageWidgetRow(
                    provider = "codex",
                    displayName = "Codex",
                    profileName = "",
                    seconds = 18_000L,
                    label = "",
                    left = 80,
                    resetsAt = now + 30_000L,
                    fetchedAt = now - 10_000L,
                    authNeeded = false,
                )
                assertEquals(wantReset, UsageWidgetText.detailText(app, listOf(active), now, false))
            }
        }
    }
}
