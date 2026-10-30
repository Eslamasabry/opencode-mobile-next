part of 'kit_top_bar.dart';

// The line under a KitTopBar's title: the scope (a place, opening a menu or
// plain), its branch and its live link.

/// The [KitTopBarScope.origin]: short words ("from Design review"), the
/// full sentence for a screen reader, and the tap that opens the other page.
/// [gone] words a page that no longer exists and turns the tap off.
class KitTopBarOrigin {
  const KitTopBarOrigin({
    required this.label,
    required this.semanticsLabel,
    required this.onTap,
    this.gone = false,
    this.originKey,
  });

  /// Wrap a title the person or server chose with `KitBidi.auto`.
  final String label;
  final String semanticsLabel;
  final VoidCallback onTap;
  final bool gone;
  final Key? originKey;
}

/// From this text scale the scope line's parts wrap instead of sharing one
/// line.
const double _kScopeWrapTextScale = 1.3;

/// The quiet line under the title: the project's name, an optional second
/// name (the server), and the branch's name, words only, each part its own
/// target at least `minTarget` tall; at large text it wraps instead of
/// growing sideways. The second name joins the line with a short fade and
/// slide.
class _KitTopBarScopeLine extends StatefulWidget {
  const _KitTopBarScopeLine({required this.scope});

  final KitTopBarScope scope;

  @override
  State<_KitTopBarScopeLine> createState() => _KitTopBarScopeLineState();
}

class _KitTopBarScopeLineState extends State<_KitTopBarScopeLine> {
  Future<void> _openMenu(BuildContext anchor) async {
    final onTap = widget.scope.onTap;
    if (onTap != null) return onTap();
    await showKitMenu(
      anchor,
      items: widget.scope.items,
      semanticsLabel: widget.scope.menuLabel,
    );
  }

  @override
  Widget build(BuildContext context) {
    final scope = widget.scope;
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final suffix = scope.suffix;
    final hasSuffix = suffix != null && suffix.isNotEmpty;
    final branch = scope.branch;
    final link = scope.link;
    if (link != null && scope.label.isEmpty) {
      return Align(
        alignment: AlignmentDirectional.centerStart,
        child: _KitTopBarLinkLine(link: link),
      );
    }
    final style = KitText.styleOf(context, KitTextRole.secondary);
    final scaler = MediaQuery.textScalerOf(context);
    final direction = Directionality.of(context);
    double widthOf(String text) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final width = painter.width;
      painter.dispose();
      return width;
    }

