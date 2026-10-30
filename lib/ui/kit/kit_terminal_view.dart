import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../l10n/app_localizations.dart';
import '../desktop/desktop_interaction.dart';
import '../theme_roles.dart';
import 'kit_buttons.dart';
import 'kit_code_surface.dart';
import 'kit_copy.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_redact.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'kit_terminal_links.dart';
import 'terminal_key_bar.dart';

/// The terminal (kit v2 §9.2), one surface in two forms.
///
/// [KitTerminalView.live] is an xterm session painted in the theme's
/// colours, with the [TerminalKeyBar] a phone keyboard lacks. The phone's
/// terminal and a server's terminal are the same surface.
///
/// [KitTerminalView.output] is a finished (or streaming) command and what it
/// printed, drawn the way a terminal does: the program picked out of its
/// path, flags and strings apart, the output's own ANSI colours kept, and
/// the end of a long output shown first (that is where a build or a test
/// run says how it went).
///
/// States: disabled, empty.
///
/// In this surface's own words: live; read-only (the disabled state: input
/// dropped, keys disabled with a reason); keys latched; keys compact; and
/// for the output form tail, all, too long and empty (only the command, or
/// nothing drawn at all).
///
/// Loading and error belong to the host: connecting, a failed socket and an
/// exited shell use `KitStateView` or `KitStatusLine`. The part never says
/// why a live view is read-only; the host does (STATE-8, COPY-17).
///
/// Output lines (and the command) pass through [KitRedact.text] before they
/// are shown and before [onOpenFull] gets them (SEC-2, SEC-4). The live
/// session is the person's own shell and is not redacted; the part never
/// copies or logs it: [selectedText] hands the selection to the host, which
/// copies it through `KitCopy.copy`.
class KitTerminalView extends StatelessWidget {
  /// A live session. The view fills its box. The key bar sits under it on
  /// mobile devices, including tablets with a mouse. A wide desktop window
  /// leaves the bar out and takes hardware keys only.
  const KitTerminalView.live({
    super.key,
    required xterm.Terminal this.terminal,
    required String this.semanticsLabel,
    this.controller,
    this.scrollController,
    this.focusNode,
    this.autofocus = true,
    this.readOnly = false,
    this.keys,
    this.interruptKeys = false,
    this.calm = false,
    this.onKeyEvent,
    this.viewKey,
    this.keysKey,
  }) : output = null,
       command = null,
       tailLines = 0,
       framed = false,
       wrap = false,
       onOpenFull = null,
       showEarlierKey = null,
       openFullKey = null;

  /// A command and its output: the last [tailLines] lines first, "Show {n}
  /// earlier lines" in place, and past [inlineLineCap] lines "Open all {n}
  /// lines" calls [onOpenFull]. Without [onOpenFull] a too-long output shows
  /// its last [inlineLineCap] lines and says so.
  const KitTerminalView.output({
    super.key,
    required String this.output,
    this.command,
    this.tailLines = 40,
    this.framed = true,
    this.wrap = true,
    this.onOpenFull,
    this.viewKey,
    this.showEarlierKey,
    this.openFullKey,
  }) : terminal = null,
       semanticsLabel = null,
       controller = null,
       scrollController = null,
       focusNode = null,
       autofocus = false,
       readOnly = true,
       keys = null,
       interruptKeys = false,
       calm = false,
       onKeyEvent = null,
       keysKey = null;

  /// The live session.
  final xterm.Terminal? terminal;

  /// The surface's name for a screen reader ("Terminal · build-server").
  final String? semanticsLabel;

  /// The live view's selection.
  final xterm.TerminalController? controller;
  final ScrollController? scrollController;
  final FocusNode? focusNode;
  final bool autofocus;

  /// Not connected, or the shell ended: the last screen stays readable,
  /// input is dropped (not queued) and the keys are disabled.
  final bool readOnly;

  /// The key bar's sticky modifiers; null draws no key bar. The part wires
  /// each key to [sendTerminalBarKey].
  final TerminalKeyBarController? keys;

  /// Leads the key bar with Ctrl-C and Ctrl-D (server terminals).
  final bool interruptKeys;

