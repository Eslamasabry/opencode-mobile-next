package io.github.eslamasabry.oc.feature.chat.composer

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.kitchat.composer.KitInsertedPicture

/** One picture a keyboard put into the field, read whole (bounded by the reader). */
public class InsertedPicture(public val mime: String?, public val bytes: ByteArray)

/**
 * S24-CHAT story 5 (Flutter `_handleInsertedContent`): Gboard's GIFs and stickers and Android's
 * clipboard-image chip arrive as content the field receives. Only the platform can read it (a
 * content URI), so the app binds a reader; without one (desktop, tests) pictures are not taken and
 * text pastes as before. The reader reads at most 10 MiB per picture and never hands back a URI.
 */
public interface ComposerInsertedPictures {
    /** The pictures in [picture]; empty = nothing it could read as a picture. */
    public suspend fun read(picture: KitInsertedPicture): Outcome<List<InsertedPicture>>
}

@ContributesTo(AppScope::class)
public interface ComposerInsertedPicturesBindings {
    @Multibinds(allowEmpty = true) public val composerInsertedPictures: Set<ComposerInsertedPictures>
}
