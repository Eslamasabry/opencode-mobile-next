package io.github.eslamasabry.oc.ui.kitchat

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTrayAction
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState

/** Paragraphs long enough to wrap, so the veil has words to blur. Newest first, as the list lays them. */
val TRANSLUCENT_ROWS: List<String> =
    (0 until 40).map { i ->
        "Reply $i: the build finished and every check passed; the next step reads the log, " +
            "compares the timings with yesterday's and writes what changed into the notes."
    }

/**
 * E20-S30 golden scene: a chat page at 412 × 800 dp whose transcript scrolls under the translucent composer (and,
 * scrolled away, under the "Jump to latest" pill). [trayOpen] opens the composer's "+" tray. The test scrolls.
 */
@Composable
fun TranslucentChromeScene(trayOpen: Boolean, words: TrayWords = TrayWords.of("en")) {
    Box(Modifier.testTag(TRANSLUCENT_SCENE).size(412.dp, 800.dp).background(LocalKitColors.current.ground)) {
        val source = rememberKitFrostState()
        val state = rememberTranscriptListState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            bottom = {
                KitComposerBox(
                    running = false,
                    above = {
                        KitComposerTray(
                            open = trayOpen,
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
                    leading = { KitComposerAdd(if (trayOpen) words.close else words.tools, onClick = {}, open = trayOpen) },
                    trailing = { KitComposerSend(words.send, onClick = {}) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                KitTranscriptList(state, TRANSLUCENT_ROWS.first(), "Jump to latest", KitMotionLevel.Off) {
                    items(TRANSLUCENT_ROWS, key = { it }, contentType = { "row" }) {
                        KitText(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), role = TypeRole.Body)
                    }
                }
            }
        }
    }
}

const val TRANSLUCENT_SCENE: String = "translucent-scene"
