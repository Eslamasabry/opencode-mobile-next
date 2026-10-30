import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xterm/core.dart' as xterm;

import '../../l10n/app_localizations.dart';
import 'kit_layout.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_text_halo.dart';
import 'kit_tokens.dart';

/// The keys a phone keyboard lacks, for a terminal
/// (docs/design/local-terminal-2026-09-24.md §3). Written for this app;
/// Termux's extra-keys view is GPLv3 and is not used.
enum TerminalBarKey {
  esc('Esc'),
  slash('/'),
  dash('-'),
  pipe('|'),
  home('Home'),
  up('↑'),
  end('End'),
  pageUp('PgUp'),
  tab('Tab'),
  ctrl('Ctrl'),
  alt('Alt'),
  tilde('~'),
  left('←'),
  down('↓'),
  right('→'),
  pageDown('PgDn'),

  /// Ctrl-C in one tap (a server terminal's first key).
  interrupt('^C'),

  /// Ctrl-D in one tap.
  endOfInput('^D');

  const TerminalBarKey(this.face);

  /// What the key shows. Key caps are the same in every language.
  final String face;

  /// The two rows, as Termux users know them, plus `| ~`.
  static const rows = [
    [esc, slash, dash, pipe, home, up, end, pageUp],
    [tab, ctrl, alt, tilde, left, down, right, pageDown],
  ];

  /// The keys a bar with `interruptKeys` leads with, on a row of their own
  /// above [rows].
  static const extraRow = [interrupt, endOfInput];

  /// The one-row bar (`TerminalKeyBar.singleRow`): the keys a phone keyboard
  /// lacks most, in the order a hand reaches for them. The first
  /// [singleRowShown] fill the width; the rest wait past the end of the row.
  static const singleRowKeys = [
    esc,
    tab,
    ctrl,
    alt,
    left,
    up,
    down,
    right,
    pipe,
    tilde,
    slash,
    dash,
    home,
    end,
    pageUp,
    pageDown,
  ];

  /// How many of [singleRowKeys] share the width of a phone.
  static const singleRowShown = 11;

  bool get isModifier => this == ctrl || this == alt;

  /// The character a symbol key types, or null for a named key.
  String? get text => switch (this) {
    slash => '/',
    dash => '-',
    pipe => '|',
    tilde => '~',
    _ => null,
  };
}

/// [text] as typed with Ctrl and/or Alt held: Ctrl turns a letter or one of
/// `@[\]^_ ?` into its control byte (Ctrl-C is 0x03), Alt puts Esc before
/// it. Only the first character takes the modifiers; text that has no
/// control form (a digit under Ctrl) passes unchanged.
String terminalModifiedText(
  String text, {
  bool ctrl = false,
  bool alt = false,
}) {
  if (text.isEmpty || (!ctrl && !alt)) return text;
  final runes = text.runes.toList();
  var first = String.fromCharCode(runes.first);
  if (ctrl) {
    final control = _controlByte(runes.first);
    if (control != null) first = String.fromCharCode(control);
  }
  if (alt) first = '\x1b$first';
  return first + String.fromCharCodes(runes.skip(1));
}

int? _controlByte(int rune) {
  if (rune >= 0x61 && rune <= 0x7a) return rune - 0x60; // a-z
  if (rune >= 0x40 && rune <= 0x5f) return rune - 0x40; // @ A-Z [ \ ] ^ _
  if (rune == 0x20) return 0; // space: NUL, as in Termux and xterm
  if (rune == 0x3f) return 0x7f; // ?: DEL
  return null;
}

/// The sticky Ctrl and Alt of a [TerminalKeyBar]. A tap latches a modifier
/// for the next key, from the bar or the phone's keyboard; the key releases
/// it.
class TerminalKeyBarController extends ChangeNotifier {
  bool _ctrl = false;
  bool _alt = false;

  bool get ctrl => _ctrl;
  bool get alt => _alt;

  void toggle(TerminalBarKey key) {
    if (key == TerminalBarKey.ctrl) _ctrl = !_ctrl;
    if (key == TerminalBarKey.alt) _alt = !_alt;
    notifyListeners();
  }

  /// The latched modifiers, released.
  ({bool ctrl, bool alt}) take() {
    final held = (ctrl: _ctrl, alt: _alt);
    if (_ctrl || _alt) {
      _ctrl = false;
      _alt = false;
      notifyListeners();
    }
    return held;
  }

