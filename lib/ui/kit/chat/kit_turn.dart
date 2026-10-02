// One turn of a conversation (docs/ux-system/kit-api/KitTurn.md; STATE-16,
// KIT-41, STATE-5, AUTO-15, KIT-23, KIT-28, LOOK-26, LOOK-27): the person's
// prompt, then everything the agent did about it until it handed back, in
// order, with exactly one footer (Copy and More) once the turn has ended and
// none while it runs. The host groups messages into turns and derives the
// phase; the turn only lays them out.
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_buttons.dart';
import '../kit_copy.dart';
import '../kit_effects.dart';
import '../kit_icon_button.dart';
import '../kit_menu.dart';
import '../kit_motion.dart';
import '../kit_since.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_message.dart';

/// At this text scale and above the footer's meta words move above the
/// buttons, which stay on one row at the end (KitTurn.md Adaptive). The
/// same threshold as KitMessage and KitToolRow.
const double _kWrapTextScale = 1.3;

/// Where a turn stands. The host derives it; the turn only lays it out.
enum KitTurnPhase {
  /// Sent; nothing has come back yet.
  starting,

  /// The agent is working.
  running,

  /// A request in this turn waits for the person.
  waitingForYou,

  /// The agent handed back.
  finished,

  /// The person stopped it.
  stopped,

  /// The server or connection went away before it finished.
  interrupted,

  /// It ended on an error (the error is a KitNotice block).
  failed,
}

/// Which part of one turn a [KitTurn] draws. A virtualised transcript
/// builds each server message as its own row, so one turn can span several
/// rows; each row names its part and the turn keeps its rhythm: no section
/// gap inside it, and the footer only on the part that ends it.
enum KitTurnSegment {
  /// The whole turn in one widget (the default).
  whole,

  /// The turn's first row (its prompt); the rest follows in later rows.
  first,

  /// A row inside the turn: neither its prompt nor its end.
  middle,

  /// The row that ends the turn: it carries the phase line and the footer.
  last,
}

/// What a running turn is doing right now, for its live line
/// ([KitTurnLive]). The host derives it from what the server has said; the
/// kit words it.
enum KitTurnActivity {
  /// The prompt is on its way to the server.
  sending,

  /// Sent, and the server has not said it started on it yet. Slow: "The
  /// server has not answered yet".
  waitingForServer,

  /// The server has the prompt and nothing has come back yet. Slow:
  /// "Waiting for the model's first word".
  waitingForModel,

  /// The model is thinking (between steps, or its reasoning streams).
  thinking,

  /// Reply words are streaming in.
  writing,

  /// A step (a tool) is running; the step rows above say which.
  working,

  /// A request in this turn waits for the person.
  waitingForYou,
}

/// The live line of a running turn: what it is doing and for how long,
/// under the reply as it grows (owner decision 2 Oct, 01B). Drawn under the
/// turn's last block from the moment the prompt is sent until the turn
/// ends; never a silent turn. It carries one pulsing dot while the agent
/// works (12A). Stop is not here: the composer's Send becomes Stop.
@immutable
class KitTurnLive {
  const KitTurnLive({
    required this.activity,
    this.since,
    this.teamAlsoWorking = false,
    this.note,
  });

  final KitTurnActivity activity;

  /// When the turn began, on this phone's clock. The line shows the time
  /// since then after [showElapsedAfter], and the waits turn slow after
  /// [slowAfter].
  final DateTime? since;

  /// The reply runs on this phone's own server while the in-app AI Team has
  /// working tasks: a slow wait for the model's first word then says the
  /// team shares the phone. False (default): the plain slow words.
  final bool teamAlsoWorking;

  /// A short second segment after the words ("Sends after this reply").
  final String? note;

  /// Under this the line says only what the turn is doing.
  static const showElapsedAfter = Duration(seconds: 5);

  /// A wait for the server or for the model's first word this long says so
  /// in its own words (a free model can take one to two minutes).
  static const slowAfter = Duration(seconds: 20);

  /// "12 s" or "2 min 5 s" ([elapsed] rounded down).
  static String elapsedText(AppLocalizations l10n, Duration elapsed) =>
      elapsed.inMinutes < 1
      ? l10n.kitTurnLiveSeconds(elapsed.inSeconds)
      : l10n.kitTurnLiveMinutes(elapsed.inMinutes, elapsed.inSeconds % 60);

