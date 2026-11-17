package io.github.eslamasabry.oc.engine.analytics.umami

import kotlinx.serialization.encodeToString
import io.github.eslamasabry.oc.engine.analytics.DeviceClass
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
    /** Never sent any more (the exact resolution is not collected); kept nullable so an older stored queue still reads. */
    val screen: String? = null,
    val url: String,
    val title: String,
    val tag: String,
    val timestamp: Long,
    val data: Map<String, String>,
    val name: String? = null,
    /**
     * The period of Umami's anonymous visitor hash. Left out, the server picks its own default; we pin it so the
     * words "changes every month" are ours. Umami v3.5.0: request-schema.ts `salt`, crypto.ts getSalt (accepts
     * day/week/month and their -ly forms, any other word is a fixed salt). Never a per-person value.
     */
    val salt: String = HASH_PERIOD,
)

public const val HASH_PERIOD: String = "month"

/** Build-owned metadata; no route, account, installation id or server profile is accepted here. */
public data class UmamiBuild(val version: String, val build: String, val tag: String = build) {
    init {
        require(version.length <= 50 && version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.\\-]+)?")))
        require(build.matches(Regex("[0-9]{1,10}")))
        require(tag == build || tag == "test-$build")
    }
}

/**
 * What the sender may say about the device: language, the Android MAJOR version, the class (phone or tablet) and a
 * browser-like User-Agent built from those. Nothing else (no model, maker, id, minor version, resolution or zone).
 */
public data class UmamiDevice(
    val language: String,
    val userAgent: String,
    val osMajor: Int,
    val deviceClass: DeviceClass,
)

/** The only device keys added to an event's data, besides `version` and `build`. */
public val umamiDeviceKeys: Set<String> = setOf("os_major", "device_class")

/** Where an event comes from: the site it counts under, the build that sent it and the device that sent it. */
public data class UmamiSource(val website: String, val build: UmamiBuild, val device: UmamiDevice)

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
    source: UmamiSource,
    atMs: Long,
): UmamiEvent? {
    val (website, build, device) = source
    val inRange = page in umamiPages.values && atMs >= 0 && atMs / 1000 <= 2_147_483_647
    val event = ProductEvent.entries.firstOrNull { it.wire == name }
    val clean = event?.let { cleanProductProps(it, props) }
    val visible = when {
        !inRange -> null
        event == ProductEvent.ScreenView || event == ProductEvent.Jank -> clean?.get("screen")?.let { umamiPages[it] }
        else -> page
    }
    if (event == null || clean == null || visible == null) return null
    return UmamiEvent(payload = UmamiPayload(
        website = website,
        language = device.language.takeIf { it.length <= 35 && it.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")) } ?: "und",
        url = visible.url,
        title = visible.title,
        tag = build.tag,
        // v3.5.0 unixTimestampParam is an INTEGER, in seconds, not createdAt or milliseconds.
        timestamp = atMs / 1000,
        data = clean + mapOf("version" to build.version, "build" to build.build) + deviceData(device),
        name = name.takeUnless { event == ProductEvent.ScreenView },
    ))
}

private fun deviceData(device: UmamiDevice): Map<String, String> = buildMap {
    if (device.osMajor in 1..999) put("os_major", device.osMajor.toString())
    when (device.deviceClass) {
        DeviceClass.Phone -> put("device_class", "phone")
        DeviceClass.Tablet -> put("device_class", "tablet")
        else -> Unit
    }
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
