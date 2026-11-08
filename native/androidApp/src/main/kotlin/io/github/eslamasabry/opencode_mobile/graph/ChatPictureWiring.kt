package io.github.eslamasabry.opencode_mobile.graph

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.feature.chat.ui.ChatPictureEncoder
import io.github.eslamasabry.oc.ui.kit.content.image.KitPngEncoder
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.withContext

/*
 * S24-CHAT story 3: the chat's picture gallery saves a marked-up picture as PNG (lossless, as Flutter's
 * markup does), off the main thread.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatPictureEncoder>())
@Inject
class AndroidChatPictureEncoder(private val dispatchers: AppDispatchers) : ChatPictureEncoder {
    override val encodePng: KitPngEncoder = { image ->
        withContext(dispatchers.io) {
            ByteArrayOutputStream().use { out ->
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }
    }
}