  /// The Terminal screen's own look: a roomier face ([calmFontSize] on
  /// [calmLineHeight]) on the gutter, the screen's ground showing through
  /// behind the text, and the one-row key bar
  /// (`TerminalKeyBar.singleRow`) on that same ground. Other hosts (agent
  /// sign-in) keep the classic dense view.
  final bool calm;

  /// The calm view's face size and line height: a comfortable read on a
  /// phone, about 45 columns at 412 dp.
  static const calmFontSize = 14.0;
  static const calmLineHeight = 1.5;

  /// Hardware keys on a PC (copy and paste chords), asked before the
  /// terminal. Ctrl+Tab and Ctrl+Shift+Tab never reach it: they move focus
  /// out of the terminal (LAY-10).
  final FocusOnKeyEventCallback? onKeyEvent;

  /// The live form: on the xterm view (a host keys it per shell). The
  /// output form: on the block; `terminal-view` by default.
  final Key? viewKey;

  /// On the key bar.
  final Key? keysKey;

  /// The printed text, ANSI escapes and all.
  final String? output;

  /// The command that printed [output], shown first after `$`.
  final String? command;

  /// How many of the last lines show before "Show earlier".
  final int tailLines;

  /// Draws its own inset box. Off where the host already frames the output
  /// (one box, not a box in a box).
  final bool framed;

  /// Wraps long lines. Off keeps each line on one line and lets the block
  /// scroll sideways, as a terminal does.
  final bool wrap;

  /// Opens a too-long output elsewhere (the file viewer). Gets the whole
  /// output with ANSI stripped and secrets masked.
  final ValueChanged<String>? onOpenFull;

  /// On "Show earlier"; `terminal-show-earlier` by default.
  final Key? showEarlierKey;

  /// On "Open all"; `terminal-open-full` by default.
  final Key? openFullKey;

  /// Past this many lines, the full output opens elsewhere instead of
  /// inline: laying out tens of thousands of lines stalls a phone.
  static const inlineLineCap = 2000;

  /// The selection's share of the accent: AppTheme's text selection.
  static const _selectionAlpha = .28;

  /// The terminal colours for [roles]; both forms and every theme pack use
  /// them. In the live view a program's own 256-colour and true-colour
  /// output is its data and is drawn as sent; the output form keeps only
  /// the 16 colours, in these roles.
  static xterm.TerminalTheme themeOf(ThemeRoles roles) {
    final selection = roles.accent.withValues(alpha: _selectionAlpha);
    return xterm.TerminalTheme(
      cursor: roles.accent,
      selection: selection,
      foreground: roles.text1,
      background: roles.ground,
      black: roles.surface3,
      red: roles.text1,
      green: roles.success,
      yellow: roles.codeString,
      blue: roles.codeType,
      magenta: roles.codeKeyword,
      cyan: roles.codeType,
      white: roles.text2,
      brightBlack: roles.text3,
      brightRed: roles.text1,
      brightGreen: roles.success,
      brightYellow: roles.codeString,
      brightBlue: roles.codeType,
      brightMagenta: roles.codeKeyword,
      brightCyan: roles.codeType,
      brightWhite: roles.text1,
      searchHitBackground: roles.surface3,
      searchHitBackgroundCurrent: selection,
      searchHitForeground: roles.text1,
    );
  }

  /// [themeOf] with a clear background: the calm view lets the screen's
  /// ground (and its quiet backdrop) show behind the text, so the terminal
  /// has no box edge of its own. Cells a program colours keep their colour.
  static xterm.TerminalTheme calmThemeOf(ThemeRoles roles) {
    final theme = themeOf(roles);
    return xterm.TerminalTheme(
      cursor: theme.cursor,
      selection: theme.selection,
      foreground: theme.foreground,
      background: roles.ground.withValues(alpha: 0),
      black: theme.black,
      red: theme.red,
      green: theme.green,
      yellow: theme.yellow,
      blue: theme.blue,
      magenta: theme.magenta,
      cyan: theme.cyan,
      white: theme.white,
      brightBlack: theme.brightBlack,
      brightRed: theme.brightRed,
      brightGreen: theme.brightGreen,
      brightYellow: theme.brightYellow,
      brightBlue: theme.brightBlue,
      brightMagenta: theme.brightMagenta,
      brightCyan: theme.brightCyan,
      brightWhite: theme.brightWhite,
      searchHitBackground: theme.searchHitBackground,
      searchHitBackgroundCurrent: theme.searchHitBackgroundCurrent,
      searchHitForeground: theme.searchHitForeground,
    );
  }

