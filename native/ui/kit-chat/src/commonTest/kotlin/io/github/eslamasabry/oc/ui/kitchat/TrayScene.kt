package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTrayAction

/** The tray's words per locale, as `:ui:words` has them (en, ar). */
data class TrayWords(
    val photos: String,
    val camera: String,
    val file: String,
    val more: String,
    val field: String,
    val tools: String,
    val close: String,
    val send: String,
) {
    companion object {
        fun of(localeTag: String): TrayWords =
            if (localeTag == "ar")
                TrayWords("الصور", "الكاميرا", "ملف", "المزيد", "اسأل OpenCode…", "الإرفاق والمزيد", "إغلاق", "إرسال")
            else TrayWords("Photos", "Camera", "File", "More", "Ask OpenCode…", "Attach and more", "Close", "Send")
    }
}

/**
 * E20-S20 golden scene: the message box at [widthDp] with "+" and, when [open], Flutter's one-row tray above the
 * field (states_tray_*.png in the Flutter goldens). [fontScale] is the person's text size.
 */
@Composable
fun TrayScene(open: Boolean, words: TrayWords, fontScale: Float = 1f, widthDp: Int = 360) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        Box(
            Modifier.testTag(TRAY_SCENE)
                .background(LocalKitColors.current.ground)
                .width(widthDp.dp)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            KitComposerBox(
                running = false,
                above = {
                    KitComposerTray(
                        open = open,
                        actions =
                            listOf(
                                KitComposerTrayAction(words.photos, KitIcons.images, {}),
                                KitComposerTrayAction(words.camera, KitIcons.camera, {}),
                                KitComposerTrayAction(words.file, KitIcons.attach, {}),
                                KitComposerTrayAction(words.more, KitIcons.more, {}),
                            ),
                    )
                },
                field = {
                    KitText(words.field, role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame)
                },
                leading = { KitComposerAdd(if (open) words.close else words.tools, onClick = {}, open = open) },
                trailing = { KitComposerSend(words.send, onClick = {}) },
            )
        }
    }
}

const val TRAY_SCENE: String = "tray-scene"
