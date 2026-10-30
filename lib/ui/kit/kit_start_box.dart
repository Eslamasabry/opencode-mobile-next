import 'package:flutter/material.dart';

import 'kit_field.dart' show kitFieldHalo;
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The field a new conversation starts with: the question is the
/// placeholder ("What would you like to build?"), the field is [minLines]
/// tall and grows as the person types, and the box glows softly in the
/// accent, as the composer does, so it is plainly where to begin. The box
/// is the style's field; the glow is a still, wide halo behind it, never
/// moving, in a style whose glow has one (hook H8: Brutal and Paper draw
/// the box alone). It brightens over [KitMotion.quick] when the field has
/// focus (instantly under reduced motion).
///
/// It is a plain text field: the draft stays in [controller], the keyboard
/// is the multiline one, and [question] names the field for a screen reader.
/// Starting lives outside it: the screen's one main button.
///
/// States: none — empty (the question shows), typed, focused and read-only
/// ([enabled] false, while the conversation is starting) all draw the same
/// box.
class KitStartBox extends StatefulWidget {
  const KitStartBox({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.question,
    this.enabled = true,
    this.autofocus = false,
    this.fieldKey,
    this.minLines = 2,
  });

  final TextEditingController controller;
  final FocusNode focusNode;

  /// The question shown while the field is empty; also its screen-reader name.
  final String question;

  /// False while the start is in flight: the words stay, nothing can be typed.
  final bool enabled;
  final bool autofocus;
  final Key? fieldKey;

  /// How many lines the field is tall before it grows with the words: two
  /// beside other choices, more when it is the screen's only one.
  final int minLines;

  /// The box never shrinks below this, so the question has room to breathe.
  static const double minHeight = 80;

  @override
  State<KitStartBox> createState() => _KitStartBoxState();
}

class _KitStartBoxState extends State<KitStartBox> {
  @override
  void initState() {
    super.initState();
    widget.focusNode.addListener(_changed);
  }

  @override
  void didUpdateWidget(KitStartBox old) {
    super.didUpdateWidget(old);
    if (old.focusNode != widget.focusNode) {
      old.focusNode.removeListener(_changed);
      widget.focusNode.addListener(_changed);
    }
  }

  @override
  void dispose() {
    widget.focusNode.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final focused = widget.focusNode.hasFocus;
    final reduced = KitMotion.reduced(context);
    final style = KitText.styleOf(context, KitTextRole.body);
    final accent = roles.accent;
    final surfaces = KitSurfaceLook.styleOf(context);
    final radius = BorderRadius.circular(tokens.cardRadius);
    // A field: Solid paints the hairline at rest and the accent at the
    // focus ring's width while it has focus; every other style its own field, its focused
    // edge while it has focus.
    final solid = surfaces == KitSurfaceStyle.solid;
    final face = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(
        color: roles.surface1,
        borderRadius: radius,
        border: Border.all(
          color: focused ? accent : roles.hairline,
          width: focused
              ? KitTokens.focusRingWidth(context)
              : KitTokens.hairlineWidth(context),
        ),
      ),
      role: KitSurfaceRole.field,
      borderRadius: radius,
      selected: focused,
    );
    // A soft halo behind the box, as the composer's while a reply runs:
    // wide and faint, with a little more light close to the edge. The
    // style says whether a field keeps it (kitFieldHalo, as the ask field):
    // print and ink draw the box alone. Solid keeps the glow for the
    // composer while an agent works, the moment it means something: the
    // start box never glows.
    final halo = solid
        ? const <BoxShadow>[]
        : [
            BoxShadow(
              color: accent.withValues(alpha: focused ? .3 : .22),
              blurRadius: focused ? 32 : 28,
            ),
            BoxShadow(
              color: accent.withValues(alpha: focused ? .16 : .1),
              blurRadius: 8,
            ),
          ];
    return AnimatedContainer(
      duration: reduced ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      constraints: const BoxConstraints(minHeight: KitStartBox.minHeight),
      // A styled face may be clear, so its halo falls outside it only and
      // never tints the words' ground; Solid's opaque face paints over its
      // own.
      decoration: kitFieldHalo(context, look: face, radius: radius, halo: halo),
      child: kitSurfaceBox(
        style: surfaces,
        decoration: face,
        child: Padding(
          // A drawn border takes its room inside the box, as a container's
          // does; a style's edge sits over the face.
          padding: EdgeInsets.symmetric(
            horizontal: tokens.space5,
            vertical: tokens.space4,
          ).add(face.padding),
          child: Semantics(
            textField: true,
            label: widget.question,
            child: TextField(
              key: widget.fieldKey,
              controller: widget.controller,
              focusNode: widget.focusNode,
              enabled: widget.enabled,
              autofocus: widget.autofocus,
              minLines: widget.minLines,
              maxLines: widget.minLines + 4,
              keyboardType: TextInputType.multiline,
              textInputAction: TextInputAction.newline,
              textCapitalization: TextCapitalization.sentences,
              style: style.copyWith(
                color: KitSurfaceLook.wordsOn(
                  context,
                  fallback: roles.text1,
                  role: KitSurfaceRole.field,
                  level: KitSurfaceLevel.surface1,
                  selected: focused,
                ),
              ),
              cursorColor: accent,
              decoration: InputDecoration(
                isCollapsed: true,
                contentPadding: EdgeInsets.zero,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                disabledBorder: InputBorder.none,
                filled: false,
                hint: ExcludeSemantics(
                  child: Text(
                    widget.question,
                    style: style.copyWith(color: roles.text2),
                  ),
                ),
                hintFadeDuration: reduced ? Duration.zero : KitMotion.quick,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