  /// What the turn is doing, in words; the slow waits say so after
  /// [slowAfter].
  static String wordsFor(
    AppLocalizations l10n,
    KitTurnActivity activity,
    Duration elapsed, {
    bool teamAlsoWorking = false,
  }) {
    final slow = elapsed >= slowAfter;
    return switch (activity) {
      KitTurnActivity.sending => l10n.kitTurnLiveSending,
      KitTurnActivity.waitingForServer =>
        slow ? l10n.kitTurnLiveServerQuiet : l10n.kitTurnLiveWaitingForServer,
      KitTurnActivity.waitingForModel =>
        slow
            ? (teamAlsoWorking
                  ? l10n.kitTurnLiveFirstWordSlowTeam
                  : l10n.kitTurnLiveFirstWordSlow)
            : l10n.kitTurnLiveThinking,
      KitTurnActivity.thinking => l10n.kitTurnLiveThinking,
      KitTurnActivity.writing => l10n.kitTurnLiveWriting,
      KitTurnActivity.working => l10n.kitTurnLiveWorking,
      KitTurnActivity.waitingForYou => l10n.kitTurnLiveWaitingForYou,
    };
  }
}

/// The one footer of a finished turn (STATE-16).
@immutable
class KitTurnFooter {
  const KitTurnFooter({
    required this.copyText,
    this.meta,
    this.menu = const <KitMenuItem>[],
    this.onMore,
    this.copyLabel,
  });

  /// The whole turn's reply prose, read at tap time (KIT-23). Never tool
  /// output or hidden text. It is copied verbatim (SEC-13): it is the
  /// person's own content.
  final String Function() copyText;

  /// Host words: "Sonnet 4.5 · 12k tokens · 10:42" (the model name as sent,
  /// COPY-2; the host isolates it with KitBidi.auto, COPY-30). Shown only on
  /// the [KitTurn.latest] turn.
  final String? meta;

  /// More: Fork from here, Revert, Read aloud, Details…
  final List<KitMenuItem> menu;

  /// Non-null: More calls this instead of showing [menu] (a host whose
  /// reply actions still live in its own sheet). More is then shown even
  /// when [menu] is empty; long-press keeps showing Copy and [menu].
  final VoidCallback? onMore;

  /// The host's words for what Copy copies when the turn is not simply
  /// done ("Copy reply so far", "Copy loaded reply"); null reads "Copy
  /// reply". The Copy button's tooltip and the long-press item use it.
  final String? copyLabel;
}

/// One turn: a prompt, then everything until the agent hands back, made of
/// steps whose boundaries are not drawn, and notices that do not end it
/// (STANDARDS STATE-16, KIT-41). A finished turn has one footer after its
/// last block; a running turn has none.
///
/// [blocks] is oldest first and holds only kit parts: `KitMessage` (reply,
/// thought, notice, marker), `KitWorkLine`, `KitToolRow` (a lone step),
/// `KitRequestCard` and `KitNotice` (an error). No block draws Copy or More;
/// the footer is the turn's only control row.
///
/// Long-press and right-click on the blocks, Shift+F10 inside the turn, and
/// the turn's semantic custom actions open the footer's menu plus a Copy
/// item, in every phase (running included). A prompt's own menu wins on the
/// prompt.
///
/// States: starting, starting (slow), running, waitingForYou, finished,
/// finished (latest), stopped, interrupted, failed, highlighted (KIT-12).
class KitTurn extends StatelessWidget {
  const KitTurn({
    super.key,
    this.prompt,
    required this.blocks,
    required this.phase,
    this.since,
    this.footer,
    this.latest = false,
    this.highlighted = false,
    this.interruptedAction,
    this.reconnecting = false,
    this.live,
    this.segment = KitTurnSegment.whole,
    this.turnKey,
    this.footerKey,
    this.copyKey,
    this.moreKey,
  });

  /// `KitMessage.prompt`; null for a turn with no prompt of its own (an
  /// automated first turn).
  final KitMessage? prompt;

  /// Oldest first; see the class comment for the allowed parts.
  final List<Widget> blocks;

  final KitTurnPhase phase;

  /// When the turn began, on this phone's clock; drives the starting line's
  /// escalation after `KitMotion.escalateAfter`.
  final DateTime? since;

  /// Drawn only when [phase] is finished, stopped, interrupted or failed.
  final KitTurnFooter? footer;

  /// The newest turn: the footer shows its meta words.
  final bool latest;

  /// The find-in-conversation current match: a surface1 band.
  final bool highlighted;

  /// "Send again" under the interrupted line; drawn only for
  /// [KitTurnPhase.interrupted].
  final KitAction? interruptedAction;

