package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitLtr
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitDecorated
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.compose.resources.stringResource

/**
 * The keys a phone keyboard lacks, for a terminal (Dart `TerminalBarKey`, lib/ui/kit/terminal_key_bar.dart; written for
 * this app: Termux's extra-keys view is GPLv3 and is not used). [face] is what the key shows; key caps are the same in
 * every language.
 */
@Immutable
public enum class KitTerminalBarKey(public val face: String) {
    Esc("Esc"), // F: lib/ui/kit/terminal_key_bar.dart:18
    Slash("/"), // F: lib/ui/kit/terminal_key_bar.dart:19
    Dash("-"), // F: lib/ui/kit/terminal_key_bar.dart:20
    Pipe("|"), // F: lib/ui/kit/terminal_key_bar.dart:21
    Home("Home"), // F: lib/ui/kit/terminal_key_bar.dart:22
    Up("↑"), // F: lib/ui/kit/terminal_key_bar.dart:23
    End("End"), // F: lib/ui/kit/terminal_key_bar.dart:24
    PageUp("PgUp"), // F: lib/ui/kit/terminal_key_bar.dart:25
    Tab("Tab"), // F: lib/ui/kit/terminal_key_bar.dart:26
    Ctrl("Ctrl"), // F: lib/ui/kit/terminal_key_bar.dart:27
    Alt("Alt"), // F: lib/ui/kit/terminal_key_bar.dart:28
    Tilde("~"), // F: lib/ui/kit/terminal_key_bar.dart:29
    Left("←"), // F: lib/ui/kit/terminal_key_bar.dart:30
    Down("↓"), // F: lib/ui/kit/terminal_key_bar.dart:31
    Right("→"), // F: lib/ui/kit/terminal_key_bar.dart:32
    PageDown("PgDn"), // F: lib/ui/kit/terminal_key_bar.dart:33
    /** Ctrl-C in one tap (a server terminal's first key). */
    Interrupt("^C"), // F: lib/ui/kit/terminal_key_bar.dart:36
    /** Ctrl-D in one tap. */
    EndOfInput("^D"); // F: lib/ui/kit/terminal_key_bar.dart:39

    public val isModifier: Boolean
        get() = this == Ctrl || this == Alt // F: lib/ui/kit/terminal_key_bar.dart:81

    /** The character a symbol key types, or null for a named key. */
    public val text: String?
        get() =
            when (this) {
                Slash -> "/" // F: lib/ui/kit/terminal_key_bar.dart:85
                Dash -> "-" // F: lib/ui/kit/terminal_key_bar.dart:86
                Pipe -> "|" // F: lib/ui/kit/terminal_key_bar.dart:87
                Tilde -> "~" // F: lib/ui/kit/terminal_key_bar.dart:88
                else -> null
            }

    public companion object {
        /** The two rows, as Termux users know them, plus `| ~`. */
        public val Rows: List<List<KitTerminalBarKey>> =
            listOf(
                listOf(Esc, Slash, Dash, Pipe, Home, Up, End, PageUp), // F: lib/ui/kit/terminal_key_bar.dart:48
                listOf(Tab, Ctrl, Alt, Tilde, Left, Down, Right, PageDown), // F: lib/ui/kit/terminal_key_bar.dart:49
            )

        /** The keys a bar with `interruptKeys` leads with, on a row of their own above [Rows]. */
        public val ExtraRow: List<KitTerminalBarKey> = listOf(Interrupt, EndOfInput) // F: lib/ui/kit/terminal_key_bar.dart:54

        /** The one-row bar's keys, in the order a hand reaches for them. */
        public val SingleRowKeys: List<KitTerminalBarKey> =
            listOf(
                Esc, Tab, Ctrl, Alt, Left, Up, Down, Right, Pipe, Tilde, Slash, Dash, Home, End, PageUp, PageDown, // F: lib/ui/kit/terminal_key_bar.dart:59-76
            )

        /** How many of [SingleRowKeys] share the width of a phone. */
        public const val SingleRowShown: Int = 11 // F: lib/ui/kit/terminal_key_bar.dart:79
    }
}

/** The order of the compact one-row bar (Dart `_compactOrder`): modifiers and arrows first, what a short screen needs most. */
private val compactOrder =
    listOf(
        KitTerminalBarKey.Esc, KitTerminalBarKey.Ctrl, KitTerminalBarKey.Alt, KitTerminalBarKey.Tab,
        KitTerminalBarKey.Left, KitTerminalBarKey.Up, KitTerminalBarKey.Down, KitTerminalBarKey.Right,
        KitTerminalBarKey.Slash, KitTerminalBarKey.Dash, KitTerminalBarKey.Pipe, KitTerminalBarKey.Tilde,
        KitTerminalBarKey.Home, KitTerminalBarKey.End, KitTerminalBarKey.PageUp, KitTerminalBarKey.PageDown, // F: lib/ui/kit/terminal_key_bar.dart:309-326
    )