  /// The live view's selected text ("" when nothing is selected), for a
  /// host's copy control (KIT-23). The kit never writes the clipboard here.
  static String selectedText(
    xterm.Terminal terminal,
    xterm.TerminalController controller,
  ) {
    final selection = controller.selection;
    if (selection == null) return '';
    return terminal.buffer.getText(selection);
  }

  @override
  Widget build(BuildContext context) {
    final terminal = this.terminal;
    if (terminal != null) return _live(context, terminal);
    return _KitTerminalOutput(
      output: output ?? '',
      command: command,
      tailLines: tailLines,
      framed: framed,
      wrap: wrap,
      onOpenFull: onOpenFull,
      viewKey: viewKey ?? const ValueKey('terminal-view'),
      showEarlierKey: showEarlierKey ?? const ValueKey('terminal-show-earlier'),
      openFullKey: openFullKey ?? const ValueKey('terminal-open-full'),
    );
  }

  Widget _live(BuildContext context, xterm.Terminal terminal) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    // A mouse says nothing about whether a tablet has a physical keyboard.
    // Keep its IME and extra keys available. Desktop builds take hardware
    // keys only, so a keystroke never arrives twice (key event and IME).
    final hardwareOnly = desktopInteractions;
    final keys = hardwareOnly && KitLayout.windowOf(context).isWide
        ? null
        : this.keys;
    final view = _KitTerminalOscHost(
      terminal: terminal,
      child: Semantics(
        container: true,
        label: semanticsLabel,
        // A screen reader cannot read a character grid; the host offers the
        // accessible transcript.
        child: ExcludeSemantics(
          child: Directionality(
            textDirection: TextDirection.ltr,
            child: xterm.TerminalView(
              terminal,
              key: viewKey,
              controller: controller,
              scrollController: scrollController,
              focusNode: focusNode,
              autofocus: autofocus,
              autoResize: true,
              theme: calm ? calmThemeOf(roles) : themeOf(roles),
              // xterm sets the box's alpha from this, whatever the theme's.
              backgroundOpacity: calm ? 0 : 1,
              textStyle: xterm.TerminalStyle.fromTextStyle(
                calm
                    ? KitText.styleFor(
                        KitTextRole.mono,
                      ).copyWith(fontSize: calmFontSize, height: calmLineHeight)
                    : KitText.styleFor(KitTextRole.mono),
              ),
              // The face grows with the person's text size up to 2x: beyond
              // it, 40 columns no longer fit a phone (A11Y-8).
              textScaler: MediaQuery.textScalerOf(
                context,
              ).clamp(maxScaleFactor: KitTokens.terminalMaxTextScale),
              padding: calm
                  ? EdgeInsetsDirectional.fromSTEB(
                      tokens.gutter,
                      tokens.space2,
                      tokens.gutter,
                      tokens.space2,
                    ).resolve(TextDirection.ltr)
                  : EdgeInsets.all(tokens.space2),
              keyboardType: TextInputType.text,
              keyboardAppearance: roles.brightness,
              deleteDetection: true,
              hardwareKeyboardOnly: hardwareOnly,
              readOnly: readOnly,
              onKeyEvent: _onKey,
              onTapUp: (details, cell) {
                final url = KitTerminalLinks.urlAt(terminal, cell);
                if (url == null) return;
                unawaited(showKitTerminalLinkSheet(context, url));
              },
            ),
          ),
        ),
      ),
    );
    if (keys == null) return view;
    // One sideways row when the window left above the keyboard is short
    // (a phone in landscape with the keyboard up). A Scaffold removes the
    // keyboard from its body's MediaQuery, so the view's own inset counts.
    final window = View.of(context);
    final keyboard = math.max(
      MediaQuery.viewInsetsOf(context).bottom,
      window.viewInsets.bottom / window.devicePixelRatio,
    );
    final compact =
        MediaQuery.sizeOf(context).height - keyboard < KitLayout.shortHeight;
    if (calm) {
      return Column(
        children: [
          // xterm paints a line scrolled half out of view past its box; with
          // no box of its own the clip keeps it off the header.
          Expanded(child: ClipRect(child: view)),
          SafeArea(
            top: false,
            child: TerminalKeyBar(
              key: keysKey,
              controller: keys,
              compact: compact,
              singleRow: true,
              enabled: !readOnly,
              onKey: (key, {required ctrl, required alt}) =>
                  sendTerminalBarKey(terminal, key, ctrl: ctrl, alt: alt),
            ),
          ),
        ],
      );
    }
    return Column(
      children: [
        Expanded(child: view),
        // The bar's band reaches the screen's edge: one surface, the safe
        // area inside it.
        TerminalKeyBar(
          key: keysKey,
          controller: keys,
          compact: compact,
          enabled: !readOnly,
          interruptKeys: interruptKeys,
          safeArea: true,
          onKey: (key, {required ctrl, required alt}) =>
              sendTerminalBarKey(terminal, key, ctrl: ctrl, alt: alt),
        ),
      ],
    );
  }

  /// Ctrl+Tab and Ctrl+Shift+Tab leave the terminal, so it is never a focus
  /// trap; Tab, Esc and the arrows go to the shell. Read-only drops the
  /// rest.
  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    final keyboard = HardwareKeyboard.instance;
    if (event.logicalKey == LogicalKeyboardKey.tab &&
        keyboard.isControlPressed) {
      if (event is! KeyUpEvent) {
        if (keyboard.isShiftPressed) {
          node.previousFocus();
        } else {
          node.nextFocus();
        }
      }
      return KeyEventResult.handled;
    }
    final host = onKeyEvent?.call(node, event) ?? KeyEventResult.ignored;
    if (host != KeyEventResult.ignored) return host;
    return readOnly ? KeyEventResult.handled : KeyEventResult.ignored;
  }
}

