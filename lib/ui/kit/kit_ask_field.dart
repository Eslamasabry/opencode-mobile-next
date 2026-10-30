import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_field.dart' show kitFieldFrame, kitFieldHalo, kitFieldRing;
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';
import 'style/kit_style.dart';

/// What a [KitAskField] is doing.
enum KitAskFieldPhase {
  /// Waiting for words: the mic while empty (where there is voice), Send
  /// once there is text.
  ready,

  /// Dictation is recording; the round button finishes it.
  listening,

  /// The recording, or the request, is on its way: the field and the button
  /// hold still.
  working,
}

/// The tone of the one line above a [KitAskField]. Never red, never amber:
/// nobody has to act on it.
enum KitAskFieldNoteTone {
  /// It went where it was meant to (a check).
  done,

  /// It did not go; the words are still in the field (an info glyph).
  notDone,
}

/// One glowing field that asks for something and sends it somewhere else:
/// the in-app preview's "Ask for a change…" (vision 2026-10-20, Preview A).
///
/// A solid pill on [ThemeRoles.surface1] with a still accent hairline and a
/// soft accent halo, as [KitStartBox] and the composer's glow: it is plainly
/// the one place to start. Inside: the field, and one round accent button at
/// the end, the screen's one main action. While the field is empty it is the
/// mic ([onVoice]; without voice it is Send, off until there are words);
/// once there is text it is Send; while [KitAskFieldPhase.listening] it
/// finishes the recording; while working it shows progress.
///
/// [note] is one quiet line above the pill ("Sent to the conversation"),
/// which fades in and out over [KitMotion.quick] (at once under reduced
/// motion).
///
/// It is not the chat composer: no attachments, no tools, no model. The
/// words stay in [controller] until the host clears them after a send it
/// accepted.
///
/// Empty shows the hint and the mic; disabled is read-only ([enabled]
/// false); working is listening or sending; answered is the "done" note.
///
/// States: empty, disabled, working, answered.
class KitAskField extends StatefulWidget {
  const KitAskField({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.hint,
    required this.sendLabel,
    required this.onSend,
    this.phase = KitAskFieldPhase.ready,
    this.onVoice,
    this.voiceLabel,
    this.listeningHint,
    this.stopListeningLabel,
    this.note,
    this.noteTone = KitAskFieldNoteTone.done,
    this.enabled = true,
    this.prominent = true,
    this.fieldKey,
    this.sendKey,
    this.voiceKey,
  });

  final TextEditingController controller;
  final FocusNode focusNode;

  /// The placeholder and the field's accessible name: "Ask for a change…".
  final String hint;

  /// Send's name, naming where it goes: "Send to the conversation".
  final String sendLabel;
  final VoidCallback onSend;

  final KitAskFieldPhase phase;

  /// The mic while the field is empty; null: no voice here.
  final VoidCallback? onVoice;

  /// The mic's name ("Speak your change").
  final String? voiceLabel;

  /// The placeholder while listening ("Listening…").
  final String? listeningHint;

  /// The round button's name while listening ("Done speaking").
  final String? stopListeningLabel;

  /// One quiet line above the pill; null shows none.
  final String? note;
  final KitAskFieldNoteTone noteTone;

  /// False: nothing can be typed or sent.
  final bool enabled;

  /// False while something else on the screen is its one main action (the
  /// page's Try again): the round button wears a quiet surface instead of
  /// the solid accent, and the ring dims.
  final bool prominent;

  final Key? fieldKey, sendKey, voiceKey;

  @override
  State<KitAskField> createState() => _KitAskFieldState();
}