/**
 * [text] as typed with Ctrl and/or Alt held (Dart `terminalModifiedText`): Ctrl turns a letter or one of `@[\]^_ ?`
 * into its control byte (Ctrl-C is 0x03), Alt puts Esc before it. Only the first character takes the modifiers; text
 * that has no control form (a digit under Ctrl) passes unchanged.
 */
public fun terminalModifiedText(
    text: String,
    ctrl: Boolean = false,
    alt: Boolean = false,
): String {
    if (text.isEmpty() || (!ctrl && !alt)) return text // F: lib/ui/kit/terminal_key_bar.dart:102
    val first = text.first()
    var head = first.toString()
    if (ctrl) {
        val control = controlByte(first.code) // NV: a terminal key is one BMP character (Dart reads a rune)
        if (control != null) head = control.toChar().toString()
    }
    if (alt) head = "\u001b$head" // F: lib/ui/kit/terminal_key_bar.dart:109 (Alt puts Esc before the key)
    return head + text.substring(1)
}

private fun controlByte(rune: Int): Int? =
    when (rune) {
        in 0x61..0x7a -> rune - 0x60 // a-z // F: lib/ui/kit/terminal_key_bar.dart:114
        in 0x40..0x5f -> rune - 0x40 // @ A-Z [ \ ] ^ _ // F: lib/ui/kit/terminal_key_bar.dart:115
        0x20 -> 0 // space: NUL, as in Termux and xterm // F: lib/ui/kit/terminal_key_bar.dart:116
        0x3f -> 0x7f // ?: DEL // F: lib/ui/kit/terminal_key_bar.dart:117
        else -> null
    }

/**
 * The sticky Ctrl and Alt of a [TerminalKeyBar] (Dart `TerminalKeyBarController`). A tap latches a modifier for the
 * next key, from the bar or the phone's keyboard; the key releases it. Compose state, so the bar redraws on a change.
 */
public class TerminalKeyBarController {
    private var _ctrl by mutableStateOf(false)
    private var _alt by mutableStateOf(false)

    public val ctrl: Boolean get() = _ctrl
    public val alt: Boolean get() = _alt

    public fun toggle(key: KitTerminalBarKey) {
        if (key == KitTerminalBarKey.Ctrl) _ctrl = !_ctrl // F: lib/ui/kit/terminal_key_bar.dart:132
        if (key == KitTerminalBarKey.Alt) _alt = !_alt // F: lib/ui/kit/terminal_key_bar.dart:133
    }

    /** The latched modifiers, released. */
    public fun take(): TerminalHeldModifiers {
        val held = TerminalHeldModifiers(_ctrl, _alt) // F: lib/ui/kit/terminal_key_bar.dart:139
        if (_ctrl || _alt) {
            _ctrl = false // F: lib/ui/kit/terminal_key_bar.dart:141
            _alt = false // F: lib/ui/kit/terminal_key_bar.dart:142
        }
        return held
    }

    /** Text from the phone's keyboard with the latched modifiers applied. */
    public fun apply(text: String): String {
        if (!_ctrl && !_alt) return text // F: lib/ui/kit/terminal_key_bar.dart:150
        val held = take()
        return terminalModifiedText(text, ctrl = held.ctrl, alt = held.alt)
    }
}

/** Dart's `({bool ctrl, bool alt})` record from [TerminalKeyBarController.take]. */
@Immutable
public data class TerminalHeldModifiers(public val ctrl: Boolean, public val alt: Boolean)

/**
 * Sends [key] to the terminal's input as a real keyboard would (Dart `sendTerminalBarKey`): symbol keys type their
 * character, Ctrl-C and Ctrl-D type their control bytes, and arrows with no modifier send the CSI/SS3 sequence the
 * cursor-keys mode picks — so programs in application-cursor mode (vim, less, htop) get the sequences they expect.
 * Returns null for the named-key path (the named keys with no modifiers), which the host terminal's own key input
 * handles (Dart `terminal.keyInput`, lib/ui/kit/terminal_key_bar.dart:180-207).
 */
