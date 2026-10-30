import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/gestures.dart'
    show PointerDeviceKind, kLongPressTimeout, kTouchSlop;

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../glass/kit_glass.dart';
import '../kit_bottom_inset.dart';
import '../kit_buttons.dart';
import '../kit_chip.dart';
import '../kit_divider.dart';
import '../kit_effects.dart';
import '../kit_field.dart';
import '../kit_icon.dart';
import '../kit_icon_button.dart';
import '../kit_layout.dart';
import '../kit_level_meter.dart';
import '../kit_live_background.dart';
import '../kit_max_width.dart';
import '../kit_menu.dart';
import '../kit_motion.dart';
import '../kit_surface_look.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tip.dart';
import '../kit_tokens.dart';
import '../motion/kit_haptics.dart';
import '../motion/kit_motion_parts.dart';
import '../motion/kit_reveal.dart';
import '../style/kit_style.dart';
import 'kit_composer_chips.dart';
import 'kit_turn.dart';

part 'kit_composer_types.dart';
part 'kit_composer_edge.dart';
part 'kit_composer_glow.dart';
part 'kit_composer_classic.dart';
part 'kit_composer_caption.dart';
part 'kit_composer_layer.dart';
part 'kit_composer_tray.dart';

/// The composer (VL §5; docs/ux-system/kit-api/KitComposer.md): one solid
/// surface2 row (concept C, owner 20 Oct 2026) holding "+", the field and
/// one trailing control: the mic while it is empty, Send once there are
/// words. Stop is not here: it sits at the trailing end of the chips row
/// above the pill while a reply runs, with or without text (owner, 30 Oct
/// 2026; [KitComposerStatusStrip.stop]). What the reply is doing is written
/// in the turn, not here; the model chip sits above the pill with the status
/// chips.
///
/// States: idle empty, idle with text, sending, busy empty, busy with text
/// (fixed delivery), busy with text that cannot send yet,
/// offline, read-only, focused, voice (every [KitVoicePhase]) (KIT-12). No
/// loading or empty state of its own.
///
/// The composer never clears or rewrites [controller]'s text: the host
/// clears it after a send it accepted (DATA-1).
class KitComposer extends StatefulWidget {
  const KitComposer({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.hint,
    required this.onSend,
    this.fieldLabel,
    this.busy = false,
    this.sending = false,
    this.canSendWhileBusy = false,
    this.delivery = KitComposerDelivery.afterThisReply,
    this.offline = false,
    this.offlineAgentName = 'OpenCode',
    this.readOnlyReason,
    this.note,
    this.attachments,
    this.suggestions,
    this.model,
    this.onTools,
    this.tray = const [],
    this.sendMenu = const [],
    this.sendTip,
    this.stuckNote,
    this.toolsDisabledReason,
    this.onVoice,
    this.voice,
    this.onOpenEditor,
    this.onContentInserted,
    this.composerKey,
    this.fieldKey,
    this.sendKey,
    this.toolsKey,
    this.voiceButtonKey,
    this.editorKey,
    this.hasAttachments = false,
    this.activityGlow,
    this.failure,
    this.quiet = false,
  });

  /// Something else on the screen is the main action now (a question the
  /// agent waits on): the mic draws as a quiet surface, not the solid
  /// accent, so the one solid mint is the question's answer. Send with
  /// words typed keeps its accent.
  final bool quiet;

  /// The message carries attachments or references, so Send is live (and
  /// the editor button shows) even with an empty field (chat-3).
  final bool hasAttachments;

  /// The soft ring sweep around the whole box while [busy], only when the
  /// person turned it on. Null follows Settings › Appearance › Effects
  /// ([KitEffects.activityGlow]).
  final bool? activityGlow;

  /// A failed send, in the pill's top row: what failed, Retry, Details.
  final KitComposerFailure? failure;

  final TextEditingController controller;
  final FocusNode focusNode;

  /// "Ask OpenCode…", "Ask {agent}…", "Message the team…".
  final String hint;
  final VoidCallback onSend;

