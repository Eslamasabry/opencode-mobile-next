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

part 'kit_composer_types.dart';
part 'kit_composer_edge.dart';
part 'kit_composer_glow.dart';
part 'kit_composer_caption.dart';
part 'kit_composer_layer.dart';

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
    this.activityGlow,
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

  /// The soft ring sweep around the whole box while [rail] runs, in
  /// addition to the living edge. Null follows Settings › Appearance ›
  /// Effects ([KitEffects.activityGlow]).
  final bool? activityGlow;

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
              activityGlow: widget.activityGlow,
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
