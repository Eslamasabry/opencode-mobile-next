package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

class TestClock(var millis: Long = 1_760_000_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

class TestProfiles(vararg ids: String) : ComposerProfiles {
    val ids: MutableSet<String> = ids.toMutableSet()

    override fun exists(profileId: String): Boolean = profileId in ids

    override fun count(): Int = ids.size
}

fun dataAttachment(name: String, text: String, mime: String = "text/plain"): PromptAttachment =
    PromptAttachment(mime = mime, filename = name, url = "data:$mime;base64,${Base64.encode(text.encodeToByteArray())}")

fun fileAttachment(name: String, path: String = "/srv/app/$name"): PromptAttachment =
    PromptAttachment(mime = "text/plain", filename = name, url = "file://$path")
