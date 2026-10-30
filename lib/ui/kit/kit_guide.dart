import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_divider.dart';
import 'kit_icon.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_row.dart';
import 'kit_screen.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One chip of a [KitGuide]: a group on its start, or one question inside
/// a group. It shows what is set now ([value]) under its [label].
@immutable
class KitGuideItem {
  const KitGuideItem({
    required this.id,
    required this.label,
    required this.value,
    this.key,
    this.done = false,
    this.next = false,
    this.held = false,
    this.semanticsLabel,
  });

  /// What [KitGuide.onItem] is called with.
  final String id;
  final String label;

  /// The current answer, in words: "Dark, Nord".
  final String value;

  /// On the chip itself (a test's or a tour's handle).
  final Key? key;

  /// Answered on the guided path: a check after the label.
  final bool done;

  /// The guide's suggestion: an accent edge and a dot.
  final bool next;

  /// Waiting on another choice ("Off in Saver"): the value reads quieter.
  /// Still a target: opening it says why.
  final bool held;

  /// What a screen reader says, with its place: "Theme, Nord, question 2
  /// of 3 in Colours". Default: the label and the value.
  final String? semanticsLabel;
}

/// The group open in a [KitGuide]: its name, and the way back to every
/// chip.
@immutable
class KitGuideGroup {
  const KitGuideGroup({
    required this.label,
    required this.onBack,
    this.backKey,
  });

  final String label;
  final VoidCallback onBack;
  final Key? backKey;
}

/// The one question a [KitGuide] shows: its [title], what it [changes] and
/// the control that answers it.
@immutable
class KitGuideQuestion {
  const KitGuideQuestion({
    required this.id,
    required this.title,
    required this.changes,
    required this.child,
  });

  /// Its identity: a new [id] slides in.
  final String id;
  final String title;

  /// "Every page, light or dark", after the word Changes.
  final String changes;
  final Widget child;
}

/// The guide's bar along the foot of the page: how far along, and the next
/// step. [onPressed] null shows only [count].
@immutable
class KitGuideNext {
  const KitGuideNext({
    required this.count,
    this.label,
    this.onPressed,
    this.ready = false,
    this.key,
  }) : assert(label == null || onPressed != null);

  /// "3 of 10".
  final String count;

  /// "Next: Theme", "Finish".
  final String? label;
  final VoidCallback? onPressed;

  /// The question was answered: the button fills with the accent.
  final bool ready;
  final Key? key;
}

/// A settings page that asks one decision at a time
/// (docs/ux-system/kit-api/KitGuide.md): a rail of chips at the top that
/// each show their current value, and below the rail either the open
/// [question] (its title, a "Changes" line naming what it affects, and its
/// control) or the page's [home] words. The choices show on the page itself
/// and the rest of the app as they are made; there is no separate preview. On a guided first visit a [next] bar along the foot
/// suggests the next chip, which carries a dot; answered chips carry a
/// check.
///
/// The rail shows the [items] given: the groups on the start view, or the
/// questions of one [group] with a way back to every chip and its place in
/// the group as dots. Tapping a chip calls [onItem] with its id.
///
/// Layout (kit-v2 §8): the rail stays on top and the question scrolls
/// under it; with large text or a short window everything scrolls as one
/// page.
///
/// Motion (150–250 ms): a new question fades and rises into place, the
/// rail cross-fades between the groups and one group's questions, and a
/// check or a dot pops in. Under reduced motion every change is instant.
/// Nothing loops, so an untouched page draws no frames.
///
/// Only the open question is built.
///
/// States: none — a frame around the controls it is given.
class KitGuide extends StatelessWidget {
  const KitGuide({
    super.key,
    required this.items,
    required this.onItem,
    this.selected,
    this.group,
    this.question,
    this.home,
    this.notice,
    this.next,
  });

  final List<KitGuideItem> items;
  final ValueChanged<String> onItem;