/// The output form's own state: whether the earlier lines are shown.
class _KitTerminalOutput extends StatefulWidget {
  const _KitTerminalOutput({
    required this.output,
    required this.command,
    required this.tailLines,
    required this.framed,
    required this.wrap,
    required this.onOpenFull,
    required this.viewKey,
    required this.showEarlierKey,
    required this.openFullKey,
  });

  final String output;
  final String? command;
  final int tailLines;
  final bool framed;
  final bool wrap;
  final ValueChanged<String>? onOpenFull;
  final Key viewKey;
  final Key showEarlierKey;
  final Key openFullKey;

  @override
  State<_KitTerminalOutput> createState() => _KitTerminalOutputState();
}

class _KitTerminalOutputState extends State<_KitTerminalOutput> {
  bool _all = false;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final command = KitRedact.text(widget.command?.trim() ?? '');
    final lines = widget.output.isEmpty
        ? const <String>[]
        : widget.output.split('\n');
    if (lines.isEmpty && command.isEmpty) return const SizedBox.shrink();

    const cap = KitTerminalView.inlineLineCap;
    final total = lines.length;
    final tooLong = total > cap;
    // Where the collapsed view and the expanded one start.
    final tailStart = math.max<int>(
      0,
      total - math.max<int>(0, widget.tailLines),
    );
    final allStart = tooLong ? total - cap : 0;
    final start = _all ? allStart : math.max<int>(tailStart, allStart);
    final canExpand = !_all && tailStart > allStart;
    final capped = tooLong && start == allStart;
    final shown = lines.sublist(start);

