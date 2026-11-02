package io.github.eslamasabry.oc.feature.schedules.ui

import io.github.eslamasabry.oc.engine.schedules.ScheduleCode

/**
 * TODO(words): copy that `:ui:words` does not have yet (this unit may not edit `:ui:words`; the l10n owner moves these
 * into `strings_schedules.xml` and deletes this file). English and Arabic, from the Flutter app's schedule copy
 * (`lib/ui/screens/scheduling/schedule_copy.dart`, `app_en.arb`/`app_ar.arb`), in plain words. Nothing else in this
 * module holds copy.
 */
public class ScheduleWords internal constructor(private val ar: Boolean) {
    private fun t(en: String, ar: String): String = if (this.ar) ar else en

    val title: String get() = t("Scheduled prompts", "الطلبات المجدولة")
    val back: String get() = t("Back", "رجوع")
    val new: String get() = t("New scheduled prompt", "طلب مجدول جديد")
    val loading: String get() = t("Loading scheduled prompts", "جارٍ تحميل الطلبات المجدولة")
    val emptyTitle: String get() = t("No scheduled prompts yet", "لا توجد طلبات مجدولة بعد")
    val emptyBody: String get() = t("Send a prompt later, or repeat it every day.", "أرسل طلبًا لاحقًا، أو كرّره كل يوم.")
    val unavailableServer: String get() = t("This server can't run scheduled prompts.", "هذا الخادم لا يستطيع تشغيل الطلبات المجدولة.")
    val unavailableServerBody: String
        get() = t("Connect to it from Servers, then come back here.", "اتصل به من صفحة الخوادم ثم ارجع إلى هنا.")
    val unavailablePhone: String get() = t("This phone can't schedule prompts.", "هذا الهاتف لا يستطيع جدولة الطلبات.")
    val unavailableStorage: String
        get() = t("Saved schedules couldn't be read on this phone.", "تعذّرت قراءة الجداول المحفوظة على هذا الهاتف.")
    val tryAgain: String get() = t("Try again", "حاول مرة أخرى")

    fun once(moment: String): String = t("Once, $moment", "مرة واحدة، $moment")
    fun daily(time: String): String = t("Every day at $time", "كل يوم الساعة $time")
    fun weekdays(time: String): String = t("Weekdays at $time", "أيام العمل الساعة $time")
    fun everyHours(hours: Int): String =
        if (hours == 1) t("Every hour", "كل ساعة") else t("Every $hours hours", "كل $hours ساعات")
    fun next(moment: String): String = t("Next: $moment", "التالي: $moment")
    val paused: String get() = t("Paused", "متوقف مؤقتًا")
    val over: String get() = t("Done, no more times", "انتهى، لا مواعيد أخرى")
    val continues: String get() = t("Continues one conversation", "يكمل محادثة واحدة")
    val active: String get() = t("On", "مفعّل")
    val runNow: String get() = t("Run now", "شغّل الآن")
    val edit: String get() = t("Edit", "تعديل")
    val delete: String get() = t("Delete", "حذف")
    val cancel: String get() = t("Cancel", "إلغاء")
    val deleteTitle: String get() = t("Delete this scheduled prompt?", "حذف هذا الطلب المجدول؟")
    val deleteBody: String
        get() = t("Its history goes too. Conversations it started stay.", "سيُحذف سجلّه أيضًا. تبقى المحادثات التي بدأها.")
    val started: String
        get() = t("Started. You'll get a notification when it finishes.", "بدأ. سيصلك إشعار عند انتهائه.")
    val digest: String get() = t("Morning summary", "ملخص الصباح")
    val digestBody: String
        get() = t("One notification each morning with what ran.", "إشعار واحد كل صباح بما تم تشغيله.")
    val openConversation: String get() = t("Open conversation", "افتح المحادثة")
    val today: String get() = t("Today", "اليوم")
    val tomorrow: String get() = t("Tomorrow", "غدًا")
    val weekdayNames: List<String>
        get() = if (ar) listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun lastRan(moment: String): String = t("Last run $moment: replied", "آخر تشغيل $moment: تم الرد")
    fun lastStarted(moment: String): String = t("Last run $moment: started", "آخر تشغيل $moment: بدأ")
    fun lastSkipped(moment: String): String = t("Last run $moment: skipped", "آخر تشغيل $moment: تم التخطي")
    fun lastFailed(moment: String): String = t("Last run $moment: didn't finish", "آخر تشغيل $moment: لم يكتمل")
    fun lastRunning(moment: String): String = t("Running since $moment", "يعمل منذ $moment")
    val late: String get() = t("ran late", "تأخر")

