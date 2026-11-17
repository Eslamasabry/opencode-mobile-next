@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o48

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.KitDurationStyle
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.text.kitElapsedSecondsMinutes
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.text.kitResetInWords
import io.github.eslamasabry.oc.ui.kit.text.kitTenthsSecondsWords
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitLogQuiet
import io.github.eslamasabry.oc.ui.words.res.linuxSvcRunningFor
import io.github.eslamasabry.oc.ui.words.res.replySpeedNoWords
import io.github.eslamasabry.oc.ui.words.res.s9LimitsResetsIn
import java.util.Locale
import org.jetbrains.compose.resources.stringResource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationWordsTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun resetNumbers() {
        Numbers.pinned = null
        Locale.setDefault(savedLocale)
    }

    @Test
    fun `durations follow Flutter largest units and clamp negative elapsed time`() {
        Locale.setDefault(Locale.ENGLISH)
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitText(kitDurationWords((-1).seconds))
                        KitText(kitDurationWords(45.minutes))
                        KitText(kitDurationWords(3.hours + 20.minutes))
                        KitText(kitDurationWords(1.days + 21.hours))
                        KitText(kitDurationWords(2.days))
                    }
                }
            }
            onNodeWithText("less than a minute").assertExists()
            onNodeWithText("45 min").assertExists()
            onNodeWithText("3 h 20 min").assertExists()
            onNodeWithText("1 d 21 h").assertExists()
            onNodeWithText("2 d").assertExists()
        }
    }

    @Test
    fun `B-L10N-020 Arabic uses all six plural forms, localized units and system digits`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "ar", stillFrame = true) {
                    val nowMs = 1_000_000_000L
                    Column {
                        KitText(kitDurationWords(1.minutes))
                        KitText(kitDurationWords(2.minutes))
                        KitText(kitDurationWords(5.minutes))
                        KitText(kitDurationWords(15.minutes))
                        KitText(kitDurationWords(2.days))
                        KitText(kitTenthsSecondsWords("12.4"))
                        KitText(kitElapsedSecondsMinutes(72))
                        KitText(kitRelativeAge(nowMs - 5 * 60_000L, nowMs))
                        KitText(kitRelativeAge(nowMs - 2 * 3_600_000L, nowMs))
                        KitText(kitResetInWords(2.hours + 10.minutes))
                    }
                }
            }
            onNodeWithText("دقيقة واحدة").assertExists()
            onNodeWithText("دقيقتان").assertExists()
            onNodeWithText("٥ دقائق").assertExists()
            onNodeWithText("١٥ دقيقة").assertExists()
            onNodeWithText("يومان").assertExists()
            onNodeWithText("١٢٫٤ ثانية").assertExists()
            onNodeWithText("دقيقة واحدة و١٢ ثانية").assertExists()
            onNodeWithText("منذ ٥ دقائق").assertExists()
            onNodeWithText("منذ ساعتين").assertExists()
            onNodeWithText("يتبقّى ساعتان و١٠ دقائق لإعادة التعيين").assertExists()
        }
    }

    @Test
    fun `B-L10N-021 Russian uses one-few-many declensions and localized units`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        Numbers.pinned = NumberSymbols.Latin
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "ru", stillFrame = true) {
                    val nowMs = 1_000_000_000L
                    Column {
                        KitText(kitRelativeAge(nowMs - 1 * 60_000L, nowMs))
                        KitText(kitRelativeAge(nowMs - 2 * 60_000L, nowMs))
                        KitText(kitRelativeAge(nowMs - 5 * 60_000L, nowMs))
                        KitText(kitRelativeAge(nowMs - 2 * 3_600_000L, nowMs))
                        KitText(kitResetInWords(2.hours + 10.minutes))
                        KitText(kitResetInWords(5.days))
                        KitText(kitTenthsSecondsWords("12.4"))
                    }
                }
            }
            onNodeWithText("1 минуту назад").assertExists()
            onNodeWithText("2 минуты назад").assertExists()
            onNodeWithText("5 минут назад").assertExists()
            onNodeWithText("2 часа назад").assertExists()
            onNodeWithText("Сброс через 2 ч 10 мин").assertExists()
            onNodeWithText("Сброс через 5 дней").assertExists()
            onNodeWithText("12.4 с").assertExists()
        }
    }

    @Test
    fun `B-L10N-022 Japanese and all remaining languages localize every duration and age shape`() {
        Numbers.pinned = NumberSymbols.Latin
        val cases = listOf(
            "ja" to listOf("1分12秒", "12.4秒", "13分36秒", "5分前", "2時間前", "2日前", "あと2時間10分でリセット"),
            "es" to listOf("1 min 12 s", "12.4 s", "13 min 36 s", "hace 5 min", "hace 2 h", "hace 2 días", "Se restablece en 2 h 10 min"),
            "fr" to listOf("1 min 12 s", "12.4 s", "13 min 36 s", "il y a 5 min", "il y a 2 h", "il y a 2 jours", "Réinitialisation dans 2 h 10 min"),
            "id" to listOf("1 menit 12 detik", "12.4 detik", "13 mnt 36 dtk", "5 menit lalu", "2 jam lalu", "2 hari lalu", "Reset dalam 2 jam 10 menit"),
            "pt" to listOf("1 min 12 s", "12.4 s", "13 min 36 s", "há 5 min", "há 2 h", "há 2 dias", "Renova em 2 h 10 min"),
            "vi" to listOf("1 phút 12 giây", "12.4 giây", "13 phút 36 giây", "5 phút trước", "2 giờ trước", "2 ngày trước", "Đặt lại sau 2 giờ 10 phút"),
            "zh" to listOf("1 分钟 12 秒", "12.4 秒", "13分 36秒", "5 分钟前", "2 小时前", "2 天前", "2 小时 10 分钟 后重置"),
        )
        val nowMs = 1_000_000_000L
        for ((loc, expected) in cases) {
            Locale.setDefault(Locale.forLanguageTag(loc))
            runComposeUiTest {
                setContent {
                    KitTheme(platform = DesktopKitPlatform(), localeTag = loc, stillFrame = true) {
                        Column {
                            KitText(kitElapsedSecondsMinutes(72))
                            KitText(kitTenthsSecondsWords("12.4"))
                            KitText(kitElapsedSecondsMinutes(13 * 60 + 36, KitDurationStyle.Compact, padSeconds = true))
                            KitText(kitRelativeAge(nowMs - 5 * 60_000L, nowMs))
                            KitText(kitRelativeAge(nowMs - 2 * 3_600_000L, nowMs))
                            KitText(kitRelativeAge(nowMs - 2 * 86_400_000L, nowMs))
                            KitText(kitResetInWords(2.hours + 10.minutes))
                        }
                    }
                }
                for (text in expected) {
                    onNodeWithText(text).assertExists()
                }
            }
        }
    }

    @Test
    fun `B-L10N-024 sub-minute duration fits standalone, for, ago, and reset-in shapes in every language`() {
        val cases = listOf(
            "en" to listOf("less than a minute", "Running for less than a minute", "Last line less than a minute ago", "resets in less than a minute"),
            "ar" to listOf("أقل من دقيقة", "تعمل منذ أقل من دقيقة", "آخر سطر منذ أقل من دقيقة", "يُعاد التعيين بعد أقل من دقيقة"),
            "es" to listOf("menos de un minuto", "En ejecución desde hace menos de un minuto", "Última línea hace menos de un minuto", "se reinicia en menos de un minuto"),
            "fr" to listOf("moins d’une minute", "En marche depuis moins d’une minute", "Dernière ligne il y a moins d’une minute", "réinitialisation dans moins d’une minute"),
            "id" to listOf("kurang dari semenit", "Berjalan selama kurang dari semenit", "Baris terakhir kurang dari semenit lalu", "diatur ulang dalam kurang dari semenit"),
            "pt" to listOf("menos de um minuto", "Em execução há menos de um minuto", "Última linha há menos de um minuto", "renova em menos de um minuto"),
            "ru" to listOf("несколько секунд", "Работает уже несколько секунд", "Последняя строка была несколько секунд назад", "сброс через несколько секунд"),
            "vi" to listOf("chưa đến một phút", "Đã chạy chưa đến một phút", "Dòng cuối cách đây chưa đến một phút", "đặt lại sau chưa đến một phút"),
            "zh" to listOf("不到一分钟", "已运行 不到一分钟", "最后一行为不到一分钟前", "不到一分钟 后重置"),
            "ja" to listOf("数秒", "数秒間実行中", "最後の行：数秒前", "あと数秒でリセット", "前回の返信：文字出力がないまま数秒後に終了"),
        )
        for ((loc, expected) in cases) {
            Locale.setDefault(Locale.forLanguageTag(loc))
            runComposeUiTest {
                setContent {
                    KitTheme(platform = DesktopKitPlatform(), localeTag = loc, stillFrame = true) {
                        val sub = kitDurationWords(15.seconds)
                        Column {
                            KitText(sub)
                            KitText(stringResource(Res.string.linuxSvcRunningFor, sub))
                            KitText(stringResource(Res.string.kitLogQuiet, sub))
                            KitText(stringResource(Res.string.s9LimitsResetsIn, sub))
                            if (loc == "ja") {
                                KitText(stringResource(Res.string.replySpeedNoWords, sub))
                            }
                        }
                    }
                }
                for (text in expected) {
                    onNodeWithText(text).assertExists()
                }
            }
        }
    }
}