    final spans = <InlineSpan>[
      if (command.isNotEmpty) ...KitTerminalText.command(command, roles),
      if (command.isNotEmpty && shown.isNotEmpty) const TextSpan(text: '\n\n'),
      for (var i = 0; i < shown.length; i++) ...[
        if (i > 0) const TextSpan(text: '\n'),
        ...KitTerminalText.line(shown[i], roles),
      ],
    ];
    final Widget text = SelectableText.rich(
      TextSpan(
        style: KitText.styleOf(context, KitTextRole.mono),
        children: spans,
      ),
      textAlign: TextAlign.start,
    );
    // An LTR region aligned left in both directions (COPY-30, KIT-32).
    final Widget block = Directionality(
      textDirection: TextDirection.ltr,
      child: MediaQuery.withClampedTextScaling(
        maxScaleFactor: KitTokens.terminalMaxTextScale,
        // The selectable text is a touch target: never under 48 dp, even
        // for one line (LAY-9).
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: widget.wrap
              ? text
              : SingleChildScrollView(
                  key: const ValueKey('terminal-view-sideways'),
                  scrollDirection: Axis.horizontal,
                  child: text,
                ),
        ),
      ),
    );
    final onOpenFull = widget.onOpenFull;
    final Widget body = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (canExpand)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitButton.tertiary(
              key: widget.showEarlierKey,
              // The count is what a tap reveals: back to the cap, not the
              // whole output, when it is too long (COPY-17).
              label: l10n.terminalShowEarlier(tailStart - allStart),
              // In place, no animation: tens of lines in a scrolling list
              // must not animate layout (MOT-5).
              onPressed: () => setState(() => _all = true),
            ),
          ),
        block,
        if (capped && onOpenFull != null)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitButton.tertiary(
              key: widget.openFullKey,
              label: l10n.terminalOpenFull(total),
              onPressed: () => onOpenFull(
                KitRedact.text(KitTerminalText.strip(widget.output)),
              ),
            ),
          )
        else if (capped)
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space2),
            child: KitText(
              l10n.kitTerminalViewShowingLast(cap),
              role: KitTextRole.secondary,
            ),
          ),
      ],
    );
    if (!widget.framed) return KeyedSubtree(key: widget.viewKey, child: body);
    return kitCodeSurface(
      context,
      key: widget.viewKey,
      fill: tokens.detailsSurface,
      shape: tokens.shapeOf(KitShape.code),
      child: Padding(padding: EdgeInsets.all(tokens.space2), child: body),
    );
  }
}

/// Terminal text as spans: pure functions, no widgets. Colours come from
/// [ThemeRoles] only; errors are `text1` in semibold, never `danger`
/// (LOOK-5), and warnings never use the attention roles (LOOK-4).
abstract final class KitTerminalText {
  static final _ansiEscape = RegExp(r'\x1B(?:\[[0-?]*[ -/]*[@-~]|[@-Z\\-_])');
  static final _sgr = RegExp(r'\x1B\[([0-9;]*)m');

  /// ANSI escape sequences removed.
  static String strip(String text) => text.replaceAll(_ansiEscape, '');

  static final _commandToken = RegExp(
    r'''"(?:[^"\\]|\\.)*"|'[^']*'|&&|\|\||[|;<>]|\s+|[^\s|;<>"']+''',
  );
  static const _operators = {'&&', '||', '|', ';', '<', '>'};
  static final _assignment = RegExp(r'^[A-Za-z_][A-Za-z0-9_]*=');

  /// "$ " then the program (its folder dimmed), flags, strings and
  /// operators, each in its own shade.
  static List<InlineSpan> command(String command, ThemeRoles roles) {
    final dim = TextStyle(color: roles.text3);
    final spans = <InlineSpan>[TextSpan(text: r'$ ', style: dim)];
    var expectProgram = true;
    for (final match in _commandToken.allMatches(command)) {
      final token = match[0]!;
      if (token.trim().isEmpty) {
        spans.add(TextSpan(text: token));
      } else if (_operators.contains(token)) {
        spans.add(
          TextSpan(
            text: token,
            style: TextStyle(color: roles.text3, fontWeight: FontWeight.w700),
          ),
        );
        expectProgram = true;
      } else if (token.startsWith('"') || token.startsWith("'")) {
        spans.add(
          TextSpan(
            text: token,
            style: TextStyle(color: roles.codeString),
          ),
        );
        expectProgram = false;
      } else if (expectProgram && _assignment.hasMatch(token)) {
        // FOO=1 before the program: set-up, not the point.
        spans.add(TextSpan(text: token, style: dim));
      } else if (expectProgram) {
        final slash = token.lastIndexOf('/');
        final folder = slash > 0 && slash < token.length - 1;
        if (folder) {
          spans.add(TextSpan(text: token.substring(0, slash + 1), style: dim));
        }
        spans.add(
          TextSpan(
            text: folder ? token.substring(slash + 1) : token,
            style: TextStyle(color: roles.accent, fontWeight: FontWeight.w700),
          ),
        );
        expectProgram = false;
      } else if (token.startsWith('-')) {
        spans.add(
          TextSpan(
            text: token,
            style: TextStyle(color: roles.codeType),
          ),
        );
      } else {
        spans.add(TextSpan(text: token));
      }
    }
    return spans;
  }

