package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.guide.KitAppearancePreview
import androidx.compose.runtime.MutableIntState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.LiveStrength
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.color.withPartner
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitLookOptionCard
import io.github.eslamasabry.oc.ui.kit.live.LiveLooks
import io.github.eslamasabry.oc.ui.kit.choice.KitPlainOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderStep
import io.github.eslamasabry.oc.ui.kit.choice.KitStepSlider
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentRow
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideQuestionHeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentMatchShort
import io.github.eslamasabry.oc.ui.words.res.appearanceAskAccent
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMode
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceAskShape
import io.github.eslamasabry.oc.ui.words.res.appearanceAskStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceAskTheme
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneCorners
import io.github.eslamasabry.oc.ui.design.effects.EdgeHold
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.kit.choice.KitGlassLookOption
import io.github.eslamasabry.oc.ui.kit.choice.KitGlassLookPicker
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneReset
import io.github.eslamasabry.oc.ui.words.res.appearanceModeAutoNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverHoldsStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSeeAllThemes
import io.github.eslamasabry.oc.ui.words.res.appearanceShapeSpacing
import io.github.eslamasabry.oc.ui.words.res.effectsMotionSystemOff
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The Appearance page (owner 2026-11-10: "redesign my appearance page ... clean both"). One page for everyone: a live
 * sample of the app at the top, then the four groups as tabs (Colours, Style, Background, Text & motion), each about
 * one screen of choices that apply at once. A new person gets the same page with the tour's bar at its foot
 * ([AppearanceGuideNext]) and "Skip the guide" under the choices; afterwards "Guide me again" sits there instead.
 * Saver holds Style and Background and says so in their tabs.
 */
@Composable
internal fun ColumnScope.AppearanceBody(ui: AppearanceUi, actions: SettingsActions, tab: MutableIntState) {
    val groups = appearanceTabs(ui)
    val at = tab.intValue.coerceIn(groups.indices)
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    Box(gutter) {
        KitAppearancePreview(
            ask = stringResource(Res.string.appearancePreviewAsk),
            reply = stringResource(Res.string.appearancePreviewReply),
            action = stringResource(Res.string.appearancePreviewAction),
            spoken = stringResource(Res.string.appearancePreviewSpoken),
            label = stringResource(Res.string.filesShowPreview),
            modifier = Modifier.testTag("appearance-preview"),
        )
    }
    val group = groups[at]
    if ((group == AppearanceGroup.Style || group == AppearanceGroup.Background) && ui.heldBySaver) {
        KitGroupNote(
            stringResource(
                if (group == AppearanceGroup.Style) Res.string.appearanceSaverHoldsStyle
                else Res.string.appearanceSaverHoldsBackground
            )
        )
    } else {
        group.questions
            // Strength has nothing to act on while the background is off: it appears with the first look.
            .filter { it in TAB_QUESTIONS && !(it == AppearanceQuestion.Strength && ui.liveLook == null) }
            .forEachIndexed { index, question ->
                AppearanceControls(ui, actions, question, onePage = true, top = if (index == 0) Space.S4.dp else Space.S6.dp)
            }
    }
    val guide = ui.guide ?: return
    Spacer(Modifier.height(Space.S4.dp))
    // A row, not a tertiary button: that drew as a bare heading with nothing to tell it is tappable.
    KitRowGroup {
        KitRow(
            title = stringResource(if (guide.done) Res.string.appearanceGuideAgain else Res.string.appearanceGuideSkip),
            modifier = Modifier.testTag(if (guide.done) "appearance-guide-again" else "appearance-guide-skip"),
            trailing = KitRowTrailing.Chevron,
            onClick = {
                if (guide.done) {
                    tab.intValue = 0
                    actions.onAppearanceAgain()
                } else actions.onAppearanceSkip()
            },
        )
    }
}

/**
 * The page's tab row. It is navigation for the page, so [SettingsFrame] pins it under the bar and the page's content
 * scrolls beneath it: it is whole at every scroll position.
 */
