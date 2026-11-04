package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanConnectionUi

/**
 * TODO(words): copy that `:ui:words` does not have yet (this unit may not edit `:ui:words`; the l10n owner moves these
 * into `strings_plans.xml` and deletes this file). English and Arabic, after the Flutter Usage › Plans copy
 * (`usage_plans_page.dart`, `app_en.arb`/`app_ar.arb`), in plain words. Nothing else in this module holds copy.
 */
public class PlansWords internal constructor(private val ar: Boolean) {
    private fun t(en: String, ar: String): String = if (this.ar) ar else en

    public companion object {
        public val English: PlansWords = PlansWords(false)
        public val Arabic: PlansWords = PlansWords(true)
    }

    val title: String get() = t("Plans and limits", "الخطط والحدود")
    val back: String get() = t("Back", "رجوع")
    val loading: String get() = t("Reading your plans", "جارٍ قراءة خططك")
    val refresh: String get() = t("Refresh", "تحديث")
    val refreshing: String get() = t("Refreshing", "جارٍ التحديث")
    val emptyTitle: String get() = t("No plans signed in yet", "لا توجد خطط مسجّلة بعد")
    val emptyBody: String
        get() = t(
            "Sign in to Codex, Claude, GLM or MiniMax on a server or on this phone to see what is left.",
            "سجّل الدخول إلى Codex أو Claude أو GLM أو MiniMax على خادم أو على هذا الهاتف لترى ما تبقّى.",
        )
    val keysLabel: String get() = t("API keys (no plan limits)", "مفاتيح API (بلا حدود خطة)")
    val addLabel: String get() = t("Not signed in anywhere", "غير مسجّل في أي مكان")
    val thisPhone: String get() = t("This phone", "هذا الهاتف")

    fun connection(c: PlanConnectionUi): String = if (c.isPhone || c.name.isBlank()) thisPhone else c.name

    fun cardTitle(name: String, connection: PlanConnectionUi?): String = if (connection == null) name else "$name · ${connection(connection)}"

    fun state(s: SubscriptionState): String =
        when (s) {
            SubscriptionState.Ok -> t("Plenty left", "متبقٍّ الكثير")
            SubscriptionState.NearLimit -> t("Getting close to the limit", "يقترب من الحد")
            SubscriptionState.Exhausted -> t("Limit reached", "تم بلوغ الحد")
            SubscriptionState.CantReadYet -> t("Signed in, but its numbers can't be read yet", "مسجّل، لكن لا يمكن قراءة أرقامه بعد")
            SubscriptionState.SignInExpired -> t("The sign-in ran out", "انتهت صلاحية تسجيل الدخول")
            SubscriptionState.NotSignedIn -> t("Not signed in", "غير مسجّل الدخول")
            SubscriptionState.ReaderBroken -> t("The numbers couldn't be read this time", "تعذّرت قراءة الأرقام هذه المرة")
            SubscriptionState.OfflineStale -> t("Offline. These are the last numbers.", "غير متصل. هذه آخر الأرقام.")
            SubscriptionState.NoLimits -> t("No limits reported", "لم يُبلَّغ عن حدود")
            SubscriptionState.ApiKeyOnly -> t("API key, no plan limits", "مفتاح API بلا حدود خطة")
        }

    fun fix(fix: UsageFix, where: PlanConnectionUi?): String? {
        val place = where?.let { connection(it) }
        return when (fix) {
            UsageFix.None -> null
            UsageFix.SignIn -> if (place == null) t("Sign in to see the numbers", "سجّل الدخول لرؤية الأرقام") else t("Sign in on $place to see the numbers", "سجّل الدخول على $place لرؤية الأرقام")
            UsageFix.AddKey -> if (place == null) t("Add the key to see the numbers", "أضف المفتاح لرؤية الأرقام") else t("Add the key on $place to see the numbers", "أضف المفتاح على $place لرؤية الأرقام")
            UsageFix.SetUp -> if (place == null) t("Set up reading to see the numbers", "اضبط القراءة لرؤية الأرقام") else t("Set up reading on $place to see the numbers", "اضبط القراءة على $place لرؤية الأرقام")
            UsageFix.TryAgain -> t("Try again", "حاول مرة أخرى")
        }
    }

    fun window(kind: UsageWindowKind, label: String): String =
        when (kind) {
            UsageWindowKind.FiveHour -> t("5 hours", "5 ساعات")
            UsageWindowKind.Weekly -> t("This week", "هذا الأسبوع")
            UsageWindowKind.Monthly -> t("This month", "هذا الشهر")
            UsageWindowKind.Other -> label.ifBlank { t("Limit", "الحد") }
        }

    fun left(percent: Int): String = t("$percent% left", "متبقٍّ $percent%")

    fun resetsIn(minutes: Long): String =
        when {
            minutes <= 0 -> t("resets now", "يُعاد الآن")
            minutes < 60 -> t("resets in $minutes min", "يُعاد بعد $minutes دقيقة")
            minutes < 48 * 60 -> t("resets in ${minutes / 60} h", "يُعاد بعد ${minutes / 60} ساعة")
            else -> t("resets in ${minutes / (24 * 60)} days", "يُعاد بعد ${minutes / (24 * 60)} يوم")
        }

    val resetSince: String get() = t("It has reset since these numbers were read.", "أُعيد ضبطه منذ قراءة هذه الأرقام.")

    fun plan(name: String): String = t("Plan: $name", "الخطة: $name")

    fun alertAt(percent: Int): String = t("Alert at $percent% used", "تنبيه عند استخدام $percent%")

    fun signedIn(connection: PlanConnectionUi, state: SignInState, online: Boolean, readsHere: Boolean): String {
        val place = connection(connection)
        val what =
            when {
                !online -> t("offline", "غير متصل")
                state == SignInState.Expired -> t("sign-in ran out", "انتهى تسجيل الدخول")
                state == SignInState.NotSignedIn -> t("not signed in", "غير مسجّل")
                readsHere -> t("numbers read here", "تُقرأ الأرقام من هنا")
                else -> t("signed in", "مسجّل")
            }
        return "$place: $what"
    }

    fun providerName(id: String): String =
        when (id) {
            "codex" -> "Codex"
            "claude" -> "Claude"
            "glm" -> "GLM"
            "minimax" -> "MiniMax"
            else -> id
        }

    val viaOpenCode: String
        get() = t("Claude's plan limits can't be read through OpenCode", "لا يمكن قراءة حدود خطة Claude عبر OpenCode")
}

@Composable
public fun plansWords(): PlansWords = if (LocalLayoutDirection.current == LayoutDirection.Rtl) PlansWords.Arabic else PlansWords.English