  static final _failure = RegExp(
    // "0 errors" and "no errors" are good news, not failures.
    r'(?<!(?:\b\d+|\bno) )\b(error|errors|failed|failure|fatal|exception|traceback|panic)\b|✗|✖|\bFAIL\b',
    caseSensitive: false,
  );
  static final _warning = RegExp(
    r'\bwarn(ing|ings)?\b|⚠',
    caseSensitive: false,
  );
  static final _pass = RegExp(
    r'\b(all tests passed|passed|succeeded|successfully|no issues found)\b|✓|✔',
    caseSensitive: false,
  );
  // Flutter and Dart test progress: "00:07 +6 ~1 -2: name".
  static final _testProgress = RegExp(r'^(\d+:\d+ )(\+\d+)( ~\d+)?( -\d+)?(:)');

  /// One output line, secrets masked: its own ANSI colours when it has
  /// them, otherwise a tint from what it says (errors, warnings, passes,
  /// test progress).
  ///
  /// Secrets are found in the text without its escapes: a value coloured
  /// apart from its name (`jq -C`, `Bearer \x1b[1mTOKEN`) is still one
  /// secret. A line holding one drops its colours and shows the masked
  /// text, tinted like plain output (SEC-2, SEC-4).
  static List<InlineSpan> line(String line, ThemeRoles roles) {
    final stripped = strip(line);
    if (line.contains('\x1B[') && !KitRedact.containsSecret(stripped)) {
      return _ansi(line, roles);
    }
    final plain = KitRedact.text(stripped);
    if (_testProgress.firstMatch(plain) case final progress?) {
      return [
        TextSpan(
          text: progress[1],
          style: TextStyle(color: roles.text3),
        ),
        TextSpan(
          text: progress[2],
          style: TextStyle(color: roles.success),
        ),
        if (progress[3] != null)
          TextSpan(
            text: progress[3],
            style: TextStyle(color: roles.text2),
          ),
        if (progress[4] != null)
          TextSpan(text: progress[4], style: _failureStyle(roles)),
        TextSpan(text: progress[5]),
        ..._tinted(plain.substring(progress.end), roles),
      ];
    }
    return _tinted(plain, roles);
  }

  static TextStyle _failureStyle(ThemeRoles roles) =>
      TextStyle(color: roles.text1, fontWeight: FontWeight.w600);

  static List<InlineSpan> _tinted(String text, ThemeRoles roles) {
    final TextStyle? style = _failure.hasMatch(text)
        ? _failureStyle(roles)
        : _warning.hasMatch(text)
        ? TextStyle(color: roles.text2, fontWeight: FontWeight.w600)
        : _pass.hasMatch(text)
        ? TextStyle(color: roles.success)
        : null;
    return [TextSpan(text: text, style: style)];
  }