@Composable
internal fun AppearanceTabRow(ui: AppearanceUi, tab: MutableIntState) {
    val groups = appearanceTabs(ui)
    KitTabStrip(
        tabs = groups.map { KitTab(appearanceGroupWords(it)) },
        selected = tab.intValue.coerceIn(groups.indices),
        onSelected = { tab.intValue = it },
        modifier = Modifier.testTag("appearance-tabs"),
    )
}

/** The page's tabs, in order; Style only where the app draws styles. */
internal fun appearanceTabs(ui: AppearanceUi): List<AppearanceGroup> =
    listOfNotNull(
        AppearanceGroup.Colours,
        AppearanceGroup.Style.takeIf { ui.styles },
        AppearanceGroup.Background,
        AppearanceGroup.Comfort,
    )

/** The questions the tabs draw (the glow question still needs its presentation fields, evidence/44.md). */
private val TAB_QUESTIONS =
    setOf(
        AppearanceQuestion.Mode,
        AppearanceQuestion.Theme,
        AppearanceQuestion.AccentColour,
        AppearanceQuestion.Style,
        AppearanceQuestion.Shape,
        AppearanceQuestion.Look,
        AppearanceQuestion.Strength,
        AppearanceQuestion.Text,
        AppearanceQuestion.Motion,
    )

