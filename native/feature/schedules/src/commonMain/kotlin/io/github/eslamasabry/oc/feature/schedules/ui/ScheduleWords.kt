package io.github.eslamasabry.oc.feature.schedules.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.engine.schedules.ScheduleCode
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.schedule40Active
import io.github.eslamasabry.oc.ui.words.res.schedule40Back
import io.github.eslamasabry.oc.ui.words.res.schedule40Cancel
import io.github.eslamasabry.oc.ui.words.res.schedule40Continues
import io.github.eslamasabry.oc.ui.words.res.schedule40ContinuesTitle
import io.github.eslamasabry.oc.ui.words.res.schedule40Conversation
import io.github.eslamasabry.oc.ui.words.res.schedule40Daily
import io.github.eslamasabry.oc.ui.words.res.schedule40Date
import io.github.eslamasabry.oc.ui.words.res.schedule40Delete
import io.github.eslamasabry.oc.ui.words.res.schedule40DeleteBody
import io.github.eslamasabry.oc.ui.words.res.schedule40DeleteTitle
import io.github.eslamasabry.oc.ui.words.res.schedule40Digest
import io.github.eslamasabry.oc.ui.words.res.schedule40DigestBody
import io.github.eslamasabry.oc.ui.words.res.schedule40Edit
import io.github.eslamasabry.oc.ui.words.res.schedule40EditTitle
import io.github.eslamasabry.oc.ui.words.res.schedule40EmptyBody
import io.github.eslamasabry.oc.ui.words.res.schedule40EmptyTitle
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorExpired
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorInvalid
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorLimit
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorMissing
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorSave
import io.github.eslamasabry.oc.ui.words.res.schedule40ErrorStale
import io.github.eslamasabry.oc.ui.words.res.schedule40EveryHour
import io.github.eslamasabry.oc.ui.words.res.schedule40EveryHours
import io.github.eslamasabry.oc.ui.words.res.schedule40FirstAt
import io.github.eslamasabry.oc.ui.words.res.schedule40Friday
import io.github.eslamasabry.oc.ui.words.res.schedule40Hours
import io.github.eslamasabry.oc.ui.words.res.schedule40IssueDate
import io.github.eslamasabry.oc.ui.words.res.schedule40IssueHours
import io.github.eslamasabry.oc.ui.words.res.schedule40IssueProject
import io.github.eslamasabry.oc.ui.words.res.schedule40IssuePrompt
import io.github.eslamasabry.oc.ui.words.res.schedule40IssueTime
import io.github.eslamasabry.oc.ui.words.res.schedule40KindDaily
import io.github.eslamasabry.oc.ui.words.res.schedule40KindInterval
import io.github.eslamasabry.oc.ui.words.res.schedule40KindOnce
import io.github.eslamasabry.oc.ui.words.res.schedule40KindWeekdays
import io.github.eslamasabry.oc.ui.words.res.schedule40LastFailed
import io.github.eslamasabry.oc.ui.words.res.schedule40LastRan
import io.github.eslamasabry.oc.ui.words.res.schedule40LastRunning
import io.github.eslamasabry.oc.ui.words.res.schedule40LastSkipped
import io.github.eslamasabry.oc.ui.words.res.schedule40LastStarted
import io.github.eslamasabry.oc.ui.words.res.schedule40Late
import io.github.eslamasabry.oc.ui.words.res.schedule40Loading
import io.github.eslamasabry.oc.ui.words.res.schedule40Missed
import io.github.eslamasabry.oc.ui.words.res.schedule40MissedCatchUp
import io.github.eslamasabry.oc.ui.words.res.schedule40MissedSkip
import io.github.eslamasabry.oc.ui.words.res.schedule40Model
import io.github.eslamasabry.oc.ui.words.res.schedule40ModelDefault
import io.github.eslamasabry.oc.ui.words.res.schedule40Monday
import io.github.eslamasabry.oc.ui.words.res.schedule40New
import io.github.eslamasabry.oc.ui.words.res.schedule40NewEachTime
import io.github.eslamasabry.oc.ui.words.res.schedule40Next
import io.github.eslamasabry.oc.ui.words.res.schedule40Once
import io.github.eslamasabry.oc.ui.words.res.schedule40OpenConversation
import io.github.eslamasabry.oc.ui.words.res.schedule40Over
import io.github.eslamasabry.oc.ui.words.res.schedule40Paused
import io.github.eslamasabry.oc.ui.words.res.schedule40Project
import io.github.eslamasabry.oc.ui.words.res.schedule40Prompt
import io.github.eslamasabry.oc.ui.words.res.schedule40PromptHelp
import io.github.eslamasabry.oc.ui.words.res.schedule40Repeat
import io.github.eslamasabry.oc.ui.words.res.schedule40RunNow
import io.github.eslamasabry.oc.ui.words.res.schedule40Saturday
import io.github.eslamasabry.oc.ui.words.res.schedule40Save
import io.github.eslamasabry.oc.ui.words.res.schedule40Saving
import io.github.eslamasabry.oc.ui.words.res.schedule40Started
import io.github.eslamasabry.oc.ui.words.res.schedule40Sunday
import io.github.eslamasabry.oc.ui.words.res.schedule40Thursday
import io.github.eslamasabry.oc.ui.words.res.schedule40Time
import io.github.eslamasabry.oc.ui.words.res.schedule40Title
import io.github.eslamasabry.oc.ui.words.res.schedule40Today
import io.github.eslamasabry.oc.ui.words.res.schedule40Tomorrow
import io.github.eslamasabry.oc.ui.words.res.schedule40TryAgain
import io.github.eslamasabry.oc.ui.words.res.schedule40Tuesday
import io.github.eslamasabry.oc.ui.words.res.schedule40UnavailableEdit
import io.github.eslamasabry.oc.ui.words.res.schedule40UnavailablePhone
import io.github.eslamasabry.oc.ui.words.res.schedule40UnavailableServer
import io.github.eslamasabry.oc.ui.words.res.schedule40UnavailableServerBody
import io.github.eslamasabry.oc.ui.words.res.schedule40UnavailableStorage
import io.github.eslamasabry.oc.ui.words.res.schedule40Wednesday
import io.github.eslamasabry.oc.ui.words.res.schedule40Weekdays
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyAgentFailed
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyAuth
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyBusy
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyCertificate
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyCleartext
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyMissed
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyNeedsYou
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyNetwork
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyStaleTarget
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyUncertain
import io.github.eslamasabry.oc.ui.words.res.schedule40WhyWrongProject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A pure snapshot of the app's acknowledged localized schedule templates. */
public class ScheduleWords internal constructor(private val templates: Map<ScheduleWord, String>) {
    /** Kept for resource-independent callers of the original pure API. */
    internal constructor(ar: Boolean) : this(compatibilityTemplates(ar))

