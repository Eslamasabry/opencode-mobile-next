package io.github.eslamasabry.oc.feature.chat.composer.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment

/** The attach paths besides the document picker (Flutter "+" sheet rows). */
public enum class AttachKind {
    /** The system photo picker; several at once, downscaled. */
    Photos,

    /** One new photo from the camera. */
    Camera,

    /** The picture on the clipboard. */
    Clipboard,
}

/**
 * A platform's photo, camera and clipboard intake. The result is bounded, MIME-checked data, never
 * a content URI; an empty list is a cancel. Desktop contributes none, so the rows stay hidden.
 */
public interface ComposerAttachSource {
    public val kinds: Set<AttachKind>

    public suspend fun attach(
        kind: AttachKind,
        current: List<PromptAttachment>,
    ): Outcome<List<PromptAttachment>>
}
