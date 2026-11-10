import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One tab of [KitSessionTabs]: a session's name, whether it is the one
/// shown, and the session's own acts (rename, stop) on long-press.
class KitSessionTab {
  const KitSessionTab({
    required this.label,
    required this.selected,
    required this.onTap,
    this.ended = false,
    this.menu = const [],
    this.menuLabel,
    this.tabKey,
  });

  /// The session's name ("Shell 2", "build-server"); wrap a name the person
  /// or a server chose with `KitBidi.auto`.
  final String label;
  final bool selected;
  final VoidCallback onTap;

  /// The session's program has ended: the name is drawn quieter and a
  /// screen reader hears [endedLabel].
  final bool ended;
  final List<KitMenuItem> menu;
  final String? menuLabel;
  final Key? tabKey;
}

/// The open sessions of one screen (a terminal's shells) as a line of pills
/// under the top bar: the one shown in solid mint, the others outlined. A
/// host shows it only when there are two or more sessions; with one the
/// line would only repeat the title.
///
/// Each pill is a 48 dp target around a smaller pill; long-press (or right
/// click) opens that session's acts. The line scrolls sideways when the
/// pills do not fit, from the gutter. The selected pill fills over
/// `KitMotion.quick` (none under reduced motion).
///
/// States: none — selected, ended and pressed are looks of each pill.
class KitSessionTabs extends StatelessWidget {
  const KitSessionTabs({
    super.key,
    required this.tabs,
    required this.semanticsLabel,
    this.endedLabel,
  });

  final List<KitSessionTab> tabs;

  /// The line's name for a screen reader: "Terminal sessions".
  final String semanticsLabel;

  /// Added to an ended tab's name for a screen reader: "ended".
  final String? endedLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    // The strip's bed: nothing under Solid, a well under Soft (the chosen
    // pill sits in it), an ink box under Brutal
    // and Paper. Where it paints, it bounds the resting pills, so they
    // drop their own outline.
    final bed = KitSurfaceLook.decorate(
      context,
      solid: const BoxDecoration(),
      role: KitSurfaceRole.track,
      borderRadius: KitSurfaceLook.stadium,
    );
    final bounded = _paints(bed);
    return Semantics(
      container: true,
      label: semanticsLabel,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsetsDirectional.symmetric(
          horizontal: tokens.gutter - tokens.space1,
        ),
        // As tall as one pill's target, whatever room the host offers.
        child: IntrinsicHeight(
          child: kitSurfaceBox(
            style: KitSurfaceLook.styleOf(context),
            decoration: bed,
            child: Row(
              children: [
                for (final tab in tabs)
                  _Pill(tab: tab, endedLabel: endedLabel, bounded: bounded),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Whether [look] shows anything: a fill, an edge or a shadow.
bool _paints(Decoration look) {
  final s = KitSurfaceDecoration.from(look);
  if (s == null) return true;
  return s.fill.a > 0 ||
      (s.edgeWidth > 0 && (s.edgeStart.a > 0 || s.edgeEnd.a > 0)) ||
      s.shadows.isNotEmpty ||
      s.hardShadow.a > 0 ||
      (s.distance > 0 && (s.lightShadow.a > 0 || s.darkShadow.a > 0));
}

class _Pill extends StatelessWidget {
  const _Pill({
    required this.tab,
    required this.endedLabel,
    required this.bounded,
  });

  final KitSessionTab tab;
  final String? endedLabel;

  /// Whether the strip's bed bounds the pills (it paints something), so a
  /// resting pill needs no outline of its own.
  final bool bounded;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final selected = tab.selected;
    final ended = tab.ended && endedLabel != null;
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.space1),
      child: KitTappable(
        tappableKey: tab.tabKey,
        onTap: tab.onTap,
        selected: selected,
        label: ended ? '${tab.label}, $endedLabel' : tab.label,
        menu: tab.menu,
        shape: KitShape.pill,
        surface: KitSurfaceLevel.ground,
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Center(
            widthFactor: 1,
            heightFactor: 1,
            child: AnimatedContainer(
              duration: reduced ? Duration.zero : KitMotion.quick,
              curve: KitMotion.enter,
              // The pill hugs its words; the 48 dp target is around it.
              padding: EdgeInsets.symmetric(
                horizontal: tokens.space4,
                vertical: tokens.space2,
              ),
              decoration: _decoration(context, roles, selected),
              child: ExcludeSemantics(child: _words(context, roles, selected)),
            ),
          ),
        ),
      ),
    );
  }

  /// The pill's name: on the chosen pill in the words colour the style
  /// gives a chosen control (hook H3; Paper inverts it), else its tone.
  Widget _words(BuildContext context, ThemeRoles roles, bool selected) {
    final tone = selected
        ? KitTextTone.onAccent
        : tab.ended
        ? KitTextTone.tertiary
        : KitTextTone.secondary;
    if (!selected) {
      return KitText(
        tab.label,
        role: KitTextRole.label,
        tone: tone,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
    }
    final style = KitText.styleOf(context, KitTextRole.label, tone: tone);
    return Text(
      tab.label,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      // Words on the pill's own fill never take the bed's plate (hook H10).
      style: style.copyWith(
        shadows: const [],
        color: KitSurfaceLook.wordsOn(
          context,
          fallback: style.color ?? roles.onAccent,
          color: roles.accent,
          selected: true,
        ),
      ),
    );
  }

  /// The chosen pill: a control in mint, chosen (the style decides how a
  /// chosen control looks). A resting pill: outlined in a hairline, or
  /// clear when the strip's bed already bounds it (the hairline's room is
  /// kept so nothing moves).
  Decoration _decoration(
    BuildContext context,
    ThemeRoles roles,
    bool selected,
  ) {
    final solid = ShapeDecoration(
      color: selected ? roles.accent : roles.ground.withValues(alpha: 0),
      shape: StadiumBorder(
        side: selected
            ? BorderSide.none
            : BorderSide(
                color: bounded ? Colors.transparent : roles.hairline,
                width: KitTokens.hairlineWidth(context),
              ),
      ),
    );
    if (!selected) return solid;
    return KitSurfaceLook.decorate(
      context,
      solid: solid,
      role: KitSurfaceRole.control,
      color: roles.accent,
      selected: true,
    );
  }
}
