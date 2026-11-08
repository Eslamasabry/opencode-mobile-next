package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.KitThemePreview
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * A theme's preview before it is applied (Dart `showThemePackPreview`, lib/ui/widgets/appearance_picker.dart:58-260):
 * the hint, the kit's picture of the theme, Light or Dark to look at it in, what the person's own light or dark
 * setting stays, then Apply, or "In use now" for the theme already in use. A theme the device cannot draw (Material
 * You without the platform's tones, [available] false) says so instead and offers nothing to apply.
 */
@Composable
internal fun ThemePreviewSheet(
    pack: PackId,
    ui: AppearanceUi,
    onDismiss: () -> Unit,
    onApply: (PackId) -> Unit,
    available: Boolean = pack != PackId.Dynamic || PackId.Dynamic in ui.packs,
) {
    // Flutter starts from the person's own setting; Follow Android shows the window's brightness.
    val windowDark = LocalKitColors.current.dark
    var dark by
        remember(pack) {
            mutableStateOf(
                when (ui.mode) {
                    AppearanceMode.System -> windowDark
                    AppearanceMode.Light -> false
                    AppearanceMode.Dark,
                    AppearanceMode.Saver -> true
                }
            )
        }
    val changed = pack != ui.pack
    KitSheet(
        visible = true,
        onDismissRequest = onDismiss,
        title = packName(pack),
        primary =
            if (available && changed)
                KitAction(stringResource(Res.string.e7AppearanceApply), { onApply(pack) })
            else null,
    ) {
        Column {
            KitText(
                stringResource(Res.string.e7AppearancePreviewHint),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S3.dp)) // F: lib/ui/widgets/appearance_picker.dart:163
            if (available) {
                KitThemePreview(
                    roles = ThemeResolver.roles(pack, null, dark, false, null),
                    label = stringResource(Res.string.appearancePickerPreviewLabel, packName(pack)),
                    modifier = Modifier.testTag("theme-component-preview"),
                )
            } else {
                KitNotice(
                    stringResource(Res.string.e7AppearanceDynamicUnavailable),
                    Modifier.testTag("appearance-dynamic-unavailable"),
                    icon = KitIcons.info,
                    liveRegion = false,
                )
            }
            Spacer(Modifier.height(Space.S4.dp)) // F: lib/ui/widgets/appearance_picker.dart:177
            if (available) {
                KitSegmented(
                    segments =
                        listOf(
                            KitSegment(false, stringResource(Res.string.e7AppearanceLight), icon = KitIcons.lightMode),
                            KitSegment(true, stringResource(Res.string.e7AppearanceDark), icon = KitIcons.darkMode),
                        ),
                    selected = dark,
                    semanticsLabel = stringResource(Res.string.appearancePickerPreviewIn),
                    onChange = { dark = it },
                )
                Spacer(Modifier.height(Space.S2.dp)) // F: lib/ui/widgets/appearance_picker.dart:201
                KitText(
                    stringResource(Res.string.e7AppearanceUsesMode, settingWords(ui.mode)),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            if (available && !changed) {
                Spacer(Modifier.height(Space.S4.dp)) // F: lib/ui/widgets/appearance_picker.dart:216
                KitNotice(
                    stringResource(Res.string.appearancePickerInUse),
                    Modifier.testTag("appearance-in-use"),
                    tone = StatusTone.Ok,
                    icon = KitIcons.check,
                    liveRegion = false,
                )
            }
        }
    }
}

/** The person's light or dark setting in Flutter's words (`appearanceLabel`): Auto reads "Follow Android" here. */
@Composable
private fun settingWords(mode: AppearanceMode): String =
    if (mode == AppearanceMode.System) stringResource(Res.string.e7AppearanceFollowAndroid) else modeWords(mode)