  /// Text from the phone's keyboard with the latched modifiers applied.
  String apply(String text) {
    if (!_ctrl && !_alt) return text;
    final held = take();
    return terminalModifiedText(text, ctrl: held.ctrl, alt: held.alt);
  }
}

/// Sends [key] to [terminal] as a real keyboard would, so programs in
/// application-cursor mode (vim, less, htop) get the sequences they expect.
/// Everything goes through the terminal's input, so a server terminal
/// (whose output goes to its socket) and a phone shell behave alike.
void sendTerminalBarKey(
  xterm.Terminal terminal,
  TerminalBarKey key, {
  bool ctrl = false,
  bool alt = false,
}) {
  final text = key.text;
  if (text != null) {
    terminal.textInput(terminalModifiedText(text, ctrl: ctrl, alt: alt));
    return;
  }
  final control = switch (key) {
    TerminalBarKey.interrupt => 'c',
    TerminalBarKey.endOfInput => 'd',
    _ => null,
  };
  if (control != null) {
    terminal.textInput(terminalModifiedText(control, ctrl: true, alt: alt));
    return;
  }
  final named = switch (key) {
    TerminalBarKey.esc => xterm.TerminalKey.escape,
    TerminalBarKey.tab => xterm.TerminalKey.tab,
    TerminalBarKey.home => xterm.TerminalKey.home,
    TerminalBarKey.end => xterm.TerminalKey.end,
    TerminalBarKey.pageUp => xterm.TerminalKey.pageUp,
    TerminalBarKey.pageDown => xterm.TerminalKey.pageDown,
    TerminalBarKey.up => xterm.TerminalKey.arrowUp,
    TerminalBarKey.down => xterm.TerminalKey.arrowDown,
    TerminalBarKey.left => xterm.TerminalKey.arrowLeft,
    TerminalBarKey.right => xterm.TerminalKey.arrowRight,
    _ => null,
  };
  if (named == null) return;
  // xterm 4.0.0 reads application cursor keys from the keypad mode, so a
  // program that sets only DECCKM would get the wrong arrows from it.
  final arrow = switch (key) {
    TerminalBarKey.up => 'A',
    TerminalBarKey.down => 'B',
    TerminalBarKey.right => 'C',
    TerminalBarKey.left => 'D',
    _ => null,
  };
  if (arrow != null && !ctrl && !alt) {
    terminal.textInput('\x1b${terminal.cursorKeysMode ? 'O' : '['}$arrow');
    return;
  }
  terminal.keyInput(named, ctrl: ctrl, alt: alt);
}

/// The NAME-1 name for the bar (kit v2 §9.2).
typedef KitTerminalKeyBar = TerminalKeyBar;

/// Two rows of terminal keys above the phone's keyboard, with sticky Ctrl
/// and Alt. [onKey] gets every non-modifier key with the modifiers that
/// were latched for it.
///
/// States: enabled, disabled (with [disabledReason] as each key's hint),
/// latched (Ctrl or Alt drawn selected until the next key), compact (one
/// row that scrolls sideways).
///
/// Every key is at least [keyHeight] square (LAY-9), `space1` from the
/// next. Where the window is too narrow for eight such keys (under 412 dp)
/// the rows scroll sideways together, so columns stay where a keyboard has
/// them; on a wider window the keys grow to fill it, capped at
/// `KitLayout.readingWidth` and centred. The keys are laid out left to
/// right in every language.
///
/// Retired by kit-hygiene: use KitTerminalKeyBar.
class TerminalKeyBar extends StatelessWidget {
  const TerminalKeyBar({
    super.key,
    required this.controller,
    required this.onKey,
    this.enabled = true,
    this.compact = false,
    this.interruptKeys = false,
    this.singleRow = false,
    this.disabledReason,
    this.safeArea = false,
  });

  final TerminalKeyBarController controller;
  final void Function(
    TerminalBarKey key, {
    required bool ctrl,
    required bool alt,
  })
  onKey;
  final bool enabled;

  /// One row that scrolls sideways instead of two, for a short screen
  /// (landscape with the keyboard up).
  final bool compact;

  /// Leads the bar with Ctrl-C and Ctrl-D ([TerminalBarKey.extraRow]): a
  /// server terminal's keys.
  final bool interruptKeys;

  /// One row of keys instead of two, sharing the width the way a phone
  /// keyboard's keys do ([TerminalBarKey.singleRowKeys]): the first eleven
  /// fill the row, the rest follow when it slides. Each key's target is its
  /// whole slot, the full bar height and at least [singleRowMinWidth] wide,
  /// with the cap drawn inside it. Where eleven keys would be narrower than
  /// that (a narrow window) the row keeps that width and slides. The caps
  /// sit on the screen's own ground, without a panel behind them.
  final bool singleRow;

  /// The least width of a key in the one-row bar. A phone keyboard's own
  /// letter keys are this narrow; the target is the full 48 dp tall.
  static const singleRowMinWidth = 34.0;

  /// What each key's hint says while the bar is disabled; by default
  /// "Unavailable while the terminal is disconnected".
  final String? disabledReason;

  /// Keeps the keys out of the system insets below and beside the bar
  /// (a [SafeArea] without its top) inside the bar's own band, so the band
  /// reaches the screen's edge as one surface. Not for [singleRow], which
  /// has no band.
  final bool safeArea;

  /// A key's height, and the least width of a key: `KitTokens.minTarget`.
  static const keyHeight = 48.0;

  /// A key's width in the one-row form.
  static const compactKeyWidth = 56.0;

  String _label(AppLocalizations l10n, TerminalBarKey key) => switch (key) {
    TerminalBarKey.esc => l10n.e7SetupEscapeKey,
    TerminalBarKey.tab => l10n.e7SetupTabKey,
    TerminalBarKey.up => l10n.e7SetupUpKey,
    TerminalBarKey.down => l10n.e7SetupDownKey,
    TerminalBarKey.left => l10n.e7SetupLeftKey,
    TerminalBarKey.right => l10n.e7SetupRightKey,
    TerminalBarKey.ctrl => l10n.localTerminalKeyCtrl,
    TerminalBarKey.alt => l10n.localTerminalKeyAlt,
    TerminalBarKey.home => l10n.kitTerminalViewKeyHome,
    TerminalBarKey.end => l10n.kitTerminalViewKeyEnd,
    TerminalBarKey.slash => l10n.kitTerminalViewKeySlash,
    TerminalBarKey.dash => l10n.kitTerminalViewKeyDash,
    TerminalBarKey.pipe => l10n.kitTerminalViewKeyPipe,
    TerminalBarKey.tilde => l10n.kitTerminalViewKeyTilde,
    TerminalBarKey.pageUp => l10n.localTerminalKeyPageUp,
    TerminalBarKey.pageDown => l10n.localTerminalKeyPageDown,
    TerminalBarKey.interrupt => l10n.e7SetupInterruptKey,
    TerminalBarKey.endOfInput => l10n.e7SetupEndInputKey,
  };

  static const _compactOrder = [
    TerminalBarKey.esc,
    TerminalBarKey.ctrl,
    TerminalBarKey.alt,
    TerminalBarKey.tab,
    TerminalBarKey.left,
    TerminalBarKey.up,
    TerminalBarKey.down,
    TerminalBarKey.right,
    TerminalBarKey.slash,
    TerminalBarKey.dash,
    TerminalBarKey.pipe,
    TerminalBarKey.tilde,
    TerminalBarKey.home,
    TerminalBarKey.end,
    TerminalBarKey.pageUp,
    TerminalBarKey.pageDown,
  ];

  // A key tap is a local choice: no haptic (MOT-11).
  void _tap(TerminalBarKey key) {
    if (key.isModifier) {
      controller.toggle(key);
      return;
    }
    final held = controller.take();
    onKey(key, ctrl: held.ctrl, alt: held.alt);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final side = math.max(keyHeight, tokens.minTarget);
    final reason = disabledReason ?? l10n.e7SetupKeyUnavailable;
    // Keys are space1 apart; each key's cap is its whole 48 dp target, so
    // no two targets overlap and a screen reader's node is the cap.
    final gap = tokens.space1;
    final compactKeys = [
      if (interruptKeys) ...TerminalBarKey.extraRow,
      ..._compactOrder,
    ];
    Widget keyOf(TerminalBarKey key, {bool calm = false}) => _Key(
      key: ValueKey('terminal-key-${key.name}'),
      face: key.face,
      label: _label(l10n, key),
      latched: switch (key) {
        TerminalBarKey.ctrl => controller.ctrl,
        TerminalBarKey.alt => controller.alt,
        _ => null,
      },
      onTap: enabled ? () => _tap(key) : null,
      disabledReason: reason,
      calm: calm,
    );
    if (singleRow && !compact) {
      return Semantics(
        container: true,
        label: l10n.localTerminalKeysLabel,
        child: ListenableBuilder(
          listenable: controller,
          builder: (context, _) => Directionality(
            textDirection: TextDirection.ltr,
            child: _SingleRow(
              keys: [
                for (final key in TerminalBarKey.singleRowKeys)
                  keyOf(key, calm: true),
              ],
              height: side,
              inset: tokens.space1,
            ),
          ),
        ),
      );
    }
    // The band the keys sit in: a track in the style's look, Solid's
    // `surface2` under a hairline as it always was.
    final band = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(
        color: roles.surface2,
        border: Border(
          top: BorderSide(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      role: KitSurfaceRole.track,
      level: KitSurfaceLevel.surface2,
      borderRadius: BorderRadius.zero,
    );
    return Semantics(
      container: true,
      label: l10n.localTerminalKeysLabel,
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: band,
        child: SafeArea(
          top: false,
          left: safeArea,
          right: safeArea,
          bottom: safeArea,
          child: ListenableBuilder(
            listenable: controller,
            builder: (context, _) => Directionality(
              // Keys sit where a keyboard has them in every language.
              textDirection: TextDirection.ltr,
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: gap),
                child: compact
                    ? SizedBox(
                        height: side,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          // Modifiers and arrows first: what a short screen
                          // needs most.
                          itemCount: compactKeys.length,
                          separatorBuilder: (_, _) => SizedBox(width: gap),
                          itemBuilder: (_, i) => SizedBox(
                            width: math.max(compactKeyWidth, side),
                            child: keyOf(compactKeys[i]),
                          ),
                        ),
                      )
                    : LayoutBuilder(
                        builder: (context, constraints) {
                          final columns = TerminalBarKey.rows.first.length;
                          final room = constraints.hasBoundedWidth
                              ? math.min(
                                  constraints.maxWidth,
                                  KitLayout.readingWidth,
                                )
                              : KitLayout.readingWidth;
                          final needed = side * columns + gap * (columns - 1);
                          final fits = needed <= room;
                          // Whole physical pixels: a fractional width puts
                          // every other cap edge on a half pixel, and it
                          // blurs (LOOK-21).
                          final dpr = MediaQuery.devicePixelRatioOf(context);
                          final width = fits
                              ? ((room - gap * (columns - 1)) / columns * dpr)
                                        .floorToDouble() /
                                    dpr
                              : side;
                          final span = fits
                              ? width * columns + gap * (columns - 1)
                              : needed;
                          Widget row(List<TerminalBarKey> keys) => Row(
                            mainAxisSize: MainAxisSize.min,
                            spacing: gap,
                            children: [
                              for (final key in keys)
                                SizedBox(
                                  width: width,
                                  height: side,
                                  child: keyOf(key),
                                ),
                            ],
                          );
                          final Widget grid = SizedBox(
                            width: span,
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              spacing: gap,
                              children: [
                                if (interruptKeys) row(TerminalBarKey.extraRow),
                                for (final keys in TerminalBarKey.rows)
                                  row(keys),
                              ],
                            ),
                          );
                          if (!fits) {
                            // Too narrow for eight 48 dp keys: the rows scroll
                            // together rather than shrink the keys.
                            return SingleChildScrollView(
                              scrollDirection: Axis.horizontal,
                              child: grid,
                            );
                          }
                          // Centred, with the lead snapped to a pixel so the
                          // grid starts on a pixel boundary.
                          final outer = constraints.hasBoundedWidth
                              ? constraints.maxWidth
                              : span;
                          final lead =
                              ((outer - span) / 2 * dpr).floorToDouble() / dpr;
                          return Align(
                            alignment: AlignmentDirectional.topStart,
                            heightFactor: 1,
                            child: Padding(
                              padding: outer > span
                                  ? EdgeInsetsDirectional.only(start: lead)
                                  : EdgeInsets.zero,
                              child: grid,
                            ),
                          );
                        },
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The one-row bar's layout: [TerminalBarKey.singleRowShown] slots share the
/// width (each at least [TerminalKeyBar.singleRowMinWidth]), whole physical
/// pixels wide; the rest follow and the row slides. The end fades while
/// more keys wait past it.
class _SingleRow extends StatefulWidget {
  const _SingleRow({
    required this.keys,
    required this.height,
    required this.inset,
  });

  final List<Widget> keys;
  final double height;
  final double inset;

  @override
  State<_SingleRow> createState() => _SingleRowState();
}

class _SingleRowState extends State<_SingleRow> {
  final _scroll = ScrollController();
  bool _more = true;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_moved);
  }

  void _moved() {
    final more =
        !_scroll.hasClients ||
        _scroll.position.pixels < _scroll.position.maxScrollExtent - 1;
    if (more != _more) setState(() => _more = more);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final dpr = MediaQuery.devicePixelRatioOf(context);
        final full = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : KitLayout.readingWidth;
        // A key grows with the window up to a target and a half wide; where
        // every key then fits (a tablet, a PC window) none waits off the
        // end, and the row centres without sliding.
        final widest = tokens.minTarget * 1.5;
        final share = (full - widget.inset * 2) / TerminalBarKey.singleRowShown;
        final width =
            (math.max(
                      TerminalKeyBar.singleRowMinWidth,
                      math.min(share, widest),
                    ) *
                    dpr)
                .floorToDouble() /
            dpr;
        final all = width * widget.keys.length + widget.inset * 2;
        if (all <= full) {
          return Center(
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: widget.inset),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final key in widget.keys)
                    SizedBox(width: width, height: widget.height, child: key),
                ],
              ),
            ),
          );
        }
        final outer = full;
        final row = SingleChildScrollView(
          controller: _scroll,
          scrollDirection: Axis.horizontal,
          padding: EdgeInsets.symmetric(horizontal: widget.inset),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final key in widget.keys)
                SizedBox(width: width, height: widget.height, child: key),
            ],
          ),
        );
        // A mask: only the alpha counts, so any opaque role works.
        final solid = tokens.roles.ground.withValues(alpha: 1);
        final edge = outer <= 0 ? 0.0 : (tokens.gutter / outer).clamp(0.0, .5);
        return Center(
          child: SizedBox(
            width: outer,
            child: ShaderMask(
              blendMode: BlendMode.dstIn,
              shaderCallback: (bounds) => LinearGradient(
                colors: [
                  solid,
                  solid,
                  _more ? solid.withValues(alpha: 0) : solid,
                ],
                stops: [0, 1 - edge, 1],
              ).createShader(bounds),
              child: row,
            ),
          ),
        );
      },
    );
  }
}