    private fun word(key: ScheduleWord, vararg args: Any): String {
        var text = templates.getValue(key)
        args.forEachIndexed { index, value -> text = text.replace("%${index + 1}\$s", value.toString()) }
        return text
    }

    val title: String get() = word(ScheduleWord.Title)
    val back: String get() = word(ScheduleWord.Back)
    val new: String get() = word(ScheduleWord.New)
    val loading: String get() = word(ScheduleWord.Loading)
    val emptyTitle: String get() = word(ScheduleWord.EmptyTitle)
    val emptyBody: String get() = word(ScheduleWord.EmptyBody)
    val unavailableServer: String get() = word(ScheduleWord.UnavailableServer)
    val unavailableServerBody: String
        get() = word(ScheduleWord.UnavailableServerBody)
    val unavailablePhone: String get() = word(ScheduleWord.UnavailablePhone)
    val unavailableStorage: String
        get() = word(ScheduleWord.UnavailableStorage)
    val tryAgain: String get() = word(ScheduleWord.TryAgain)

    fun once(moment: String): String = word(ScheduleWord.Once, moment)
    fun daily(time: String): String = word(ScheduleWord.Daily, time)
    fun weekdays(time: String): String = word(ScheduleWord.Weekdays, time)
    fun everyHours(hours: Int): String =
        if (hours == 1) word(ScheduleWord.EveryHour) else word(ScheduleWord.EveryHours, hours)
    fun next(moment: String): String = word(ScheduleWord.Next, moment)
    val paused: String get() = word(ScheduleWord.Paused)
    val over: String get() = word(ScheduleWord.Over)
    val continues: String get() = word(ScheduleWord.Continues)
    val active: String get() = word(ScheduleWord.Active)
    val runNow: String get() = word(ScheduleWord.RunNow)
    val edit: String get() = word(ScheduleWord.Edit)
    val delete: String get() = word(ScheduleWord.Delete)
    val cancel: String get() = word(ScheduleWord.Cancel)
    val deleteTitle: String get() = word(ScheduleWord.DeleteTitle)
    val deleteBody: String
        get() = word(ScheduleWord.DeleteBody)
    val started: String
        get() = word(ScheduleWord.Started)
    val digest: String get() = word(ScheduleWord.Digest)
    val digestBody: String
        get() = word(ScheduleWord.DigestBody)
    val openConversation: String get() = word(ScheduleWord.OpenConversation)
    val today: String get() = word(ScheduleWord.Today)
    val tomorrow: String get() = word(ScheduleWord.Tomorrow)
    val weekdayNames: List<String>
        get() =
            listOf(
                ScheduleWord.Monday,
                ScheduleWord.Tuesday,
                ScheduleWord.Wednesday,
                ScheduleWord.Thursday,
                ScheduleWord.Friday,
                ScheduleWord.Saturday,
                ScheduleWord.Sunday,
            ).map { word(it) }