  /// The field's accessible name; null: "Message".
  final String? fieldLabel;

  /// A reply is being written.
  final bool busy;

  /// Send's own tap is in flight.
  final bool sending;

  /// The server accepts a send during a reply.
  final bool canSendWhileBusy;

  /// Send queues: "Send when back online".
  final bool offline;

  /// Whose answer a waiting message is for ("Sends when OpenCode answers").
  final String offlineAgentName;

  /// What Send does while a reply runs. It is fixed by the agent, never a
  /// choice: the words say it on the button and in the note.
  final KitComposerDelivery delivery;

  /// Non-null: no typing; the reason is shown in the pill.
  final String? readOnlyReason;

  /// One muted line at the top of the pill ("Goes to the team's planner").
  final String? note;

  /// Non-null: "+" is hidden and this shows in the note line.
  final String? toolsDisabledReason;

  /// [KitComposerChips.attachments], above the field.
  final KitComposerChips? attachments;

  /// [KitComposerChips.suggestions], above the field.
  final KitComposerChips? suggestions;

  /// The model chip in the bottom row: only for a window too tight for the
  /// status line above the field ([KitComposerStatusStrip.model], decision
  /// 14A), where the chip would crowd the composer out. Otherwise null.
  final Widget? model;

  /// "+": the host's tools sheet (attach, photos, commands…).
  final VoidCallback? onTools;

  /// What "+" opens in place (plus-menu concept C): a compact row of round
  /// buttons attached to the box above the field, "+" turning into "×".
  /// Empty: "+" runs [onTools] instead. Needs [onTools] to show "+" at all.
  final List<KitComposerTrayAction> tray;

  /// Send's long-press menu ("Send later or repeat"). With a menu, a
  /// finger sends when it lifts (read from the pointer, so a button that
  /// slid away under it still sends) unless it was held for the menu;
  /// without one a finger sends the moment it lands ([_sendOnPress]).
  final List<KitMenuItem> sendMenu;

  /// The teaching system's tip over Send ("Hold Send to send later or
  /// repeat"), shown while non-null.
  final KitComposerTip? sendTip;

  /// The host's words for the note while the running step looks stuck ("Waits
  /// for git add -A to finish. Stop it to get through now."); it replaces
  /// the delivery's own note. Null: the delivery's note.
  final String? stuckNote;

  /// The mic: the host enters voice mode; null: no voice.
  final VoidCallback? onVoice;

  /// Full-screen editor; shown only when there is text.
  final VoidCallback? onOpenEditor;

  /// Non-null: voice mode.
  final KitComposerVoice? voice;

  /// IME images.
  final ValueChanged<KeyboardInsertedContent>? onContentInserted;

  final Key? composerKey,
      fieldKey,
      sendKey,
      toolsKey,
      voiceButtonKey,
      editorKey;

  /// Lays [composer] over the bottom of [body] (the transcript) as the
  /// floating navigation layer: [body] scrolls under the glass, and the
  /// composer's height is published with KitBottomInset.add so the last
  /// message, KitJumpPill and KitUndo stay clear of it. The keyboard lifts
  /// the composer; nothing else moves.
  ///
  /// [composer] is the glass [KitComposer], or the host's widget that
  /// builds one. [above] is solid content pinned over the composer (a
  /// request card, a note, a find bar): it sits on the ground, edge to edge
  /// on its own gutters, so the body passes under it unseen; the composer
  /// stays the only glass (visual language §6). The published height
  /// counts both, and the layer never grows past the body: [above] gets
  /// the room the composer leaves. [aboveMinHeight] is kept free for
  /// [above] whatever the composer's height (a waiting request at large
  /// text with the keyboard up): the composer then scrolls within the rest,
  /// its field and Send in view. Zero when [above] has nothing to protect.
  static Widget layer({
    Key? key,
    required Widget body,
    required Widget composer,
    Widget? above,
    double aboveMinHeight = 0,
  }) => _KitComposerLayer(
    key: key,
    body: body,
    composer: composer,
    above: above,
    aboveMinHeight: aboveMinHeight,
  );

