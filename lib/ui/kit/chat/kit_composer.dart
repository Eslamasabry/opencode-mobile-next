import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' show PathMetric;
import 'package:flutter/gestures.dart' show PointerDeviceKind;

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
import '../kit_field.dart';
import '../kit_icon.dart';
import '../kit_icon_button.dart';
import '../kit_layout.dart';
import '../kit_level_meter.dart';
import '../kit_motion.dart';
import '../kit_segmented.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_haptics.dart';
import '../kit_effects.dart';
import '../kit_since.dart';
import '../motion/kit_motion_parts.dart';
import 'kit_composer_chips.dart';
import 'kit_turn.dart';

/// A send that failed, for the composer's top row: what failed in plain
/// words, Retry, and Details when there is technical text to show.
@immutable
class KitComposerFailure {
  const KitComposerFailure({
    required this.words,
    required this.onRetry,
    this.onDetails,
    this.retryKey,
  });

  final String words;
  final VoidCallback onRetry;
  final VoidCallback? onDetails;
  final Key? retryKey;
}

/// What Send does while a reply is being written.
enum KitComposerDelivery {
  /// The default (P6.6 "Queue over Steer"): sent when the reply finishes.
  afterThisReply,

  /// Steer: reaches the agent at its next step (OpenCode 2 only).
  addToThisTurn,
}

/// Where voice mode stands (P10.3).
enum KitVoicePhase {
  /// Getting the microphone.
  starting,

  /// Recording; the level meter moves.
  listening,

  /// Turning speech into text.
  transcribing,

  /// A voice turn was sent; waiting for the reply.
  waitingReply,

  /// Reading the reply aloud.
  speakingReply,

  /// The reply could not be read automatically; "Read it aloud" is offered.
  replyReady,

  /// A request needs the person; listening waits.
  paused,

  /// The app may not use the microphone: explained in the mode, with a fix.
  micDenied,

  /// The engine failed: the reason, with a fix.
  failed,
}

/// Voice mode's state and actions. Non-null [KitComposer.voice] turns the
/// pill into voice mode.
@immutable
class KitComposerVoice {
  const KitComposerVoice({
    required this.phase,
    required this.onExit,
    this.conversation = false,
    this.level,
    this.listeningSince,
    this.onListen,
    this.onStopListening,
    this.onStopSpeaking,
    this.onReadReply,
    this.readRepliesAloud = false,
    this.onReadRepliesAloudChanged,
    this.reason,
    this.fix,
    this.voiceKey,
  });

  final KitVoicePhase phase;

  /// Leaves voice mode; the draft keeps what was said.
  final VoidCallback onExit;

  /// True: speech is sent and replies loop (voice conversation); false:
  /// dictation into the draft.
  final bool conversation;

  /// 0..1 while listening ([KitLevelMeter.listen]).
  final ValueListenable<double>? level;

  /// When this recording began: the elapsed time ("0:42"), no cap (P10.3).
  final DateTime? listeningSince;

  /// Starts listening (idle, replyReady, after a reply).
  final VoidCallback? onListen;

  /// Ends this recording: dictation fills the draft, conversation sends.
  final VoidCallback? onStopListening;

  /// speakingReply: stop reading aloud.
  final VoidCallback? onStopSpeaking;

  /// replyReady: read the reply by hand.
  final VoidCallback? onReadReply;

  final bool readRepliesAloud;

  /// Null: the "Read replies aloud" toggle is not shown.
  final ValueChanged<bool>? onReadRepliesAloudChanged;

  /// micDenied / failed: the words; replyReady: why the reply was not read
  /// aloud, when it was not.
  final String? reason;

  /// micDenied: "Allow microphone"; failed: "Try again".
  final KitAction? fix;

  final Key? voiceKey;
}

/// The composer (VL §5; docs/ux-system/kit-api/KitComposer.md): a surface2
/// glass pill (KitGlass, dimmed) holding attach, the field, the model chip,
/// voice, and send or stop. Send is an accent circle; Stop is a danger
/// circle with an on-danger square.
///
/// States: idle empty, idle with text, sending, busy empty, busy with text
/// (stop + send, delivery choice), busy with text that cannot send yet,
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
    this.onStop,
    this.stopping = false,
    this.sending = false,
    this.canSendWhileBusy = false,
    this.delivery = KitComposerDelivery.afterThisReply,
    this.onDeliveryChanged,
    this.offline = false,
    this.readOnlyReason,
    this.note,
    this.attachments,
    this.suggestions,
    this.model,
    this.onTools,
    this.toolsDisabledReason,
    this.onVoice,
    this.voice,
    this.onOpenEditor,
    this.onContentInserted,
    this.composerKey,
    this.fieldKey,
    this.sendKey,
    this.stopKey,
    this.toolsKey,
    this.voiceButtonKey,
    this.editorKey,
    this.deliveryKey,
    this.hasAttachments = false,
    this.rail,
    this.failure,
    this.railNote,
  });

  /// The message carries attachments or references, so Send is live (and
  /// the editor button shows) even with an empty field (chat-3).
  final bool hasAttachments;

  /// The running reply's status: a pill on the composer's top border (a
  /// bump of the box's own outline) with a small spinner, the phase words,
  /// the time and a red Stop. It grows out of the border line when a reply
  /// starts and shrinks back into it when it ends; the composer grows
  /// upward by the pill's height while it shows. A live region: it
  /// announces the phase, not the seconds. Sending reads as thinking.
  final KitTurnLive? rail;

  /// A failed send, in the same pill: what failed, Retry, Details. Wins
  /// over [rail].
  final KitComposerFailure? failure;

  /// A short second segment after the status words ("Sends after this
  /// reply", the AI team's "also working" hint).
  final String? railNote;

  final TextEditingController controller;
  final FocusNode focusNode;

  /// "Ask OpenCode…", "Ask {agent}…", "Message the team…".
  final String hint;
  final VoidCallback onSend;

  /// The field's accessible name; null: "Message".
  final String? fieldLabel;

  /// A reply is being written.
  final bool busy;

  /// Stop's own tap is in flight.
  final bool stopping;

  /// Send's own tap is in flight.
  final bool sending;

  /// The server accepts a send during a reply.
  final bool canSendWhileBusy;

  /// Send queues: "Send when back online".
  final bool offline;

  /// Null: no Stop in the composer (a host that cannot stop, or one whose
  /// Stop is on the running turn's live line); while busy and empty the
  /// trailing control is then the mic.
  final VoidCallback? onStop;
  final KitComposerDelivery delivery;

  /// Null: no choice (the words still say "after this reply").
  final ValueChanged<KitComposerDelivery>? onDeliveryChanged;

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

  /// [KitComposerChips.model], in the bottom row.
  final KitComposerChips? model;

  /// "+": the host's tools sheet (attach, photos, commands…).
  final VoidCallback? onTools;

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
      stopKey,
      toolsKey,
      voiceButtonKey,
      editorKey,
      deliveryKey;

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