class _KitAskFieldState extends State<KitAskField> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    widget.focusNode.addListener(_changed);
  }

  @override
  void didUpdateWidget(KitAskField old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
    }
    if (old.focusNode != widget.focusNode) {
      old.focusNode.removeListener(_changed);
      widget.focusNode.addListener(_changed);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    widget.focusNode.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  bool get _hasText => widget.controller.text.trim().isNotEmpty;

  void _send() {
    if (!widget.enabled || widget.phase != KitAskFieldPhase.ready) return;
    if (!_hasText) return;
    widget.onSend();
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final duration = reduced ? Duration.zero : KitMotion.quick;
    final focused = widget.focusNode.hasFocus;
    final listening = widget.phase == KitAskFieldPhase.listening;
    final working = widget.phase == KitAskFieldPhase.working;
    final style = KitText.styleOf(context, KitTextRole.body);
    final accent = roles.accent;
    final lit = focused || listening;
    final glow = widget.prominent || lit ? 1.0 : .5;
    final hint = listening
        ? (widget.listeningHint ?? widget.hint)
        : widget.hint;
    // Large text keeps the field to two lines, so what it is about stays
    // in view above it.
    final large = MediaQuery.textScalerOf(context).scale(1) >= 1.3;

    // A soft wide halo with a little more light close to the edge, as the
    // start box and the composer's glow: still, never a loop.
    List<BoxShadow> haloOf({required double wide, required double near}) => [
      BoxShadow(
        color: accent.withValues(alpha: (lit ? .4 : wide) * glow),
        blurRadius: lit ? 30 : 26,
      ),
      BoxShadow(
        color: accent.withValues(alpha: (lit ? .3 : near) * glow),
        blurRadius: 6,
      ),
    ];
    final halo = haloOf(wide: .32, near: .22);
    // A style that asks for the field halo itself (KitStyleTokens.fieldHalo:
    // Soft's well on a ground-matched page) keeps it plainly lit at rest;
    // the default rest light all but vanishes into its shadows.
    final styleHalo =
        kitStyleSpec(KitSurfaceLook.styleOf(context)).tokens.fieldHalo == true
        ? haloOf(wide: .5, near: .36)
        : halo;
    final radius = BorderRadius.circular(tokens.composerRadius);
    final own = kitFieldFrame(
      context,
      fill: roles.surface1,
      edge: accent.withValues(alpha: (lit ? 1 : .85) * glow),
      thick: true,
      radius: radius,
      shadows: halo,
    );
    // Solid's glowing pill is the field's own look; every other style makes
    // it that style's field (a tint and rim, a well, an ink box), lit while
    // it has focus or listens, and rings it outside while focused.
    final look = KitSurfaceLook.decorate(
      context,
      solid: own,
      role: KitSurfaceRole.field,
      selected: lit,
    );
    // The halo around a style's own field, as the start box has it
    // (kitFieldHalo); Solid's own frame carries its halo itself.
    final Decoration around = identical(look, own)
        ? const KitSurfaceDecoration(fill: Colors.transparent)
        : kitFieldHalo(
            context,
            look: look,
            radius: KitSurfaceDecoration.radiusOf(look) ?? radius,
            halo: styleHalo,
          );
    final pill = kitFieldRing(
      context,
      own: own,
      look: look,
      color: focused ? accent : null,
      child: DecoratedBox(
        decoration: around,
        child: AnimatedContainer(
          duration: duration,
          curve: KitMotion.enter,
          constraints: BoxConstraints(
            minHeight: tokens.minTarget + tokens.space3,
          ),
          decoration: look,
          child: Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space5,
              end: tokens.space1,
              top: tokens.space1,
              bottom: tokens.space1,
            ),
            child: Row(
              // The round button stays at the end of the last line as the
              // words grow.
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  // The field is a full touch target tall, its words centred.
                  child: ConstrainedBox(
                    constraints: BoxConstraints(minHeight: tokens.minTarget),
                    child: Semantics(
                      textField: true,
                      label: widget.hint,
                      child: TextField(
                        key: widget.fieldKey,
                        controller: widget.controller,
                        focusNode: widget.focusNode,
                        enabled: widget.enabled && !working && !listening,
                        minLines: 1,
                        maxLines: large ? 2 : 4,
                        keyboardType: TextInputType.multiline,
                        textAlignVertical: TextAlignVertical.center,
                        textInputAction: TextInputAction.send,
                        textCapitalization: TextCapitalization.sentences,
                        onSubmitted: (_) => _send(),
                        style: style.copyWith(color: roles.text1),
                        cursorColor: accent,
                        decoration: InputDecoration(
                          isCollapsed: true,
                          contentPadding: EdgeInsets.symmetric(
                            vertical: tokens.space2,
                          ),
                          border: InputBorder.none,
                          enabledBorder: InputBorder.none,
                          focusedBorder: InputBorder.none,
                          disabledBorder: InputBorder.none,
                          filled: false,
                          hint: ExcludeSemantics(
                            child: Text(
                              hint,
                              maxLines: large ? 2 : 1,
                              overflow: TextOverflow.ellipsis,
                              style: style.copyWith(color: roles.text2),
                            ),
                          ),
                          hintFadeDuration: duration,
                        ),
                      ),
                    ),
                  ),
                ),
                SizedBox(width: tokens.space2),
                _button(context),
              ],
            ),
          ),
        ),
      ),
    );

    final note = widget.note;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitReveal(
          child: note == null
              ? null
              : AnimatedSwitcher(
                  duration: duration,
                  child: Padding(
                    key: ValueKey(note),
                    padding: EdgeInsetsDirectional.only(
                      start: tokens.space5,
                      end: tokens.space5,
                      bottom: tokens.space3,
                    ),
                    child: Semantics(
                      liveRegion: true,
                      child: Row(
                        children: [
                          Icon(
                            widget.noteTone == KitAskFieldNoteTone.done
                                ? AppIconography.check
                                : AppIconography.info,
                            size: KitIconSize.small.logical,
                            color: widget.noteTone == KitAskFieldNoteTone.done
                                ? accent
                                : roles.text2,
                          ),
                          SizedBox(width: tokens.space2),
                          Expanded(
                            child: Text(
                              note,
                              style: KitText.styleOf(
                                context,
                                KitTextRole.caption,
                              ).copyWith(color: roles.text2),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
        ),
        pill,
      ],
    );
  }

  Widget _button(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final still = KitMotion.reduced(context);
    final listening = widget.phase == KitAskFieldPhase.listening;
    final working = widget.phase == KitAskFieldPhase.working;
    final voice = widget.onVoice;
    final mic = !working && !listening && !_hasText && voice != null;
    final live =
        widget.enabled &&
        !working &&
        (listening || mic || (_hasText && !listening));
    final solid = (live || working) && widget.prominent;
    final fill = solid ? roles.accent : roles.surface3;
    final ink = solid
        // Hook H3: a style may say the words on its accent.
        ? KitSurfaceLook.wordsOn(
            context,
            fallback: roles.onAccent,
            color: roles.accent,
          )
        : live
        ? roles.text1
        : roles.text3;

    final String label;
    final VoidCallback? onTap;
    final IconData icon;
    final Key? key;
    if (listening) {
      label = widget.stopListeningLabel ?? widget.sendLabel;
      onTap = voice;
      icon = AppIconography.check;
      key = widget.voiceKey;
    } else if (mic) {
      label = widget.voiceLabel ?? widget.sendLabel;
      onTap = widget.enabled ? voice : null;
      icon = AppIconography.mic;
      key = widget.voiceKey;
    } else {
      label = widget.sendLabel;
      onTap = live ? _send : null;
      icon = AppIconography.send;
      key = widget.sendKey;
    }

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
    } else {
      glyph = Icon(icon, size: KitIconSize.medium.logical, color: ink);
      // Send points the way the words go; the mic and the check do not.
      if (rtl && icon == AppIconography.send) {
        glyph = Transform.flip(flipX: true, child: glyph);
      }
    }

    final dimension = tokens.minTarget;
    // A round control from the style resolver: the main action keeps the
    // accent as its own face; the quiet disc is the style's.
    final disc = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(color: fill, shape: BoxShape.circle),
      role: KitSurfaceRole.control,
      level: KitSurfaceLevel.surface3,
      circle: true,
      color: solid ? fill : null,
    );
    // Hook H9 for a control nested in the field: a style that flattens
    // nested surfaces casts nothing from inside the field, so no shadow
    // crosses the field's edge (Brutal's mic).
    final flat =
        disc is KitSurfaceDecoration &&
        kitStyleSpec(KitSurfaceLook.styleOf(context)).tokens.flattensNested;
    return KitTappable(
      tappableKey: key,
      shape: KitShape.circle,
      label: label,
      tooltip: label,
      disabledReason: onTap == null ? widget.hint : null,
      onTap: onTap,
      child: SizedBox.square(
        dimension: dimension,
        child: AnimatedContainer(
          duration: still ? Duration.zero : KitMotion.quick,
          curve: KitMotion.enter,
          decoration: flat ? disc.withoutCast : disc,
          child: Center(
            child: AnimatedSwitcher(
              duration: still ? Duration.zero : KitMotion.quick,
              child: KeyedSubtree(
                key: ValueKey(
                  'kit-ask-field-${working ? 'working' : icon.codePoint}',
                ),
                child: glyph,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