    fun lastRan(moment: String): String = word(ScheduleWord.LastRan, moment)
    fun lastStarted(moment: String): String = word(ScheduleWord.LastStarted, moment)
    fun lastSkipped(moment: String): String = word(ScheduleWord.LastSkipped, moment)
    fun lastFailed(moment: String): String = word(ScheduleWord.LastFailed, moment)
    fun lastRunning(moment: String): String = word(ScheduleWord.LastRunning, moment)
    val late: String get() = word(ScheduleWord.Late)

    /** B-NAV-055: what happened and what to do next, for each fixed run code. */
    fun why(code: String?): String =
        when (code) {
            null -> ""
            "needs_you", "permission_required" ->
                word(ScheduleWord.WhyNeedsYou)
            "auth_required" ->
                word(ScheduleWord.WhyAuth)
            "agent_failed" ->
                word(ScheduleWord.WhyAgentFailed)
            "timeout", "uncertain_delivery", "interrupted" ->
                word(ScheduleWord.WhyUncertain)
            "busy" -> word(ScheduleWord.WhyBusy)
            "stale_target" -> word(ScheduleWord.WhyStaleTarget)
            "wrong_project" ->
                word(ScheduleWord.WhyWrongProject)
            "missed" -> word(ScheduleWord.WhyMissed)
            "unsupported" -> unavailableServer
            "certificate_invalid", "certificate_changed" ->
                word(ScheduleWord.WhyCertificate)
            "cleartext_unconfirmed" ->
                word(ScheduleWord.WhyCleartext)
            else -> word(ScheduleWord.WhyNetwork)
        }

    fun error(code: ScheduleCode): String =
        when (code) {
            ScheduleCode.ScheduleLimit -> word(ScheduleWord.ErrorLimit)
            ScheduleCode.StaleSchedule -> word(ScheduleWord.ErrorStale)
            ScheduleCode.ScheduleMissing -> word(ScheduleWord.ErrorMissing)
            ScheduleCode.ScheduleExpired -> word(ScheduleWord.ErrorExpired)
            ScheduleCode.Unsupported -> unavailablePhone
            ScheduleCode.InvalidSchedule, ScheduleCode.InvalidProfile ->
                word(ScheduleWord.ErrorInvalid)
            else -> word(ScheduleWord.ErrorSave)
        }

    // ---- Editor
    val editTitle: String get() = word(ScheduleWord.EditTitle)
    val prompt: String get() = word(ScheduleWord.Prompt)
    val promptHelp: String get() = word(ScheduleWord.PromptHelp)
    val project: String get() = word(ScheduleWord.Project)
    val repeat: String get() = word(ScheduleWord.Repeat)
    val kindOnce: String get() = word(ScheduleWord.KindOnce)
    val kindDaily: String get() = word(ScheduleWord.KindDaily)
    val kindWeekdays: String get() = word(ScheduleWord.KindWeekdays)
    val kindInterval: String get() = word(ScheduleWord.KindInterval)
    val time: String get() = word(ScheduleWord.Time)
    val date: String get() = word(ScheduleWord.Date)
    val hours: String get() = word(ScheduleWord.Hours)
    val firstAt: String get() = word(ScheduleWord.FirstAt)
    val missed: String get() = word(ScheduleWord.Missed)
    val missedCatchUp: String get() = word(ScheduleWord.MissedCatchUp)
    val missedSkip: String get() = word(ScheduleWord.MissedSkip)
    val model: String get() = word(ScheduleWord.Model)
    val modelDefault: String get() = word(ScheduleWord.ModelDefault)
    val conversation: String get() = word(ScheduleWord.Conversation)
    val newEachTime: String get() = word(ScheduleWord.NewEachTime)
    fun continuesTitle(title: String?): String =
        if (title.isNullOrBlank()) continues else word(ScheduleWord.ContinuesTitle, title)
    val save: String get() = word(ScheduleWord.Save)
    val saving: String get() = word(ScheduleWord.Saving)
    val unavailableEdit: String get() = word(ScheduleWord.UnavailableEdit)
    val issuePrompt: String get() = word(ScheduleWord.IssuePrompt)
    val issueProject: String get() = word(ScheduleWord.IssueProject)
    val issueTime: String get() = word(ScheduleWord.IssueTime)
    val issueDate: String get() = word(ScheduleWord.IssueDate)
    val issueHours: String get() = word(ScheduleWord.IssueHours)

    public companion object {
        public val English: ScheduleWords by lazy { ScheduleWords(ar = false) }
        public val Arabic: ScheduleWords by lazy { ScheduleWords(ar = true) }
    }
}