public fun terminalBarKeyInput(
    key: KitTerminalBarKey,
    ctrl: Boolean = false,
    alt: Boolean = false,
    applicationCursorKeys: Boolean = false,
): String? {
    val text = key.text
    if (text != null) return terminalModifiedText(text, ctrl = ctrl, alt = alt) // F: lib/ui/kit/terminal_key_bar.dart:166-169
    val control =
        when (key) {
            KitTerminalBarKey.Interrupt -> "c" // F: lib/ui/kit/terminal_key_bar.dart:172
            KitTerminalBarKey.EndOfInput -> "d" // F: lib/ui/kit/terminal_key_bar.dart:173
            else -> null
        }
    if (control != null) return terminalModifiedText(control, ctrl = true, alt = alt) // F: lib/ui/kit/terminal_key_bar.dart:176-178
    val arrow =
        when (key) {
            KitTerminalBarKey.Up -> "A" // F: lib/ui/kit/terminal_key_bar.dart:197
            KitTerminalBarKey.Down -> "B" // F: lib/ui/kit/terminal_key_bar.dart:198
            KitTerminalBarKey.Right -> "C" // F: lib/ui/kit/terminal_key_bar.dart:199
            KitTerminalBarKey.Left -> "D" // F: lib/ui/kit/terminal_key_bar.dart:200
            else -> null
        }
    if (arrow != null && !ctrl && !alt) {
        return "\u001b[${if (applicationCursorKeys) "O" else "["}$arrow" // F: lib/ui/kit/terminal_key_bar.dart:203-205
    }
    return null
}

/** A key's height, and the least width of a key: `KitTokens.minTarget` (Dart `TerminalKeyBar.keyHeight`). */
internal const val TERMINAL_KEY_HEIGHT = 48f // F: lib/ui/kit/terminal_key_bar.dart:283

/** A key's width in the one-row form (Dart `TerminalKeyBar.compactKeyWidth`). */
internal const val TERMINAL_KEY_COMPACT_WIDTH = 56f // F: lib/ui/kit/terminal_key_bar.dart:286

/** The least width of a key in the one-row bar (Dart `TerminalKeyBar.singleRowMinWidth`). */
internal const val TERMINAL_KEY_SINGLE_ROW_MIN_WIDTH = 34f // F: lib/ui/kit/terminal_key_bar.dart:270

/** The accessible name of [key] (Dart `TerminalKeyBar._label`); every key of the bar has one. */
@Composable
private fun keyLabel(key: KitTerminalBarKey): String =
    stringResource(
        when (key) {
            KitTerminalBarKey.Esc -> Res.string.e7SetupEscapeKey // F: lib/ui/kit/terminal_key_bar.dart:289
            KitTerminalBarKey.Tab -> Res.string.e7SetupTabKey // F: lib/ui/kit/terminal_key_bar.dart:290
            KitTerminalBarKey.Up -> Res.string.e7SetupUpKey // F: lib/ui/kit/terminal_key_bar.dart:291
            KitTerminalBarKey.Down -> Res.string.e7SetupDownKey // F: lib/ui/kit/terminal_key_bar.dart:292
            KitTerminalBarKey.Left -> Res.string.e7SetupLeftKey // F: lib/ui/kit/terminal_key_bar.dart:293
            KitTerminalBarKey.Right -> Res.string.e7SetupRightKey // F: lib/ui/kit/terminal_key_bar.dart:294
            KitTerminalBarKey.Ctrl -> Res.string.localTerminalKeyCtrl // F: lib/ui/kit/terminal_key_bar.dart:295
            KitTerminalBarKey.Alt -> Res.string.localTerminalKeyAlt // F: lib/ui/kit/terminal_key_bar.dart:296
            KitTerminalBarKey.Home -> Res.string.kitTerminalViewKeyHome // F: lib/ui/kit/terminal_key_bar.dart:297
            KitTerminalBarKey.End -> Res.string.kitTerminalViewKeyEnd // F: lib/ui/kit/terminal_key_bar.dart:298
            KitTerminalBarKey.Slash -> Res.string.kitTerminalViewKeySlash // F: lib/ui/kit/terminal_key_bar.dart:299
            KitTerminalBarKey.Dash -> Res.string.kitTerminalViewKeyDash // F: lib/ui/kit/terminal_key_bar.dart:300
            KitTerminalBarKey.Pipe -> Res.string.kitTerminalViewKeyPipe // F: lib/ui/kit/terminal_key_bar.dart:301
            KitTerminalBarKey.Tilde -> Res.string.kitTerminalViewKeyTilde // F: lib/ui/kit/terminal_key_bar.dart:302
            KitTerminalBarKey.PageUp -> Res.string.localTerminalKeyPageUp // F: lib/ui/kit/terminal_key_bar.dart:303
            KitTerminalBarKey.PageDown -> Res.string.localTerminalKeyPageDown // F: lib/ui/kit/terminal_key_bar.dart:304
            KitTerminalBarKey.Interrupt -> Res.string.e7SetupInterruptKey // F: lib/ui/kit/terminal_key_bar.dart:305
            KitTerminalBarKey.EndOfInput -> Res.string.e7SetupEndInputKey // F: lib/ui/kit/terminal_key_bar.dart:306
        },
    )

/**
 * Two rows of terminal keys above the phone's keyboard, with sticky Ctrl and Alt (Dart `TerminalKeyBar`, the NAME-1
 * `KitTerminalKeyBar`; lib/ui/kit/terminal_key_bar.dart). [onKey] gets every non-modifier key with the modifiers that
 * were latched for it.
 *
 * States: enabled, disabled (with [disabledReason] as each key's hint), latched (Ctrl or Alt drawn selected until the
 * next key), compact (one row that scrolls sideways), singleRow (one row sharing the phone's width), interruptKeys
 * (Ctrl-C and Ctrl-D lead on a row of their own).
 *
 * Every key is at least [TERMINAL_KEY_HEIGHT] square (LAY-9), `space1` from the next. Where the window is too narrow
 * for eight such keys (under 412 dp) the rows scroll sideways together, so columns stay where a keyboard has them; on
 * a wider window the keys grow to fill it, capped at the reading width and centred. The keys are laid out left to
 * right in every language.
 */
@Composable
public fun TerminalKeyBar(
    controller: TerminalKeyBarController,
    onKey: (key: KitTerminalBarKey, ctrl: Boolean, alt: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true, // F: lib/ui/kit/terminal_key_bar.dart:234
    compact: Boolean = false, // F: lib/ui/kit/terminal_key_bar.dart:235
    interruptKeys: Boolean = false, // F: lib/ui/kit/terminal_key_bar.dart:236
    singleRow: Boolean = false, // F: lib/ui/kit/terminal_key_bar.dart:237
    disabledReason: String? = null, // F: lib/ui/kit/terminal_key_bar.dart:238
    safeArea: Boolean = false, // F: lib/ui/kit/terminal_key_bar.dart:239
) {
    val tokens = LocalKitTokens.current
    val side = max(TERMINAL_KEY_HEIGHT.dp, tokens.minTarget.dp) // F: lib/ui/kit/terminal_key_bar.dart:283,343
    val reason = disabledReason ?: stringResource(Res.string.e7SetupKeyUnavailable) // F: lib/ui/kit/terminal_key_bar.dart:344
    val barLabel = stringResource(Res.string.localTerminalKeysLabel) // F: lib/ui/kit/terminal_key_bar.dart:368
    val colors = LocalKitColors.current
    // Keys are space1 apart; each key's cap is its whole 48 dp target, so no two targets overlap and a screen
    // reader's node is the cap.
    val gap = Space.S1.dp // F: lib/ui/kit/terminal_key_bar.dart:347
    val compactKeys = buildList {
        if (interruptKeys) addAll(KitTerminalBarKey.ExtraRow) // F: lib/ui/kit/terminal_key_bar.dart:349
        addAll(compactOrder)
    }

    val latched: (KitTerminalBarKey) -> Boolean? = { key ->
        when (key) { // F: lib/ui/kit/terminal_key_bar.dart:356-360
            KitTerminalBarKey.Ctrl -> controller.ctrl
            KitTerminalBarKey.Alt -> controller.alt
            else -> null
        }
    }

    fun tap(key: KitTerminalBarKey) {
        // A key tap is a local choice: no haptic (MOT-11). A modifier latches; any other key releases what was held.
        if (key.isModifier) {
            controller.toggle(key) // F: lib/ui/kit/terminal_key_bar.dart:330-332
            return
        }
        val held = controller.take()
        onKey(key, held.ctrl, held.alt) // F: lib/ui/kit/terminal_key_bar.dart:334-335
    }

    if (singleRow && !compact) {
        // The caps sit on the screen's own ground, without a panel behind them.
        Box(modifier.semantics { contentDescription = barLabel }) {
            KitLtr {
                TerminalKeyBarSingleRow(
                    keys = KitTerminalBarKey.SingleRowKeys, // F: lib/ui/kit/terminal_key_bar.dart:375
                    height = side,
                    inset = gap, // F: lib/ui/kit/terminal_key_bar.dart:379
                ) { key ->
                    TerminalKeyCap(
                        key = key,
                        latched = latched(key),
                        onTap = if (enabled) ({ tap(key) }) else null,
                        disabledReason = reason,
                        calm = true, // F: lib/ui/kit/terminal_key_bar.dart:376
                    )
                }
            }
        }
        return
    }
    // The band the keys sit in: a track in the style's look, Solid's `surface2` under a hairline as it always was.
    val density = LocalDensity.current
    val hairlinePx = with(density) { (1f / density.density).dp.toPx() } // F: lib/ui/kit/terminal_key_bar.dart:392-395 lib/ui/kit/kit_tokens.dart:315-318
    val bandLook =
        terminalSurfaceLook(
            role = SurfaceRole.Track, // F: lib/ui/kit/terminal_key_bar.dart:398
            level = SurfaceLevel.Surface2, // F: lib/ui/kit/terminal_key_bar.dart:399
            radiusDp = 0f, // F: lib/ui/kit/terminal_key_bar.dart:400 (BorderRadius.zero)
            ownFill = colors.surface2, // F: lib/ui/kit/terminal_key_bar.dart:390
            color = null,
            selected = false,
            pressed = false,
        )
    val safe =
        if (safeArea) {
            // Keeps the keys out of the system insets below and beside the bar inside the bar's own band.
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) // F: lib/ui/kit/terminal_key_bar.dart:408-412
        } else {
            Modifier
        }
    Box(
        modifier
            .semantics { contentDescription = barLabel } // F: lib/ui/kit/terminal_key_bar.dart:402-404
            .then(bandLook.modifier)
            .drawBehind { drawLine(colors.hairline, Offset(0f, hairlinePx / 2), Offset(size.width, hairlinePx / 2), hairlinePx) }, // NV: the hairline stroke is centred on the top edge so it fills Flutter's Border top band (terminal_key_bar.dart:391-397)
    ) {
        KitLtr { // Keys sit where a keyboard has them in every language. // F: lib/ui/kit/terminal_key_bar.dart:415-417
            Column(
                safe
                    .padding(vertical = gap), // F: lib/ui/kit/terminal_key_bar.dart:418-419
                verticalArrangement = Arrangement.spacedBy(gap), // F: lib/ui/kit/terminal_key_bar.dart:475
            ) {
                if (compact) {
                    // Modifiers and arrows first: what a short screen needs most.
                    Row(
                        Modifier.height(side).horizontalScroll(rememberScrollState()), // F: lib/ui/kit/terminal_key_bar.dart:421-423
                        horizontalArrangement = Arrangement.spacedBy(gap), // F: lib/ui/kit/terminal_key_bar.dart:428
                    ) {
                        compactKeys.forEach { key ->
                            Box(Modifier.width(max(TERMINAL_KEY_COMPACT_WIDTH.dp, side))) { // F: lib/ui/kit/terminal_key_bar.dart:430
                                TerminalKeyCap(
                                    key = key,
                                    latched = latched(key),
                                    onTap = if (enabled) ({ tap(key) }) else null,
                                    disabledReason = reason,
                                    calm = false,
                                )
                            }
                        }
                    }
                } else {
                    TerminalKeyBarGrid(
                        rows =
                            buildList {
                                if (interruptKeys) add(KitTerminalBarKey.ExtraRow) // F: lib/ui/kit/terminal_key_bar.dart:477
                                addAll(KitTerminalBarKey.Rows) // F: lib/ui/kit/terminal_key_bar.dart:478-479
                            },
                        side = side,
                        gap = gap,
                    ) { key ->
                        TerminalKeyCap(
                            key = key,
                            latched = latched(key),
                            onTap = if (enabled) ({ tap(key) }) else null,
                            disabledReason = reason,
                            calm = false,
                        )
                    }
                }
            }
        }
    }
}

/** The full bar's rows (Dart `TerminalKeyBar`'s `LayoutBuilder`): eight columns, pixel-snapped, centred, or scrolling together. */
@Composable
private fun TerminalKeyBarGrid(
    rows: List<List<KitTerminalBarKey>>,
    side: Dp,
    gap: Dp,
    key: @Composable (KitTerminalBarKey) -> Unit,
) {
    BoxWithConstraints {
        val columns = KitTerminalBarKey.Rows.first().size // F: lib/ui/kit/terminal_key_bar.dart:437
        val room =
            if (constraints.hasBoundedWidth) {
                min(maxWidth, Layout.READING_WIDTH.dp) // F: lib/ui/kit/terminal_key_bar.dart:438-443
            } else {
                Layout.READING_WIDTH.dp
            }
        val needed = side * columns + gap * (columns - 1) // F: lib/ui/kit/terminal_key_bar.dart:444
        val fits = needed <= room // F: lib/ui/kit/terminal_key_bar.dart:445
        // Whole physical pixels: a fractional width puts every other cap edge on a half pixel, and it blurs (LOOK-21).
        val dpr = LocalDensity.current.density
        val width =
            if (fits) {
                (floor(((room - gap * (columns - 1)) / columns).value * dpr) / dpr).dp // F: lib/ui/kit/terminal_key_bar.dart:449-454
            } else {
                side // F: lib/ui/kit/terminal_key_bar.dart:454
            }
        val span =
            if (fits) {
                width * columns + gap * (columns - 1) // F: lib/ui/kit/terminal_key_bar.dart:455-456
            } else {
                needed // F: lib/ui/kit/terminal_key_bar.dart:457
            }
        // Centred, with the lead snapped to a pixel so the grid starts on a pixel boundary.
        val outer =
            if (constraints.hasBoundedWidth) {
                maxWidth // F: lib/ui/kit/terminal_key_bar.dart:493-495
            } else {
                span
            }
        val lead = (floor(((outer - span) / 2).value * dpr) / dpr).dp // F: lib/ui/kit/terminal_key_bar.dart:496-497
        Box(
            Modifier
                .align(Alignment.TopStart) // F: lib/ui/kit/terminal_key_bar.dart:498-499
                .then(if (outer > span) Modifier.padding(start = lead) else Modifier), // F: lib/ui/kit/terminal_key_bar.dart:501-504
        ) {
            Column(
                Modifier
                    .width(span) // F: lib/ui/kit/terminal_key_bar.dart:470-471
                    .then(if (!fits) Modifier.horizontalScroll(rememberScrollState()) else Modifier), // F: lib/ui/kit/terminal_key_bar.dart:483-490
                verticalArrangement = Arrangement.spacedBy(gap), // F: lib/ui/kit/terminal_key_bar.dart:475
            ) {
                rows.forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { // F: lib/ui/kit/terminal_key_bar.dart:458-461
                        keys.forEach { k ->
                            Box(Modifier.size(width, side)) { key(k) } // F: lib/ui/kit/terminal_key_bar.dart:462-467
                        }
                    }
                }
            }
        }
    }
}