enum _Trailing { mic, send, sendDisabled, sending, stop, none }

class _KitComposerState extends State<KitComposer> {
  bool _hasText = false;

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

  /// The height a touch caret handle hangs below the line it marks (the
  /// Material handle; Cupertino's is shorter).
  static const double _handleClearance = 22;

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

  void _sendOnTap() {
    if (_sentOnPress) return;
    _send();
  }

  bool get _suggestionsShown {
    final s = widget.suggestions;
    return s != null && !_suggestionsHidden && s.hasSuggestionContent;
  }

  void _escape() {
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
    if (_readOnly) {
      return widget.busy && widget.onStop != null
          ? _Trailing.stop
          : _Trailing.none;
    }
    final stop = widget.onStop != null;
    // One trailing control (owner Fix): Send when there is something to
    // send, Stop when there is not. While a run works and words are typed,
    // Stop moves to the start of the row ([_stopLeads]), never beside Send.
    if (widget.sending) return _Trailing.sending;
    if (widget.busy) {
      // A host whose Stop lives on the running turn ([KitTurnLive]) passes
      // no [onStop]: the mic stays, so a message can be spoken while the
      // reply runs and sent after it.
      if (!_hasContent) {
        return stop
            ? _Trailing.stop
            : widget.onVoice != null
            ? _Trailing.mic
            : _Trailing.sendDisabled;
      }
      if (widget.canSendWhileBusy) return _Trailing.send;
      return stop ? _Trailing.stop : _Trailing.none;
    }
    if (_hasContent) return _Trailing.send;
    return widget.onVoice != null ? _Trailing.mic : _Trailing.sendDisabled;
  }

  /// Stop while the trailing slot is Send: a run works and there is
  /// something to send. It sits at the start of the row, after "+", so a
  /// thumb reaching for Send never lands on it.
  bool get _stopLeads =>
      !_readOnly &&
      widget.busy &&
      widget.onStop != null &&
      (widget.sending || (_hasContent && widget.canSendWhileBusy));

  String _sendWords(AppLocalizations l10n) {
    if (widget.sending) return l10n.kitComposerSending;
    if (widget.offline) return l10n.kitComposerSendOffline;
    if (widget.busy) {
      return widget.delivery == KitComposerDelivery.addToThisTurn
          ? l10n.kitComposerAddToTurn
          : l10n.kitComposerSendAfter;
    }
    return l10n.kitComposerSend;
  }

  bool get _deliveryShown =>
      !_readOnly &&
      widget.busy &&
      _hasContent &&
      widget.canSendWhileBusy &&
      widget.onDeliveryChanged != null;

  String? _noteLine(AppLocalizations l10n) {
    final reason = widget.readOnlyReason;
    if (reason != null) return reason;
    final parts = <String>[
      if (widget.note case final note? when note.isNotEmpty) note,
      if (widget.toolsDisabledReason case final r? when r.isNotEmpty) r,
      if (widget.offline) l10n.kitComposerOffline,
      if (widget.busy && _hasContent && !widget.canSendWhileBusy)
        l10n.kitComposerCannotSendYet,
      if (widget.busy &&
          _hasContent &&
          widget.canSendWhileBusy &&
          widget.onDeliveryChanged == null)
        widget.delivery == KitComposerDelivery.addToThisTurn
            ? l10n.kitComposerAddToTurn
            : l10n.kitComposerSendsAfter,
    ];
    return parts.isEmpty ? null : parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) {
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
            child: _LivingEdge(
              live: widget.rail,
              failure: widget.failure,
              note: widget.railNote,
              radius: radius,
              glass: KitGlass(
                borderRadius: BorderRadius.circular(radius),
                dim: true,
                shadow: true,
                flow: true,
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

    final bottomRow = <Widget>[
      if (!readOnly &&
          widget.onTools != null &&
          widget.toolsDisabledReason == null)
        KitIconButton(
          key: widget.toolsKey,
          icon: AppIconography.add,
          tooltip: l10n.kitComposerTools,
          onPressed: widget.onTools,
        ),
      if (_stopLeads) _stopControl(l10n),
      if (!readOnly && widget.model != null)
        Flexible(
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: widget.model!,
          ),
        )
      else
        const Spacer(),
      KitSwap(child: _trailingControl(context, l10n)),
    ];
    // The full-screen editor opens from the field's top corner, where it
    // belongs to the words, not from the send row (owner Fix).
    final editor = !readOnly && _hasContent && widget.onOpenEditor != null;
    // One tree whether or not the editor button shows: the field keeps its
    // place (and its editing state, selection and keyboard) as words come
    // and go, a run finishes or the keyboard resizes the page.
    final fieldWithEditor = Stack(
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

    // What sits above the field: the note, the delivery choice, the
    // suggestions and the attachments. When they do not fit (large text on
    // a small window, where the delivery choice stacks as radio rows) they
    // scroll and give their room up to the field and the send row, which
    // always stay; nothing is truncated (KIT-24).
    final accessories = <Widget>[
      if (note != null)
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            end: tokens.space3,
            top: tokens.space2,
          ),
          child: KitText(
            note,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
      if (_deliveryShown)
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space2,
            end: tokens.space2,
            top: tokens.space2,
          ),
          child: KitSegmented<KitComposerDelivery>(
            key: widget.deliveryKey,
            semanticsLabel: l10n.kitComposerDeliveryLabel,
            selected: widget.delivery,
            onChanged: widget.onDeliveryChanged,
            segments: [
              KitSegment(
                value: KitComposerDelivery.afterThisReply,
                label: l10n.kitComposerSendAfterShort,
              ),
              KitSegment(
                value: KitComposerDelivery.addToThisTurn,
                label: l10n.kitComposerAddToTurnShort,
              ),
            ],
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

    final handleClearance = _hasText && available >= 6 * tokens.minTarget
        ? _handleClearance
        : 0.0;
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
            Padding(
              // Keyed so a note, suggestions or attachments appearing above it
              // never re-create the field (focus and the keyboard stay).
              key: const ValueKey<String>('kit-composer-field-slot'),
              padding: EdgeInsetsDirectional.only(
                start: tokens.space3,
                end: tokens.space3,
                // The status caption straddles the border, so its lower half
                // sits in this room; it is constant, so the box never
                // changes height when a reply starts.
                top: tokens.space1,
                // With words in the field its caret handle can hang below
                // the last line; this keeps it off the send row, so the
                // prompt editor and Send stay pressable (320 dp, 2x text).
                // A short room keeps the height for the field instead.
                bottom: handleClearance,
              ),
              child: fieldWithEditor,
            ),
            Row(children: bottomRow),
          ],
        );
      },
    );
  }

  Widget _stopControl(AppLocalizations l10n) => _Circle(
    key: const ValueKey('kit-composer-stop'),
    kind: _CircleKind.stop,
    label: l10n.kitComposerStop,
    tappableKey: widget.stopKey,
    working: widget.stopping,
    disabledReason: widget.stopping ? l10n.kitWorking : null,
    onTap: widget.stopping ? null : widget.onStop,
  );

  Widget _trailingControl(BuildContext context, AppLocalizations l10n) {
    final wide = KitLayout.windowOf(context).isWide;
    final trailing = _trailing();
    final sendShortcut = wide && !_readOnly ? 'Enter' : null;

    Widget stop() => _stopControl(l10n);

    Widget send({bool enabled = true}) => Listener(
      onPointerDown: enabled ? _sendOnPress : null,
      onPointerUp: (_) => _endPress(),
      onPointerCancel: (_) => _sentOnPress = false,
      child: _Circle(
        key: const ValueKey('kit-composer-send'),
        kind: enabled ? _CircleKind.send : _CircleKind.sendDisabled,
        label: _sendWords(l10n),
        tappableKey: widget.sendKey,
        shortcut: sendShortcut,
        working: widget.sending,
        disabledReason: widget.sending
            ? l10n.kitComposerSending
            : (enabled ? null : widget.hint),
        onTap: enabled && !widget.sending ? _sendOnTap : null,
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
      ),
      _Trailing.send => send(),
      _Trailing.sending => send(),
      _Trailing.sendDisabled => send(enabled: false),
      _Trailing.stop => stop(),
    };
  }
}

