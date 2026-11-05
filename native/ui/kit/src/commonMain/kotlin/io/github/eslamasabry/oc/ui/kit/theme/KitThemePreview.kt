package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewCode
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewPrimary
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewSecondary
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewSegment
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewTitle
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewWorking
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.tokens.Space

/** A contained colour preview: no system-bar changes, preference writes or density scaling. */
@Composable
public fun KitThemePreview(pack: PackId, dark: Boolean, content: @Composable () -> Unit) {
    val roles = remember(pack, dark) { ThemeResolver.roles(pack, null, dark, false, null) }
    val colors = remember(roles) { roles.toKitColors() }
    CompositionLocalProvider(LocalKitRoles provides roles, LocalKitColors provides colors) {
        MaterialTheme(colorScheme = MaterialBridge.scheme(roles)) {
            Box(Modifier.background(colors.ground).padding(Space.S4.dp)) { content() }
        }
    }
}

/**
 * A picture of a candidate theme (Dart `KitThemePreview`): a panel with a row (glyph, title, "Working" with the working
 * mark), a code line, the needs-you word, a selected segment and a primary and a secondary button, all drawn in [roles]
 * rather than the app's current theme. One semantics node labelled [label]; nothing inside it is interactive.
 *
 * Cap: reading
 */
@Composable
public fun KitThemePreview(roles: Roles, label: String, modifier: Modifier = Modifier) {
    val colors = remember(roles) { roles.toKitColors() }
    CompositionLocalProvider(LocalKitRoles provides roles, LocalKitColors provides colors) {
        val tokens = LocalKitTokens.current
        val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
        Box(modifier.kitMaxWidth(CapRole.Reading).clearAndSetSemantics { contentDescription = label }) {
            Column(Modifier.fillMaxWidth().then(look.modifier).padding(tokens.panelPadding.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(FlutterKitTokens.ICON_TILE_SIZE.dp), contentAlignment = Alignment.Center) { KitGlyph(KitIcons.terminal, colors.text2, tokens.smallIcon.dp) }
                    Spacer(Modifier.width(Space.S3.dp))
                    Column(Modifier.weight(1f)) {
                        KitText(stringResource(Res.string.kitThemePreviewTitle), role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
                        Spacer(Modifier.height(Space.S1.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KitWorkingMark(colors.accent, size = tokens.smallIcon.dp)
                            Spacer(Modifier.width(Space.S1.dp))
                            KitText(stringResource(Res.string.kitThemePreviewWorking), role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(Space.S3.dp))
                Box(
                    Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(FlutterKitTokens.CODE_RADIUS.dp)).padding(Space.S2.dp),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        KitText(KitBidi.ltr(stringResource(Res.string.kitThemePreviewCode)), role = TypeRole.Mono, kind = KitTextKind.Frame)
                    }
                }
                Spacer(Modifier.height(Space.S3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitGlyph(KitIcons.statusDot, colors.attention, tokens.smallIcon.dp)
                    Spacer(Modifier.width(Space.S1.dp))
                    KitText(stringResource(Res.string.kitThemePreviewNeedsYou), role = TypeRole.Secondary, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 1)
                }
                Spacer(Modifier.height(Space.S3.dp))
                Row(
                    Modifier.background(colors.surface3, KitShapeId.Pill.shape()).padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitGlyph(KitIcons.check, colors.accent, tokens.smallIcon.dp)
                    Spacer(Modifier.width(Space.S1.dp))
                    KitText(stringResource(Res.string.kitThemePreviewSegment), role = TypeRole.Label, kind = KitTextKind.Frame)
                }
                Spacer(Modifier.height(Space.S3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    Box(Modifier.weight(1f)) { KitButton(stringResource(Res.string.kitThemePreviewPrimary), onClick = {}, role = KitButtonRole.Primary, fullWidth = true) }
                    Box(Modifier.weight(1f)) { KitButton(stringResource(Res.string.kitThemePreviewSecondary), onClick = {}, role = KitButtonRole.Secondary, fullWidth = true) }
                }
            }
        }
    }
}