/// One key: a [TerminalBarKey.face] on a `surface3` cap, `accent` while
/// latched, `text3` while disabled. The cap is the whole target (at least
/// 48 dp square), and its screen-reader node is exactly the cap.
class _Key extends StatefulWidget {
  const _Key({
    super.key,
    required this.face,
    required this.label,
    required this.latched,
    required this.onTap,
    required this.disabledReason,
    this.calm = false,
  });

  /// The one-row bar's cap: a `surface2` cap with a hairline edge, drawn
  /// inside its slot (the slot is the target), the face in the sans face.
  final bool calm;

  final String face;
  final String label;

  /// Null for a key that does not latch.
  final bool? latched;
  final VoidCallback? onTap;
  final String disabledReason;

  @override
  State<_Key> createState() => _KeyState();
}

class _KeyState extends State<_Key> {
  bool _hovered = false;
  bool _focused = false;
  // The cap answers a touch on the next frame (KitPressTracker), and a
  // quick tap still shows it.
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final onTap = widget.onTap;
    final enabled = onTap != null;
    final on = widget.latched == true;
    // Hover and pressed step the cap up by the hairline's tint: surface3 is
    // the top surface step, so there is no role above it (KitTappable.md).
    final fill = !enabled
        ? roles.surface3
        : on
        ? roles.accent
        : _hovered || _press.shown
        ? Color.alphaBlend(roles.hairline, roles.surface3)
        : roles.surface3;
    final tone = !enabled
        ? KitTextTone.tertiary
        : on
        ? KitTextTone.onAccent
        : KitTextTone.primary;
    // Hook H3: a latched cap's face takes the colour the style says reads
    // on it, so a style that sets a latched key in reverse keeps it
    // legible; every other cap keeps its own tone. Resolved inside the
    // cap, so the face keeps its plate (hook H10).
    Widget label(KitTextRole role) => on
        ? Builder(
            builder: (context) {
              final own = KitText.styleOf(context, role, tone: tone);
              final ink = KitSurfaceLook.wordsOn(
                context,
                fallback: own.color!,
                role: KitSurfaceRole.control,
                level: widget.calm
                    ? KitSurfaceLevel.surface2
                    : KitSurfaceLevel.surface3,
                color: roles.accent,
                selected: true,
                pressed: enabled && _press.shown,
              );
              return Text(
                widget.face,
                style: own.copyWith(color: ink),
                maxLines: 1,
              );
            },
          )
        : KitText(widget.face, role: role, tone: tone, maxLines: 1);