/** Resource selection follows the app locale, including fallback locales; layout direction does not select copy. */
@Composable
internal fun resourceScheduleWords(): ScheduleWords {
    val templates = ScheduleWord.entries.associateWith { stringResource(it.resource()) }
    return remember(templates) { ScheduleWords(templates) }
}

internal enum class ScheduleWord {
    Title,
    Back,
    New,
    Loading,
    EmptyTitle,
    EmptyBody,
    UnavailableServer,
    UnavailableServerBody,
    UnavailablePhone,
    UnavailableStorage,
    TryAgain,
    Once,
    Daily,
    Weekdays,
    EveryHour,
    EveryHours,
    Next,
    Paused,
    Over,
    Continues,
    Active,
    RunNow,
    Edit,
    Delete,
    Cancel,
    DeleteTitle,
    DeleteBody,
    Started,
    Digest,
    DigestBody,
    OpenConversation,
    Today,
    Tomorrow,
    LastRan,
    LastStarted,
    LastSkipped,
    LastFailed,
    LastRunning,
    Late,
    WhyNeedsYou,
    WhyAuth,
    WhyAgentFailed,
    WhyUncertain,
    WhyBusy,
    WhyStaleTarget,
    WhyWrongProject,
    WhyMissed,
    WhyCertificate,
    WhyCleartext,
    WhyNetwork,
    ErrorLimit,
    ErrorStale,
    ErrorMissing,
    ErrorExpired,
    ErrorInvalid,
    ErrorSave,
    EditTitle,
    Prompt,
    PromptHelp,
    Project,
    Repeat,
    KindOnce,
    KindDaily,
    KindWeekdays,
    KindInterval,
    Time,
    Date,
    Hours,
    FirstAt,
    Missed,
    MissedCatchUp,
    MissedSkip,
    Model,
    ModelDefault,
    Conversation,
    NewEachTime,
    ContinuesTitle,
    Save,
    Saving,
    UnavailableEdit,
    IssuePrompt,
    IssueProject,
    IssueTime,
    IssueDate,
    IssueHours,
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday,
}

