package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent

/**
 * Settings › Help › Report a problem (R1, 2026-11-09; one flow for the old Report a problem and B-190's Send a problem
 * report). The host builds the report's facts and redacts the person's words; [send] sends exactly the text the person
 * could read under "What's included", only when they tap Send report, and answers the report code.
 */
public interface ProblemReportSender {
    /** The report's facts, already redacted, with no words yet. Reading them sends nothing. Never throws. */
    public suspend fun build(): ProblemReportContent

    /** The person's [text] as it would leave the phone: passwords, keys and addresses taken out. */
    public fun words(text: String): String

    /** This build can send a report (the vendor is built in). Without it, Copy text leads the page. */
    public val canSend: Boolean

    /** Sends [text] once; the report code, or null when it could not be sent. Never throws. */
    public suspend fun send(text: String): String?

    /** "Report code" in Settings › About, or null without a vendor. */
    public suspend fun reportCode(): String?
}

/** Where the person is on Report a problem. Only Send report sends; leaving the page sends nothing. */
public sealed interface ReportStage {
    public data object Writing : ReportStage

    public data object Sending : ReportStage

    /** The send did not go through: one plain line, and Send report again. */
    public data object Failed : ReportStage

    public data class Sent(val code: String) : ReportStage
}

/**
 * Report a problem's state: what the report holds ([content], null while it is read), whether this build sends it,
 * the stage, and the two things that open over the page ("What's included" and the public-post question).
 */
public data class ReportUi(
    val content: ProblemReportContent? = null,
    val canSend: Boolean = false,
    val stage: ReportStage = ReportStage.Writing,
    val showIncluded: Boolean = false,
    val confirmPublic: Boolean = false,
)