  static List<InlineSpan> _ansi(String line, ThemeRoles roles) {
    final theme = KitTerminalView.themeOf(roles);
    final normal = [
      theme.black,
      theme.red,
      theme.green,
      theme.yellow,
      theme.blue,
      theme.magenta,
      theme.cyan,
      theme.white,
    ];
    final bright = [
      theme.brightBlack,
      theme.brightRed,
      theme.brightGreen,
      theme.brightYellow,
      theme.brightBlue,
      theme.brightMagenta,
      theme.brightCyan,
      theme.brightWhite,
    ];
    final spans = <InlineSpan>[];
    Color? color;
    var bold = false;
    var faint = false;
    var at = 0;
    void emit(String text) {
      final clean = KitRedact.text(strip(text));
      if (clean.isEmpty) return;
      spans.add(
        TextSpan(
          text: clean,
          style: TextStyle(
            color: faint ? roles.text3 : color,
            fontWeight: bold ? FontWeight.w700 : null,
          ),
        ),
      );
    }

    for (final match in _sgr.allMatches(line)) {
      emit(line.substring(at, match.start));
      at = match.end;
      final codes = match[1]!.isEmpty
          ? const [0]
          : match[1]!.split(';').map((c) => int.tryParse(c) ?? 0).toList();
      for (var i = 0; i < codes.length; i++) {
        final code = codes[i];
        if (code == 0) {
          color = null;
          bold = false;
          faint = false;
        } else if (code == 1) {
          bold = true;
        } else if (code == 2) {
          faint = true;
        } else if (code == 22) {
          bold = false;
          faint = false;
        } else if (code >= 30 && code <= 37) {
          color = normal[code - 30];
        } else if (code >= 90 && code <= 97) {
          color = bright[code - 90];
        } else if (code == 39) {
          color = null;
        } else if (code == 38 || code == 48) {
          // 256-colour and true-colour: skip their arguments.
          i += i + 1 < codes.length && codes[i + 1] == 5 ? 2 : 4;
        }
      }
    }
    emit(line.substring(at));
    return spans.isEmpty ? [const TextSpan(text: '')] : spans;
  }
}

/// Listens for OSC 52 clipboard writes from the shown session and says so.
///
/// Only a mounted (foreground) view listens; the handler it replaced is put
/// back when it goes away. A copy shows "Copied to clipboard" over the top
/// of the terminal for two seconds.
class _KitTerminalOscHost extends StatefulWidget {
  const _KitTerminalOscHost({required this.terminal, required this.child});

  final xterm.Terminal terminal;
  final Widget child;

  @override
  State<_KitTerminalOscHost> createState() => _KitTerminalOscHostState();
}

class _KitTerminalOscHostState extends State<_KitTerminalOscHost> {
  void Function(String, List<String>)? _previous;
  Timer? _hide;
  bool _shown = false;

  @override
  void initState() {
    super.initState();
    _attach(widget.terminal);
  }

  @override
  void didUpdateWidget(_KitTerminalOscHost old) {
    super.didUpdateWidget(old);
    if (old.terminal != widget.terminal) {
      _detach(old.terminal);
      _attach(widget.terminal);
    }
  }

  void _attach(xterm.Terminal terminal) {
    _previous = terminal.onPrivateOSC;
    terminal.onPrivateOSC = (code, args) {
      if (code == '52') {
        _copy(args);
      } else {
        _previous?.call(code, args);
      }
    };
  }

  void _detach(xterm.Terminal terminal) {
    terminal.onPrivateOSC = _previous;
  }

  void _copy(List<String> args) {
    final text = KitTerminalOsc52.parse(args);
    if (text == null) return;
    if (!mounted) return;
    unawaited(KitCopy.copy(context, text, redact: false));
    _hide?.cancel();
    setState(() => _shown = true);
    _hide = Timer(KitMotion.terminalCopiedNote, () {
      if (mounted) setState(() => _shown = false);
    });
  }

  @override
  void dispose() {
    _hide?.cancel();
    _detach(widget.terminal);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final style = KitSurfaceLook.styleOf(context);
    return Stack(
      fit: StackFit.passthrough,
      children: [
        widget.child,
        Positioned(
          top: tokens.space3,
          left: 0,
          right: 0,
          child: IgnorePointer(
            child: AnimatedOpacity(
              opacity: _shown ? 1 : 0,
              duration: KitMotion.standard,
              child: Center(
                child: Semantics(
                  liveRegion: true,
                  label: _shown ? l10n.terminalCopiedToClipboard : '',
                  child: ExcludeSemantics(
                    // A floating pill over the terminal, in the style's
                    // look (Solid: `surface3`).
                    child: kitSurfaceBox(
                      key: const ValueKey('terminal-copied-toast'),
                      style: style,
                      decoration: KitSurfaceLook.decorate(
                        context,
                        solid: ShapeDecoration(
                          color: tokens.roles.surface3,
                          shape: const StadiumBorder(),
                        ),
                        role: KitSurfaceRole.floating,
                        level: KitSurfaceLevel.surface3,
                      ),
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: tokens.space3,
                          vertical: tokens.space2,
                        ),
                        child: KitText(
                          l10n.terminalCopiedToClipboard,
                          role: KitTextRole.secondary,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