private fun ScheduleWord.resource(): StringResource = when (this) {
    ScheduleWord.Title -> Res.string.schedule40Title
    ScheduleWord.Back -> Res.string.schedule40Back
    ScheduleWord.New -> Res.string.schedule40New
    ScheduleWord.Loading -> Res.string.schedule40Loading
    ScheduleWord.EmptyTitle -> Res.string.schedule40EmptyTitle
    ScheduleWord.EmptyBody -> Res.string.schedule40EmptyBody
    ScheduleWord.UnavailableServer -> Res.string.schedule40UnavailableServer
    ScheduleWord.UnavailableServerBody -> Res.string.schedule40UnavailableServerBody
    ScheduleWord.UnavailablePhone -> Res.string.schedule40UnavailablePhone
    ScheduleWord.UnavailableStorage -> Res.string.schedule40UnavailableStorage
    ScheduleWord.TryAgain -> Res.string.schedule40TryAgain
    ScheduleWord.Once -> Res.string.schedule40Once
    ScheduleWord.Daily -> Res.string.schedule40Daily
    ScheduleWord.Weekdays -> Res.string.schedule40Weekdays
    ScheduleWord.EveryHour -> Res.string.schedule40EveryHour
    ScheduleWord.EveryHours -> Res.string.schedule40EveryHours
    ScheduleWord.Next -> Res.string.schedule40Next
    ScheduleWord.Paused -> Res.string.schedule40Paused
    ScheduleWord.Over -> Res.string.schedule40Over
    ScheduleWord.Continues -> Res.string.schedule40Continues
    ScheduleWord.Active -> Res.string.schedule40Active
    ScheduleWord.RunNow -> Res.string.schedule40RunNow
    ScheduleWord.Edit -> Res.string.schedule40Edit
    ScheduleWord.Delete -> Res.string.schedule40Delete
    ScheduleWord.Cancel -> Res.string.schedule40Cancel
    ScheduleWord.DeleteTitle -> Res.string.schedule40DeleteTitle
    ScheduleWord.DeleteBody -> Res.string.schedule40DeleteBody
    ScheduleWord.Started -> Res.string.schedule40Started
    ScheduleWord.Digest -> Res.string.schedule40Digest
    ScheduleWord.DigestBody -> Res.string.schedule40DigestBody
    ScheduleWord.OpenConversation -> Res.string.schedule40OpenConversation
    ScheduleWord.Today -> Res.string.schedule40Today
    ScheduleWord.Tomorrow -> Res.string.schedule40Tomorrow
    ScheduleWord.LastRan -> Res.string.schedule40LastRan
    ScheduleWord.LastStarted -> Res.string.schedule40LastStarted
    ScheduleWord.LastSkipped -> Res.string.schedule40LastSkipped
    ScheduleWord.LastFailed -> Res.string.schedule40LastFailed
    ScheduleWord.LastRunning -> Res.string.schedule40LastRunning
    ScheduleWord.Late -> Res.string.schedule40Late
    ScheduleWord.WhyNeedsYou -> Res.string.schedule40WhyNeedsYou
    ScheduleWord.WhyAuth -> Res.string.schedule40WhyAuth
    ScheduleWord.WhyAgentFailed -> Res.string.schedule40WhyAgentFailed
    ScheduleWord.WhyUncertain -> Res.string.schedule40WhyUncertain
    ScheduleWord.WhyBusy -> Res.string.schedule40WhyBusy
    ScheduleWord.WhyStaleTarget -> Res.string.schedule40WhyStaleTarget
    ScheduleWord.WhyWrongProject -> Res.string.schedule40WhyWrongProject
    ScheduleWord.WhyMissed -> Res.string.schedule40WhyMissed
    ScheduleWord.WhyCertificate -> Res.string.schedule40WhyCertificate
    ScheduleWord.WhyCleartext -> Res.string.schedule40WhyCleartext
    ScheduleWord.WhyNetwork -> Res.string.schedule40WhyNetwork
    ScheduleWord.ErrorLimit -> Res.string.schedule40ErrorLimit
    ScheduleWord.ErrorStale -> Res.string.schedule40ErrorStale
    ScheduleWord.ErrorMissing -> Res.string.schedule40ErrorMissing
    ScheduleWord.ErrorExpired -> Res.string.schedule40ErrorExpired
    ScheduleWord.ErrorInvalid -> Res.string.schedule40ErrorInvalid
    ScheduleWord.ErrorSave -> Res.string.schedule40ErrorSave
    ScheduleWord.EditTitle -> Res.string.schedule40EditTitle
    ScheduleWord.Prompt -> Res.string.schedule40Prompt
    ScheduleWord.PromptHelp -> Res.string.schedule40PromptHelp
    ScheduleWord.Project -> Res.string.schedule40Project
    ScheduleWord.Repeat -> Res.string.schedule40Repeat
    ScheduleWord.KindOnce -> Res.string.schedule40KindOnce
    ScheduleWord.KindDaily -> Res.string.schedule40KindDaily
    ScheduleWord.KindWeekdays -> Res.string.schedule40KindWeekdays
    ScheduleWord.KindInterval -> Res.string.schedule40KindInterval
    ScheduleWord.Time -> Res.string.schedule40Time
    ScheduleWord.Date -> Res.string.schedule40Date
    ScheduleWord.Hours -> Res.string.schedule40Hours
    ScheduleWord.FirstAt -> Res.string.schedule40FirstAt
    ScheduleWord.Missed -> Res.string.schedule40Missed
    ScheduleWord.MissedCatchUp -> Res.string.schedule40MissedCatchUp
    ScheduleWord.MissedSkip -> Res.string.schedule40MissedSkip
    ScheduleWord.Model -> Res.string.schedule40Model
    ScheduleWord.ModelDefault -> Res.string.schedule40ModelDefault
    ScheduleWord.Conversation -> Res.string.schedule40Conversation
    ScheduleWord.NewEachTime -> Res.string.schedule40NewEachTime
    ScheduleWord.ContinuesTitle -> Res.string.schedule40ContinuesTitle
    ScheduleWord.Save -> Res.string.schedule40Save
    ScheduleWord.Saving -> Res.string.schedule40Saving
    ScheduleWord.UnavailableEdit -> Res.string.schedule40UnavailableEdit
    ScheduleWord.IssuePrompt -> Res.string.schedule40IssuePrompt
    ScheduleWord.IssueProject -> Res.string.schedule40IssueProject
    ScheduleWord.IssueTime -> Res.string.schedule40IssueTime
    ScheduleWord.IssueDate -> Res.string.schedule40IssueDate
    ScheduleWord.IssueHours -> Res.string.schedule40IssueHours
    ScheduleWord.Monday -> Res.string.schedule40Monday
    ScheduleWord.Tuesday -> Res.string.schedule40Tuesday
    ScheduleWord.Wednesday -> Res.string.schedule40Wednesday
    ScheduleWord.Thursday -> Res.string.schedule40Thursday
    ScheduleWord.Friday -> Res.string.schedule40Friday
    ScheduleWord.Saturday -> Res.string.schedule40Saturday
    ScheduleWord.Sunday -> Res.string.schedule40Sunday
}