  /// With [KitTurnPhase.interrupted]: the connection is coming back, so the
  /// reply may still finish; the line says so and offers nothing to resend.
  final bool reconnecting;

  /// Non-null: the turn is running and this is its live line (what it is
  /// doing and the time, with a pulsing dot). It is drawn in place of the
  /// phase line on whichever part the host gives it to, the prompt
  /// included, so a turn with nothing back yet still says it is working.
  final KitTurnLive? live;

  /// Which part of the turn this widget draws ([KitTurnSegment]). Only
  /// [KitTurnSegment.whole] and [KitTurnSegment.last] draw the phase line
  /// and the footer and end with the section gap; [KitTurnSegment.first]
  /// ends with the prompt's gap and [KitTurnSegment.middle] with the gap
  /// between blocks.
  final KitTurnSegment segment;

  final Key? turnKey, footerKey, copyKey, moreKey;

  @override
  Widget build(BuildContext context) => _TurnFrame(turn: this);
}

bool _ended(KitTurnPhase phase) => switch (phase) {
  KitTurnPhase.finished ||
  KitTurnPhase.stopped ||
  KitTurnPhase.interrupted ||
  KitTurnPhase.failed => true,
  KitTurnPhase.starting ||
  KitTurnPhase.running ||
  KitTurnPhase.waitingForYou => false,
};

class _TurnFrame extends StatefulWidget {
  const _TurnFrame({required this.turn});

  final KitTurn turn;

  @override
  State<_TurnFrame> createState() => _TurnFrameState();
}

class _TurnFrameState extends State<_TurnFrame> {
  bool _menuOpen = false;

  KitTurnFooter? get _footer => widget.turn.footer;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  /// Verbatim (SEC-13): the reply is the person's own content.
  void _copyReply() {
    final footer = _footer;
    if (footer == null) return;
    KitCopy.copy(context, footer.copyText(), redact: false);
  }

  /// The long-press menu: Copy, then the footer's menu.
  List<KitMenuItem> _menuItems() {
    final footer = _footer;
    if (footer == null) return const <KitMenuItem>[];
    return <KitMenuItem>[
      KitMenuItem(
        label: footer.copyLabel ?? _l10n.kitTurnCopy,
        icon: AppIconography.copy,
        onSelected: _copyReply,
      ),
      ...footer.menu,
    ];
  }

  Future<void> _openMenu({
    required List<KitMenuItem> items,
    BuildContext? anchor,
    Offset? position,
  }) async {
    if (items.isEmpty || _menuOpen) return;
    _menuOpen = true;
    try {
      await showKitMenu(
        anchor ?? context,
        items: items,
        position: position,
        semanticsLabel: _l10n.kitTurnActions,
      );
    } finally {
      _menuOpen = false;
    }
  }