    /** B-NAV-055: what happened and what to do next, for each fixed run code. */
    fun why(code: String?): String =
        when (code) {
            null -> ""
            "needs_you", "permission_required" ->
                t("It's waiting for your answer. Open the conversation to continue.", "ينتظر ردّك. افتح المحادثة للمتابعة.")
            "auth_required" ->
                t("The server asked to sign in again. Check the saved password in Servers.", "طلب الخادم تسجيل الدخول مجددًا. راجع كلمة المرور المحفوظة في الخوادم.")
            "agent_failed" ->
                t("The agent stopped with an error. Open the conversation to see what happened.", "توقف الوكيل بخطأ. افتح المحادثة لترى ما حدث.")
            "timeout", "uncertain_delivery", "interrupted" ->
                t(
                    "It didn't finish in time. It was not sent again; check the conversation before running it again.",
                    "لم يكتمل في الوقت المحدد. لم يُرسل مرة أخرى؛ راجع المحادثة قبل تشغيله مجددًا.",
                )
            "busy" -> t("The conversation was busy, so this time was skipped.", "كانت المحادثة مشغولة، فتم تخطي هذه المرة.")
            "stale_target" -> t("The schedule or its server changed, so this time was skipped.", "تغيّر الجدول أو خادمه، فتم تخطي هذه المرة.")
            "wrong_project" ->
                t("The conversation is in another project. Edit the schedule to pick it again.", "المحادثة في مشروع آخر. عدّل الجدول لاختيارها مجددًا.")
            "missed" -> t("The phone was off or asleep at that time, so it was skipped.", "كان الهاتف مغلقًا أو نائمًا في ذلك الوقت، فتم التخطي.")
            "unsupported" -> unavailableServer
            "certificate_invalid", "certificate_changed" ->
                t("The server's certificate isn't trusted. Check the server in Servers.", "شهادة الخادم غير موثوقة. راجع الخادم في الخوادم.")
            "cleartext_unconfirmed" ->
                t("The server needs your OK for an unencrypted connection. Check it in Servers.", "يحتاج الخادم موافقتك على اتصال غير مشفّر. راجعه في الخوادم.")
            else -> t("The server couldn't be reached. It will try again next time.", "تعذّر الوصول إلى الخادم. سيحاول مجددًا في المرة القادمة.")
        }

    fun error(code: ScheduleCode): String =
        when (code) {
            ScheduleCode.ScheduleLimit -> t("This server already has 100 schedules. Delete one first.", "لدى هذا الخادم 100 جدول بالفعل. احذف واحدًا أولًا.")
            ScheduleCode.StaleSchedule -> t("This schedule changed somewhere else. Open it again to edit.", "تغيّر هذا الجدول في مكان آخر. افتحه مجددًا للتعديل.")
            ScheduleCode.ScheduleMissing -> t("This schedule was deleted.", "تم حذف هذا الجدول.")
            ScheduleCode.ScheduleExpired -> t("That time has passed. Pick a time in the future.", "مضى ذلك الوقت. اختر وقتًا في المستقبل.")
            ScheduleCode.Unsupported -> unavailablePhone
            ScheduleCode.InvalidSchedule, ScheduleCode.InvalidProfile ->
                t("Check the prompt, project and time.", "راجع الطلب والمشروع والوقت.")
            else -> t("Couldn't save on this phone. Try again.", "تعذّر الحفظ على هذا الهاتف. حاول مرة أخرى.")
        }

    // ---- Editor
    val editTitle: String get() = t("Edit scheduled prompt", "تعديل الطلب المجدول")
    val prompt: String get() = t("Prompt", "الطلب")
    val promptHelp: String get() = t("Sent exactly as written, at the time you pick.", "يُرسل كما كُتب تمامًا، في الوقت الذي تختاره.")
    val project: String get() = t("Project folder", "مجلد المشروع")
    val repeat: String get() = t("Repeat", "التكرار")
    val kindOnce: String get() = t("Once", "مرة")
    val kindDaily: String get() = t("Every day", "يوميًا")
    val kindWeekdays: String get() = t("Weekdays", "أيام العمل")
    val kindInterval: String get() = t("Every few hours", "كل بضع ساعات")
    val time: String get() = t("Time (24-hour, like 09:30)", "الوقت (24 ساعة، مثل 09:30)")
    val date: String get() = t("Date (like 2026-11-04)", "التاريخ (مثل 2026-11-04)")
    val hours: String get() = t("Every how many hours (1 to 168)", "كل كم ساعة (من 1 إلى 168)")
    val firstAt: String get() = t("Starting at this time today", "بدءًا من هذا الوقت اليوم")
    val missed: String get() = t("If the phone was off at that time", "إذا كان الهاتف مغلقًا في ذلك الوقت")
    val missedCatchUp: String get() = t("Run once when it can", "شغّله مرة عندما يمكن")
    val missedSkip: String get() = t("Skip that time", "تخطَّ ذلك الوقت")
    val model: String get() = t("Model", "النموذج")
    val modelDefault: String get() = t("Server default", "الافتراضي في الخادم")
    val conversation: String get() = t("Conversation", "المحادثة")
    val newEachTime: String get() = t("A new conversation each time", "محادثة جديدة كل مرة")
    fun continuesTitle(title: String?): String =
        if (title.isNullOrBlank()) continues else t("Continues “$title”", "يكمل «$title»")
    val save: String get() = t("Save schedule", "احفظ الجدول")
    val saving: String get() = t("Saving", "جارٍ الحفظ")
    val unavailableEdit: String get() = t("This schedule can't be edited here.", "لا يمكن تعديل هذا الجدول هنا.")
    val issuePrompt: String get() = t("Write the prompt first.", "اكتب الطلب أولًا.")
    val issueProject: String get() = t("Choose the project folder.", "اختر مجلد المشروع.")
    val issueTime: String get() = t("Write the time like 09:30.", "اكتب الوقت مثل 09:30.")
    val issueDate: String get() = t("Write the date like 2026-11-04.", "اكتب التاريخ مثل 2026-11-04.")
    val issueHours: String get() = t("Write a number of hours from 1 to 168.", "اكتب عدد ساعات من 1 إلى 168.")

    public companion object {
        public val English: ScheduleWords = ScheduleWords(ar = false)
        public val Arabic: ScheduleWords = ScheduleWords(ar = true)
    }
}