/** Compatibility snapshots for pure callers. Production always constructs its snapshot from resources above. */
private fun compatibilityTemplates(ar: Boolean): Map<ScheduleWord, String> =
    if (ar) mapOf(
        ScheduleWord.Title to "الطلبات المجدولة",
        ScheduleWord.Back to "رجوع",
        ScheduleWord.New to "طلب مجدول جديد",
        ScheduleWord.Loading to "جارٍ تحميل الطلبات المجدولة",
        ScheduleWord.EmptyTitle to "لا توجد طلبات مجدولة بعد",
        ScheduleWord.EmptyBody to "أرسل طلبًا لاحقًا، أو كرّره كل يوم.",
        ScheduleWord.UnavailableServer to "هذا الخادم لا يستطيع تشغيل الطلبات المجدولة.",
        ScheduleWord.UnavailableServerBody to "اتصل به من صفحة الخوادم ثم ارجع إلى هنا.",
        ScheduleWord.UnavailablePhone to "هذا الهاتف لا يستطيع جدولة الطلبات.",
        ScheduleWord.UnavailableStorage to "تعذّرت قراءة الجداول المحفوظة على هذا الهاتف.",
        ScheduleWord.TryAgain to "حاول مرة أخرى",
        ScheduleWord.Once to "مرة واحدة، %1\$s",
        ScheduleWord.Daily to "كل يوم الساعة %1\$s",
        ScheduleWord.Weekdays to "أيام العمل الساعة %1\$s",
        ScheduleWord.EveryHour to "كل ساعة",
        ScheduleWord.EveryHours to "كل %1\$s ساعات",
        ScheduleWord.Next to "التالي: %1\$s",
        ScheduleWord.Paused to "متوقف مؤقتًا",
        ScheduleWord.Over to "انتهى، لا مواعيد أخرى",
        ScheduleWord.Continues to "يكمل محادثة واحدة",
        ScheduleWord.Active to "مفعّل",
        ScheduleWord.RunNow to "شغّل الآن",
        ScheduleWord.Edit to "تعديل",
        ScheduleWord.Delete to "حذف",
        ScheduleWord.Cancel to "إلغاء",
        ScheduleWord.DeleteTitle to "حذف هذا الطلب المجدول؟",
        ScheduleWord.DeleteBody to "سيُحذف سجلّه أيضًا. تبقى المحادثات التي بدأها.",
        ScheduleWord.Started to "بدأ. سيصلك إشعار عند انتهائه.",
        ScheduleWord.Digest to "ملخص الصباح",
        ScheduleWord.DigestBody to "إشعار واحد كل صباح بما تم تشغيله.",
        ScheduleWord.OpenConversation to "افتح المحادثة",
        ScheduleWord.Today to "اليوم",
        ScheduleWord.Tomorrow to "غدًا",
        ScheduleWord.LastRan to "آخر تشغيل %1\$s: تم الرد",
        ScheduleWord.LastStarted to "آخر تشغيل %1\$s: بدأ",
        ScheduleWord.LastSkipped to "آخر تشغيل %1\$s: تم التخطي",
        ScheduleWord.LastFailed to "آخر تشغيل %1\$s: لم يكتمل",
        ScheduleWord.LastRunning to "يعمل منذ %1\$s",
        ScheduleWord.Late to "تأخر",
        ScheduleWord.WhyNeedsYou to "ينتظر ردّك. افتح المحادثة للمتابعة.",
        ScheduleWord.WhyAuth to "طلب الخادم تسجيل الدخول مجددًا. راجع كلمة المرور المحفوظة في الخوادم.",
        ScheduleWord.WhyAgentFailed to "توقف الوكيل بخطأ. افتح المحادثة لترى ما حدث.",
        ScheduleWord.WhyUncertain to "لم يكتمل في الوقت المحدد. لم يُرسل مرة أخرى؛ راجع المحادثة قبل تشغيله مجددًا.",
        ScheduleWord.WhyBusy to "كانت المحادثة مشغولة، فتم تخطي هذه المرة.",
        ScheduleWord.WhyStaleTarget to "تغيّر الجدول أو خادمه، فتم تخطي هذه المرة.",
        ScheduleWord.WhyWrongProject to "المحادثة في مشروع آخر. عدّل الجدول لاختيارها مجددًا.",
        ScheduleWord.WhyMissed to "كان الهاتف مغلقًا أو نائمًا في ذلك الوقت، فتم التخطي.",
        ScheduleWord.WhyCertificate to "شهادة الخادم غير موثوقة. راجع الخادم في الخوادم.",
        ScheduleWord.WhyCleartext to "يحتاج الخادم موافقتك على اتصال غير مشفّر. راجعه في الخوادم.",
        ScheduleWord.WhyNetwork to "تعذّر الوصول إلى الخادم. سيحاول مجددًا في المرة القادمة.",
        ScheduleWord.ErrorLimit to "لدى هذا الخادم 100 جدول بالفعل. احذف واحدًا أولًا.",
        ScheduleWord.ErrorStale to "تغيّر هذا الجدول في مكان آخر. افتحه مجددًا للتعديل.",
        ScheduleWord.ErrorMissing to "تم حذف هذا الجدول.",
        ScheduleWord.ErrorExpired to "مضى ذلك الوقت. اختر وقتًا في المستقبل.",
        ScheduleWord.ErrorInvalid to "راجع الطلب والمشروع والوقت.",
        ScheduleWord.ErrorSave to "تعذّر الحفظ على هذا الهاتف. حاول مرة أخرى.",
        ScheduleWord.EditTitle to "تعديل الطلب المجدول",
        ScheduleWord.Prompt to "الطلب",
        ScheduleWord.PromptHelp to "يُرسل كما كُتب تمامًا، في الوقت الذي تختاره.",
        ScheduleWord.Project to "مجلد المشروع",
        ScheduleWord.Repeat to "التكرار",
        ScheduleWord.KindOnce to "مرة",
        ScheduleWord.KindDaily to "يوميًا",
        ScheduleWord.KindWeekdays to "أيام العمل",
        ScheduleWord.KindInterval to "كل بضع ساعات",
        ScheduleWord.Time to "الوقت (24 ساعة، مثل 09:30)",
        ScheduleWord.Date to "التاريخ (مثل 2026-11-04)",
        ScheduleWord.Hours to "كل كم ساعة (من 1 إلى 168)",
        ScheduleWord.FirstAt to "بدءًا من هذا الوقت اليوم",
        ScheduleWord.Missed to "إذا كان الهاتف مغلقًا في ذلك الوقت",
        ScheduleWord.MissedCatchUp to "شغّله مرة عندما يمكن",
        ScheduleWord.MissedSkip to "تخطَّ ذلك الوقت",
        ScheduleWord.Model to "النموذج",
        ScheduleWord.ModelDefault to "الافتراضي في الخادم",
        ScheduleWord.Conversation to "المحادثة",
        ScheduleWord.NewEachTime to "محادثة جديدة كل مرة",
        ScheduleWord.ContinuesTitle to "يكمل «%1\$s»",
        ScheduleWord.Save to "احفظ الجدول",
        ScheduleWord.Saving to "جارٍ الحفظ",
        ScheduleWord.UnavailableEdit to "لا يمكن تعديل هذا الجدول هنا.",
        ScheduleWord.IssuePrompt to "اكتب الطلب أولًا.",
        ScheduleWord.IssueProject to "اختر مجلد المشروع.",
        ScheduleWord.IssueTime to "اكتب الوقت مثل 09:30.",
        ScheduleWord.IssueDate to "اكتب التاريخ مثل 2026-11-04.",
        ScheduleWord.IssueHours to "اكتب عدد ساعات من 1 إلى 168.",
        ScheduleWord.Monday to "الاثنين",
        ScheduleWord.Tuesday to "الثلاثاء",
        ScheduleWord.Wednesday to "الأربعاء",
        ScheduleWord.Thursday to "الخميس",
        ScheduleWord.Friday to "الجمعة",
        ScheduleWord.Saturday to "السبت",
        ScheduleWord.Sunday to "الأحد",
    ) else mapOf(
        ScheduleWord.Title to "Scheduled prompts",
        ScheduleWord.Back to "Back",
        ScheduleWord.New to "New scheduled prompt",
        ScheduleWord.Loading to "Loading scheduled prompts",
        ScheduleWord.EmptyTitle to "No scheduled prompts yet",
        ScheduleWord.EmptyBody to "Send a prompt later, or repeat it every day.",
        ScheduleWord.UnavailableServer to "This server can't run scheduled prompts.",
        ScheduleWord.UnavailableServerBody to "Connect to it from Servers, then come back here.",
        ScheduleWord.UnavailablePhone to "This phone can't schedule prompts.",
        ScheduleWord.UnavailableStorage to "Saved schedules couldn't be read on this phone.",
        ScheduleWord.TryAgain to "Try again",
        ScheduleWord.Once to "Once, %1\$s",
        ScheduleWord.Daily to "Every day at %1\$s",
        ScheduleWord.Weekdays to "Weekdays at %1\$s",
        ScheduleWord.EveryHour to "Every hour",
        ScheduleWord.EveryHours to "Every %1\$s hours",
        ScheduleWord.Next to "Next: %1\$s",
        ScheduleWord.Paused to "Paused",
        ScheduleWord.Over to "Done, no more times",
        ScheduleWord.Continues to "Continues one conversation",
        ScheduleWord.Active to "On",
        ScheduleWord.RunNow to "Run now",
        ScheduleWord.Edit to "Edit",
        ScheduleWord.Delete to "Delete",
        ScheduleWord.Cancel to "Cancel",
        ScheduleWord.DeleteTitle to "Delete this scheduled prompt?",
        ScheduleWord.DeleteBody to "Its history goes too. Conversations it started stay.",
        ScheduleWord.Started to "Started. You'll get a notification when it finishes.",
        ScheduleWord.Digest to "Morning summary",
        ScheduleWord.DigestBody to "One notification each morning with what ran.",
        ScheduleWord.OpenConversation to "Open conversation",
        ScheduleWord.Today to "Today",
        ScheduleWord.Tomorrow to "Tomorrow",
        ScheduleWord.LastRan to "Last run %1\$s: replied",
        ScheduleWord.LastStarted to "Last run %1\$s: started",
        ScheduleWord.LastSkipped to "Last run %1\$s: skipped",
        ScheduleWord.LastFailed to "Last run %1\$s: didn't finish",
        ScheduleWord.LastRunning to "Running since %1\$s",
        ScheduleWord.Late to "ran late",
        ScheduleWord.WhyNeedsYou to "It's waiting for your answer. Open the conversation to continue.",
        ScheduleWord.WhyAuth to "The server asked to sign in again. Check the saved password in Servers.",
        ScheduleWord.WhyAgentFailed to "The agent stopped with an error. Open the conversation to see what happened.",
        ScheduleWord.WhyUncertain to "It didn't finish in time. It was not sent again; check the conversation before running it again.",
        ScheduleWord.WhyBusy to "The conversation was busy, so this time was skipped.",
        ScheduleWord.WhyStaleTarget to "The schedule or its server changed, so this time was skipped.",
        ScheduleWord.WhyWrongProject to "The conversation is in another project. Edit the schedule to pick it again.",
        ScheduleWord.WhyMissed to "The phone was off or asleep at that time, so it was skipped.",
        ScheduleWord.WhyCertificate to "The server's certificate isn't trusted. Check the server in Servers.",
        ScheduleWord.WhyCleartext to "The server needs your OK for an unencrypted connection. Check it in Servers.",
        ScheduleWord.WhyNetwork to "The server couldn't be reached. It will try again next time.",
        ScheduleWord.ErrorLimit to "This server already has 100 schedules. Delete one first.",
        ScheduleWord.ErrorStale to "This schedule changed somewhere else. Open it again to edit.",
        ScheduleWord.ErrorMissing to "This schedule was deleted.",
        ScheduleWord.ErrorExpired to "That time has passed. Pick a time in the future.",
        ScheduleWord.ErrorInvalid to "Check the prompt, project and time.",
        ScheduleWord.ErrorSave to "Couldn't save on this phone. Try again.",
        ScheduleWord.EditTitle to "Edit scheduled prompt",
        ScheduleWord.Prompt to "Prompt",
        ScheduleWord.PromptHelp to "Sent exactly as written, at the time you pick.",
        ScheduleWord.Project to "Project folder",
        ScheduleWord.Repeat to "Repeat",
        ScheduleWord.KindOnce to "Once",
        ScheduleWord.KindDaily to "Every day",
        ScheduleWord.KindWeekdays to "Weekdays",
        ScheduleWord.KindInterval to "Every few hours",
        ScheduleWord.Time to "Time (24-hour, like 09:30)",
        ScheduleWord.Date to "Date (like 2026-11-04)",
        ScheduleWord.Hours to "Every how many hours (1 to 168)",
        ScheduleWord.FirstAt to "Starting at this time today",
        ScheduleWord.Missed to "If the phone was off at that time",
        ScheduleWord.MissedCatchUp to "Run once when it can",
        ScheduleWord.MissedSkip to "Skip that time",
        ScheduleWord.Model to "Model",
        ScheduleWord.ModelDefault to "Server default",
        ScheduleWord.Conversation to "Conversation",
        ScheduleWord.NewEachTime to "A new conversation each time",
        ScheduleWord.ContinuesTitle to "Continues “%1\$s”",
        ScheduleWord.Save to "Save schedule",
        ScheduleWord.Saving to "Saving",
        ScheduleWord.UnavailableEdit to "This schedule can't be edited here.",
        ScheduleWord.IssuePrompt to "Write the prompt first.",
        ScheduleWord.IssueProject to "Choose the project folder.",
        ScheduleWord.IssueTime to "Write the time like 09:30.",
        ScheduleWord.IssueDate to "Write the date like 2026-11-04.",
        ScheduleWord.IssueHours to "Write a number of hours from 1 to 168.",
        ScheduleWord.Monday to "Mon",
        ScheduleWord.Tuesday to "Tue",
        ScheduleWord.Wednesday to "Wed",
        ScheduleWord.Thursday to "Thu",
        ScheduleWord.Friday to "Fri",
        ScheduleWord.Saturday to "Sat",
        ScheduleWord.Sunday to "Sun",
    )
