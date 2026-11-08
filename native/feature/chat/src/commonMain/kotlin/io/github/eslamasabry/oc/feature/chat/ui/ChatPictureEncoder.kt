package io.github.eslamasabry.oc.feature.chat.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.ui.kit.content.image.KitPngEncoder

/**
 * S24-CHAT story 3: turns a marked-up picture into PNG bytes for the gallery's Edit (Flutter
 * `KitImageMarkup` saves a PNG). Platform code, so the app binds it; without one (desktop, tests) the
 * chat keeps its one-picture viewer.
 */
public interface ChatPictureEncoder {
    public val encodePng: KitPngEncoder
}

@ContributesTo(AppScope::class)
public interface ChatPictureEncoderBindings {
    @Multibinds(allowEmpty = true) public val chatPictureEncoders: Set<ChatPictureEncoder>
}
