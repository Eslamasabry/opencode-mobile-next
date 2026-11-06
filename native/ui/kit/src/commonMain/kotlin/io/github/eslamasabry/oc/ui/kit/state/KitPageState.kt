package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Flutter's page-size KitStateView: centered in bounded room, natural height in a list, scrollable
 * at large text.
 */
@Composable
public fun KitPageState(
    kind: KitStateKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector,
    progress: KitProgress? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    since: TimeMark? = null,
    onSlow: List<KitAction> = emptyList(),
    prerequisite: Boolean = false,
    details: String? = null,
) {
    require(!prerequisite || primary != null)
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    var slow by
        remember(since) {
            mutableStateOf(since != null && StateViewRules.isSlow(since.elapsedNow()))
        }
    LaunchedEffect(since) {
        if (since != null && !slow) {
            delay((STATE_ESCALATE_AFTER - since.elapsedNow()).coerceAtLeast(Duration.ZERO))
            slow = true
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        val scroll = rememberScrollState()
        Box(
            Modifier.fillMaxWidth()
                .then(if (constraints.hasBoundedHeight) Modifier.verticalScroll(scroll) else Modifier)
                .heightIn(min = height)
                .padding(
                    horizontal = tokens.gutter.dp,
                    vertical = FlutterKitTokens.STATE_VERTICAL_PADDING.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.kitMaxWidth(CapRole.State).fillMaxWidth()) {
                KitSurface(
                    level = SurfaceLevel.Surface3,
                    shape = KitShapeId.Tile,
                    padding = KitSurfacePadding.None,
                ) {
                    Box(
                        Modifier.size(FlutterKitTokens.MARK_SIZE.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KitGlyph(
                            icon,
                            when (kind) {
                                KitStateKind.Loading,
                                KitStateKind.Working -> colors.accent
                                KitStateKind.Error -> colors.text1
                                KitStateKind.Finished -> colors.success
                                else -> colors.text2
                            },
                            FlutterKitTokens.MARK_ICON_SIZE.dp,
                        )
                    }
                }
                Spacer(Modifier.height(Space.S5.dp))
                KitText(title, Modifier.semantics { heading() }, role = TypeRole.Title)
                val words =
                    if (slow && since != null)
                        stringResource(
                            Res.string.kitSinceStillWaiting,
                            since
                                .elapsedNow()
                                .inWholeSeconds
                                .coerceAtLeast(STATE_ESCALATE_AFTER.inWholeSeconds),
                        )
                    else body
                if (words != null) {
                    Spacer(Modifier.height(Space.S2.dp))
                    KitText(words, tone = TextTone.Secondary)
                }
                if (progress != null) {
                    Spacer(Modifier.height(Space.S5.dp))
                    KitProgressView(progress)
                }
                if (primary != null || secondary != null) {
                    Spacer(Modifier.height(Space.S6.dp))
                    KitActionBlock(primary = primary, secondary = secondary)
                }
                if (details != null) {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitDetailsFold(
                        label = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        // Plain codes and short reasons; technical text goes through the Redactor's `text` slot.
                        notes = listOf(details),
                    )
                }
                if (slow && onSlow.isNotEmpty()) {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitActionBlock(tertiary = onSlow)
                }
            }
        }
    }
}
