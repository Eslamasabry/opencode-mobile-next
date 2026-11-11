package io.github.eslamasabry.oc.feature.chat.header.presentation

/**
 * "Include this server's address" on Open on another phone (F: `SessionAddressOffer`,
 * lib/ui/widgets/session_handoff_sheets.dart). It exists only when the connected server reports the address
 * handoff capability and the app can read the server's installation id; otherwise the sheet shows the local link
 * and no switch, exactly as Flutter does today. [hostPort] is the address shown beside the switch, or null when the
 * saved address cannot go in a link (the switch then says why and stays off). [build] runs on every switch-on and
 * on every Copy, never cached, so a secret registered since the last build is refused.
 */
public class PhoneAddressOffer(
    public val hostPort: String?,
    public val build: () -> PhoneAddressResult,
)

/** What one build gave: the link to show, or the reason there is none. */
public sealed interface PhoneAddressResult {
    public data class Link(val link: String) : PhoneAddressResult

    public data class Failed(val failure: PhoneAddressFailure) : PhoneAddressResult
}

/** The plain-words reasons an address cannot go in a link (each has a `sessionAddressFail*` sentence). */
public enum class PhoneAddressFailure {
    Unavailable,
    InvalidLink,
    TooLarge,
    Credentials,
    PrivateRouteRequired,
    Unreachable,
    TimedOut,
    InvalidDescriptor,
}

/** The sheet's rule for what to draw: the portable link, a failure, or the local link (F: `_ContinueOnPhoneBody`). */
public sealed interface PhoneSheetShow {
    public data class Portable(val link: String) : PhoneSheetShow

    public data class Failure(val failure: PhoneAddressFailure) : PhoneSheetShow

    public data class Local(val link: String) : PhoneSheetShow

    public data object None : PhoneSheetShow
}

public object PhoneSheetRules {
    /** [include] is the switch; it is off every time the sheet opens, and cannot be on without an offer and a host. */
    public fun show(local: String?, offer: PhoneAddressOffer?, include: Boolean): PhoneSheetShow {
        if (local == null) return PhoneSheetShow.None
        if (offer == null || offer.hostPort == null || !include) return PhoneSheetShow.Local(local)
        return when (val r = offer.build()) {
            is PhoneAddressResult.Link -> PhoneSheetShow.Portable(r.link)
            is PhoneAddressResult.Failed -> PhoneSheetShow.Failure(r.failure)
        }
    }
}