@Composable
private fun ColumnScope.AppearanceControls(
    ui: AppearanceUi,
    actions: SettingsActions,
    question: AppearanceQuestion?,
    onePage: Boolean = false,
    top: Dp = Space.S6.dp,
) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    if (question == null || question == AppearanceQuestion.Mode) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskMode),
            question,
            onePage,
            top,
        )
        Box(gutter) {
            // s33 theme-switch-premium plan step 3: the mode control slides, rims and morphs its pixel icons.
            KitModeSegmented(
                segments = AppearanceMode.entries.map { KitModeSegment(it, modeWords(it), icon = modeIcon(it)) },
                selected = ui.mode,
                semanticsLabel = stringResource(Res.string.appearanceAskMode),
                modifier = Modifier.testTag("appearance-mode"),
                onChange = actions::onMode,
            )
        }
        when (ui.mode) {
            AppearanceMode.System -> KitGroupNote(stringResource(Res.string.appearanceModeAutoNote))
            AppearanceMode.Saver -> KitGroupNote(stringResource(Res.string.appearanceSaverNote))
            else -> Unit
        }
    }

    if (question == null || question == AppearanceQuestion.Theme) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskTheme),
            question,
            onePage,
            top,
        )
        val guided = !onePage
        val columns = if (guided) 2 else 3 // F: lib/ui/screens/settings/appearance_screen.dart:904
        val base = if (guided) 84 else 96 // the picture of a theme needs the room (Flutter 64: appearance_screen.dart:905)
        val scaleHeight =
            30 * // F: lib/ui/screens/settings/appearance_screen.dart:905
                LocalDensity.current
                    .fontScale // F: lib/ui/screens/settings/appearance_screen.dart:905
        val picks =
            if (!guided && ui.seeAllCount > 0)
                ui.packs.take(6) // F: lib/ui/screens/settings/appearance_screen.dart:896
            else ui.packs // F: lib/ui/screens/settings/appearance_screen.dart:896
        val shown = if (ui.pack in picks || picks.isEmpty()) picks else picks.dropLast(1) + ui.pack
        Box(gutter) {
            AppearanceGrid(shown, columns, (base + scaleHeight).dp) { pack ->
                KitThemeOptionCard(
                    pack = pack,
                    label = packName(pack),
                    selected = ui.pack == pack,
                    onSelect = { actions.onPack(pack) },
                    modifier = Modifier.testTag("appearance-pack-${pack.name}"),
                )
            }
        }
        if (ui.seeAllCount > 0) {
            // Flutter's "See all" fills the column under the grid (lib/ui/screens/settings/
            // appearance_screen.dart:783-786, :933); the gutter sits outside the button so the kit's own
            // control cap does not shorten it.
            Box(Modifier.fillMaxWidth().padding(top = Space.S3.dp).then(gutter)) {
                KitButton(
                    stringResource(Res.string.appearanceSeeAllThemes, ui.seeAllCount),
                    actions::onSeeAllPacks,
                    Modifier.testTag("appearance-see-all"),
                    role = KitButtonRole.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
    if (question == null || question == AppearanceQuestion.AccentColour) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskAccent),
            question,
            onePage,
            top,
        )
        Box(gutter) {
            // Flutter's Accent: KitAccentRow.grid, "Match" first in the theme's own accent, then every swatch with its
            // name, four to a row, each guarded for this theme (lib/ui/screens/settings/appearance_screen.dart:946-962).
            val dark = LocalKitColors.current.dark
            val own = Packs.of(ui.pack).palette(dark).roles
            KitAccentRow(
                semanticsLabel = stringResource(Res.string.appearanceAskAccent),
                matchLabel = stringResource(Res.string.appearanceAccentMatchShort),
                themeAccent = Color(own.accent.v),
                swatches =
                    Accents.swatches.map { sw ->
                        KitAccentSwatch(sw.id, Color(own.withPartner(sw.of(dark)).partner.v), accentWords(sw.id))
                    },
                selected = ui.accent,
                onChanged = actions::onAccent,
                modifier = Modifier.testTag("appearance-accent"),
                grid = true,
            )
        }
        KitGroupNote(stringResource(Res.string.appearanceAccentMatchNote, packName(ui.pack)))
    }

    if (question == null || question == AppearanceQuestion.Style) {
        // A style the app does not draw would be a choice that changes nothing: listed only where
        // it is
        // drawn.
        if (ui.styles) {
            AppearanceControlHeading(
                stringResource(Res.string.appearanceAskStyle),
                question,
                onePage,
                top,
            )
            val styleHeld =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
                else KitEnabled.Enabled
            Box(gutter) {
                val columns = 2 // F: lib/ui/screens/settings/appearance_screen.dart:973
                AppearanceGrid(SurfaceStyle.entries, columns) { style ->
                    KitStyleOptionCard(
                        style = Style.valueOf(style.name),
                        label = styleWords(style),
                        selected = style == ui.style,
                        enabled = styleHeld.isEnabled,
                        disabledReason = styleHeld.reason,
                        onSelect = { actions.onStyle(style) },
                        modifier = Modifier.testTag("appearance-style-${style.name}"),
                    )
                }
            }
            if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))
        }
    }
    if ((question == null || question == AppearanceQuestion.Shape) && ui.styles) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskShape),
            question,
            onePage,
            top,
        )
        val held =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
            else KitEnabled.Enabled
        AppearanceKnobLabel(stringResource(Res.string.appearanceFineTuneCorners))
        Box(gutter) {
            KitSegmented(
                segments = Corners.entries.map { KitSegment(it, cornersWords(it)) },
                selected = ui.corners,
                semanticsLabel = stringResource(Res.string.appearanceFineTuneCorners),
                modifier = Modifier.testTag("appearance-corners"),
                enabled = held,
                onChange = actions::onCorners,
            )
        }
        AppearanceKnobLabel(stringResource(Res.string.appearanceShapeSpacing))
        Box(gutter) {
            KitSegmented(
                segments = Density.entries.map { KitSegment(it, spacingWords(it)) },
                selected = ui.spacing,
                semanticsLabel = stringResource(Res.string.appearanceShapeSpacing),
                modifier = Modifier.testTag("appearance-spacing"),
                enabled = held,
                onChange = actions::onSpacing,
            )
        }
        // Glass only: its look is a choice like Corners and Spacing, in plain sight (no row to a sheet, no second tap
        // on the Glass card).
        if (ui.style == SurfaceStyle.Glass) {
            val motionHeld = ui.glassLookHold == EdgeHold.Motion
            val lookHeld =
                when {
                    !held.isEnabled -> held
                    motionHeld -> KitEnabled.Disabled(stringResource(Res.string.appearanceGlassLookHeldMotion))
                    else -> KitEnabled.Enabled
                }
            AppearanceKnobLabel(stringResource(Res.string.appearanceGlassLook))
            Box(gutter) {
                KitSegmented(
                    segments = ui.glassLooks.map { KitSegment(it, glassLookWords(it)) },
                    selected = ui.glassLook.takeIf { it in ui.glassLooks } ?: ui.glassLooks.first(),
                    semanticsLabel = stringResource(Res.string.appearanceGlassLook),
                    modifier = Modifier.testTag("appearance-glass-look"),
                    enabled = lookHeld,
                    onChange = actions::onGlassLook,
                )
            }
            when {
                motionHeld -> KitGroupNote(stringResource(Res.string.appearanceGlassLookHeldMotion))
                ui.glassLookHold == EdgeHold.Platform -> KitGroupNote(stringResource(Res.string.appearanceGlassLookNeedsAndroid13))
                ui.glassLook == GlassLookId.Frosted -> KitGroupNote(stringResource(Res.string.appearanceGlassLookFrostedLighter))
                else -> Unit
            }
            // Preview builds: every number of the Glass material over a live preview.
            if (ui.glassLab) {
                KitRowGroup {
                    KitRow(
                        title = stringResource(Res.string.glassLabTitle),
                        modifier = Modifier.testTag("appearance-glass-lab"),
                        supporting = stringResource(Res.string.glassLabRowLine),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onOpen(io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage.GlassLab) },
                    )
                }
            }
        }
        val defaults = StyleResolver.spec(Style.valueOf(ui.style.name)).tokens.defaults
        KitGroupNote(
            stringResource(
                Res.string.appearanceShapeNote,
                styleWords(ui.style),
                cornersWords(defaults.corners),
                spacingWords(defaults.density),
            )
        )
        if (ui.fineTuned && !ui.heldBySaver) {
            KitButton(
                stringResource(Res.string.appearanceFineTuneReset, styleWords(ui.style)),
                actions::onResetFineTune,
                gutter.testTag("appearance-fine-tune-reset"),
                role = KitButtonRole.Tertiary,
                fullWidth = false,
            )
        }
    }

    if (question == null || question == AppearanceQuestion.Look) {
        // Background › Look (GLASS_REF appearance_screen.dart:1041-1083): Off and every look the app draws, each a
        // live sample behind a tiny conversation (E20-S28). A look the native app does not draw yet is not offered.
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskLook),
            question,
            onePage,
            top,
        )
        val held =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsBackground))
            else KitEnabled.Enabled
        val scale = LocalDensity.current.fontScale
        // Flutter lists Off and its picks until "See all" opens every look; the chosen look always shows (the one
        // page in the last place, so the rows stay full) (GLASS_REF appearance_screen.dart:1113-1126, :1155-1160).
        var allLooks by rememberSaveable { mutableStateOf(false) }
        val picks = LOOK_PICKS.filter(LiveLooks::draws).let { if (onePage) it.take(5) else it }
        val chosen = ui.liveLook?.takeIf(LiveLooks::draws)
        val looks =
            if (allLooks) LiveLooks.offered + listOfNotNull(chosen?.takeIf { it !in LiveLooks.offered })
            else
                picks.filter { !onePage || chosen == null || chosen in picks || it != picks.last() } +
                    listOfNotNull(chosen?.takeIf { it !in picks })
        Box(gutter) {
            AppearanceGrid(
                listOf(null) + looks,
                3, // FG: lib/ui/screens/settings/appearance_screen.dart:1055
                (136 + 44 * scale).dp, // FG: lib/ui/screens/settings/appearance_screen.dart:1055
            ) { look ->
                KitLookOptionCard(
                    look = look,
                    label = stringResource(if (look == null) Res.string.appearanceLookOff else LiveLooks.label(look)),
                    selected = ui.liveLook == look,
                    onSelect = { actions.onLiveLook(look) },
                    modifier = Modifier.fillMaxSize().testTag("appearance-look-${look ?: "off"}"),
                    enabled = held.isEnabled,
                    disabledReason = held.reason,
                )
            }
        }
        if (!allLooks) {
            Box(Modifier.fillMaxWidth().padding(top = Space.S3.dp).then(gutter)) {
                KitButton(
                    stringResource(Res.string.appearanceSeeAllLooks, LiveLooks.offered.size),
                    { allLooks = true },
                    Modifier.testTag("appearance-see-all-looks"),
                    role = KitButtonRole.Secondary,
                    fullWidth = true,
                )
            }
        }
        if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsBackground))
    }
    if (question == null || question == AppearanceQuestion.Strength) {
        // Background › How strong? (GLASS_REF appearance_screen.dart:1085-1117).
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskStrength),
            question,
            onePage,
            top,
        )
        if (ui.liveLook == null) {
            KitGroupNote(stringResource(Res.string.appearanceStrengthNeedsLook))
        } else {
            val held =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsBackground))
                else KitEnabled.Enabled
            Box(gutter) {
                KitSegmented(
                    segments = LiveStrength.entries.map { KitSegment(it, strengthWords(it)) },
                    selected = ui.liveStrength,
                    semanticsLabel = stringResource(Res.string.appearanceAskStrength),
                    modifier = Modifier.testTag("appearance-strength"),
                    enabled = held,
                    onChange = actions::onLiveStrength,
                )
            }
            KitGroupNote(stringResource(Res.string.appearanceIntensityHelp))
        }
    }

    if (question == null || question == AppearanceQuestion.Text) {
        // B-KIT-059: Default until chosen; applies at the app root at once, this page included.
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskText),
            question,
            onePage,
            top,
        )
        Box(gutter) {
            KitStepSlider(
                steps = AppTextSize.entries.map { KitSliderStep(it, textSizeWords(it)) },
                selected = ui.textSize,
                semanticsLabel = stringResource(Res.string.textSizeTitle),
                modifier = Modifier.testTag("appearance-text-size"),
                onChange = actions::onTextSize,
            )
        }
        KitGroupNote(stringResource(Res.string.appearanceTextHelp))
    }
    if (question == null || question == AppearanceQuestion.Motion) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskMotion),
            question,
            onePage,
            top,
        )
        val shownMotion = if (ui.heldBySaver) MotionLevel.Off else ui.motion
        val hold =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverMotion))
            else KitEnabled.Enabled
        val note =
            when {
                ui.heldBySaver -> stringResource(Res.string.appearanceSaverMotion)
                ui.systemMotionOff && ui.motion != MotionLevel.Off ->
                    stringResource(Res.string.effectsMotionSystemOff)
                else -> null
            }
        Box(gutter) {
            if (onePage)
                KitSegmented(
                    segments =
                        MotionLevel.entries.map {
                            KitSegment(it, motionWords(it), icon = motionIcon(it))
                        },
                    tall = true,
                    selected = shownMotion,
                    semanticsLabel = stringResource(Res.string.appearanceAskMotion),
                    modifier = Modifier.testTag("appearance-motion"),
                    enabled = hold,
                    onChange = actions::onMotion,
                )
            else
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    MotionLevel.entries.forEach { level ->
                        KitPlainOptionCard(
                            label = motionWords(level),
                            caption = motionHint(level),
                            icon = motionIcon(level),
                            selected = shownMotion == level,
                            onSelect = { actions.onMotion(level) },
                            enabled = hold,
                            modifier = Modifier.testTag("appearance-motion-${level.name}"),
                        )
                    }
                }
        }
        if (onePage) KitGroupNote(note ?: motionHint(shownMotion))
        else note?.let { KitGroupNote(it) }
    }
}

