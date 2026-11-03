package io.github.eslamasabry.oc.feature.about.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.feature.about.presentation.ProvenanceLine

/**
 * TODO(words): copy `:ui:words` does not have yet (this unit may not edit `:ui:words`; the feature/settings
 * PendingWords pattern). English is the Flutter text (lib/ui/screens/about_screen.dart `buildProvenanceBody`), with
 * its Arabic. The l10n owner moves every line here into `:ui:words` (strings_about.xml, all languages) and deletes this
 * file. Every other sentence on these pages is already a `:ui:words` string.
 */
internal object AboutPendingWords {
    data class Line(val en: String, val ar: String)

    val noticesTitle = Line("Open source notices", "إشعارات المصادر المفتوحة")
    val noticesFailed = Line("The notices couldn't be loaded.", "تعذّر تحميل الإشعارات.")

    fun provenance(line: ProvenanceLine): Line =
        when (line) {
            ProvenanceLine.BuiltWithAi ->
                Line("This independent app is built heavily with AI assistance.", "هذا تطبيق مستقل بُني بمساعدة كبيرة من الذكاء الاصطناعي.")
            ProvenanceLine.AndroidPrimary ->
                Line("Android is the primary supported platform.", "أندرويد هو النظام الأساسي المدعوم.")
            ProvenanceLine.DesktopExperimental ->
                Line(
                    "Desktop builds are experimental and have not been hardware-tested.",
                    "نسخ سطح المكتب تجريبية ولم تُختبر على أجهزة حقيقية.",
                )
            ProvenanceLine.ReportWhatBreaks ->
                Line("Report what breaks to help improve the app.", "أبلغ عمّا يتعطل لتساعد في تحسين التطبيق.")
        }
}

/**
 * The line in the reader's language: Arabic when the app reads right to left (KitTheme sets the direction from the app
 * locale, and Arabic is the app's only right-to-left language), English otherwise; the other languages wait for
 * `:ui:words`. A feature may not read the locale itself (K1).
 */
@Composable
internal fun AboutPendingWords.Line.text(): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) ar else en