enum _CircleKind { send, sendDisabled, mic, stop, listen, voiceSend, voiceDone }

/// A 40 dp circle in a 48 dp target: Send, the mic, Stop.
/// What the edge says now: a running reply or a failed send.
@immutable
class _EdgeData {
  const _EdgeData({this.live, this.failure, this.note});

  final KitTurnLive? live;
  final KitComposerFailure? failure;
  final String? note;

  bool get isEmpty => live == null && failure == null;
}

/// The light that runs on the outline: mutable, painted by [_EdgePainter],
/// stepped by the state's ticker.
class _EdgeLight {
  /// Head position along the outline, 0..1 of one lap.
  double phase = 0;

  /// Laps per second.
  double speed = 0;

  /// 0..1.
  double bright = 0;

  /// 0..1: how far the heartbeat at the gap's edges has taken over.
  double stall = 0;

  /// 0..1: the faint accent tint (only while text streams).
  double tint = 0;

  /// Tail length as a share of the outline.
  double tail = 0.3;

  /// Seconds, for the breathing and the heartbeat.
  double time = 0;

  /// Calm: a still glow instead of a travelling light (0..1).
  double still = 0;
}

/// "The edge is the status": the composer's top border eases down into one
/// wide, shallow dip that cradles the phase words (with a small red stop),
/// and a soft light travels the outline at the pace of the model.
///
/// - Zero height change: the caption straddles the border line.
/// - Full motion: the dip deepens from nothing along one bell; a soft glow
///   runs the outline (slow and breathing while thinking, following
///   [KitTurnLive.pace] while writing, settling to a breathing hue at the
///   dip's edges when the server is quiet, never faster than
///   [KitMotion.edgeLightMaxLapsPerSecond]); at the end it fades into the
///   border as the dip straightens. A failure washes the outline once in
///   the neutral text colour, never red (LOOK-5).
/// - Calm: the caption, a half-depth dip and a slow breathing hue.
/// - Off or reduced motion: the caption and the dip, still.
///
/// The light is decorative and left out of semantics; the caption is the
/// live region. The ticker only runs while there is something to show, and
/// stops with the route (TickerMode) and the app in the background.
class _LivingEdge extends StatefulWidget {
  const _LivingEdge({
    required this.glass,
    required this.radius,
    this.live,
    this.failure,
    this.note,
  });

  final Widget glass;
  final double radius;
  final KitTurnLive? live;
  final KitComposerFailure? failure;
  final String? note;

  @override
  State<_LivingEdge> createState() => _LivingEdgeState();
}