    // The project's name and the server's after it, whole: the server is
    // left out where both do not fit.
    final withSuffix = hasSuffix
        ? widthOf(scope.label) + widthOf(' \u00b7 $suffix')
        : 0.0;
    // Words only (owner 20 Oct 2026, concept C keeps the title alone): the
    // project's name and the branch's, each its own 48 dp target, joined by
    // a quiet dot. No glyphs and no chevron: the line is a place, not a
    // control panel.
    Widget word(
      String text, {
      required String semanticsLabel,
      required VoidCallback onTap,
      Key? tappableKey,
      Widget? leading,
      Widget? trailing,
      double trailingNeeds = 0,
    }) => Semantics(
      container: true,
      label: semanticsLabel,
      button: true,
      excludeSemantics: true,
      onTap: onTap,
      child: KitTappable(
        tappableKey: tappableKey,
        onTap: onTap,
        shape: KitShape.button,
        surface: KitSurfaceLevel.ground,
        child: ConstrainedBox(
          // A short word ("Git") still starts where the line's words do:
          // the target is 48 dp wide, the word sits at its start.
          constraints: BoxConstraints(
            minHeight: tokens.minTarget,
            minWidth: tokens.minTarget,
          ),
          child: trailing == null
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ?leading,
                    Flexible(
                      child: KitText(
                        text,
                        role: KitTextRole.secondary,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                )
              // With a second name (the server): said whole when both fit,
              // and left out when they do not; a name cut in the middle
              // reads as broken (owner, 2026-10-27). The composer names
              // the agent too, and the label still says the server.
              : LayoutBuilder(
                  builder: (context, constraints) {
                    final fits =
                        !constraints.maxWidth.isFinite ||
                        trailingNeeds <= constraints.maxWidth;
                    return Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        ?leading,
                        Flexible(
                          child: KitText(
                            text,
                            role: KitTextRole.secondary,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (fits) trailing,
                      ],
                    );
                  },
                ),
        ),
      ),
    );
    // The dot rides at the start of the branch's own target, so the two
    // words keep an even gap whatever their length.
    final separator = ExcludeSemantics(
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.space2,
          end: tokens.space2,
        ),
        child: KitText(
          '\u00b7',
          role: KitTextRole.secondary,
          tone: KitTextTone.tertiary,
        ),
      ),
    );
    final Widget project = !scope.opens
        // A plain place, said once, with nothing to open.
        ? Semantics(
            container: true,
            label: scope.semanticsLabel,
            excludeSemantics: true,
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.minTarget),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                widthFactor: 1,
                heightFactor: 1,
                child: LayoutBuilder(
                  builder: (context, constraints) => KitText(
                    hasSuffix &&
                            (!constraints.maxWidth.isFinite ||
                                withSuffix <= constraints.maxWidth)
                        ? '${scope.label} \u00b7 $suffix'
                        : scope.label,
                    role: KitTextRole.secondary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
            ),
          )
        : Builder(
            builder: (anchor) => word(
              scope.label,
              semanticsLabel: scope.semanticsLabel,
              tappableKey: scope.chipKey,
              onTap: () => unawaited(_openMenu(anchor)),
              trailingNeeds: withSuffix,
              // The server follows the project, when more than one is connected,
              // with a short fade and slide.
              trailing: KitPresence(
                show: hasSuffix,
                child: TweenAnimationBuilder<double>(
                  tween: Tween<double>(end: hasSuffix ? 0 : 1),
                  duration: reduced ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  builder: (context, slide, child) => Transform.translate(
                    offset: Offset(
                      (Directionality.of(context) == TextDirection.rtl
                              ? -1
                              : 1) *
                          slide *
                          tokens.space2,
                      0,
                    ),
                    child: child,
                  ),
                  child: KitText(
                    ' \u00b7 ${suffix ?? ''}',
                    role: KitTextRole.secondary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
            ),
          );
    final origin = scope.origin;
    // [dot]: the quiet dot that joins it to the part before, left out where
    // the part starts a line of its own.
    Widget? originWordWith({required bool dot}) => origin == null
        ? null
        : Semantics(
            container: true,
            label: origin.semanticsLabel,
            button: !origin.gone,
            excludeSemantics: true,
            onTap: origin.gone ? null : origin.onTap,
            child: KitTappable(
              tappableKey: origin.originKey,
              onTap: origin.gone ? null : origin.onTap,
              disabledReason: origin.gone ? origin.label : null,
              shape: KitShape.button,
              surface: KitSurfaceLevel.ground,
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: tokens.minTarget),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (dot) separator,
                    Flexible(
                      child: KitText(
                        origin.label,
                        role: KitTextRole.secondary,
                        tone: origin.gone
                            ? KitTextTone.tertiary
                            : KitTextTone.secondary,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
    final originWord = originWordWith(dot: scope.label.isNotEmpty);
    Widget? branchWordWith({required bool dot}) => branch == null
        ? null
        : word(
            branch.label,
            semanticsLabel: branch.semanticsLabel,
            tappableKey: branch.chipKey,
            onTap: branch.onTap,
            leading: dot ? separator : null,
          );
    final branchWord = branchWordWith(dot: true);
    if (branch == null && link == null && originWord == null) {
      return Align(alignment: AlignmentDirectional.centerStart, child: project);
    }
    // The origin rides on the one line and gives way first: the project and
    // the branch keep a share each, the origin takes what is left and is
    // cut, so the line never grows a row for it. At large text
    // the parts wrap like the rest of the line.
    final large =
        MediaQuery.textScalerOf(context).scale(1) >= _kScopeWrapTextScale;
    if (originWord != null && link == null && !large) {
      // What each part needs whole; a part is cut only past its share.
      final dot = 2 * tokens.space2 + widthOf('\u00b7');
      final projectNeeds = scope.label.isEmpty
          ? 0.0
          : math.max(
              tokens.minTarget,
              widthOf(scope.label) +
                  (hasSuffix ? widthOf(' \u00b7 $suffix') : 0),
            );
      final branchNeeds = branch == null
          ? 0.0
          : math.max(tokens.minTarget, dot + widthOf(branch.label));
      final originNeeds =
          (scope.label.isEmpty ? 0.0 : dot) + widthOf(origin!.label);
      return LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          final branchRoom = math.min(branchNeeds + 1, width * .25);
          // The origin is said whole when it can be: the server's name
          // after the project gives way first (the composer names the
          // agent too), the project keeps at least a third.
          // When the project and the server do not fit beside the whole
          // origin, the server is left out (never cut).
          final projectRoom = math.min(
            projectNeeds + 1,
            math.max(width - branchRoom - originNeeds, width / 3),
          );
          return Row(
            children: [
              if (scope.label.isNotEmpty)
                ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: projectRoom),
                  child: project,
                ),
              // Before the branch: the branch's 48 dp target ends the line,
              // so its spare room is not a gap in the middle of the words.
              Flexible(child: originWord),
              if (branchWord != null)
                ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: branchRoom),
                  child: branchWord,
                ),
            ],
          );
        },
      );
    }
    // The project keeps the room its name needs; the branch follows it on the
    // same line and drops under it where the words are too large to share
    // (large text), ellipsizing only when it alone is wider than the line.
    // A part that starts a line starts with its words, never with the dot
    // that joins it to the part before ("· Git"): where a part will not fit
    // after the one before, it goes without its dot, and the part before
    // takes the rest of its line so the break is certain.
    return LayoutBuilder(
      builder: (context, constraints) {
        final room = constraints.maxWidth;
        final dot = 2 * tokens.space2 + widthOf('\u00b7');
        final suffixShown = hasSuffix && withSuffix <= room;
        final projectWidth = scope.label.isEmpty
            ? 0.0
            : math.min(
                room,
                math.max(
                  scope.opens ? tokens.minTarget : 0.0,
                  widthOf(scope.label) +
                      (suffixShown ? widthOf(' \u00b7 $suffix') : 0),
                ),
              );
        // Where the next part starts on its line, and where the branch
        // starts on its own.
        var x = projectWidth;
        var branchStart = 0.0;
        // Whether the part after [x] fits on its line with its dot.
        bool fits(double withDot) => x == 0 || x + withDot <= room - .5;
        var branchDot = true;
        var projectFills = false;
        if (branch != null) {
          final needs = math.max(tokens.minTarget, dot + widthOf(branch.label));
          if (scope.label.isNotEmpty && !fits(needs)) {
            branchDot = false;
            projectFills = true;
            branchStart = 0;
            x = math.min(
              room,
              math.max(tokens.minTarget, widthOf(branch.label)),
            );
          } else {
            branchStart = x;
            x = math.min(room, x + needs);
          }
        }
        var originDot = scope.label.isNotEmpty;
        var branchFills = false;
        if (origin != null && originDot) {
          if (!fits(dot + widthOf(origin.label))) {
            originDot = false;
            if (branch != null) {
              branchFills = true;
            } else {
              projectFills = true;
            }
          }
        }
        // The rest of the line, for the part a break follows.
        Widget fill(Widget part, double start) => SizedBox(
          width: math.max(0, room - start),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            heightFactor: 1,
            child: part,
          ),
        );
        final branchPart = branchWordWith(dot: branchDot);
        final originPart = originWordWith(dot: originDot);
        return Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            if (scope.label.isNotEmpty)
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: room),
                child: projectFills ? fill(project, 0) : project,
              ),
            if (branchPart != null)
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: room),
                child: branchFills ? fill(branchPart, branchStart) : branchPart,
              ),
            if (originPart != null)
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: room),
                child: originPart,
              ),
            if (link != null)
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: room),
                child: Padding(
                  padding: EdgeInsetsDirectional.only(start: tokens.space2),
                  child: _KitTopBarLinkLine(link: link),
                ),
              ),
          ],
        );
      },
    );
  }
}

