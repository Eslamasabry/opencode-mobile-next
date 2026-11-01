package io.github.eslamasabry.oc.protocol.paseo.feed

/** What `create_agent_request.config.modeId` carries for a new conversation. */
public sealed interface PaseoCreateMode {
    /** Send `modeId = id`. */
    public data class Send(val id: String) : PaseoCreateMode

    /** Send no `modeId`: the daemon's own default applies. */
    public data object Omit : PaseoCreateMode

    /** Refuse before any create (`PaseoException(Unavailable)`): the required mode is not offered. */
    public data object Refuse : PaseoCreateMode
}

/**
 * The mode rule of `PaseoGateway._createAgent` (lib/paseo/gateway/prompts.dart:150-163), B-AGENT-154 "phone Claude
 * always asks for the asking mode". [defaultProviderModes] is the gateway option (the phone helper passes
 * `{claude: default}`); [listedModes] are the ids the provider snapshot lists for [provider].
 *
 * - A provider with a required default mode must list it — except that Paseo 0.9.2 on a phone can list no Claude modes
 *   at all: the required mode is then still sent by name (the daemon refuses it if it doesn't know it).
 * - Otherwise a chosen mode is sent only when the provider lists it.
 */
public fun paseoCreateMode(
    provider: String,
    requestedMode: String?,
    defaultProviderModes: Map<String, String>,
    listedModes: Collection<String>,
): PaseoCreateMode {
    val mode = requestedMode ?: defaultProviderModes[provider]
    if (provider in defaultProviderModes && (mode == null || (listedModes.isNotEmpty() && mode !in listedModes))) {
        return PaseoCreateMode.Refuse
    }
    val send = mode != null && (mode in listedModes || (listedModes.isEmpty() && defaultProviderModes[provider] == mode))
    return if (send) PaseoCreateMode.Send(mode!!) else PaseoCreateMode.Omit
}
