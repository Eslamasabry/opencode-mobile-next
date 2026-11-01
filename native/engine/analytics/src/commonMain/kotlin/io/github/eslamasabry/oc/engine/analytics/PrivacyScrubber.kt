package io.github.eslamasabry.oc.engine.analytics

/** What kind of content a string looked like. A rejection names the kind and the field, never the value. */
public enum class LeakKind { Url, Email, Address, Path, Credential, Identifier, FreeText, ControlCharacter, TooLong }

/** Which part of an event a rejection is about. */
public enum class ScrubField {
    EventName, PropKey, PropValue, HourUtc, InstallId, SessionId, AppVersion, AppBuild, OsMajor, Language,
}

public enum class RejectReason { UnknownEvent, MissingProp, UnexpectedProp, ValueNotAllowed, LooksLikeContent, BadFormat }

public sealed interface ScrubVerdict {
    public data object Clean : ScrubVerdict

    /** Carries no part of the rejected value, so a rejection can be logged or counted safely. */
    public data class Rejected(val reason: RejectReason, val field: ScrubField, val leak: LeakKind? = null) : ScrubVerdict
}

/**
 * Pattern checks for content that must never leave the phone: links, emails, addresses, paths and file names,
 * credentials and tokens, ids, free text. The second line of defence: the first is the schema allowlist (every
 * property value must be one of its spec's listed values), which the scrubber applies before these patterns.
 */
public object LeakDetector {
    private const val MAX_LENGTH = 48

    private val URL = Regex("""(?i)([a-z][a-z0-9+.-]*://|^www\.|^mailto:|^data:|^content:|^file:|^intent:)""")
    private val EMAIL = Regex("""[^\s@]+@[^\s@]+""")
    private val IPV4 = Regex("""(^|[^0-9])\d{1,3}(\.\d{1,3}){3}([^0-9]|$)""")
    /** Common name suffixes; any other dotted word is reported as a file name (both are rejected). */
    private val HOST_SUFFIX =
        Regex("""(?i)\.(local|lan|home|internal|localdomain|arpa|com|net|org|io|dev|app|ai|cloud|co|me|sh|xyz|tech|edu|gov)$""")
    private val FILE_NAME = Regex("""(?i)\.[a-z0-9]{1,8}$""")
    private val UUID = Regex("""(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
    private val HEX_RUN = Regex("""(?i)[0-9a-f]{16,}""")
    private val CREDENTIAL =
        Regex(
            """(?i)(^|[^a-z])(sk|pk|rk)[-_][a-z0-9_-]{6,}|gh[pousr]_[a-z0-9]{8,}|github_pat_|glpat-|xox[abprs]-|""" +
                """AKIA[0-9A-Z]{12,}|AIza[0-9a-z_-]{10,}|eyJ[a-z0-9_-]{8,}|bearer|basic\s|""" +
                """passw(or)?d|secret|token|api[-_]?key|private[-_]?key|BEGIN [A-Z ]*KEY""",
        )
    private val DIGIT_RUN = Regex("""\d{7,}""")

    /** Null when [value] looks like a harmless short identifier (an enum or bucket name). */
    public fun detect(value: String): LeakKind? {
        if (value.any { it.code < 0x20 || it.code == 0x7f }) return LeakKind.ControlCharacter
        if (value.length > MAX_LENGTH) return LeakKind.TooLong
        if (URL.containsMatchIn(value)) return LeakKind.Url
        if (EMAIL.containsMatchIn(value)) return LeakKind.Email
        if (CREDENTIAL.containsMatchIn(value)) return LeakKind.Credential
        if ('/' in value || '\\' in value || value.startsWith("~")) return LeakKind.Path
        if (IPV4.containsMatchIn(value) || ':' in value) return LeakKind.Address
        if (value.any { it.isWhitespace() }) return LeakKind.FreeText
        if (UUID.containsMatchIn(value) || HEX_RUN.containsMatchIn(value) || DIGIT_RUN.containsMatchIn(value)) {
            return LeakKind.Identifier
        }
        if ('.' in value) {
            val fileName = FILE_NAME.containsMatchIn(value) && !HOST_SUFFIX.containsMatchIn(value)
            return if (fileName) LeakKind.Path else LeakKind.Address
        }
        if (value.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return LeakKind.FreeText
        if (value.any { it.code > 0x7e }) return LeakKind.FreeText
        if (mixedHighEntropy(value)) return LeakKind.Credential
        return null
    }

    /** A long run mixing upper case, lower case and digits looks like a key, not like an enum name. */
    private fun mixedHighEntropy(value: String): Boolean {
        if (value.length < 20) return false
        val digits = value.count { it.isDigit() }
        val upper = value.count { it.isUpperCase() }
        val lower = value.count { it.isLowerCase() }
        return digits >= 3 && upper >= 3 && lower >= 3
    }
}

/**
 * The privacy gate every event passes before it is queued and again before it is sent. It rejects by construction:
 * the event name must be registered, the property keys must equal the spec's, every value must be one the spec lists,
 * the ids must have the exact shape the app generates, and every name, key and value must pass [LeakDetector].
 */
public object PrivacyScrubber {
    private val ID = Regex("^[0-9a-f]{32}$")
    private val APP_VERSION = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}(-[a-z]{1,12})?$""")
    private val LANG = Regex("^[a-z]{2,3}$")
    private val NAME = Regex("^[a-z][a-z0-9_]{0,39}$")

