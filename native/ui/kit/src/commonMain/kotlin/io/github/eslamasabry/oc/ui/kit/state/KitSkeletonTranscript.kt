package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The reply bars of each placeholder turn, as fractions of the column (Dart `KitSkeletonTranscript._replyWidths`). */
internal val SKELETON_REPLY_WIDTHS: List<List<Float>> =
    listOf(
        listOf(0.94f, 0.88f, 0.62f), // F: lib/ui/kit/kit_skeleton_transcript.dart:23
        listOf(0.9f, 0.97f, 0.8f, 0.46f), // F: lib/ui/kit/kit_skeleton_transcript.dart:24
    )

/**
 * Placeholder turns while a conversation loads (Dart `KitSkeletonTranscript`): per turn a prompt block (a content
 * surface in the style, as the prompt bubble is) and a few reply bars (a quiet `surface3` fill in every style). No
 * words, no motion, hidden from screen readers. It sits at the bottom, where the newest turn will appear; in a short
 * space the turns nearest the composer show and the rest is cut off at the top, never an overflow.
 *
 * States: loading (decorative)
 * Cap: Conversation (the detail pane's 700 dp)
 * Motion: none
 * Replaces: KitSkeletonTranscript (lib/ui/kit/kit_skeleton_transcript.dart)
 */
@Composable
public fun KitSkeletonTranscript(modifier: Modifier = Modifier, turns: Int = 2) { // F: lib/ui/kit/kit_skeleton_transcript.dart:18
    val tokens = LocalKitTokens.current
    val roles = LocalKitColors.current
    val promptHeight = Space.S6 + Space.S3
    val prompt =
        kitSurfaceLook(
            KitSurfaceInput(role = SurfaceRole.Content, shape = KitShapeId.Button, level = SurfaceLevel.Surface3, ownFill = roles.surface3),
        )
    val barShape = KitShapeId.Pill.shape()
    val column: @Composable () -> Unit = {
        Column(
            Modifier
                .widthIn(max = Layout.PANE_DETAIL_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(start = tokens.gutter.dp, top = tokens.gutter.dp, end = tokens.gutter.dp, bottom = (tokens.gutter + Space.S2).dp)
                .testTag("kit-skeleton-transcript"),
        ) {
            for (turn in 0 until turns) {
                if (turn > 0) Spacer(Modifier.height((Space.S4 + Space.S3).dp))
                Box(Modifier.fillMaxWidth().height(promptHeight.dp).then(prompt.modifier))
                Spacer(Modifier.height(Space.S4.dp))
                for (width in SKELETON_REPLY_WIDTHS[turn % SKELETON_REPLY_WIDTHS.size]) {
                    Box(
                        Modifier
                            .padding(bottom = Space.S2.dp)
                            .fillMaxWidth(width)
                            .height(Space.S2.dp)
                            .background(roles.surface3, barShape),
                    )
                }
            }
        }
    }
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        if (constraints.hasBoundedHeight) {
            // OverflowBox at the bottom: the nearest turns show, the rest is clipped at the top.
            Box(Modifier.fillMaxWidth().height(maxHeight).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), contentAlignment = Alignment.BottomCenter) { column() }
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { column() }
        }
    }
}