  @override
  State<KitComposer> createState() => _KitComposerState();
}

enum _Trailing { mic, send, sendDisabled, sending, none }

class _KitComposerState extends State<KitComposer> {
  bool _hasText = false;

  /// The tray "+" opens ([KitComposer.tray]).
  bool _trayOpen = false;

  void _toggleTray() => setState(() => _trayOpen = !_trayOpen);

  void _pickFromTray(KitComposerTrayAction action) {
    setState(() => _trayOpen = false);
    action.onTap();
  }

  /// Esc hid the suggestions; they come back when the text changes or the
  /// host hands new ones.
  bool _suggestionsHidden = false;
  String _textAtHide = '';

  @override
  void initState() {
    super.initState();
    _hasText = _textOf(widget.controller);
    widget.controller.addListener(_onText);
  }

  @override
  void didUpdateWidget(KitComposer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onText);
      widget.controller.addListener(_onText);
      _hasText = _textOf(widget.controller);
    }
    if (oldWidget.suggestions != widget.suggestions) {
      _suggestionsHidden = false;
    }
  }

  @override
  void dispose() {
    _holdTimer?.cancel();
    widget.controller.removeListener(_onText);
    super.dispose();
  }

  static bool _textOf(TextEditingController c) => c.text.trim().isNotEmpty;

  void _onText() {
    final has = _textOf(widget.controller);
    final unhide = _suggestionsHidden && widget.controller.text != _textAtHide;
    if (has != _hasText || unhide) {
      setState(() {
        _hasText = has;
        if (unhide) _suggestionsHidden = false;
      });
    }
  }

  bool get _hasContent => _hasText || widget.hasAttachments;

  bool get _readOnly => widget.readOnlyReason != null;

  bool get _canSendNow =>
      !_readOnly &&
      widget.voice == null &&
      _hasContent &&
      !widget.sending &&
      (!widget.busy || widget.canSendWhileBusy);

  void _send() {
    if (!_canSendNow) return;
    KitHaptics.send(context);
    widget.onSend();
  }

  /// True from a touch press that already sent until the same touch ends, so
  /// the tap that follows it does not send twice.
  bool _sentOnPress = false;

  /// A finger sends the moment it lands on Send. When the keyboard hides the
  /// composer slides down under the finger, and a release on a moved button
  /// was lost (the first Send only hid the keyboard). Mouse, stylus and
  /// screen readers still send on the tap.
  void _sendOnPress(PointerDownEvent event) {
    if (event.kind != PointerDeviceKind.touch || !_canSendNow) return;
    _sentOnPress = true;
    _send();
  }

  void _endPress() {
    if (_sentOnPress) scheduleMicrotask(() => _sentOnPress = false);
  }

  /// With a long-press menu ([KitComposer.sendMenu]) a finger cannot send
  /// the moment it lands, or a long-press could not offer the menu without
  /// sending. It sends when it lifts, unless it was held long enough for
  /// the menu or slid away. The lift is read from the pointer itself, not
  /// from a tap gesture, so it still counts when the composer slides down
  /// under the finger as the keyboard hides (the lost first Send).
  int? _heldPointer;
  Offset _heldAt = Offset.zero;
  bool _heldLong = false;
  Timer? _holdTimer;

  void _pressDown(PointerDownEvent event) {
    if (widget.sendMenu.isEmpty) {
      _sendOnPress(event);
      return;
    }
    if (event.kind != PointerDeviceKind.touch || !_canSendNow) return;
    _heldPointer = event.pointer;
    _heldAt = event.position;
    _heldLong = false;
    _holdTimer?.cancel();
    _holdTimer = Timer(kLongPressTimeout, () => _heldLong = true);
  }

  void _pressUp(PointerUpEvent event) {
    if (event.pointer == _heldPointer) {
      _holdTimer?.cancel();
      _heldPointer = null;
      final slid = (event.position - _heldAt).distance > kTouchSlop;
      if (!_heldLong && !slid && _canSendNow) {
        // The tap that follows in this same lift does not send again.
        _sentOnPress = true;
        _send();
      }
    }
    _endPress();
  }

  void _pressCancel(PointerCancelEvent event) {
    _sentOnPress = false;
    if (event.pointer == _heldPointer) {
      _holdTimer?.cancel();
      _heldPointer = null;
    }
  }

  void _sendOnTap() {
    if (_sentOnPress) return;
    _send();
  }

  bool get _suggestionsShown {
    final s = widget.suggestions;
    return s != null && !_suggestionsHidden && s.hasSuggestionContent;
  }

  void _escape() {
    if (_trayOpen) {
      setState(() => _trayOpen = false);
      return;
    }
    if (_suggestionsShown) {
      setState(() {
        _suggestionsHidden = true;
        _textAtHide = widget.controller.text;
      });
      return;
    }
    final voice = widget.voice;
    if (voice != null) {
      voice.onExit();
      return;
    }
    widget.focusNode.unfocus();
  }

  _Trailing _trailing() {
    if (_readOnly) return _Trailing.none;
    // One trailing control: Send when there is something to send, the mic
    // when there is not. Stop is not here (it sits above the pill, in the
    // chips row: [KitComposerStatusStrip.stop]).
    if (widget.sending) return _Trailing.sending;
    if (widget.busy) {
      if (!_hasContent) {
        return widget.onVoice != null ? _Trailing.mic : _Trailing.sendDisabled;
      }
      return widget.canSendWhileBusy ? _Trailing.send : _Trailing.none;
    }
    if (_hasContent) return _Trailing.send;
    return widget.onVoice != null ? _Trailing.mic : _Trailing.sendDisabled;
  }

  /// Whether [text] runs past one line of the field ([width] wide): only
  /// then does the full-screen editor's button take a corner of it.
  static bool _runsPastOneLine(
    BuildContext context,
    String text,
    TextStyle style,
    double width,
  ) {
    if (text.contains('\n')) return true;
    if (!width.isFinite || width <= 0) return false;
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout(maxWidth: width);
    final over = painter.didExceedMaxLines;
    painter.dispose();
    return over;
  }

  String _sendWords(AppLocalizations l10n) {
    if (widget.sending) return l10n.kitComposerSending;
    if (widget.offline) return l10n.kitComposerSendOffline;
    if (widget.busy) {
      return switch (widget.delivery) {
        KitComposerDelivery.addToThisTurn => l10n.kitComposerAddToTurn,
        KitComposerDelivery.stopAndSend => l10n.kitComposerStopAndSend,
        KitComposerDelivery.afterThisReply => l10n.kitComposerSendAfter,
      };
    }
    return l10n.kitComposerSend;
  }

  String? _noteLine(AppLocalizations l10n) {
    final reason = widget.readOnlyReason;
    if (reason != null) return reason;
    final parts = <String>[
      if (widget.note case final note? when note.isNotEmpty) note,
      if (widget.toolsDisabledReason case final r? when r.isNotEmpty) r,
      if (widget.offline)
        l10n.kitComposerWaitsForAgent(widget.offlineAgentName),
      if (widget.busy && _hasContent && !widget.canSendWhileBusy)
        l10n.kitComposerCannotSendYet,
      if (widget.busy && _hasContent && widget.canSendWhileBusy)
        widget.stuckNote ??
            switch (widget.delivery) {
              KitComposerDelivery.addToThisTurn => l10n.kitComposerAddsToReply,
              KitComposerDelivery.stopAndSend =>
                l10n.kitComposerCantTakeMidReply(widget.offlineAgentName),
              KitComposerDelivery.afterThisReply => l10n.kitComposerSendsAfter,
            },
    ];
    return parts.isEmpty ? null : parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final radius = KitLayout.windowOf(context).isWide
        ? tokens.composerRadiusWide
        : tokens.composerRadius;
    final voice = widget.voice;

    final content = voice == null
        ? KeyedSubtree(
            key: const ValueKey('kit-composer-text'),
            child: _textContent(context, l10n),
          )
        : KeyedSubtree(
            key: const ValueKey('kit-composer-voice'),
            child: _VoiceContent(voice: voice, l10n: l10n),
          );

    return CallbackShortcuts(
      bindings: {const SingleActivator(LogicalKeyboardKey.escape): _escape},
      child: FocusTraversalGroup(
        policy: WidgetOrderTraversalPolicy(),
        child: Semantics(
          key: widget.composerKey,
          container: true,
          explicitChildNodes: true,
          // The whole pill counts as inside the field for "tap outside":
          // a mouse or stylus press on Send would otherwise drop the
          // focus first, the keyboard would hide, the page would slide
          // down under the pointer and the release would land on nothing
          // (the first Send press only hid the keyboard).
          child: TextFieldTapRegion(
            child: _ComposerFrame(
              active: widget.busy,
              radius: radius,
              activityGlow: widget.activityGlow,
              surface: KitGlass(
                borderRadius: BorderRadius.circular(radius),
                shadow: true,
                child: Padding(
                  padding: EdgeInsets.all(tokens.space1),
                  child: KitSwap(
                    pace: KitPace.standard,
                    alignment: AlignmentDirectional.bottomCenter,
                    child: content,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _textContent(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final media = MediaQuery.of(context);
    final note = _noteLine(l10n);
    final short = KitLayout.isShort(context);
    // Under [KitComposer.layer] the room is the layer's own height (a page
    // frame has already taken the keyboard off it and hidden the inset);
    // elsewhere, the window less the keyboard.
    final available =
        _KitComposerRoom.of(context) ??
        media.size.height - media.viewInsets.bottom;
    final fieldCap = (available * KitLayout.composerMaxShare)
        .floorToDouble()
        .clamp(tokens.minTarget, double.infinity);
    final readOnly = _readOnly;
    final inserted = widget.onContentInserted;

    final field = ConstrainedBox(
      // At least one 48 dp target tall (LAY-9): the field's own node is
      // the tap target a screen reader and G5 measure.
      constraints: BoxConstraints(
        minHeight: tokens.minTarget,
        maxHeight: fieldCap,
      ),
      // The field needs a Material ancestor; the glass is the composer's
      // own surface, so a host that floats it outside a Scaffold (the
      // layer) still works.
      child: Material(
        type: MaterialType.transparency,
        child: KitField.composer(
          label: widget.fieldLabel ?? l10n.kitComposerField,
          controller: widget.controller,
          hint: widget.hint,
          focusNode: widget.focusNode,
          onSubmitted: (_) => _send(),
          contentInsertion: inserted == null
              ? null
              : ContentInsertionConfiguration(onContentInserted: inserted),
          maxLines: short ? 3 : 8,
          enabled: !readOnly,
          disabledReason: widget.readOnlyReason,
          fieldKey: widget.fieldKey,
        ),
      ),
    );

    // One row (concept C, owner 20 Oct 2026): "+" at the start, the field,
    // and the one trailing control (mic, Send or Stop) at the end, all on
    // the field's last line as it grows.
    final tools =
        !readOnly &&
        widget.onTools != null &&
        widget.toolsDisabledReason == null;
    final hasTray = tools && widget.tray.isNotEmpty;
    final trayShown = hasTray && _trayOpen;
    // The full-screen editor opens from the field's top corner, where it
    // belongs to the words, not from the send row (owner Fix). Only once the
    // words run past one line, or the message carries files (the editor is
    // where they are reviewed): a short message needs no bigger editor.
    final canEdit = !readOnly && _hasContent && widget.onOpenEditor != null;
    final bodyStyle = KitText.styleOf(context, KitTextRole.body);
    // One tree whether or not the editor button shows: the field keeps its
    // place (and its editing state, selection and keyboard) as words come
    // and go, a run finishes or the keyboard resizes the page.
    final fieldWithEditor = LayoutBuilder(
      builder: (context, box) => ListenableBuilder(
        listenable: widget.controller,
        child: field,
        builder: (context, field) {
          final editor =
              canEdit &&
              (widget.hasAttachments ||
                  _runsPastOneLine(
                    context,
                    widget.controller.text,
                    bodyStyle,
                    box.maxWidth,
                  ));
          return Stack(
            children: [
              Padding(
                padding: editor
                    ? EdgeInsetsDirectional.only(end: tokens.minTarget)
                    : EdgeInsets.zero,
                child: field,
              ),
              if (editor)
                PositionedDirectional(
                  top: 0,
                  end: 0,
                  child: KitIconButton(
                    key: widget.editorKey,
                    icon: AppIconography.expand,
                    tooltip: l10n.kitComposerEditor,
                    onPressed: widget.onOpenEditor,
                  ),
                ),
            ],
          );
        },
      ),
    );

    // What sits above the field: the note, the
    // suggestions and the attachments. When they do not fit (large text on
    // a small window) they
    // scroll and give their room up to the field and the send row, which
    // always stay; nothing is truncated (KIT-24).
    final accessories = <Widget>[
      if (widget.failure case final failure?) _FailureRow(failure: failure),
      if (note != null)
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            end: tokens.space3,
            top: tokens.space2,
          ),
          // A note that changes (a send's words as the agent starts working,
          // an offline line) fades to the new words instead of jumping.
          child: KitSwap(
            alignment: AlignmentDirectional.centerStart,
            child: KitText(
              note,
              key: ValueKey<String>(note),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        ),
      if (!readOnly && _suggestionsShown)
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space2,
            end: tokens.space2,
            top: tokens.space2,
          ),
          child: widget.suggestions!,
        ),
      if (!readOnly && widget.attachments != null)
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space2,
            end: tokens.space2,
            top: tokens.space2,
          ),
          child: widget.attachments!,
        ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final above = accessories.isEmpty
            ? null
            : Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: accessories,
              );
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (above != null)
              constraints.hasBoundedHeight
                  ? Flexible(
                      child: SingleChildScrollView(
                        key: const ValueKey<String>('kit-composer-accessories'),
                        child: above,
                      ),
                    )
                  : above,
            KitReveal(
              child: trayShown
                  ? _ComposerTray(actions: widget.tray, onPicked: _pickFromTray)
                  : null,
            ),
            // Once the words run past one line of the one-row field, they
            // take the composer's whole width and the controls move to a row
            // under them (owner, 24 Oct 2026: "+", Stop, the editor and Send
            // beside a growing message left it a narrow column). Measured
            // against the one-row field's width however it is laid out now,
            // so the two layouts never take turns. The field stays the first
            // row's keyed child either way: its focus, selection and the
            // keyboard stay as it moves.
            ListenableBuilder(
              listenable: widget.controller,
              builder: (context, _) {
                final leading = <Widget>[
                  if (tools)
                    // "+" turns into "×" while the tray is open (a quarter
                    // of a quarter turn), instantly with reduced motion.
                    AnimatedRotation(
                      turns: trayShown ? 1 / 8 : 0,
                      duration: KitMotion.reduced(context)
                          ? Duration.zero
                          : KitMotion.quick,
                      curve: KitMotion.enter,
                      child: KitIconButton(
                        key: widget.toolsKey,
                        icon: AppIconography.add,
                        tooltip: trayShown
                            ? l10n.kitComposerTrayClose
                            : l10n.kitComposerTools,
                        onPressed: hasTray ? _toggleTray : widget.onTools,
                      ),
                    ),
                ];
                final trailing = <Widget>[
                  if (!readOnly && widget.model != null) widget.model!,
                  KitSwap(child: _trailingControl(context, l10n)),
                ];
                final rowField =
                    constraints.maxWidth -
                    tokens.minTarget * (leading.length + 1) -
                    tokens.space2 -
                    (leading.isEmpty ? tokens.space3 : 0);
                final stacked =
                    !readOnly &&
                    _runsPastOneLine(
                      context,
                      widget.controller.text,
                      bodyStyle,
                      rowField,
                    );
                final fieldSlot = Expanded(
                  // Keyed (the row's own child) so a note, suggestions or
                  // attachments appearing above it, or the controls moving
                  // under it, never re-create the field (focus and the
                  // keyboard stay).
                  key: const ValueKey<String>('kit-composer-field-slot'),
                  child: Padding(
                    padding: !stacked && tools
                        ? EdgeInsetsDirectional.only(end: tokens.space2)
                        : EdgeInsetsDirectional.only(
                            start: tokens.space3,
                            end: tokens.space2,
                          ),
                    child: fieldWithEditor,
                  ),
                );
                return Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        if (!stacked) ...leading,
                        fieldSlot,
                        if (!stacked) ...trailing,
                      ],
                    ),
                    if (stacked)
                      Row(
                        key: const ValueKey<String>('kit-composer-controls'),
                        children: [...leading, const Spacer(), ...trailing],
                      ),
                  ],
                );
              },
            ),
          ],
        );
      },
    );
  }

  Widget _trailingControl(BuildContext context, AppLocalizations l10n) {
    final wide = KitLayout.windowOf(context).isWide;
    final trailing = _trailing();
    final sendShortcut = wide && !_readOnly ? 'Enter' : null;

    // An agent that cannot take a message mid-reply: Send is an amber
    // labelled "Stop and send", so the stop is plain before the tap.
    final stopAndSend =
        widget.busy &&
        !widget.sending &&
        widget.delivery == KitComposerDelivery.stopAndSend;

    Widget send({bool enabled = true}) => Listener(
      onPointerDown: enabled ? _pressDown : null,
      onPointerUp: _pressUp,
      onPointerCancel: _pressCancel,
      child: KitTip(
        tipKey: const ValueKey('kit-composer-send-tip-bubble'),
        message: widget.sendTip?.message ?? '',
        visible: widget.sendTip != null && enabled,
        onDismissed: () => widget.sendTip?.onDismissed(),
        child: enabled && stopAndSend
            ? _Circle(
                key: const ValueKey('kit-composer-send'),
                kind: _CircleKind.stopAndSend,
                label: l10n.kitComposerStopAndSend,
                tappableKey: widget.sendKey,
                shortcut: sendShortcut,
                menu: widget.sendMenu,
                onTap: _sendOnTap,
              )
            : _Circle(
                key: const ValueKey('kit-composer-send'),
                kind: enabled ? _CircleKind.send : _CircleKind.sendDisabled,
                label: _sendWords(l10n),
                tappableKey: widget.sendKey,
                shortcut: sendShortcut,
                working: widget.sending,
                menu: widget.sending ? const [] : widget.sendMenu,
                disabledReason: widget.sending
                    ? l10n.kitComposerSending
                    : (enabled ? null : widget.hint),
                onTap: enabled && !widget.sending ? _sendOnTap : null,
              ),
      ),
    );

    return switch (trailing) {
      _Trailing.none => const SizedBox.shrink(key: ValueKey('none')),
      _Trailing.mic => _Circle(
        key: const ValueKey('kit-composer-mic'),
        kind: _CircleKind.mic,
        label: l10n.kitComposerVoice,
        tappableKey: widget.voiceButtonKey,
        onTap: widget.onVoice,
        secondary: widget.quiet,
      ),
      _Trailing.send => send(),
      _Trailing.sending => send(),
      _Trailing.sendDisabled => send(enabled: false),
    };
  }
}

enum _CircleKind {
  send,
  stopAndSend,
  sendDisabled,
  mic,
  stop,
  listen,
  voiceSend,
  voiceDone,
}
