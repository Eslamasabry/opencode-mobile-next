import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A page's own heading under a bar that carries no title (the project's
/// Files page, vision 2026-10-20): the large title, then one quiet line
/// close under it saying what the page belongs to ("neon-breaker") or
/// where it is ("neon-breaker / src").
///
/// With [onLineTap] the line is a switcher: a chevron after the words, a
/// 48 dp target that reaches up into the title's own line gap so the words
/// still sit close under it, and [lineTapLabel] for screen readers ("Project
/// neon-breaker. Switch project"). The title is a header; [namesRoute]
/// makes it the route's name when the page is a route of its own.
///
/// With [lineNeedsYou] the line is the page's one needs-you line ("2 need
/// you"): the attention tone at label weight, and with [onLineTap] a
/// forward chevron, since it opens a page rather than switching this one.
///
/// It sits on the page's rails (the gutter at both sides) a step under the
/// bar: a switcher line, a plain line, or none. Without [onLineTap] the line
/// is plain words, not a target.
///
/// States: disabled.
class KitPageHeading extends StatelessWidget {
  const KitPageHeading({
    super.key,
    required this.title,
    this.line,
    this.onLineTap,
    this.lineTapLabel,
    this.namesRoute = false,
    this.lineNeedsYou = false,
    this.titleKey,
    this.lineKey,
    this.lineTapKey,
  }) : assert(
         onLineTap == null || (lineTapLabel ?? '') != '',
         'KitPageHeading: a switcher line needs a lineTapLabel',
       );

  /// The place, in the person's words; wrap a name they chose with
  /// `KitBidi.auto`.
  final String title;

  /// The quiet line under the title; null shows none.
  final String? line;
  final VoidCallback? onLineTap;
  final String? lineTapLabel;
  final bool namesRoute;

  /// The line says what waits on the person: amber, and it opens.
  final bool lineNeedsYou;
  final Key? titleKey, lineKey, lineTapKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final line = this.line;
    Widget? quiet;
    if (line != null) {
      final tone = lineNeedsYou ? KitTextTone.attention : KitTextTone.secondary;
      final words = KitText(
        line,
        key: lineKey,
        role: lineNeedsYou ? KitTextRole.label : KitTextRole.body,
        tone: tone,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
      final onTap = onLineTap;
      final body = ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.minTarget),
        child: onTap == null
            ? Align(alignment: AlignmentDirectional.centerStart, child: words)
            : Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(child: words),
                  SizedBox(width: tokens.space1),
                  KitIcon(
                    lineNeedsYou
                        ? AppIconography.chevronRight
                        : AppIconography.chevronDown,
                    size: KitIconSize.small,
                    tone: tone,
                  ),
                ],
              ),
      );
      // The 48 dp target reaches up into the title's line gap: the words
      // sit close under the title, as one heading.
      quiet = Transform.translate(
        offset: Offset(0, -tokens.space2),
        child: onTap == null
            ? body
            : KitTappable(
                tappableKey: lineTapKey,
                label: lineTapLabel,
                shape: KitShape.button,
                surface: KitSurfaceLevel.ground,
                onTap: onTap,
                child: body,
              ),
      );
    }
    return Padding(
      // A step of air under the bar (or a wide window's toolbar hairline).
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        top: tokens.space2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            header: true,
            namesRoute: namesRoute,
            child: KitText(
              title,
              key: titleKey,
              role: KitTextRole.largeTitle,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          ?quiet,
        ],
      ),
    );
  }
}