  /// The id of the item whose [question] is open.
  final String? selected;

  /// Null on the start view.
  final KitGuideGroup? group;
  final KitGuideQuestion? question;

  /// Under the rail when no question is open.
  final Widget? home;

  /// A notice over the question (a save that failed).
  final Widget? notice;
  final KitGuideNext? next;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final rail = AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.standard,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      // The outgoing chips keep their own height while they fade.
      layoutBuilder: (current, previous) => Stack(
        alignment: AlignmentDirectional.topStart,
        children: [...previous, ?current],
      ),
      child: _Rail(
        key: ValueKey(group?.label ?? '_start'),
        items: items,
        selected: selected,
        group: group,
        onItem: onItem,
      ),
    );
    final bar = next == null ? null : _NextBar(next: next!);
    return LayoutBuilder(
      builder: (context, box) {
        final scale = MediaQuery.textScalerOf(context).scale(16) / 16;
        final ordinaryText = scale < 1.3;
        final pinned = ordinaryText && box.maxHeight >= KitLayout.shortHeight;
        Widget body({required bool scrolls}) => _Body(
          question: question,
          home: home,
          notice: notice,
          scrolls: scrolls,
        );
        // The rail keeps the gutter itself (_Rail): flat rows run the
        // page's width. In a group the "‹ All" line is a full touch target
        // with air of its own, so it sits right under the top bar, where the
        // preview used to be, instead of under a gap.
        final railPadded = group == null
            ? Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space2),
                child: rail,
              )
            : rail;
        // Unbounded (inside another scroll view): one column, as tall as
        // it is.
        if (!box.hasBoundedHeight) {
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [railPadded, body(scrolls: false), ?bar],
          );
        }
        if (pinned) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              railPadded,
              Expanded(child: body(scrolls: true)),
              ?bar,
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: ListView(
                padding: EdgeInsetsDirectional.only(top: tokens.space1),
                children: [railPadded, body(scrolls: false)],
              ),
            ),
            ?bar,
          ],
        );
      },
    );
  }
}

Widget _topStack(Widget? current, List<Widget> previous) => Stack(
  alignment: AlignmentDirectional.topStart,
  children: [
    for (final child in previous) Positioned.fill(child: child),
    ?current,
  ],
);

/// The chips: the groups two by two, or one group's questions in a line
/// under its name.
class _Rail extends StatelessWidget {
  const _Rail({
    super.key,
    required this.items,
    required this.selected,
    required this.group,
    required this.onItem,
  });

  final List<KitGuideItem> items;
  final String? selected;
  final KitGuideGroup? group;
  final ValueChanged<String> onItem;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final gap = tokens.space2;
    Widget chip(KitGuideItem item) => _GuideChip(
      key: item.key,
      item: item,
      selected: item.id == selected,
      onTap: () => onItem(item.id),
    );
    // Two by two at ordinary text; one under another when large text would
    // squeeze a word onto three lines.
    final scale = MediaQuery.textScalerOf(context).scale(16) / 16;
    final open = group;
    // Flat lists (Solid): the start view is the page's own list, one row
    // per group that opens it (a chevron), parted by hairlines at the words
    // like every other list in the app.
    if (open == null && KitSurfaceLook.flatLists(context)) {
      return KitRowGroup(
        children: [
          for (final item in items)
            KitRow(
              key: item.key,
              title: item.label,
              titleMaxLines: 2,
              titleMark: item.done ? const _DoneMark() : null,
              supporting: TextSpan(
                text: item.value,
                style: item.held ? TextStyle(color: tokens.roles.text3) : null,
              ),
              supportingMaxLines: 2,
              trailing: _GuideRowEnd(next: item.next),
              onTap: () => onItem(item.id),
            ),
        ],
      );
    }
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: _chips(context, tokens, chip, scale, gap),
    );
  }

  Widget _chips(
    BuildContext context,
    KitTokens tokens,
    Widget Function(KitGuideItem item) chip,
    double scale,
    double gap,
  ) {
    final open = group;
    if (open == null) {
      final perRow = scale >= 1.6 ? 1 : 2;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var row = 0; row < items.length; row += perRow) ...[
            if (row > 0) SizedBox(height: gap),
            IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final (i, item)
                      in items.skip(row).take(perRow).indexed) ...[
                    if (i > 0) SizedBox(width: gap),
                    Expanded(child: chip(item)),
                  ],
                ],
              ),
            ),
          ],
        ],
      );
    }
    final at = items.indexWhere((item) => item.id == selected);
    final stacked = scale >= 1.3;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Crumb(group: open, count: items.length, at: at),
        SizedBox(height: tokens.space2),
        if (stacked)
          for (final (i, item) in items.indexed) ...[
            if (i > 0) SizedBox(height: gap),
            chip(item),
          ]
        else
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final (i, item) in items.indexed) ...[
                  if (i > 0) SizedBox(width: tokens.space2 - 2),
                  Expanded(child: chip(item)),
                ],
              ],
            ),
          ),
      ],
    );
  }
}

