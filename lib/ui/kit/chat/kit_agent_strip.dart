// KitAgentStrip — docs/ux-system/kit-api/KitAgentStrip.md (frozen API,
// wave 0). The row of who is working on a team task, and the agents'
// identity tint moved from widgets/agent_color.dart onto the theme's roles.
import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../kit_bidi.dart';
import '../kit_chip.dart';
import '../kit_layout.dart';
import '../kit_max_width.dart';
import '../kit_motion.dart';
import '../kit_needs_you.dart';
import '../kit_surface_look.dart';
import '../kit_tappable.dart';
import '../kit_task_mark.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_motion_parts.dart';

/// One agent on a task: the lead, a worker or a reviewer.
@immutable
class KitAgent {
  const KitAgent({
    required this.id,
    required this.name,
    required this.state,
    this.role,
    this.paused = false,
    this.onOpen,
    this.key,
  }) : assert(
         !paused ||
             state == KitTaskState.waiting ||
             state == KitTaskState.working,
         'KitAgent: paused only applies to waiting or working (STATE-11)',
       );

  final Object id;

  /// The short name ("furiosa"), or the role word when it has none.
  final String name;

  /// From the agent's session (the team decision), never the agents list.
  final KitTaskState state;

  /// "Lead", "Worker", "Reviewer" (team vocabulary words, COPY-13).
  final String? role;

  /// A heat pause or a force stop the app can resume (waiting or working
  /// only).
  final bool paused;

  /// Opens its conversation in watching mode; null: not tappable (the lead).
  final VoidCallback? onOpen;

  /// The chip's key (today's `ValueKey('team-conversation-family-<id>')`).
  final Key? key;
}

/// Who is working on a team task, in order: the lead, then workers and
/// reviewers as they joined. A team conversation arranges it under its
/// header (team-conversation-2026-09-26); each agent opens its own
/// conversation. Chat parts follow the transcript turn model (STATE-16,
/// KIT-41): this strip sits outside the turns and never inside one.
///
/// States: mixed, all working, needs you, paused, all done, lead only,
/// overflowing, empty (KIT-12).
class KitAgentStrip extends StatefulWidget {
  const KitAgentStrip({
    super.key,
    required this.agents,
    this.semanticsLabel,
    this.stripKey,
  });

  /// Empty renders nothing.
  final List<KitAgent> agents;

  /// The group's name; null: "Agents on this task".
  final String? semanticsLabel;

  /// The strip's own key (today's `ValueKey('team-conversation-family')`).
  final Key? stripKey;

  @override
  State<KitAgentStrip> createState() => _KitAgentStripState();
}

class _KitAgentStripState extends State<KitAgentStrip> {
  // The agents present on the first build: they are simply there. An agent
  // that joins later fades in at the end (Motion).
  late final Set<Object> _initial = {for (final a in widget.agents) a.id};

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (widget.agents.isEmpty) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final wide = KitLayout.windowOf(context).isWide;
    // A chip never grows past the strip's own width, so a long name
    // truncates instead of pushing the chip out of reach (A11Y-8).
    final maxChipWidth = (MediaQuery.sizeOf(context).width - tokens.gutter * 2)
        .clamp(tokens.minTarget, double.infinity)
        .floorToDouble();
    final chips = [
      for (final agent in widget.agents)
        _KitAgentChip(
          key: agent.key ?? ValueKey(('kit-agent', agent.id)),
          agent: agent,
          appear: !_initial.contains(agent.id),
          maxWidth: maxChipWidth,
        ),
    ];
    final Widget body;
    if (wide) {
      body = Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitChipWrap(children: chips),
        ),
      );
    } else {
      body = SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < chips.length; i++) ...[
              if (i > 0) SizedBox(width: tokens.space2),
              chips[i],
            ],
          ],
        ),
      );
    }
    return Semantics(
      key: widget.stripKey,
      container: true,
      explicitChildNodes: true,
      label: widget.semanticsLabel ?? l10n.kitAgentStripLabel,
      child: body,
    );
  }
}

class _KitAgentChip extends StatelessWidget {
  const _KitAgentChip({
    super.key,
    required this.agent,
    required this.appear,
    required this.maxWidth,
  });

  final KitAgent agent;
  final bool appear;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final name = KitBidi.auto(agent.name);
    final role = agent.role;
    final word = KitTaskMark.wordFor(
      context,
      agent.state,
      paused: agent.paused,
    );
    final words = l10n.kitAgentLabel(
      role == null ? 'no' : 'yes',
      name,
      role ?? '',
      word,
    );
    // needsYou is exactly KitNeedsYou's mark, the only attention look here
    // (LOOK-24); every other state is KitTaskMark at its designed size.
    final mark = agent.state == KitTaskState.needsYou
        ? KitNeedsYou.mark()
        : KitTaskMark(state: agent.state, paused: agent.paused);
    final content = ConstrainedBox(
      constraints: BoxConstraints(
        minHeight: tokens.minTarget,
        maxWidth: maxWidth,
      ),
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.space2,
          end: tokens.space3,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            KitSwap(
              child: KeyedSubtree(
                key: ValueKey((agent.state, agent.paused)),
                child: mark,
              ),
            ),
            SizedBox(width: tokens.space2),
            Flexible(
              child: KitText(
                name,
                role: KitTextRole.label,
                tone: KitTextTone.primary,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
    final onOpen = agent.onOpen;
    final Widget chip;
    if (onOpen == null) {
      // The lead: plain, not a button and not a Tab stop.
      chip = Semantics(
        label: words,
        excludeSemantics: true,
        child: Tooltip(
          message: words,
          excludeFromSemantics: true,
          child: content,
        ),
      );
    } else {
      chip = Semantics(
        hint: l10n.kitAgentOpen(name),
        child: KitTappable(
          onTap: onOpen,
          label: words,
          tooltip: words,
          shape: KitShape.pill,
          surface: KitSurfaceLevel.surface3,
          child: content,
        ),
      );
    }
    // A chip in the style's look, asked the way KitChip asks: a control
    // pill on `surface3`.
    final filled = kitSurfaceBox(
      style: KitSurfaceLook.styleOf(context),
      decoration: KitSurfaceLook.decorate(
        context,
        solid: ShapeDecoration(
          color: tokens.fillOf(KitSurfaceLevel.surface3),
          shape: tokens.shapeOf(KitShape.pill),
        ),
        role: KitSurfaceRole.control,
        level: KitSurfaceLevel.surface3,
      ),
      child: chip,
    );
    if (!appear) return filled;
    // A joining agent fades in at the end (paint only, no size animation);
    // under reduced motion it is simply there.
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      builder: (context, opacity, child) =>
          Opacity(opacity: opacity, child: child),
      child: filled,
    );
  }
}

/// An agent's identity tint, moved from widgets/agent_color.dart onto the
/// theme's roles. Identity is carried by the name, never by colour (VL §1
/// "one accent"; KitAvatar's rule), so every agent gets the same quiet
/// role until the owner decides otherwise (KitAgentStrip.md, Open
/// question 1).
abstract final class KitAgentTint {
  /// `text2` for every agent. [serverColor] (the OpenCode agent's
  /// configured colour) is accepted and ignored for now.
  static Color of(
    BuildContext context, {
    required String name,
    String? serverColor,
  }) => KitTokens.of(context).roles.text2;

  /// The same without a context, for the ColorScheme-only wrappers:
  /// `scheme.onSurfaceVariant`, which ThemeRoles sets from `text2`.
  static Color ofScheme(
    ColorScheme scheme, {
    required String name,
    String? serverColor,
  }) => scheme.onSurfaceVariant;
}
