package io.github.eslamasabry.oc.engine.agents.handoff

/**
 * EN + AR wording for the handoff's typed reasons, keyed by enum (Flutter asserts some of these words). Accepted for
 * this port only; the UI's words module is the long-term home.
 */
public object HandoffWords {
    public enum class Language { En, Ar }

    /** Flutter `failureDetail` technical words for a stale scope. */
    public fun stale(reason: HandoffStaleReason, language: Language = Language.En): String = when (language) {
        Language.En -> when (reason) {
            HandoffStaleReason.NotCurrent -> "source changed"
            HandoffStaleReason.SourceConnectionClosed -> "source connection closed"
            HandoffStaleReason.AgentConnectionClosed -> "agent connection closed"
            HandoffStaleReason.SourceMoved -> "source moved to another project"
            HandoffStaleReason.AgentMoved -> "agent connection moved to another project"
            HandoffStaleReason.ProfileUnavailable -> "profile unavailable"
        }
        Language.Ar -> when (reason) {
            HandoffStaleReason.NotCurrent -> "تغيّر المصدر"
            HandoffStaleReason.SourceConnectionClosed -> "أُغلق اتصال المصدر"
            HandoffStaleReason.AgentConnectionClosed -> "أُغلق اتصال الوكيل"
            HandoffStaleReason.SourceMoved -> "انتقل المصدر إلى مشروع آخر"
            HandoffStaleReason.AgentMoved -> "انتقل اتصال الوكيل إلى مشروع آخر"
            HandoffStaleReason.ProfileUnavailable -> "الملف الشخصي غير متاح"
        }
    }

    /** Flutter `HandoffUnavailableReason.message`. */
    public fun unavailable(reason: HandoffUnavailableReason, language: Language = Language.En): String = when (language) {
        Language.En -> when (reason) {
            HandoffUnavailableReason.ProjectNotShared -> "These conversations are not connected to the same project."
            HandoffUnavailableReason.SafeFileWriterUnavailable ->
                "This destination cannot safely save a conversation record yet."
            HandoffUnavailableReason.TargetDispatchUnavailable -> "This destination cannot start a linked conversation yet."
        }
        Language.Ar -> when (reason) {
            HandoffUnavailableReason.ProjectNotShared -> "هاتان المحادثتان غير متصلتين بالمشروع نفسه."
            HandoffUnavailableReason.SafeFileWriterUnavailable -> "لا تستطيع هذه الوجهة حفظ سجل المحادثة بأمان بعد."
            HandoffUnavailableReason.TargetDispatchUnavailable -> "لا تستطيع هذه الوجهة بدء محادثة مرتبطة بعد."
        }
    }
}