/// "‹ All", the group's name, and dots for the questions up to here.
class _Crumb extends StatelessWidget {
  const _Crumb({required this.group, required this.count, required this.at});

  final KitGuideGroup group;
  final int count;
  final int at;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final glide = KitMotion.reduced(context) ? Duration.zero : KitMotion.quick;
    return Row(
      children: [
        KitTappable(
          key: group.backKey,
          onTap: group.onBack,
          label: l10n.kitGuideBack,
          shape: KitShape.button,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.minTarget),
            child: Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.space1,
                end: tokens.space2,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const KitIcon(
                    AppIconography.back,
                    size: KitIconSize.small,
                    tone: KitTextTone.secondary,
                  ),
                  SizedBox(width: tokens.space1),
                  KitText(
                    l10n.kitGuideAll,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: 1,
                  ),
                ],
              ),
            ),
          ),
        ),
        SizedBox(width: tokens.space2),
        Expanded(
          child: Semantics(
            header: true,
            child: KitText(
              group.label,
              role: KitTextRole.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ),
        ExcludeSemantics(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 0; i < count; i++)
                Padding(
                  padding: EdgeInsetsDirectional.only(start: tokens.space1),
                  child: TweenAnimationBuilder<Color?>(
                    duration: glide,
                    curve: KitMotion.enter,
                    tween: ColorTween(
                      end: i <= at ? roles.accent : roles.hairline,
                    ),
                    builder: (context, color, _) => DecoratedBox(
                      decoration: KitSurfaceDecoration(
                        circle: true,
                        fill: color ?? roles.hairline,
                      ),
                      child: const SizedBox.square(dimension: 6),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// An answered group's check after its name on the flat start view.
class _DoneMark extends StatelessWidget {
  const _DoneMark();

  @override
  Widget build(BuildContext context) => const KitIcon(
    AppIconography.check,
    size: KitIconSize.small,
    tone: KitTextTone.accent,
  );
}

/// The end of a group's row on the flat start view: the guide's dot when
/// the group is next, then the chevron of a row that opens a page.
class _GuideRowEnd extends StatelessWidget {
  const _GuideRowEnd({required this.next});

  final bool next;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final glide = KitMotion.reduced(context) ? Duration.zero : KitMotion.quick;
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedScale(
            scale: next ? 1 : KitMotion.markFrom,
            duration: glide,
            curve: KitMotion.enter,
            child: AnimatedOpacity(
              opacity: next ? 1 : 0,
              duration: glide,
              curve: KitMotion.enter,
              child: DecoratedBox(
                decoration: KitSurfaceDecoration(
                  circle: true,
                  fill: tokens.roles.accent,
                ),
                child: const SizedBox.square(dimension: 7),
              ),
            ),
          ),
          // Where a KitRowValue's chevron sits, so it lines up with the
          // chevrons of every other list.
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space1,
              end: tokens.space3,
            ),
            child: Icon(
              AppIconography.chevronRight,
              size: tokens.iconSize(context, tokens.smallIconSize),
              color: tokens.roles.text3,
            ),
          ),
        ],
      ),
    );
  }
}