  void _invoke(KitMenuItem item) {
    final copyText = item.copyText;
    if (copyText != null) {
      KitCopy.copy(context, copyText());
    } else {
      item.onSelected();
    }
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final key = event.logicalKey;
    final isMenuKey =
        key == LogicalKeyboardKey.contextMenu ||
        (key == LogicalKeyboardKey.f10 &&
            HardwareKeyboard.instance.isShiftPressed);
    if (!isMenuKey) return KeyEventResult.ignored;
    final items = _menuItems();
    if (items.isEmpty) return KeyEventResult.ignored;
    _openMenu(items: items);
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final turn = widget.turn;
    final tokens = KitTokens.of(context);
    final l10n = _l10n;
    final menuItems = _menuItems();
    final hasMenu = menuItems.isNotEmpty;

    final children = <Widget>[];
    void add(Widget child, double gap) {
      if (children.isNotEmpty) children.add(SizedBox(height: gap));
      children.add(child);
    }

    if (turn.prompt case final prompt?) add(prompt, 0);

    if (turn.blocks.isNotEmpty) {
      Widget blocks = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var i = 0; i < turn.blocks.length; i++) ...[
            if (i > 0) SizedBox(height: tokens.space3),
            turn.blocks[i],
          ],
        ],
      );
      if (hasMenu) {
        // Inner parts with their own long-press (a tool row, selectable
        // prose) win the gesture arena; the turn's menu takes the rest.
        blocks = GestureDetector(
          behavior: HitTestBehavior.translucent,
          excludeFromSemantics: true,
          onLongPressStart: (details) =>
              _openMenu(items: menuItems, position: details.globalPosition),
          onSecondaryTapUp: (details) =>
              _openMenu(items: menuItems, position: details.globalPosition),
          child: blocks,
        );
      }
      add(blocks, tokens.space4);
    }

    final ends =
        turn.segment == KitTurnSegment.whole ||
        turn.segment == KitTurnSegment.last;
    final phaseLine = turn.live != null
        ? _KitTurnLiveLine(live: turn.live!)
        : ends
        ? _phaseLine(context, turn, l10n)
        : null;
    if (phaseLine != null) {
      add(phaseLine, turn.blocks.isEmpty ? tokens.space4 : tokens.space3);
    }

    final footer = turn.footer;
    if (footer != null && ends && _ended(turn.phase)) {
      add(_footerRow(context, turn, footer, tokens, l10n), tokens.space1);
    }

    Widget body = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: children,
    );

    body = _HighlightBand(highlighted: turn.highlighted, child: body);

    body = Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: _onKey,
      child: body,
    );

    final gapAfter = switch (turn.segment) {
      // The newest turn sits right above the composer: the kit's standard
      // spacing, not a section gap of its own.
      KitTurnSegment.whole ||
      KitTurnSegment.last => turn.latest ? tokens.space2 : tokens.sectionGap,
      KitTurnSegment.first => tokens.space4,
      KitTurnSegment.middle => tokens.space3,
    };
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: gapAfter),
      child: Semantics(
        key: turn.turnKey,
        container: true,
        explicitChildNodes: true,
        customSemanticsActions: hasMenu
            ? <CustomSemanticsAction, VoidCallback>{
                for (final item in menuItems.where((item) => item.enabled))
                  CustomSemanticsAction(label: item.label): () => _invoke(item),
              }
            : null,
        child: body,
      ),
    );
  }

  Widget? _phaseLine(
    BuildContext context,
    KitTurn turn,
    AppLocalizations l10n,
  ) {
    Widget line(String words) => Align(
      alignment: AlignmentDirectional.topStart,
      child: KitText(
        words,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    );
    return switch (turn.phase) {
      KitTurnPhase.starting when turn.blocks.isEmpty => KitSince(
        since: turn.since,
        builder: (context, status) => line(
          status.isSlow
              ? l10n.kitTurnStillStarting(status.elapsed.inSeconds)
              : l10n.kitTurnStarting,
        ),
      ),
      KitTurnPhase.stopped => line(l10n.kitTurnStopped),
      KitTurnPhase.interrupted => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          line(
            turn.reconnecting
                ? l10n.kitTurnReconnecting
                : l10n.kitTurnInterrupted,
          ),
          if (turn.interruptedAction case final action?)
            Align(
              alignment: AlignmentDirectional.topStart,
              child: KitButton.fromAction(action, role: KitButtonRole.tertiary),
            ),
        ],
      ),
      _ => null,
    };
  }

  Widget _footerRow(
    BuildContext context,
    KitTurn turn,
    KitTurnFooter footer,
    KitTokens tokens,
    AppLocalizations l10n,
  ) {
    final meta = turn.latest ? footer.meta : null;
    final buttons = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        KitIconButton(
          key: turn.copyKey,
          icon: AppIconography.copy,
          tooltip: footer.copyLabel ?? l10n.kitTurnCopy,
          onPressed: _copyReply,
        ),
        if (footer.menu.isNotEmpty || footer.onMore != null) ...[
          SizedBox(width: tokens.space2),
          Builder(
            builder: (anchor) => KitIconButton(
              key: turn.moreKey,
              icon: AppIconography.more,
              tooltip: l10n.kitTurnMore,
              onPressed:
                  footer.onMore ??
                  () => _openMenu(items: footer.menu, anchor: anchor),
            ),
          ),
        ],
      ],
    );
    final metaText = meta == null || meta.isEmpty
        ? null
        : KitText(meta, role: KitTextRole.caption, tone: KitTextTone.tertiary);
    final stacked =
        MediaQuery.textScalerOf(context).scale(1) >= _kWrapTextScale;

    final Widget row;
    if (metaText == null) {
      row = Align(alignment: AlignmentDirectional.centerEnd, child: buttons);
    } else if (stacked) {
      row = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          metaText,
          Align(alignment: AlignmentDirectional.centerEnd, child: buttons),
        ],
      );
    } else {
      row = Row(
        children: [
          Expanded(child: metaText),
          SizedBox(width: tokens.space2),
          buttons,
        ],
      );
    }
    return KeyedSubtree(key: turn.footerKey, child: row);
  }
}

/// The find-in-conversation band: surface1 with the panel radius, painted
/// `space2` outside the turn's content so the layout never moves when the
/// match changes (MOT-5). It cross-fades on `KitMotion.quick`.
class _HighlightBand extends StatelessWidget {
  const _HighlightBand({required this.highlighted, required this.child});