@Composable
private fun AppearanceKnobLabel(title: String) {
    KitText(
        title,
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = Space.S3.dp, bottom = Space.S2.dp),
        role = TypeRole.Label,
    )
}

@Composable
private fun AppearanceControlHeading(
    title: String,
    question: AppearanceQuestion?,
    onePage: Boolean,
    top: Dp,
) {
    if (onePage || question == null) {
        // The page names the choice ("Theme", "Shape"); the tour's questions went with its steps. A choice named as
        // its own tab is (Style on the Style tab) gets no second heading, only its room.
        val words = if (question != null) appearanceQuestionWords(question) else title
        if (question != null && words == appearanceGroupWords(question.group)) Spacer(Modifier.height(top))
        else AppearanceQuestionHeading(words, top)
        return
    }
    val changes =
        when (question) {
            AppearanceQuestion.Mode -> Res.string.appearanceChangesMode
            AppearanceQuestion.Theme -> Res.string.appearanceChangesTheme
            AppearanceQuestion.AccentColour -> Res.string.appearanceChangesAccent
            AppearanceQuestion.Style -> Res.string.appearanceChangesStyle
            AppearanceQuestion.Shape -> Res.string.appearanceChangesShape
            AppearanceQuestion.Look -> Res.string.appearanceChangesLook
            AppearanceQuestion.Strength -> Res.string.appearanceChangesStrength
            AppearanceQuestion.Text -> Res.string.appearanceChangesText
            AppearanceQuestion.Motion -> Res.string.appearanceChangesMotion
            AppearanceQuestion.Glow -> Res.string.appearanceChangesGlow
        }
    KitGuideQuestionHeading(
        title,
        stringResource(Res.string.kitGuideChanges),
        stringResource(changes),
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = Space.S4.dp, bottom = Space.S3.dp),
    )
}