/// One chip: its label (with a check once answered), its value, and the
/// guide's dot when it is next. Chosen, it is tinted with an accent edge.
class _GuideChip extends StatelessWidget {
  const _GuideChip({
    super.key,
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final KitGuideItem item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final glide = KitMotion.reduced(context) ? Duration.zero : KitMotion.quick;
    final radius = BorderRadius.circular(tokens.cardRadius);
    final marked = selected || item.next;
    // Flat lists (Solid): a resting chip is its fill alone, no outline.
    final edge = marked
        ? roles.accent
        : KitSurfaceLook.flatLists(context)
        ? Colors.transparent
        : roles.hairline;
    final own = KitSurfaceDecoration(
      fill: selected
          ? Color.alphaBlend(roles.accent.withValues(alpha: .14), roles.ground)
          : roles.surface1,
      borderRadius: radius,
      edgeStart: edge,
      edgeEnd: edge,
      edgeWidth: selected
          ? KitTokens.focusRingWidth(context)
          : KitTokens.hairlineWidth(context),
    );
    final face = KitSurfaceLook.decorate(
      context,
      solid: own,
      role: KitSurfaceRole.control,
      selected: selected,
    );
    // A style's own face keeps the accent edge the choice needs.
    final ring = marked && !identical(face, own)
        ? KitSurfaceDecoration(
            borderRadius: KitSurfaceDecoration.radiusOf(face) ?? radius,
            fill: Colors.transparent,
            edgeStart: roles.accent,
            edgeEnd: roles.accent,
            edgeWidth: KitTokens.focusRingWidth(context),
          )
        : null;
    final words = Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.space3,
        end: tokens.space3 + tokens.space1,
        top: tokens.space2,
        bottom: tokens.space2,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Row(
            children: [
              Flexible(
                child: KitText(
                  item.label,
                  role: KitTextRole.rowTitle,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              AnimatedScale(
                scale: item.done ? 1 : KitMotion.markFrom,
                duration: glide,
                curve: KitMotion.enter,
                child: AnimatedOpacity(
                  opacity: item.done ? 1 : 0,
                  duration: glide,
                  curve: KitMotion.enter,
                  child: item.done
                      ? Padding(
                          padding: EdgeInsetsDirectional.only(
                            start: tokens.space1,
                          ),
                          child: const KitIcon(
                            AppIconography.check,
                            size: KitIconSize.small,
                            tone: KitTextTone.accent,
                          ),
                        )
                      : const SizedBox.shrink(),
                ),
              ),
            ],
          ),
          KitText(
            item.value,
            role: KitTextRole.secondary,
            tone: item.held
                ? KitTextTone.tertiary
                : selected
                ? KitTextTone.primary
                : KitTextTone.secondary,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
    return KitTappable(
      onTap: onTap,
      label: item.semanticsLabel ?? '${item.label}, ${item.value}',
      selected: selected,
      shape: KitShape.card,
      child: ExcludeSemantics(
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: kitSurfaceBox(
            decoration: face,
            child: Stack(
              children: [
                ring == null
                    ? words
                    : DecoratedBox(
                        position: DecorationPosition.foreground,
                        decoration: ring,
                        child: words,
                      ),
                PositionedDirectional(
                  top: tokens.space2,
                  end: tokens.space2,
                  child: AnimatedScale(
                    scale: item.next ? 1 : KitMotion.markFrom,
                    duration: glide,
                    curve: KitMotion.enter,
                    child: AnimatedOpacity(
                      opacity: item.next ? 1 : 0,
                      duration: glide,
                      curve: KitMotion.enter,
                      child: DecoratedBox(
                        decoration: KitSurfaceDecoration(
                          circle: true,
                          fill: roles.accent,
                        ),
                        child: const SizedBox.square(dimension: 7),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Under the rail: the notice, then the open question (or the home words),
/// sliding in when it changes.
class _Body extends StatelessWidget {
  const _Body({
    required this.question,
    required this.home,
    required this.notice,
    required this.scrolls,
  });

  final KitGuideQuestion? question;
  final Widget? home;
  final Widget? notice;
  final bool scrolls;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final open = question;
    final id = open?.id ?? '_home';
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ?notice,
        if (open != null) ...[
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space4,
            ),
            child: _Header(question: open),
          ),
          SizedBox(height: tokens.space3),
          open.child,
        ] else
          ?home,
      ],
    );
    final page = scrolls
        ? ListView(
            key: ValueKey('kit-guide-body-$id'),
            padding: EdgeInsetsDirectional.only(
              bottom: KitScreen.endPadding(context),
            ),
            children: [content],
          )
        : Padding(
            key: ValueKey('kit-guide-body-$id'),
            padding: EdgeInsetsDirectional.only(
              bottom: KitScreen.endPadding(context),
            ),
            child: content,
          );
    return AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.standard,
      reverseDuration: reduced ? Duration.zero : KitMotion.quick,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      layoutBuilder: scrolls
          ? _topStack
          : (current, previous) => current ?? const SizedBox.shrink(),
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(0, .02),
            end: Offset.zero,
          ).animate(animation),
          child: child,
        ),
      ),
      child: page,
    );
  }
}

/// The question in words, and what it changes.
class _Header extends StatelessWidget {
  const _Header({required this.question});

  final KitGuideQuestion question;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: KitText(question.title, role: KitTextRole.headline),
        ),
        SizedBox(height: tokens.space1),
        MergeSemantics(
          child: Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: l10n.kitGuideChanges,
                  style: KitText.styleOf(
                    context,
                    KitTextRole.caption,
                  ).copyWith(color: tokens.roles.accent),
                ),
                const TextSpan(text: '  '),
                TextSpan(
                  text: question.changes,
                  style: KitText.styleOf(
                    context,
                    KitTextRole.secondary,
                  ).copyWith(color: tokens.roles.text2),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// How far along, and the next step.
class _NextBar extends StatelessWidget {
  const _NextBar({required this.next});

  final KitGuideNext next;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final label = next.label;
    final count = KitText(
      next.count,
      role: KitTextRole.secondary,
      tone: KitTextTone.secondary,
      maxLines: 2,
    );
    final button = label == null
        ? null
        : KitButton(
            role: next.ready ? KitButtonRole.primary : KitButtonRole.secondary,
            label: label,
            onPressed: next.onPressed,
            expand: false,
          );
    // At large text the step takes the bar's whole width under the count.
    final large = MediaQuery.textScalerOf(context).scale(16) / 16 >= 1.3;
    return Column(
      key: next.key,
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const KitDivider(),
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.gutter,
            end: tokens.gutter,
            top: tokens.space2,
            bottom: tokens.space2 + MediaQuery.viewPaddingOf(context).bottom,
          ),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.minTarget),
            child: large
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      count,
                      if (label != null) ...[
                        SizedBox(height: tokens.space2),
                        KitButton(
                          role: next.ready
                              ? KitButtonRole.primary
                              : KitButtonRole.secondary,
                          label: label,
                          onPressed: next.onPressed,
                        ),
                      ],
                    ],
                  )
                : Row(
                    children: [
                      Expanded(child: count),
                      if (button != null) ...[
                        SizedBox(width: tokens.space2),
                        Flexible(
                          flex: 2,
                          child: Align(
                            alignment: AlignmentDirectional.centerEnd,
                            child: button,
                          ),
                        ),
                      ],
                    ],
                  ),
          ),
        ),
      ],
    );
  }
}