class _LivingEdgeState extends State<_LivingEdge>
    with TickerProviderStateMixin {
  static const _sealSeconds = 1.2;
  double _sealBright = 0;

  /// When [KitTurnLive.pace] last changed, on the light's own clock: the
  /// pace decays from then, so a screen that stops rebuilding as words stop
  /// still lets the light settle.
  double _paceStamp = 0;

  late final AnimationController _open = AnimationController(vsync: this);
  late final AnimationController _flash = AnimationController(
    vsync: this,
    duration: KitMotion.composerFlash,
  );
  late final Ticker _ticker = createTicker(_step);
  final _light = _EdgeLight();
  final _repaint = ValueNotifier<int>(0);

  late _EdgeData _data = _current;
  late _EdgeData _shown = _current;
  Duration _last = Duration.zero;
  double? _seal;
  bool _closing = false;
  bool _ticked = false;
  double _captionWidth = 0;

  _EdgeData get _current =>
      _EdgeData(live: widget.live, failure: widget.failure, note: widget.note);

  @override
  void initState() {
    super.initState();
    _open.value = _data.isEmpty ? 0 : 1;
  }

  /// Full and Calm animate the light (Calm only breathes a hue); Off and
  /// reduced motion keep it still. Tests keep loops off, like every loop.
  bool get _loops =>
      KitMotion.loops &&
      !KitMotion.reduced(context) &&
      KitEffects.of(context).motion != KitMotionLevel.off;

  /// Only Full lets the light travel.
  bool get _travels => _loops && _level == KitMotionLevel.full;
  KitMotionLevel get _level => KitEffects.of(context).motion;

  void _animateOpen(double to) {
    if (KitMotion.reduced(context)) {
      _open.value = to;
      return;
    }
    _open.animateTo(
      to,
      duration: _level == KitMotionLevel.calm
          ? KitMotion.composerOpenCalm
          : KitMotion.composerOpen,
      curve: KitMotion.composerOpenCurve,
    );
  }

  @override
  void didUpdateWidget(_LivingEdge old) {
    super.didUpdateWidget(old);
    final before = _data;
    _data = _current;
    if (before.live?.pace != _data.live?.pace) _paceStamp = _light.time;
    if (!_data.isEmpty) {
      _shown = _data;
      _seal = null;
      _closing = false;
      _animateOpen(1);
      if (_data.failure != null && before.failure == null && _loops) {
        _flash.forward(from: 0);
      }
      final live = _data.live;
      if (live?.activity == KitTurnActivity.writing && !_ticked) {
        _ticked = true;
        // The first word: a light tick, under the person's own vibration
        // setting.
        KitHaptics.send(context);
      }
      if (_loops) {
        _wake();
      } else {
        _stillGlow(live);
      }
    } else if (!before.isEmpty) {
      _ticked = false;
      if (before.live != null && _loops) {
        // Sealed: one quick lap, then the gap closes.
        _seal = 0;
        _sealBright = _light.bright;
        _wake();
      } else {
        _light.still = 0;
        _repaint.value++;
        _animateOpen(0);
      }
    }
  }

  void _wake() {
    if (!_ticker.isActive) {
      _last = Duration.zero;
      unawaited(_ticker.start());
    }
  }

  void _stillGlow(KitTurnLive? live) {
    _light.still = live == null
        ? 0
        : live.activity == KitTurnActivity.writing
        ? 0.3 + 0.7 * live.pace.clamp(0.0, 1.0)
        : 0.3;
    _light.tint = live?.activity == KitTurnActivity.writing ? 1 : 0;
    _repaint.value++;
  }

  void _step(Duration elapsed) {
    final dt = _last == Duration.zero
        ? 0.016
        : ((elapsed - _last).inMicroseconds / 1e6).clamp(0.0, 0.05);
    _last = elapsed;
    final l = _light;
    l.time += dt;
    // Hue changes cross-fade over the hue-fade time; speed changes ease
    // over the speed-ease time, so nothing jerks.
    final k =
        1 - math.exp(-dt / (KitMotion.edgeLightHueFade.inMilliseconds / 1500));
    final kSpeed =
        1 -
        math.exp(-dt / (KitMotion.edgeLightSpeedEase.inMilliseconds / 1500));
    final seal = _seal;
    if (seal != null) {
      // Done: the glow keeps its pace and fades into the border as the gap
      // closes.
      final st = seal + dt;
      _seal = st;
      final u = (st / _sealSeconds).clamp(0.0, 1.0);
      l.phase = (l.phase + l.speed * dt) % 1;
      l.bright = _sealBright * (1 - KitMotion.sealFade.transform(u));
      l.stall = 0;
      if (st >= _sealSeconds * 0.4 && !_closing) {
        _closing = true;
        _animateOpen(0);
      }
      if (st >= _sealSeconds) {
        _seal = null;
        l.bright = 0;
        l.tint = 0;
        _ticker.stop();
      }
      _repaint.value++;
      return;
    }
    final live = _data.live;
    var speedT = 0.0, brightT = 0.0, tintT = 0.0, stallT = 0.0, tail = 0.32;
    if (live != null) {
      final since = live.since;
      final waited = since == null
          ? Duration.zero
          : clock.now().difference(since);
      final quiet =
          (live.activity == KitTurnActivity.waitingForServer ||
              live.activity == KitTurnActivity.waitingForModel) &&
          waited >= KitTurnLive.slowAfter;
      final max = KitMotion.edgeLightMaxLapsPerSecond;
      final rest = KitMotion.edgeLightThinkingLapsPerSecond;
      switch (live.activity) {
        case KitTurnActivity.writing:
          final p =
              live.pace.clamp(0.0, 1.0) *
              math.exp(-(l.time - _paceStamp) / 1.5);
          speedT = rest * 0.6 + (max - rest * 0.6) * p;
          brightT = 0.6 + 0.4 * p;
          tintT = 1;
          tail = 0.24;
        case KitTurnActivity.working:
          speedT = rest * 1.2;
          brightT = 0.6;
        case KitTurnActivity.waitingForYou:
          brightT = 0.3;
        default:
          if (quiet) {
            stallT = 1;
          } else {
            speedT = rest;
            brightT = 0.7 + 0.25 * math.sin(l.time * 2 * math.pi / 4.5);
          }
      }
    }
    if (!_travels) speedT = 0;
    l.speed += (speedT - l.speed) * kSpeed;
    l.bright += (brightT - l.bright) * k;
    l.tint += (tintT - l.tint) * k;
    l.stall += (stallT - l.stall) * k;
    l.tail += (tail - l.tail) * k;
    l.phase = (l.phase + l.speed * dt) % 1;
    _repaint.value++;
    if (live == null && l.bright < 0.01 && l.stall < 0.01) _ticker.stop();
  }

  @override
  void dispose() {
    _ticker.dispose();
    _open.dispose();
    _flash.dispose();
    _repaint.dispose();
    super.dispose();
  }

  void _measured(Size size) {
    if (!mounted || size.width == _captionWidth) return;
    setState(() => _captionWidth = size.width);
  }

  static const _captionSize = 12.0;
  static const _bendDepth = 7.0;
  static const _bendMargin = 28.0;

  /// Where the caption's x-height centre sits, measured from the top of its
  /// text box: the caption is placed so that centre lies on the border line,
  /// not the box's middle.
  (double top, double height) _xCentre(BuildContext context) {
    final style = KitText.styleOf(
      context,
      KitTextRole.caption,
      tone: KitTextTone.secondary,
    ).copyWith(fontSize: _captionSize, fontWeight: FontWeight.w500);
    final painter = TextPainter(
      text: TextSpan(text: 'x', style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout();
    final baseline = painter.computeDistanceToActualBaseline(
      TextBaseline.alphabetic,
    );
    final xHeight = 0.52 * painter.textScaler.scale(_captionSize);
    final result = (baseline - xHeight / 2, painter.height);
    painter.dispose();
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final level = _level;
    final reduced = KitMotion.reduced(context);
    final roles = tokens.roles;
    final (xCentre, textHeight) = _xCentre(context);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: _captionWidth),
      duration: reduced ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      builder: (context, captionWidth, _) => AnimatedBuilder(
        animation: _open,
        builder: (context, _) {
          final open = _open.value;
          // The dip is one bell: as wide as the caption plus a margin each
          // side, as deep as 7 dp (half that in Calm). It deepens from
          // nothing along the same curve; nothing appears at its ends.
          final bendHalf = open > 0 ? captionWidth / 2 + _bendMargin : 0.0;
          final dip =
              _bendDepth * open * (level == KitMotionLevel.calm ? 0.5 : 1);
          // Once the gap has closed on an empty edge nothing stays behind:
          // a hidden caption would keep Stop in the tree and tappable.
          final idle = open == 0 && _data.isEmpty;
          return Stack(
            clipBehavior: Clip.none,
            children: [
              ClipPath(
                clipper: _BendClipper(bendHalf, dip),
                child: widget.glass,
              ),
              if (!idle || _light.bright > 0.01)
                Positioned.fill(
                  child: IgnorePointer(
                    child: ExcludeSemantics(
                      child: RepaintBoundary(
                        child: CustomPaint(
                          painter: _EdgePainter(
                            light: _light,
                            open: open,
                            flash: _flash,
                            radius: widget.radius,
                            bendHalf: bendHalf,
                            dip: dip,
                            comet: _travels,
                            still: !_travels && level != KitMotionLevel.off,
                            neutral: roles.text1,
                            accent: roles.accent,
                            wash: roles.text1,
                            rim: roles.hairline,
                            rimWidth: KitTokens.hairlineWidth(context),
                            repaint: Listenable.merge([_repaint, _flash]),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              if (!idle)
                Positioned(
                  top: 0.5 - 24,
                  left: 0,
                  right: 0,
                  height: 48,
                  child: Center(
                    child: Opacity(
                      opacity: ((open - 0.5) / 0.5).clamp(0.0, 1.0),
                      child: _SizeReporter(
                        onSize: _measured,
                        child: _EdgeCaption(
                          data: _shown,
                          xCentre: xCentre,
                          textHeight: textHeight,
                          size: _captionSize,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// The bend: the border eases down into one wide, shallow dip that cradles
/// the caption and eases back up. A raised cosine of a flattened argument,
/// so the bottom is broad and there is no corner anywhere: the slope is zero
/// at both ends and at the middle. [dx] is the distance from the centre.
double _dipDepth(double dx, double half, double depth) {
  if (half <= 0) return 0;
  final u = dx.abs() / half;
  if (u >= 1) return 0;
  return depth * 0.5 * (1 + math.cos(math.pi * math.pow(u, 2.4)));
}

/// Points along the dip from left to right on the line at [top] plus the
/// depth, for the clip, the outline and the rim.
List<Offset> _dipPoints(double centre, double half, double depth, double top) {
  const n = 48;
  return [
    for (var i = 0; i <= n; i++)
      Offset(
        centre - half + 2 * half * i / n,
        top + _dipDepth(-half + 2 * half * i / n, half, depth),
      ),
  ];
}

class _BendClipper extends CustomClipper<Path> {
  const _BendClipper(this.half, this.dip);

  final double half;
  final double dip;

  @override
  Path getClip(Size size) {
    final all = Path()
      ..addRect(Rect.fromLTRB(-80, -80, size.width + 80, size.height + 80));
    if (dip < 0.3 || half <= 0) return all;
    final points = _dipPoints(size.width / 2, half, dip, 0);
    final cut = Path()..moveTo(points.first.dx, -0.5);
    for (final point in points) {
      cut.lineTo(point.dx, point.dy);
    }
    cut
      ..lineTo(points.last.dx, -0.5)
      ..close();
    return Path.combine(PathOperation.difference, all, cut);
  }

  @override
  bool shouldReclip(_BendClipper old) => old.half != half || old.dip != dip;
}

/// Draws the light on the outline (see [_LivingEdge]). Everything is drawn
/// on the composer's own outline: the same rounded rectangle the glass
/// clips to, inset by half the rim so the light sits on the border and not
/// beside it. The path starts at the top centre, where the gap is, and runs
/// clockwise.
class _EdgePainter extends CustomPainter {
  _EdgePainter({
    required this.light,
    required this.open,
    required this.flash,
    required this.radius,
    required this.bendHalf,
    required this.dip,
    required this.comet,
    required this.still,
    required this.neutral,
    required this.accent,
    required this.wash,
    required this.rim,
    required this.rimWidth,
    required super.repaint,
  });

  final _EdgeLight light;
  final double open;
  final Animation<double> flash;
  final double radius;
  final double bendHalf;
  final double dip;
  final bool comet;
  final bool still;
  final Color neutral;
  final Color accent;
  final Color wash;

  /// The glass's own edge colour and width, for the parted border's ends.
  final Color rim;
  final double rimWidth;

  static const _inset = 0.5;

  /// Starts at the top centre (at the bottom of the bend when there is
  /// one) and runs clockwise.
  static Path _outline(Size size, double radius, double bendHalf, double dip) {
    final r = math.max(radius - _inset, 1.0);
    final l = _inset,
        t = _inset,
        rt = size.width - _inset,
        b = size.height - _inset;
    final c = size.width / 2;
    final path = Path();
    final bent = dip >= 0.5;
    if (bent) {
      final points = _dipPoints(c, bendHalf, dip, t);
      path.moveTo(c, t + dip);
      for (final point in points) {
        if (point.dx > c) path.lineTo(point.dx, point.dy);
      }
    } else {
      path.moveTo(c, t);
    }
    path
      ..lineTo(rt - r, t)
      ..arcTo(
        Rect.fromLTWH(rt - 2 * r, t, 2 * r, 2 * r),
        -math.pi / 2,
        math.pi / 2,
        false,
      )
      ..lineTo(rt, b - r)
      ..arcTo(
        Rect.fromLTWH(rt - 2 * r, b - 2 * r, 2 * r, 2 * r),
        0,
        math.pi / 2,
        false,
      )
      ..lineTo(l + r, b)
      ..arcTo(
        Rect.fromLTWH(l, b - 2 * r, 2 * r, 2 * r),
        math.pi / 2,
        math.pi / 2,
        false,
      )
      ..lineTo(l, t + r)
      ..arcTo(Rect.fromLTWH(l, t, 2 * r, 2 * r), math.pi, math.pi / 2, false);
    if (bent) {
      final points = _dipPoints(c, bendHalf, dip, t);
      for (final point in points) {
        if (point.dx < c) path.lineTo(point.dx, point.dy);
      }
    }
    return path..close();
  }

  Paint _line(Color color, double width, {double blur = 0}) => Paint()
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round
    ..strokeWidth = width
    ..color = color
    ..maskFilter = blur > 0 ? MaskFilter.blur(BlurStyle.normal, blur) : null;

  /// A soft hue with its head at [head] and a long tail behind it: wide,
  /// blurred, low-alpha pieces that overlap into one smooth glow, faint at
  /// both ends. No core, no head: only a fade.
  void _glow(
    Canvas canvas,
    PathMetric metric,
    double head,
    double tail,
    double direction,
    Color color,
    double strength,
  ) {
    const pieces = 20;
    final length = metric.length;
    for (var i = 0; i < pieces; i++) {
      final u0 = i / pieces, u1 = (i + 1) / pieces, mid = (u0 + u1) / 2;
      final fadeIn = KitMotion.tailFadeIn.transform(
        (mid / 0.14).clamp(0.0, 1.0),
      );
      final profile = math.pow(1 - mid, 1.5) * fadeIn;
      // Neighbouring blurs overlap, so each piece carries about half.
      final alpha = 0.2 * strength * profile;
      if (alpha < 0.004) continue;
      _extract(
        canvas,
        metric,
        length,
        head - direction * u0 * tail,
        head - direction * (u1 + 0.02) * tail,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 16
          ..strokeCap = StrokeCap.butt
          ..color = color.withValues(alpha: alpha.clamp(0.0, 1.0))
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
      );
    }
  }

  @override
  void paint(Canvas canvas, Size size) {
    final outline = _outline(size, radius, bendHalf, dip);
    final metric = outline.computeMetrics().first;
    final length = metric.length;
    final half = bendHalf;
    final color = Color.lerp(
      neutral,
      accent,
      light.tint.clamp(0.0, 1.0) * 0.5,
    )!;
    canvas.save();
    final f = flash.value;
    if (f > 0 && f < 1) {
      // A neutral wash along the outline, gone within a moment.
      canvas.drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 6
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5)
          ..color = wash.withValues(alpha: 0.2 * (1 - f)),
      );
    }
    if (comet && light.bright > 0.02) {
      _glow(
        canvas,
        metric,
        light.phase * length,
        light.tail * length,
        1,
        color,
        light.bright,
      );
    }
    if (still && light.still + light.bright > 0.02) {
      // Calm and reduced: no travel, a slow breathing hue on the outline.
      canvas.drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 8
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6)
          ..color = color.withValues(
            alpha: 0.10 * math.max(light.bright, light.still),
          ),
      );
    }
    canvas.restore();
    if (dip >= 0.3 && bendHalf > 0) {
      // The bend's own edge: the glass rim was cut away with the dip, so the
      // hairline follows the curve, a little fainter in the deepest part
      // where the caption reads over it.
      final c = size.width / 2;
      final points = _dipPoints(c, bendHalf, dip, _inset);
      final edge = Path()..moveTo(points.first.dx, points.first.dy);
      for (final point in points.skip(1)) {
        edge.lineTo(point.dx, point.dy);
      }
      final box = Rect.fromLTRB(c - bendHalf, 0, c + bendHalf, dip + 2);
      canvas.drawPath(
        edge,
        _line(rim, rimWidth)
          ..shader = LinearGradient(
            colors: [
              rim,
              rim.withValues(alpha: rim.a * 0.85),
              rim.withValues(alpha: rim.a * 0.35),
              rim.withValues(alpha: rim.a * 0.85),
              rim,
            ],
            stops: const [0, 0.25, 0.5, 0.75, 1],
          ).createShader(box),
      );
    }
    if (light.stall > 0.01 && open > 0.01) {
      // A stall, seen without reading: a slow breathing hue at each edge of
      // the gap, no travel.
      final beat = 0.5 + 0.5 * math.sin(light.time * 2 * math.pi / 3.2);
      final s = light.stall * (0.35 + 0.65 * beat);
      final edge = half;
      for (final dir in [1.0, -1.0]) {
        _glow(canvas, metric, dir * (edge + 6), 90, dir, neutral, s);
      }
    }
  }

  static void _extract(
    Canvas canvas,
    PathMetric metric,
    double length,
    double from,
    double to,
    Paint paint,
  ) {
    var a = math.min(from, to), b = math.max(from, to);
    if (b - a <= 0) return;
    if (a >= 0 && b <= length) {
      canvas.drawPath(metric.extractPath(a, b), paint);
    } else if (b <= 0 || a >= length) {
      final shift = (a / length).floor() * length;
      canvas.drawPath(metric.extractPath(a - shift, b - shift), paint);
    } else if (a < 0) {
      canvas.drawPath(metric.extractPath(0, b), paint);
      canvas.drawPath(metric.extractPath(length + a, length), paint);
    } else {
      canvas.drawPath(metric.extractPath(a, length), paint);
      canvas.drawPath(metric.extractPath(0, b - length), paint);
    }
  }

  @override
  bool shouldRepaint(_EdgePainter old) =>
      old.bendHalf != bendHalf ||
      old.dip != dip ||
      old.open != open ||
      old.radius != radius ||
      old.comet != comet ||
      old.still != still ||
      old.neutral != neutral ||
      old.rim != rim;
}

/// What is written in the gap: the phase words with the time and a small
/// red stop; or "Didn't send", Retry and Details. Neutral words (text2;
/// a failure's text1 with a neutral glyph); red only for Stop. The words are the live region: the
/// label is the phase, so it announces phase changes and not the seconds.
class _EdgeCaption extends StatelessWidget {
  const _EdgeCaption({
    required this.data,
    required this.xCentre,
    required this.textHeight,
    required this.size,
  });

  final _EdgeData data;

  /// Where the words' x-height centre lies below the top of their box.
  final double xCentre;
  final double textHeight;
  final double size;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final failed = data.failure;
    final live = data.live;
    if (failed != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: _failure(context, l10n, tokens, failed),
      );
    }
    if (live == null) return const SizedBox.shrink();
    final top = 24 - xCentre;
    final row = Padding(
      // 48 dp tall in all, with the words' x-height centre at its middle,
      // which is where the border line runs.
      padding: EdgeInsets.fromLTRB(
        _railEnd,
        top,
        _railEnd,
        _railHeight - top - textHeight,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _running(context, l10n, live),
          if (live.onStop != null) ...[
            const SizedBox(width: _stopGap),
            Padding(
              padding: EdgeInsets.only(top: xCentre - _stopGap),
              child: _square(tokens, _stopSide),
            ),
          ],
        ],
      ),
    );
    if (live.onStop == null) return row;
    return KitTappable(
      tappableKey: live.stopKey,
      label: l10n.kitTurnLiveStop,
      shape: KitShape.button,
      onTap: live.stopping ? null : live.onStop,
      disabledReason: live.stopping ? l10n.kitTurnLiveStopping : null,
      child: row,
    );
  }

  // The rail's hand-set measures, in logical pixels (48 dp tall in all).
  static const _railHeight = 48.0;
  static const _railEnd = 6.0;
  static const _stopGap = 4.0;
  static const _stopSide = 8.0;
  static const _stopRadius = 2.0;

  static Widget _square(KitTokens tokens, double side) => Container(
    width: side,
    height: side,
    decoration: BoxDecoration(
      color: tokens.roles.danger,
      borderRadius: BorderRadius.circular(_stopRadius),
    ),
  );

  Widget _text(String line, KitTextTone tone, {double? fontSize}) =>
      KitText.rich(
        TextSpan(
          text: line,
          style: TextStyle(
            fontWeight: FontWeight.w500,
            fontSize: fontSize ?? size,
          ),
        ),
        role: KitTextRole.caption,
        tone: tone,
        maxLines: 1,
        tabular: true,
      );

  Widget _words(String line, String spoken, String phase, KitTextTone tone) =>
      Semantics(
        liveRegion: true,
        label: spoken,
        child: ExcludeSemantics(
          child: AnimatedSwitcher(
            duration: KitMotion.quick,
            child: KeyedSubtree(key: ValueKey(phase), child: _text(line, tone)),
          ),
        ),
      );

  Widget _running(
    BuildContext context,
    AppLocalizations l10n,
    KitTurnLive live,
  ) {
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
        if (data.note != null) line = '$line · ${data.note}';
        return _words(
          line,
          data.note == null ? words : '$words. ${data.note}',
          words,
          KitTextTone.secondary,
        );
      },
    );
  }

  List<Widget> _failure(
    BuildContext context,
    AppLocalizations l10n,
    KitTokens tokens,
    KitComposerFailure failure,
  ) => [
    const SizedBox(width: _railEnd),
    // A failure is neutral (LOOK-5, B2): text1 words after the neutral
    // error glyph; only Stop on this edge is red.
    const KitIcon(
      AppIconography.error,
      size: KitIconSize.small,
      tone: KitTextTone.primary,
    ),
    const SizedBox(width: _stopGap),
    _words(failure.words, failure.words, failure.words, KitTextTone.primary),
    KitTappable(
      tappableKey: failure.retryKey,
      label: l10n.kitComposerRailRetry,
      shape: KitShape.button,
      onTap: failure.onRetry,
      child: SizedBox(
        height: tokens.minTarget - 8,
        child: Center(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.space2),
            child: _text('· ${l10n.kitComposerRailRetry}', KitTextTone.primary),
          ),
        ),
      ),
    ),
    if (failure.onDetails != null)
      KitTappable(
        label: l10n.kitDetails,
        shape: KitShape.button,
        onTap: failure.onDetails,
        child: SizedBox(
          width: tokens.minTarget - 16,
          height: tokens.minTarget - 8,
          child: const Center(
            child: KitIcon(AppIconography.info, size: KitIconSize.small),
          ),
        ),
      ),
    const SizedBox(width: _railEnd),
  ];
}

class _Circle extends StatelessWidget {
  const _Circle({
    super.key,
    required this.kind,
    required this.label,
    required this.onTap,
    this.tappableKey,
    this.shortcut,
    this.working = false,
    this.disabledReason,
  });

  final _CircleKind kind;
  final String label;
  final VoidCallback? onTap;
  final Key? tappableKey;
  final String? shortcut;
  final bool working;
  final String? disabledReason;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final stop = kind == _CircleKind.stop;
    final disabled = kind == _CircleKind.sendDisabled;
    // Stop is the destructive variant: the same red as a destructive kit
    // button, so ending a run reads as ending something.
    final fill = stop
        ? roles.dangerFill
        : disabled
        ? roles.surface3
        : roles.accent;
    final ink = stop
        ? roles.onDangerFill
        : disabled
        ? roles.text3
        : roles.onAccent;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final still = KitMotion.reduced(context);

    Widget glyph;
    if (working) {
      glyph = still
          ? Icon(
              AppIconography.statusDot,
              size: KitIconSize.small.logical,
              color: ink,
            )
          : SizedBox.square(
              dimension: KitIconSize.small.logical,
              child: CircularProgressIndicator(strokeWidth: 2, color: ink),
            );
    } else if (stop) {
      const side = KitTokens.composerStopSquare;
      glyph = SizedBox.square(
        key: const ValueKey('kit-composer-stop-square'),
        dimension: side,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: ink,
            borderRadius: BorderRadius.circular(side / 4),
          ),
        ),
      );
    } else {
      final icon = switch (kind) {
        _CircleKind.mic || _CircleKind.listen => AppIconography.mic,
        _CircleKind.voiceDone => AppIconography.check,
        _ => AppIconography.send,
      };
      glyph = Icon(icon, size: KitIconSize.small.logical, color: ink);
      // The send glyph points the way the words go (LAY-8); the mic does
      // not mirror.
      if (rtl && icon == AppIconography.send) {
        glyph = Transform.flip(flipX: true, child: glyph);
      }
    }

    return KitTappable(
      tappableKey: tappableKey,
      shape: KitShape.circle,
      label: label,
      tooltip: label,
      shortcut: shortcut,
      disabledReason: onTap == null ? (disabledReason ?? label) : null,
      onTap: onTap,
      child: SizedBox.square(
        dimension: tokens.minTarget,
        child: Center(
          child: SizedBox.square(
            dimension: KitTokens.composerActionSize,
            child: DecoratedBox(
              key: ValueKey('kit-composer-circle-${kind.name}'),
              decoration: BoxDecoration(color: fill, shape: BoxShape.circle),
              child: Center(child: glyph),
            ),
          ),
        ),
      ),
    );
  }
}

/// Voice mode inside the pill: exit, the level meter with the phase words
/// and elapsed time, the read-aloud toggle, and the trailing circle.
class _VoiceContent extends StatefulWidget {
  const _VoiceContent({required this.voice, required this.l10n});

  final KitComposerVoice voice;
  final AppLocalizations l10n;

  @override
  State<_VoiceContent> createState() => _VoiceContentState();
}

class _VoiceContentState extends State<_VoiceContent> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _syncTimer();
  }

  @override
  void didUpdateWidget(_VoiceContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncTimer();
  }

  void _syncTimer() {
    final ticking =
        widget.voice.phase == KitVoicePhase.listening &&
        widget.voice.listeningSince != null;
    if (ticking && _tick == null) {
      _tick = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() {});
      });
    } else if (!ticking) {
      _tick?.cancel();
      _tick = null;
    }
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  String _words(AppLocalizations l10n) => switch (widget.voice.phase) {
    KitVoicePhase.starting => l10n.kitVoiceStarting,
    KitVoicePhase.listening => l10n.kitVoiceListening,
    KitVoicePhase.transcribing => l10n.kitVoiceTranscribing,
    KitVoicePhase.waitingReply => l10n.kitVoiceWaitingReply,
    KitVoicePhase.speakingReply => l10n.kitVoiceSpeaking,
    KitVoicePhase.replyReady => l10n.kitVoiceReplyReady,
    KitVoicePhase.paused => l10n.kitVoicePaused,
    KitVoicePhase.micDenied => l10n.kitVoiceMicDenied,
    KitVoicePhase.failed => l10n.kitVoiceFailed,
  };

  String? _elapsed(AppLocalizations l10n) {
    final since = widget.voice.listeningSince;
    if (widget.voice.phase != KitVoicePhase.listening || since == null) {
      return null;
    }
    var seconds = clock.now().difference(since).inSeconds;
    if (seconds < 0) seconds = 0;
    return l10n.kitVoiceElapsed(
      '${seconds ~/ 60}',
      (seconds % 60).toString().padLeft(2, '0'),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = widget.l10n;
    final voice = widget.voice;
    final phase = voice.phase;
    final words = _words(l10n);
    final elapsed = _elapsed(l10n);
    final level = voice.level;
    final problem =
        phase == KitVoicePhase.micDenied || phase == KitVoicePhase.failed;
    final reason = problem || phase == KitVoicePhase.replyReady
        ? voice.reason
        : null;

    final Widget? trailing = switch (phase) {
      KitVoicePhase.listening => _Circle(
        key: const ValueKey('kit-voice-stop-listening'),
        kind: voice.conversation
            ? _CircleKind.voiceSend
            : _CircleKind.voiceDone,
        label: voice.conversation ? l10n.kitVoiceSend : l10n.kitVoiceDone,
        onTap: voice.onStopListening == null
            ? null
            : () {
                if (voice.conversation) KitHaptics.send(context);
                voice.onStopListening!();
              },
        disabledReason: words,
      ),
      KitVoicePhase.speakingReply => _Circle(
        key: const ValueKey('kit-voice-stop-reading'),
        kind: _CircleKind.stop,
        label: l10n.kitVoiceStopReading,
        onTap: voice.onStopSpeaking,
        disabledReason: words,
      ),
      KitVoicePhase.replyReady when voice.onListen != null => _Circle(
        key: const ValueKey('kit-voice-listen'),
        kind: _CircleKind.listen,
        label: l10n.kitVoiceListen,
        onTap: voice.onListen,
      ),
      _ => null,
    };

    final extras = <Widget>[
      if (phase == KitVoicePhase.replyReady && voice.onReadReply != null)
        KitButton.tertiary(
          label: l10n.kitVoiceReadReply,
          onPressed: voice.onReadReply,
        ),
      if (problem && voice.fix != null)
        KitButton.fromAction(
          voice.fix!,
          role: KitButtonRole.tertiary,
          expand: false,
        ),
      if (voice.onReadRepliesAloudChanged != null)
        KitChip.action(
          label: l10n.kitVoiceReadAloud,
          selected: voice.readRepliesAloud,
          onPressed: () =>
              voice.onReadRepliesAloudChanged!(!voice.readRepliesAloud),
        ),
    ];

    final wordsBlock = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // A Wrap, not a Row: at 320 dp with large text the meter and the
        // elapsed time move to their own line instead of squeezing the words.
        Wrap(
          spacing: tokens.space2,
          runSpacing: tokens.space1,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            if (phase == KitVoicePhase.listening && level != null)
              ExcludeSemantics(
                child: KitLevelMeter.listen(listenable: level, active: true),
              ),
            Semantics(
              liveRegion: true,
              container: true,
              label: words,
              excludeSemantics: true,
              // Body, not secondary: the phase words take the field's
              // place as the pill's main line, and 14 dp words beside the
              // meter fall under G5's measured contrast (record §1).
              child: KitText(
                words,
                role: KitTextRole.body,
                tone: KitTextTone.primary,
              ),
            ),
            if (elapsed != null)
              ExcludeSemantics(
                child: KitText(
                  elapsed,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                  tabular: true,
                ),
              ),
          ],
        ),
        if (reason != null && reason.isNotEmpty)
          KitText(
            reason,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
      ],
    );

    return Column(
      key: voice.voiceKey,
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            KitIconButton(
              icon: AppIconography.close,
              tooltip: l10n.kitVoiceLeave,
              onPressed: voice.onExit,
            ),
            SizedBox(width: tokens.space1),
            Expanded(child: wordsBlock),
            if (trailing != null) KitSwap(child: trailing),
          ],
        ),
        if (extras.isNotEmpty)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space2,
              end: tokens.space2,
              bottom: tokens.space1,
            ),
            child: Wrap(
              spacing: tokens.space2,
              runSpacing: tokens.space1,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: extras,
            ),
          ),
      ],
    );
  }
}