/** `KitSurfaceLook.decorate` with the radius the part gives the style (Dart passes `borderRadius` straight through). */
@Composable
private fun terminalSurfaceLook(
    role: SurfaceRole,
    level: SurfaceLevel,
    radiusDp: Float,
    ownFill: Color?,
    color: Color?,
    selected: Boolean,
    pressed: Boolean,
): KitSurfaceLook {
    val state = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val r = StyleResolver.corner(radiusDp, state.knobs.corners, state.spec.tokens.sharpRadius) // F: lib/ui/kit/kit_surface_look.dart:185-199 (the knob mapping the resolver applies)
    val shape: Shape = if (r <= 0f) RectangleShape else RoundedCornerShape(r.dp)
    val request =
        remember(role, level, r, color, selected, pressed, state) {
            SurfaceRequest(
                roles = roles,
                role = role,
                knobs = state.knobs,
                level = level,
                radii = Radii.all(r),
                color = color?.let { Argb(it.toArgb()) },
                selected = selected,
                pressed = pressed,
            )
        }
    val decoration = remember(request, state) { state.spec.resolve(request) }
    val words = remember(request, state) { state.spec.onColor(request) }
    if (decoration == null) {
        // Solid keeps the part's own look: its fill under its shape.
        val modifier = if (ownFill == null) Modifier else Modifier.background(ownFill, shape) // F: lib/ui/kit/kit_surface_look.dart:171-172
        return KitSurfaceLook(modifier, words?.let { Color(it.v) }, shape)
    }
    return KitSurfaceLook(Modifier.kitDecorated(decoration), words?.let { Color(it.v) }, shape)
}