  final bool highlighted;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final still = KitMotion.reduced(context);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: highlighted ? 1 : 0),
      duration: still ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      builder: (context, amount, child) => CustomPaint(
        painter: amount == 0
            ? null
            : _BandPainter(
                color: tokens.roles.surface1.withValues(
                  alpha: tokens.roles.surface1.a * amount,
                ),
                outset: tokens.space2,
                radius: tokens.panelCornerRadius,
              ),
        child: child,
      ),
      child: child,
    );
  }
}

class _BandPainter extends CustomPainter {
  const _BandPainter({
    required this.color,
    required this.outset,
    required this.radius,
  });

  final Color color;
  final double outset;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).inflate(outset);
    canvas.drawRRect(
      RRect.fromRectAndRadius(rect, Radius.circular(radius)),
      Paint()..color = color,
    );
  }

  @override
  bool shouldRepaint(_BandPainter old) =>
      old.color != color || old.outset != outset || old.radius != radius;
}

/// The running turn's one live line: a pulsing dot and "Thinking · 12 s".
/// The words are a live region that announces the phase, not the seconds.
class _KitTurnLiveLine extends StatelessWidget {
  const _KitTurnLiveLine({required this.live});

  final KitTurnLive live;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final activity = live.activity == KitTurnActivity.sending
        ? KitTurnActivity.thinking
        : live.activity;
    return KitSince(
      since: live.since,
      ticks: KitSinceTicks.seconds,
      builder: (context, status) {
        final slow = status.elapsed >= KitTurnLive.slowAfter;
        final words = switch (activity) {
          KitTurnActivity.waitingForServer when slow =>
            l10n.kitComposerPillNoAnswer,
          KitTurnActivity.waitingForServer => l10n.kitTurnLiveThinking,
          KitTurnActivity.waitingForModel when !slow =>
            l10n.kitTurnLiveThinking,
          _ => KitTurnLive.wordsFor(
            l10n,
            activity,
            status.elapsed,
            teamAlsoWorking: live.teamAlsoWorking,
          ),
        };
        var line = status.elapsed < KitTurnLive.showElapsedAfter
            ? l10n.kitTurnLiveNow(words)
            : l10n.kitTurnLiveFor(
                words,
                KitTurnLive.elapsedText(l10n, status.elapsed),
              );
        final note = live.note;
        if (note != null && note.isNotEmpty) line = '$line · $note';
        return Row(
          key: const ValueKey('kit-turn-live-line'),
          mainAxisSize: MainAxisSize.min,
          children: [
            const _LiveDot(),
            SizedBox(width: tokens.space2),
            Flexible(
              // A live region, read as the phase; the seconds are not
              // announced every time they change.
              child: Semantics(
                liveRegion: true,
                label: note == null || note.isEmpty ? words : '$words. $note',
                child: ExcludeSemantics(
                  child: KitText(
                    line,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    tabular: true,
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

/// The one thing that moves in a chat (owner decision 2 Oct, 12A): a small
/// dot that pulses while the agent works. Calm pulses at half the pace; Off,
/// reduced motion and tests keep it still.
class _LiveDot extends StatefulWidget {
  const _LiveDot();

  @override
  State<_LiveDot> createState() => _LiveDotState();
}

class _LiveDotState extends State<_LiveDot>
    with SingleTickerProviderStateMixin {
  static const _size = 8.0;
  late final AnimationController _pulse = AnimationController(vsync: this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final level = KitEffects.of(context).motion;
    if (!KitMotion.loops ||
        KitMotion.reduced(context) ||
        level == KitMotionLevel.off) {
      _pulse.stop();
      return;
    }
    _pulse.duration = level == KitMotionLevel.calm
        ? const Duration(milliseconds: 2800)
        : const Duration(milliseconds: 1400);
    if (!_pulse.isAnimating) _pulse.repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = KitTokens.of(context).roles.accent;
    return ExcludeSemantics(
      child: AnimatedBuilder(
        animation: _pulse,
        builder: (context, _) {
          final t = KitMotion.pulse.transform(_pulse.value);
          return SizedBox.square(
            dimension: _size * 1.6,
            child: Center(
              child: Opacity(
                opacity: 1 - 0.55 * t,
                child: Container(
                  key: const ValueKey('kit-turn-live-dot'),
                  width: _size * (1 - 0.25 * t),
                  height: _size * (1 - 0.25 * t),
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
