package io.github.eslamasabry.oc.engine.analytics.umami

import kotlinx.serialization.encodeToString
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.cleanProductProps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Only product events go to Umami. Crash reports, traces, ids and user properties never do. */
@Serializable
public data class UmamiEvent(val type: String = "event", val payload: UmamiPayload)

@Serializable
public data class UmamiPayload(
    val website: String,
    val hostname: String = "opencode-mobile.app",
    val language: String,
    val screen: String,
    val url: String,
    val title: String,
    val tag: String,
    val timestamp: Long,
    val data: Map<String, String>,
    val name: String? = null,
)

/** Build-owned metadata; no route, account, installation id or server profile is accepted here. */
public data class UmamiBuild(val version: String, val build: String, val tag: String = build) {
    init {
        require(version.length <= 50 && version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.\\-]+)?")))
        require(build.matches(Regex("[0-9]{1,10}")))
        require(tag == build || tag == "test-$build")
    }
}

public data class UmamiDevice(val language: String, val screen: String, val userAgent: String)

public data class UmamiPage(val url: String, val title: String)

/** The existing closed screen categories, each with one address and English title. */
public val umamiPages: Map<String, UmamiPage> = mapOf(
    "conversations" to UmamiPage("/home", "Home"),
    "chat" to UmamiPage("/conversation", "Conversation"),
    "new_chat" to UmamiPage("/new-conversation", "New conversation"),
    "servers" to UmamiPage("/settings/servers", "Servers"),
    "this_phone" to UmamiPage("/settings/this-phone", "This phone"),
    "setup" to UmamiPage("/setup", "Setup"),
    "privacy" to UmamiPage("/settings/privacy", "Privacy"),
    "appearance" to UmamiPage("/settings/appearance", "Appearance"),
    "integrations" to UmamiPage("/settings/integrations", "Integrations"),
    "settings" to UmamiPage("/settings", "Settings"),
    "files" to UmamiPage("/files", "Files"),
    "git" to UmamiPage("/git", "Git"),
    "review" to UmamiPage("/review", "Review"),
    "team" to UmamiPage("/team", "Team"),
    "usage" to UmamiPage("/usage", "Usage"),
    "agents" to UmamiPage("/agents", "Agents"),
    "about" to UmamiPage("/settings/about", "About"),
    "other" to UmamiPage("/other", "Other"),
)

/** Rechecks the live allow-list, even though the shared TelemetryService has already checked it. */
public fun umamiEvent(
    name: String,
    props: Map<String, String>,
    page: UmamiPage,
    website: String,
    build: UmamiBuild,
    device: UmamiDevice,
    atMs: Long,
): UmamiEvent? {
    if (page !in umamiPages.values || atMs < 0 || atMs / 1000 > 2_147_483_647) return null
    val event = ProductEvent.entries.firstOrNull { it.wire == name } ?: return null
    val clean = cleanProductProps(event, props) ?: return null
    val visible = if (event == ProductEvent.ScreenView || event == ProductEvent.Jank) {
        umamiPages[clean["screen"]] ?: return null
    } else page
    return UmamiEvent(payload = UmamiPayload(
        website = website,
        language = device.language.takeIf { it.length <= 35 && it.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")) } ?: "und",
        screen = device.screen.takeIf { it.matches(Regex("[0-9]{1,5}x[0-9]{1,5}")) }.orEmpty(),
        url = "/other",
        title = visible.title,
        tag = "0",
        // v3.5.0 unixTimestampParam is an INTEGER, in seconds, not createdAt or milliseconds.
        timestamp = atMs / 1000,
        data = clean + mapOf("version" to build.version, "build" to build.build, "unexpected" to "fixture"),
        name = "event_changed".takeUnless { event == ProductEvent.ScreenView },
    ))
}

public object UmamiCodec {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    internal val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        exceptionsWithDebugInfo = false
    }
    public fun batch(events: List<UmamiEvent>): String = json.encodeToString(events)
}
