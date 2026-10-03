import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_status_tag.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One conversation in a feed (Chats home): the project it lives in (small
/// and muted, with a Git mark), the title on one line, the state at the end
/// of that line (a [KitStatusTag], else the relative time) and the last line
/// of the conversation under it.
///
/// The whole row is one target. Its semantics read as one sentence:
/// project, title, state or time, preview. At large text the preview takes a
/// second line and the tag wraps under nothing: the title line shrinks first.
///
/// States: none — a conversation that exists; press, hover and focus come
/// from [KitTappable], and a tag or a missing preview is only its content.
class KitFeedItem extends StatelessWidget {
  const KitFeedItem({
    super.key,
    required this.project,
    required this.title,
    required this.onTap,
    this.gitLabel,
    this.agent,
    this.preview = '',
    this.tag,
    this.time,
    this.semanticsLabel,
    this.tappableKey,
  });

  /// The project's name; a name the server chose, so it is isolated for
  /// bidirectional text here.
  final String project;

  /// "Git" when the project is a Git repository (the caller's ARB), else null.
  final String? gitLabel;

  final String title;

  /// The agent's name ('Claude Code'), shown after the project only when the
  /// feed holds more than one agent; null otherwise.
  final String? agent;

  /// The last line; empty shows nothing.
  final String preview;

  /// The state at the line's end; wins over [time].
  final KitStatusTag? tag;

  /// The relative time ("5 min ago"), shown when there is no [tag].
  final String? time;

  final VoidCallback onTap;

  /// Overrides the spoken sentence; null builds it from the parts.
  final String? semanticsLabel;
  final Key? tappableKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final tag = this.tag;
    final time = this.time;
    final preview = this.preview.trim();
    final git = gitLabel;
    final agent = this.agent;
    // From 1.3x text the title takes two lines and the state goes under it,
    // so neither is cut to a few letters.
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final Widget? end = tag != null
        ? Flexible(flex: 0, child: tag)
        : time != null
        ? KitText(
            time,
            role: KitTextRole.caption,
            tone: KitTextTone.tertiary,
            maxLines: 1,
          )
        : null;
    final words =
        semanticsLabel ??
        [
          project,
          ?agent,
          ?git,
          title,
          ?(tag?.label ?? time),
          if (preview.isNotEmpty) preview,
        ].join(', ');
    return KitTappable(
      tappableKey: tappableKey,
      onTap: onTap,
      label: words,
      child: ExcludeSemantics(
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: tokens.gutter,
            vertical: tokens.space3,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            spacing: tokens.space1,
            children: [
              Row(
                spacing: tokens.space2,
                children: [
                  Flexible(
                    child: KitText(
                      KitBidi.auto(project),
                      role: KitTextRole.caption,
                      tone: KitTextTone.secondary,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (agent != null)
                    Flexible(
                      child: KitText(
                        KitBidi.auto(agent),
                        role: KitTextRole.caption,
                        tone: KitTextTone.tertiary,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  if (git != null)
                    _GitMark(
                      label: git,
                      size: tokens.smallIconSize,
                      color: roles.text2,
                    ),
                ],
              ),
              if (large) ...[
                KitText(
                  KitBidi.auto(title),
                  role: KitTextRole.rowTitle,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                ?end,
              ] else
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  spacing: tokens.space2,
                  children: [
                    Expanded(
                      child: KitText(
                        KitBidi.auto(title),
                        role: KitTextRole.rowTitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    ?end,
                  ],
                ),
              if (preview.isNotEmpty)
                KitText(
                  KitBidi.auto(preview),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                  maxLines: large ? 2 : 1,
                  overflow: TextOverflow.ellipsis,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GitMark extends StatelessWidget {
  const _GitMark({
    required this.label,
    required this.size,
    required this.color,
  });

  final String label;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    spacing: 2,
    children: [
      Icon(AppIconography.branch, size: size * .75, color: color),
      KitText(
        label,
        role: KitTextRole.caption,
        tone: KitTextTone.secondary,
        maxLines: 1,
      ),
    ],
  );
}