/// [KitComposer.layer]: the body fills the space and scrolls under the
/// glass; the composer floats at the bottom, lifted by the keyboard, and
/// its measured height is published to the body through
/// [KitBottomInset.add].
class _KitComposerLayer extends StatefulWidget {
  const _KitComposerLayer({
    super.key,
    required this.body,
    required this.composer,
    this.above,
    this.aboveMinHeight = 0,
  });

  final Widget body;
  final Widget composer;
  final Widget? above;
  final double aboveMinHeight;

  @override
  State<_KitComposerLayer> createState() => _KitComposerLayerState();
}

class _KitComposerLayerState extends State<_KitComposerLayer> {
  double _height = 0;

  void _onSize(Size size) {
    if (!mounted || size.height == _height) return;
    setState(() => _height = size.height);
  }

  /// [above] on the ground over [composer], within [maxHeight] (null:
  /// unbounded): the composer keeps its height and [above] gets the rest.
  Widget _aboveAndComposer(
    KitTokens tokens,
    double? maxHeight,
    Widget above,
    Widget composer,
  ) {
    final band = ColoredBox(
      color: tokens.roles.ground,
      child: Center(
        heightFactor: 1,
        child: ConstrainedBox(
          // The composer's width plus the gutters the parts above draw
          // themselves.
          constraints: BoxConstraints(
            maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter,
          ),
          child: above,
        ),
      ),
    );
    final column = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        maxHeight == null ? band : Flexible(child: band),
        if (maxHeight == null)
          composer
        else
          // Never taller than the room: on a small window at a large text
          // size (320 dp, 2.5x) the composer scrolls within it, its field
          // and Send in view, instead of running off the screen.
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: (maxHeight - widget.aboveMinHeight).clamp(
                0.0,
                maxHeight,
              ),
            ),
            child: SingleChildScrollView(
              key: const ValueKey('kit-composer-room'),
              reverse: true,
              primary: false,
              child: composer,
            ),
          ),
      ],
    );
    if (maxHeight == null) return column;
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: column,
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inherited = KitBottomInset.of(context).bottom;
    final composer = Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.space2,
      ),
      child: Center(
        heightFactor: 1,
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: KitLayout.paneDetailMaxWidth,
          ),
          child: widget.composer,
        ),
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) => Stack(
        children: [
          PositionedDirectional(
            start: 0,
            end: 0,
            top: 0,
            bottom: 0,
            child: KitBottomInset.add(extraBottom: _height, child: widget.body),
          ),
          // The composer's band is solid ground from its top edge to the
          // window's bottom edge, across the full width: the transcript
          // scrolls out of sight at the composer and never shows beside
          // or beneath it (owner report, build 2055). The measured height
          // excludes [inherited], which the body already clears.
          PositionedDirectional(
            start: 0,
            end: 0,
            bottom: 0,
            child: ColoredBox(
              key: const ValueKey('kit-composer-band'),
              color: tokens.roles.ground,
              child: Padding(
                padding: EdgeInsetsDirectional.only(bottom: inherited),
                child: _SizeReporter(
                  onSize: _onSize,
                  // One structure with or without [above], so the composer's
                  // field keeps its state and focus when a part comes or goes.
                  child: _aboveAndComposer(
                    tokens,
                    constraints.hasBoundedHeight
                        ? (constraints.maxHeight - inherited).clamp(
                            0.0,
                            double.infinity,
                          )
                        : null,
                    widget.above ?? const SizedBox.shrink(),
                    _KitComposerRoom(
                      height: constraints.hasBoundedHeight
                          ? constraints.maxHeight
                          : null,
                      child: composer,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The height [KitComposer.layer] lays its composer out in: the field's
/// cap is a share of it (KitLayout.composerMaxShare).
class _KitComposerRoom extends InheritedWidget {
  const _KitComposerRoom({required this.height, required super.child});

  final double? height;

  static double? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitComposerRoom>()?.height;

  @override
  bool updateShouldNotify(_KitComposerRoom oldWidget) =>
      height != oldWidget.height;
}

/// Reports its child's laid-out size after each layout that changes it.
class _SizeReporter extends SingleChildRenderObjectWidget {
  const _SizeReporter({required this.onSize, super.child});

  final ValueChanged<Size> onSize;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderSizeReporter(onSize);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderSizeReporter renderObject,
  ) {
    renderObject.onSize = onSize;
  }
}

class _RenderSizeReporter extends RenderProxyBox {
  _RenderSizeReporter(this.onSize);

  ValueChanged<Size> onSize;
  Size? _last;

  @override
  void performLayout() {
    super.performLayout();
    if (size != _last) {
      _last = size;
      final reported = size;
      WidgetsBinding.instance.addPostFrameCallback((_) => onSize(reported));
    }
  }
}
