package io.github.eslamasabry.oc.engine.agents.errors

/** The two languages the Flutter app ships agent error wording in. */
public enum class AgentErrorLanguage { En, Ar }

/**
 * What to show for an agent error (Flutter `agentErrorWords`): a sentence a person can use and what happens next.
 * [humanized] is true when a cause was recognised, so it may override a typed error's general headline. All copy is
 * app-authored; server text belongs in Details.
 */
public data class AgentErrorMessage(public val headline: String, public val hint: String?, public val humanized: Boolean)

/**
 * EN + AR wording keyed by [AgentErrorCause], copied from `lib/l10n/app_en.arb` / `app_ar.arb` (`agentError*`). Kept in
 * this package for the parity port; the UI words module owns copy and should take this table over.
 */
public object AgentErrorWords {
    public fun forText(raw: String, language: AgentErrorLanguage): AgentErrorMessage {
        val cause = AgentErrorText.classify(raw)
            ?: return AgentErrorMessage(unknown(language).first, unknown(language).second, humanized = false)
        val (headline, hint) = forCause(cause, language)
        return AgentErrorMessage(headline, hint, humanized = true)
    }

    /** Headline and hint for a recognised [cause]. */
    public fun forCause(cause: AgentErrorCause, language: AgentErrorLanguage): Pair<String, String> =
        when (language) {
            AgentErrorLanguage.En -> en(cause)
            AgentErrorLanguage.Ar -> ar(cause)
        }

    /** The generic headline and hint for unrecognised text (never the text itself). */
    public fun unknown(language: AgentErrorLanguage): Pair<String, String> = when (language) {
        AgentErrorLanguage.En -> "The request could not finish." to
            "Check the conversation before sending again. Details may help explain what happened."
        AgentErrorLanguage.Ar -> "تعذّر إكمال الطلب." to
            "تحقّق من المحادثة قبل الإرسال مجددًا. قد تساعد التفاصيل في توضيح ما حدث."
    }

    private fun en(cause: AgentErrorCause): Pair<String, String> = when (cause) {
        AgentErrorCause.ConnectionDropped -> "The connection to the model dropped." to
            "Check the internet on the device running OpenCode. If the reply has stopped, check the conversation before sending again."
        AgentErrorCause.TimedOut -> "The model took too long to answer." to
            "If the reply has stopped, try a smaller request or another model."
        AgentErrorCause.ProviderUnreachable -> "The server couldn’t reach the model provider." to
            "Check the internet connection on the computer running OpenCode, then try again."
        AgentErrorCause.RequestTooLarge -> "This request was too large to send to the model." to
            "Send fewer or smaller attachments, or shorten the conversation, then try again."
        AgentErrorCause.RateLimited -> "The model provider is limiting how fast you can send." to
            "Wait a little. If the reply has stopped, try again or choose another model."
        AgentErrorCause.ProviderBusy -> "The model provider is overloaded right now." to
            "If the reply has stopped, try again later or choose another model."
        AgentErrorCause.OutOfCredit -> "The provider account is out of credit or quota." to
            "Top up the account, or switch to another provider or model."
        AgentErrorCause.FreeTierLimit -> "OpenCode's free models stopped this reply." to
            "Long conversations are often refused on the free tier, even when you've used little today. Shortening this one or starting a new one usually helps."
        AgentErrorCause.UsageLimit -> "This model's usage limit is reached for now." to
            "Choose another model to keep going, or continue after the limit resets."
        AgentErrorCause.ProviderAuth -> "The model provider needs a valid sign-in." to
            "Open provider settings to sign in or replace the API key, then try again."
        AgentErrorCause.ServerDiskFull -> "The computer running OpenCode is out of disk space." to
            "Free some space there, then try again."
    }

    private fun ar(cause: AgentErrorCause): Pair<String, String> = when (cause) {
        AgentErrorCause.ConnectionDropped -> "انقطع الاتصال بالنموذج." to
            "تحقّق من اتصال الإنترنت على الجهاز الذي يشغّل OpenCode. إذا توقف الرد، فتحقّق من المحادثة قبل الإرسال مجددًا."
        AgentErrorCause.TimedOut -> "استغرق النموذج وقتًا طويلًا للرد." to
            "إذا توقف الرد، فجرّب طلبًا أصغر أو نموذجًا آخر."
        AgentErrorCause.ProviderUnreachable -> "تعذّر على الخادم الوصول إلى مزوّد النموذج." to
            "تحقق من اتصال الإنترنت على الجهاز الذي يشغّل OpenCode ثم حاول مجددًا."
        AgentErrorCause.RequestTooLarge -> "كان هذا الطلب أكبر من أن يُرسل إلى النموذج." to
            "أرسل مرفقات أقل أو أصغر، أو قصّر المحادثة، ثم حاول مجددًا."
        AgentErrorCause.RateLimited -> "يحدّ مزوّد النموذج من سرعة الإرسال." to
            "انتظر قليلًا. إذا توقف الرد، فحاول مجددًا أو اختر نموذجًا آخر."
        AgentErrorCause.ProviderBusy -> "مزوّد النموذج مثقل بالطلبات الآن." to
            "إذا توقف الرد، فحاول مجددًا لاحقًا أو اختر نموذجًا آخر."
        AgentErrorCause.OutOfCredit -> "نفد رصيد حساب المزوّد أو حصته." to
            "اشحن الحساب، أو انتقل إلى مزوّد أو نموذج آخر."
        AgentErrorCause.FreeTierLimit -> "أوقفت نماذج OpenCode المجانية هذا الرد." to
            "كثيرًا ما تُرفض المحادثات الطويلة في الخطة المجانية حتى لو استخدمتَ القليل اليوم. غالبًا ما يفيد تقصير هذه المحادثة أو بدء محادثة جديدة."
        AgentErrorCause.UsageLimit -> "بلغ هذا النموذج حدّ الاستخدام في الوقت الحالي." to
            "اختر نموذجًا آخر لتتابع، أو تابع بعد أن يُعاد ضبط الحد."
        AgentErrorCause.ProviderAuth -> "يحتاج مزوّد النموذج إلى تسجيل دخول صالح." to
            "افتح إعدادات المزوّد لتسجيل الدخول أو استبدال مفتاح API، ثم حاول مجددًا."
        AgentErrorCause.ServerDiskFull -> "نفدت مساحة القرص على الجهاز الذي يشغّل OpenCode." to
            "حرّر بعض المساحة هناك ثم حاول مجددًا."
    }
}