    public fun check(event: WireEvent): ScrubVerdict {
        if (!NAME.matches(event.name)) return leakOr(event.name, ScrubField.EventName, RejectReason.UnknownEvent)
        val spec = AnalyticsSchema.spec(event.name) ?: return ScrubVerdict.Rejected(RejectReason.UnknownEvent, ScrubField.EventName)
        for (key in event.props.keys) {
            if (spec.prop(key) == null) return leakOr(key, ScrubField.PropKey, RejectReason.UnexpectedProp)
        }
        for (prop in spec.props) {
            val value = event.props[prop.key] ?: return ScrubVerdict.Rejected(RejectReason.MissingProp, ScrubField.PropKey)
            if (!prop.permits(value)) return leakOr(value, ScrubField.PropValue, RejectReason.ValueNotAllowed)
            if (prop.kind != PropKind.Version) {
                LeakDetector.detect(value)?.let { return looksLikeContent(ScrubField.PropValue, it) }
            }
            LeakDetector.detect(prop.key)?.let { return looksLikeContent(ScrubField.PropKey, it) }
        }
        if (event.hourUtc < 0 || event.hourUtc % HOUR_MS != 0L) {
            return ScrubVerdict.Rejected(RejectReason.BadFormat, ScrubField.HourUtc)
        }
        if (!ID.matches(event.install)) return leakOr(event.install, ScrubField.InstallId, RejectReason.BadFormat)
        if (!ID.matches(event.session)) return leakOr(event.session, ScrubField.SessionId, RejectReason.BadFormat)
        return checkSystem(event.system)
    }

    public fun checkSystem(system: SystemProps): ScrubVerdict =
        when {
            !APP_VERSION.matches(system.appVersion) -> leakOr(system.appVersion, ScrubField.AppVersion, RejectReason.BadFormat)
            system.appBuild !in 0..VersionCode.MAX -> ScrubVerdict.Rejected(RejectReason.BadFormat, ScrubField.AppBuild)
            system.osMajor !in 0..999 -> ScrubVerdict.Rejected(RejectReason.BadFormat, ScrubField.OsMajor)
            !LANG.matches(system.lang) -> leakOr(system.lang, ScrubField.Language, RejectReason.BadFormat)
            else -> ScrubVerdict.Clean
        }

    /** A typed event must produce exactly its spec's keys, each of the spec's kind. */
    public fun checkTyped(event: AnalyticsEvent): ScrubVerdict {
        val props = event.props()
        val spec = event.spec
        if (AnalyticsSchema.spec(spec.name) !== spec) {
            return ScrubVerdict.Rejected(RejectReason.UnknownEvent, ScrubField.EventName)
        }
        if (props.keys.any { spec.prop(it) == null }) return ScrubVerdict.Rejected(RejectReason.UnexpectedProp, ScrubField.PropKey)
        for (prop in spec.props) {
            val value = props[prop.key] ?: return ScrubVerdict.Rejected(RejectReason.MissingProp, ScrubField.PropKey)
            if (value.kind != prop.kind || !prop.permits(value.wire)) {
                return ScrubVerdict.Rejected(RejectReason.ValueNotAllowed, ScrubField.PropValue)
            }
        }
        return ScrubVerdict.Clean
    }

    private fun looksLikeContent(field: ScrubField, kind: LeakKind) =
        ScrubVerdict.Rejected(RejectReason.LooksLikeContent, field, kind)

    /** Prefer naming the leak kind when the offending text looks like content. */
    private fun leakOr(value: String, field: ScrubField, fallback: RejectReason): ScrubVerdict.Rejected =
        LeakDetector.detect(value)?.let { ScrubVerdict.Rejected(RejectReason.LooksLikeContent, field, it) }
            ?: ScrubVerdict.Rejected(fallback, field)
}