/**
 * One key (Dart `_Key`): a [KitTerminalBarKey.face] on a `surface3` cap, `accent` while latched, `text3` while
 * disabled. The cap is the whole target (at least 48 dp square), and its screen-reader node is exactly the cap; the
 * calm cap of the one-row bar sits inside its slot by half a step on each side.
 */
@Composable
private fun TerminalKeyCap(
    key: KitTerminalBarKey,
    latched: Boolean?,
    onTap: (() -> Unit)?,
    disabledReason: String,
    calm: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val enabled = onTap != null
    val on = latched == true
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    // Hover and pressed step the cap up by the hairline's tint: surface3 is the top surface step, so there is no role
    // above it (KitTappable.md).
    val fill =
        when {
            !enabled -> colors.surface3 // F: lib/ui/kit/terminal_key_bar.dart:690-691
            on -> colors.accent // F: lib/ui/kit/terminal_key_bar.dart:692-693
            hovered || pressed -> Color(roles.hairline.over(roles.surface3).v) // F: lib/ui/kit/terminal_key_bar.dart:694-695
            else -> colors.surface3
        }
    val calmFill =
        when {
            on -> colors.accent // F: lib/ui/kit/terminal_key_bar.dart:760-761
            hovered || pressed -> colors.surface3 // F: lib/ui/kit/terminal_key_bar.dart:762-763
            else -> colors.surface2 // F: lib/ui/kit/terminal_key_bar.dart:764
        }
    val tone =
        when {
            !enabled -> TextTone.Tertiary // F: lib/ui/kit/terminal_key_bar.dart:697-698
            on -> TextTone.OnAccent // F: lib/ui/kit/terminal_key_bar.dart:699-700
            else -> TextTone.Primary
        }
    val buttonRadius = (Shapes.of(KitShapeId.Button) as Corner.Radius).dp // F: lib/ui/kit/terminal_key_bar.dart:750-752 (tokens.buttonRadius)
    val capRadiusDp = if (calm) buttonRadius * .72f else buttonRadius.toFloat() // F: lib/ui/kit/terminal_key_bar.dart:750-752
    val radius = RoundedCornerShape(capRadiusDp)
    val density = LocalDensity.current.density
    val focusRingWidth = (if (density <= 1f) max(2f / density, 1f) else 2f / density).dp // F: lib/ui/kit/kit_tokens.dart:322-327 (tokens.focusRingWidth)
    val hairlineWidth = (1f / density).dp // F: lib/ui/kit/kit_tokens.dart:315-318 (tokens.hairlineWidth)
    // Hook H3: a latched cap's face takes the colour the style says reads on it, so a style that sets a latched key in
    // reverse keeps it legible; every other cap keeps its own tone. Resolved inside the cap, so the face keeps its
    // plate (hook H10).
    val capLook =
        terminalSurfaceLook(
            role = SurfaceRole.Control, // F: lib/ui/kit/terminal_key_bar.dart:778-779
            level = if (calm) SurfaceLevel.Surface2 else SurfaceLevel.Surface3, // F: lib/ui/kit/terminal_key_bar.dart:780
            radiusDp = capRadiusDp,
            ownFill = if (calm) calmFill else fill, // F: lib/ui/kit/terminal_key_bar.dart:757-764
            color = if (on) colors.accent else null, // F: lib/ui/kit/terminal_key_bar.dart:782
            selected = on, // F: lib/ui/kit/terminal_key_bar.dart:783
            pressed = enabled && pressed, // F: lib/ui/kit/terminal_key_bar.dart:784
        )
    val ring = focused && enabled // F: lib/ui/kit/terminal_key_bar.dart:749
    // The ring is drawn over the style's own face; the calm cap's hairline edge is drawn inside the cap, so no parent
    // clip cuts it (LOOK-21).
    val edge =
        when {
            ring -> Modifier.border(focusRingWidth, colors.accent, radius) // F: lib/ui/kit/terminal_key_bar.dart:753-756,786-794
            calm -> Modifier.border(hairlineWidth, if (on) colors.accent else colors.hairline, radius) // F: lib/ui/kit/terminal_key_bar.dart:769-773
            else -> Modifier
        }
    val label = keyLabel(key)
    // The calm cap sits inside its slot by half a step on each side; the slot stays the target, as a keyboard draws
    // its keys.
    val half = Space.S1.dp / 2 // F: lib/ui/kit/terminal_key_bar.dart:732
    Box(
        modifier
            .fillMaxSize()
            .semantics {
                role = Role.Button // F: lib/ui/kit/terminal_key_bar.dart:806
                contentDescription = label // F: lib/ui/kit/terminal_key_bar.dart:809
                if (!enabled) stateDescription = disabledReason // F: lib/ui/kit/terminal_key_bar.dart:810
                if (latched != null) toggleableState = if (latched) ToggleableState.On else ToggleableState.Off // F: lib/ui/kit/terminal_key_bar.dart:807
                if (on) selected = true
            }
            // The cap answers a touch on the next frame, and a quick tap still shows it; the whole slot is the target.
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = { onTap?.invoke() }), // F: lib/ui/kit/terminal_key_bar.dart:828-838
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(if (calm) Modifier.padding(horizontal = half, vertical = Space.S1.dp) else Modifier) // F: lib/ui/kit/terminal_key_bar.dart:733-736,840-845
                .then(capLook.modifier)
                .then(edge),
            contentAlignment = Alignment.Center,
        ) {
            // Named keys in the sans face; arrows and symbols in mono, whose glyphs exist in every language's theme.
            val textRole =
                if (calm && key.face.any { it in 'a'..'z' || it in 'A'..'Z' }) TypeRole.Secondary else TypeRole.Mono // F: lib/ui/kit/terminal_key_bar.dart:850-853
            // Caps stop growing at the terminal key text-scale cap so "PgDn" fits its key; the key's full name is in
            // its semantics (A11Y-8).
            ClampedTextScale(FlutterKitTokens.TERMINAL_KEY_MAX_TEXT_SCALE) { // F: lib/ui/kit/terminal_key_bar.dart:740-741
                FitScaleDown { // F: lib/ui/kit/terminal_key_bar.dart:744 (FittedBox scaleDown)
                    // A latched cap's words sit on its own opaque accent: no plate from the band around it.
                    val capColors = if (on && capLook.words != null) colors.copy(onAccent = capLook.words!!) else colors // F: lib/ui/kit/terminal_key_bar.dart:706-728,799-803
                    CompositionLocalProvider(LocalKitColors provides capColors) {
                        KitText(key.face, role = textRole, tone = tone, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** `MediaQuery.withClampedTextScaling` (the cap the Dart file names) over Compose's density font scale. */
@Composable
private fun ClampedTextScale(cap: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, cap))) { content() }
}

/** `FittedBox(fit: BoxFit.scaleDown)`: the child laid out unconstrained, then shrunk to fit the width there is. */
@Composable
private fun FitScaleDown(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout { constraints ->
        val placeable = subcompose(Unit) { content() }.first().measure(androidx.compose.ui.unit.Constraints())
        if (!constraints.hasBoundedWidth) {
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        } else {
            val scale = minOf(1f, constraints.maxWidth.toFloat() / maxOf(placeable.width, 1)) // NV: guard a zero-wide face
            layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
                placeable.placeRelativeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- looks-pass compatibility
// The three screens that predate the exact port (LocalTerminalScreen, RemoteTerminalScreen, TerminalScreen) keep these
// entry points; the exact rebuild above is what the Flutter part ports (terminal_key_bar.dart).

public data class KitTerminalKey(
    val label: String,
    val latched: Boolean? = null,
    val testTag: String? = null,
    val onClick: () -> Unit,
)

/** A visible, horizontally scrollable key bar shared by local and server terminals. */
@Composable
public fun KitTerminalKeyBar(
    keys: List<KitTerminalKey>,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("terminal-key-bar")
    ) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = key.onClick,
                role = if (key.latched == true) KitButtonRole.Primary else KitButtonRole.Secondary,
                enabled = enabled,
                modifier =
                    Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp) // F: lib/ui/kit/terminal_key_bar.dart:283 (keyHeight: a key's least side)
                        .let { m -> key.testTag?.let { m.testTag(it) } ?: m }
                        .semantics {
                            key.latched?.let {
                                toggleableState =
                                    if (it) ToggleableState.On else ToggleableState.Off
                            }
                        },
            )
        }
    }
}