    // The calm cap sits inside its slot by half a step on each side (so a
    // step between caps), its face kept off the cap's edge the same way.
    final half = tokens.space1 / 2;
    final capInset = EdgeInsetsDirectional.symmetric(
      horizontal: half,
      vertical: tokens.space1,
    );
    final faceInset = EdgeInsetsDirectional.symmetric(horizontal: half);
    // Caps stop growing at 1.3x so "PgDn" fits its key; the key's full name
    // is in its semantics (A11Y-8).
    Widget face(KitTextRole role) => MediaQuery.withClampedTextScaling(
      maxScaleFactor: KitTokens.terminalKeyMaxTextScale,
      child: Padding(
        padding: widget.calm ? faceInset : EdgeInsets.zero,
        child: FittedBox(fit: BoxFit.scaleDown, child: label(role)),
      ),
    );
    // The cap is a control in the style's look; Solid keeps its own: the
    // calm cap a `surface2` key under a hairline, the full cap [fill].
    final ring = _focused && enabled;
    final radius = BorderRadius.circular(
      widget.calm ? tokens.buttonRadius * .72 : tokens.buttonRadius,
    );
    final ringSide = BorderSide(
      color: roles.accent,
      width: KitTokens.focusRingWidth(context),
    );
    final solid = BoxDecoration(
      color: !widget.calm
          ? fill
          : on
          ? roles.accent
          : _hovered || _press.shown
          ? roles.surface3
          : roles.surface2,
      borderRadius: radius,
      // Drawn inside the cap, so no parent clip cuts it (LOOK-21).
      border: ring
          ? Border.fromBorderSide(ringSide)
          : widget.calm
          ? Border.all(
              color: on ? roles.accent : roles.hairline,
              width: KitTokens.hairlineWidth(context),
            )
          : null,
    );
    final look = KitSurfaceLook.decorate(
      context,
      solid: solid,
      role: KitSurfaceRole.control,
      level: widget.calm ? KitSurfaceLevel.surface2 : KitSurfaceLevel.surface3,
      borderRadius: radius,
      color: on ? roles.accent : null,
      selected: on,
      pressed: enabled && _press.shown,
    );
    // A style's own face has no focus border: the ring is drawn over it.
    // Always the same wrapper, so focus never rebuilds the cap.
    Widget cap(Widget child) => DecoratedBox(
      position: DecorationPosition.foreground,
      decoration: ring && !identical(look, solid)
          ? solid.copyWith(
              color: Colors.transparent,
              border: Border.fromBorderSide(ringSide),
            )
          : const BoxDecoration(),
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: look,
        // A latched cap's words sit on its own opaque accent: no plate
        // from the band around it (KitTextHalo does not yet read a styled
        // surface's fill as covering).
        child: on ? KitTextHalo(ground: null, child: child) : child,
      ),
    );
    return Semantics(
      button: true,
      toggled: widget.latched,
      enabled: enabled,
      label: widget.label,
      hint: enabled ? null : widget.disabledReason,
      onTap: onTap,
      excludeSemantics: true,
      child: FocusableActionDetector(
        enabled: enabled,
        mouseCursor: enabled
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        onShowHoverHighlight: (value) => setState(() => _hovered = value),
        onShowFocusHighlight: (value) => setState(() => _focused = value),
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              onTap?.call();
              return null;
            },
          ),
        },
        child: _press.listen(
          context: context,
          enabled: enabled,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onTap == null
                ? null
                : () {
                    _press.confirm();
                    onTap();
                  },
            onTapCancel: enabled ? _press.cancel : null,
            child: widget.calm
                ? Padding(
                    // The slot is the target; the cap sits inside it with
                    // room on every side, as a keyboard draws its keys.
                    padding: capInset,
                    // Named keys in the sans face; arrows and symbols in
                    // mono, whose glyphs exist in every language's theme.
                    child: cap(
                      Center(
                        child: face(
                          RegExp('[A-Za-z]').hasMatch(widget.face)
                              ? KitTextRole.secondary
                              : KitTextRole.mono,
                        ),
                      ),
                    ),
                  )
                : cap(Center(child: face(KitTextRole.mono))),
          ),
        ),
      ),
    );
  }
}