/** The one-page question is a reading heading, not a muted section caption. */
@Composable
private fun AppearanceQuestionHeading(title: String, top: Dp = Space.S6.dp) {
    KitText(
        title,
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = top, bottom = Space.S3.dp)
            .semantics { heading() },
        role = TypeRole.RowTitle,
    )
}

/** Flutter's `_lookPicks`, in its order (GLASS_REF lib/ui/screens/settings/appearance_screen.dart:58-66). */
// The first row is the looks that are finished best, the owner's glow among them (2026-11-10); the rest are one tap away.
private val LOOK_PICKS = listOf("aurora", "glowSpill", "mesh", "silk", "topo", "lightRays", "dotRipple")

@Composable
private fun <T> AppearanceGrid(
    choices: List<T>,
    columns: Int,
    cellHeight: Dp? = null,
    cell: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                for (index in 0 until columns) {
                    Box(
                        Modifier.weight(1f)
                            .then(if (cellHeight != null) Modifier.height(cellHeight) else Modifier)
                    ) {
                        // By index, not getOrNull: a null choice (the Look picker's Off) is a cell too.
                        if (index < row.size) cell(row[index])
                    }
                }
            }
        }
    }
}

private fun motionIcon(level: MotionLevel) =
    when (level) {
        MotionLevel.Full -> KitIcons.sparkle
        MotionLevel.Calm -> KitIcons.waveform
        MotionLevel.Off -> KitIcons.stopCircle
    }

@Composable
internal fun strengthWords(strength: LiveStrength): String =
    stringResource(
        when (strength) {
            LiveStrength.Subtle -> Res.string.liveIntensitySubtle
            LiveStrength.Normal -> Res.string.liveIntensityNormal
            LiveStrength.Bold -> Res.string.liveIntensityBold
        }
    )

private fun modeIcon(mode: AppearanceMode) = when (mode) {
    AppearanceMode.System -> KitIcons.systemTheme
    AppearanceMode.Light -> KitIcons.lightMode
    AppearanceMode.Dark -> KitIcons.darkMode
    AppearanceMode.Saver -> KitIcons.leaf
}