/**
 * Ctrl-C and Ctrl-D as one-tap keys (Dart `interruptKeys`): screens with their own key list put
 * these first so a running command can always be stopped.
 */
@Composable
public fun kitTerminalInterruptKeys(onSend: (String) -> Unit): List<KitTerminalKey> =
    controlKeys(listOf(TerminalBarKey.Interrupt, TerminalBarKey.EndOfInput), onSend)

/**
 * The default control-byte bar (Ctrl-C, Ctrl-D, Esc, Tab) for terminals whose screen does not
 * supply its own keys. Direct bytes, never command-line text or a queued send; keys remain
 * reachable in narrow windows.
 */
@Composable
public fun KitTerminalKeyBar(
    onSend: (String) -> Unit,
    enabled: KitEnabled,
    modifier: Modifier = Modifier,
) {
    KitTerminalKeyBar(
        keys = controlKeys(TerminalBarKey.entries, onSend),
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun controlKeys(
    keys: List<TerminalBarKey>,
    onSend: (String) -> Unit,
): List<KitTerminalKey> =
    keys.map { key ->
        KitTerminalKey(
            label =
                stringResource(
                    when (key) {
                        TerminalBarKey.Interrupt -> Res.string.terminalParityInterrupt
                        TerminalBarKey.EndOfInput -> Res.string.terminalParityEndInput
                        TerminalBarKey.Escape -> Res.string.terminalParityEscape
                        TerminalBarKey.Tab -> Res.string.terminalParityTab
                    }
                ),
            testTag = "terminal-key-${key.name}",
            onClick = { onSend(TerminalText.keyBytes(key)) },
        )
    }