/// [KitTopBarLink] drawn: the live mark, the muted words, " · ", the act in
/// accent. Fades in over `KitMotion.quick` when it appears (none under
/// reduced motion).
class _KitTopBarLinkLine extends StatelessWidget {
  const _KitTopBarLinkLine({required this.link});

  final KitTopBarLink link;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final roles = tokens.roles;
    final muted = KitText.styleOf(context, KitTextRole.secondary);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: reduced ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      builder: (context, shown, child) => Opacity(opacity: shown, child: child),
      child: Semantics(
        container: true,
        button: true,
        label: '${link.label}, ${link.action}',
        excludeSemantics: true,
        onTap: link.onTap,
        child: KitTappable(
          tappableKey: link.linkKey,
          onTap: link.onTap,
          shape: KitShape.button,
          surface: KitSurfaceLevel.ground,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.minTarget),
            child: Padding(
              padding: EdgeInsetsDirectional.zero,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // A still mint dot: running, said in the words beside it.
                  SizedBox.square(
                    dimension: tokens.space2,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: roles.accent,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                  SizedBox(width: tokens.space2),
                  Flexible(
                    child: KitText.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: link.label, style: muted),
                          TextSpan(text: ' \u00b7 ', style: muted),
                          TextSpan(
                            text: link.action,
                            style: muted.copyWith(
                              color: roles.accent,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                      role: KitTextRole.secondary,
                      // The act is never cut: large text wraps the line.
                      maxLines: 4,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
