import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'glass/kit_glass.dart';
import 'kit_screen.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_divider.dart';
import 'kit_icon.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_optical.dart';
import 'kit_needs_you.dart';
import 'kit_presence.dart';
import 'kit_status_mark.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

part 'kit_top_bar_centred.dart';
part 'kit_top_bar_pill.dart';
part 'kit_top_bar_scope.dart';
part 'kit_top_bar_tail.dart';

/// How the bar leaves the page. Back sits at the start, Close at the end
/// (LAY-8, Appendix A #77).
enum KitTopBarExit {
  /// Close at the end for a fullscreenDialog route; Back at the start when
  /// the route can pop; nothing at a root or inside a KitScreen pane.
  auto,
  back,
  close,
  none,
}

void _noop() {}

/// The small chip under the title that names the thing the page belongs to
/// (a conversation's project) and opens a compact menu of what can be done
/// there. It is its own control: the chip's semantics label is
/// [semanticsLabel], and the title's header label does not repeat it.
class KitTopBarScope {
  const KitTopBarScope({
    required this.label,
    required this.semanticsLabel,
    this.items = const [],
    this.onTap,
    this.icon,
    this.chipKey,
    this.menuLabel,
    this.suffix,
    this.branch,
    this.link,
    this.origin,
  });

  /// Where this page's work came from or went ("from Design review"), on
  /// the same line, before the branch: its own quiet target that opens the
  /// other page. It takes the room the line has left and is cut first; it never
  /// adds a row of its own.
  final KitTopBarOrigin? origin;

  /// A live thing the page knows about, said at the end of the same line
  /// with its one act ("Dev server running · Open preview"). Its own
  /// target; it drops under the name when the line has no room. With an
  /// empty [label] the line is the link alone (the host says the name
  /// elsewhere).
  final KitTopBarLink? link;

  /// Whether the name opens anything. A scope with no [items] and no
  /// [onTap] is a plain line: no target.
  bool get opens => onTap != null || items.isNotEmpty;

  /// A second, separate chip at the end of the same line: the Git branch of
  /// the project. It is its own target and opens the host's Git sheet, so
  /// it adds no row to the bar.
  final KitTopBarBranch? branch;

  /// The name shown on the chip; wrap a name the person or server chose
  /// with `KitBidi.auto`.
  final String label;

  /// What a screen reader says: "Project IPTV_King, opens a menu".
  final String semanticsLabel;

  /// The menu the chip opens, when it opens one ([onTap] is null).
  final List<KitMenuItem> items;

  /// The chip goes straight to a page instead of a menu (the conversation's
  /// project chip opens the project's Files). Wins over [items].
  final VoidCallback? onTap;
  final IconData? icon;
  final Key? chipKey;

  /// The menu's name for a screen reader.
  final String? menuLabel;

  /// A second name on the same line, after " · " (the server, when more than
  /// one is connected).
  final String? suffix;
}

/// The live line of a [KitTopBarScope]: a mint mark, what is running in
/// muted words, then the act in accent ("Dev server running · Open
/// preview"). The whole line is one target at least `minTarget` tall.
/// Shown only from a real signal; the host never guesses it.
class KitTopBarLink {
  const KitTopBarLink({
    required this.label,
    required this.action,
    required this.onTap,
    this.linkKey,
  });

  /// What is running: "Dev server running".
  final String label;

  /// The act, naming its target: "Open preview".
  final String action;
  final VoidCallback onTap;
  final Key? linkKey;
}

/// The branch chip of a [KitTopBarScope]: the branch's name (or "Git" when
/// there is none to name) and one tap that opens the Git sheet.
class KitTopBarBranch {
  const KitTopBarBranch({
    required this.label,
    required this.semanticsLabel,
    required this.onTap,
    this.chipKey,
    this.icon,
  });

  /// The branch name; wrap a name the person chose with `KitBidi.auto`.
  final String label;

  /// What a screen reader says: "Git, branch main. Opens the Git actions."
  final String semanticsLabel;
  final VoidCallback onTap;
  final Key? chipKey;
  final IconData? icon;
}

bool _actionsHaveIcons(List<KitAction> actions) {
  for (final action in actions) {
    if (action.icon == null) return false;
  }
  return true;
}

bool _noDestructiveActions(List<KitAction> actions) {
  for (final action in actions) {
    if (action.destructive) return false;
  }
  return true;
}

/// The screen's header (kit-v2.md §1.18, docs/ux-system/kit-api/KitTopBar.md):
/// where you are in the person's words, one line of state, a switcher when
/// the title switches something, and the page's few actions — one icon plus
/// the overflow on a phone, labelled actions on a PC.
///
/// It is not a `PreferredSizeWidget`: it has no fixed height (the row is at
/// least `minTarget` + 2 × `space1`) and grows at 200 % text.
///
/// States: default, subtitle (neutral, working, needs-you, not answering),
/// switcher, brand, disabled actions (moved into the overflow with their
/// reason, STATE-8), shell (connected, reconnecting, not answering,
/// needs-you badge). No loading, empty or error state of its own.
class KitTopBar extends StatelessWidget {
  const KitTopBar({
    super.key,
    required this.title,
    this.subtitle,
    this.subtitleTail,
    this.subtitleTone = AppStatusTone.neutral,
    this.needsYou = 0,
    this.onTitleTap,
    this.titleTapLabel,
    this.actions = const [],
    this.accessory,
    this.accessoryShown = true,
    this.menu = const [],
    this.brand = false,
    this.exit = KitTopBarExit.auto,
    this.onExit,
    this.titleKey,
    this.exitKey,
    this.menuKey,
    this.menuLabel,
    this.scope,
    this.centred = false,
  }) : controls = null,
       assert(
         onTitleTap == null || (titleTapLabel ?? '') != '',
         'KitTopBar: a switcher title (onTitleTap) needs a titleTapLabel',
       ),
       assert(
         !brand || exit == KitTopBarExit.none || exit == KitTopBarExit.auto,
         'KitTopBar: brand only at a root (exit none or auto)',
       );

  /// The shell's top controls (compact and medium): KitShellControls in its
  /// bar layout, plus the page actions. No title: the dock names the tab and
  /// the tab's body shows its own largeTitle. The other fields take their
  /// neutral values (title '', exit none, brand false, needsYou 0).
  const KitTopBar.shell({
    super.key,
    required KitShellControls this.controls,
    this.actions = const [],
    this.menu = const [],
    this.menuKey,
    this.menuLabel,
  }) : title = '',
       subtitle = null,
       subtitleTail = null,
       subtitleTone = AppStatusTone.neutral,
       needsYou = 0,
       onTitleTap = null,
       titleTapLabel = null,
       brand = false,
       exit = KitTopBarExit.none,
       onExit = null,
       titleKey = null,
       exitKey = null,
       accessory = null,
       accessoryShown = true,
       scope = null,
       centred = false;

  /// A sheet-like header for a page that makes one new thing (New
  /// conversation): the title centred in title size between its exit and
  /// the same room on the other side. No actions, menu or scope.
  final bool centred;

  /// Non-null only for [KitTopBar.shell].
  final KitShellControls? controls;

  /// The place, in the person's words (COPY-10).
  final String title;

  /// One line of state: "Working · 2 min", the server. Words from the one
  /// status source for the entity, never hand-built.
  final String? subtitle;

  /// The subtitle's last words ("Next: review"), drawn only while the whole
  /// line fits (the state is never cut for them); always read aloud.
  final String? subtitleTail;
  final AppStatusTone subtitleTone;

  /// Greater than 0: the subtitle starts with [KitNeedsYou.span]; a
  /// switcher title carries [KitNeedsYou.badge].
  final int needsYou;

  /// The title is a switcher (project, conversation): chevron and button
  /// semantics.
  final VoidCallback? onTitleTap;

  /// What the switcher does: "Switch project".
  final String? titleTapLabel;

  /// Most important first; each has an icon, none is destructive.
  final List<KitAction> actions;

  /// A small status part kept in the bar next to the overflow whatever the
  /// width (the chat's context ring): shown before the ⋯ button, never
  /// moved into it. Its own tap target is its own business.
  final Widget? accessory;

  /// Whether the accessory shows now. Flipping it fades the accessory in or
  /// out and opens or closes its slot (no jump); false from the start
  /// builds nothing.
  final bool accessoryShown;

  /// The overflow's own entries, after the actions that did not fit.
  final List<KitMenuItem> menu;

  /// The product mark in place of the title (root pages only).
  final bool brand;

  /// The chip under the title (and the subtitle) naming what the page
  /// belongs to, with its menu. Null shows none.
  final KitTopBarScope? scope;
  final KitTopBarExit exit;

  /// Overrides the pop (the shell's nested Files back).
  final VoidCallback? onExit;
  final Key? titleKey;
  final Key? exitKey;
  final Key? menuKey;

  /// What the overflow holds, when [menu] is one thing's menu: the
  /// button's tooltip and the menu's name ("Conversation menu"). Default
  /// "More".
  final String? menuLabel;

  /// The exit [exit] resolves to at [context]'s route.
  static KitTopBarExit resolveExit(BuildContext context, KitTopBarExit exit) {
    if (exit != KitTopBarExit.auto) return exit;
    // A detail or side pane of KitScreen.twoPane/threePane has no Back
    // (KitScreen.md §6, KitTopBar.md test 2).
    if (KitScreen.inPane(context)) return KitTopBarExit.none;
    final route = ModalRoute.of(context);
    if (route == null) return KitTopBarExit.none;
    if (route is PageRoute && route.fullscreenDialog) {
      return KitTopBarExit.close;
    }
    if (!route.isFirst && Navigator.of(context).canPop()) {
      return KitTopBarExit.back;
    }
    return KitTopBarExit.none;
  }

  @override
  Widget build(BuildContext context) {
    assert(_actionsHaveIcons(actions), 'KitTopBar: every action needs an icon');
    assert(
      _noDestructiveActions(actions),
      'KitTopBar: a destructive act goes in menu, last, and confirms '
      '(KIT-28), never in actions',
    );
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final window = KitLayout.isShort(context)
        ? KitWindow.compact
        : KitLayout.windowOf(context);
    final controls = this.controls;
    final minHeight = tokens.minTarget + 2 * tokens.space1;

    if (controls != null) {
      final trailing = _KitTopBarActions(
        actions: actions,
        menu: menu,
        window: window == KitWindow.compact
            ? KitWindow.compact
            : KitWindow.medium,
        menuKey: menuKey,
        menuLabel: menuLabel,
        reserved: 0,
      );
      // The concepts' room under the status bar: space4 above the pill.
      return Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.gutter,
          end: tokens.gutter,
          top: tokens.space4,
          bottom: tokens.space1,
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Row(
            children: [
              Expanded(child: controls),
              if (actions.isNotEmpty || menu.isNotEmpty) ...[
                SizedBox(width: tokens.space2),
                trailing,
              ],
            ],
          ),
        ),
      );
    }

    final resolved = resolveExit(context, exit);
    final brand = this.brand && resolved == KitTopBarExit.none;
    void leave() {
      final onExit = this.onExit;
      if (onExit != null) {
        onExit();
      } else {
        Navigator.of(context).maybePop();
      }
    }

    Widget exitButton(IconData icon, String label) => KitIconButton(
      key: exitKey,
      icon: icon,
      tooltip: label,
      onPressed: leave,
    );

    if (centred) {
      assert(
        actions.isEmpty &&
            menu.isEmpty &&
            accessory == null &&
            scope == null &&
            onTitleTap == null &&
            !brand,
        'KitTopBar: a centred title stands alone beside its exit',
      );
      return _KitCentredBar(
        title: title,
        titleKey: titleKey,
        back: resolved == KitTopBarExit.back
            ? exitButton(AppIconography.back, l10n.kitTopBarBack)
            : null,
        close: resolved == KitTopBarExit.close
            ? exitButton(AppIconography.close, l10n.kitTopBarClose)
            : null,
      );
    }

    final toolbar =
        window == KitWindow.large ||
        (window == KitWindow.expanded && KitLayout.finePointer(context));
    // The back arrow, the title's first line and the actions share one
    // centre (owner, 2026-10-27: "Why aren't these aligned to the middle of
    // each other?"): the title's line, not the title block with the lines
    // under it. A line shorter than a touch target moves down to the
    // buttons' centre; a taller one (large text) moves the buttons down.
    final titleLine = brand ? null : _KitTopBarTitle.lineCentre(context, title);
    final lift = titleLine == null ? 0.0 : tokens.minTarget / 2 - titleLine;
    final titleTop = lift > 0 ? lift : 0.0;
    final controlsTop = lift < 0 ? -lift : 0.0;
    Widget control(Widget child) => controlsTop == 0
        ? child
        : Padding(
            padding: EdgeInsetsDirectional.only(top: controlsTop),
            child: child,
          );
    final bar = LayoutBuilder(
      builder: (context, constraints) {
        // The buttons sit on the title's first line. A tall title block
        // (large text, the server and project lines under it) used to centre
        // them against itself, which left the title above the back arrow.
        final row = Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (resolved == KitTopBarExit.back) ...[
              control(exitButton(AppIconography.back, l10n.kitTopBarBack)),
              // The title's words start on the page's text rail (56 dp,
              // KitTokens.textRail), where the rows' words below it do; a
              // switcher's tappable pads its words by space2 itself.
              SizedBox(
                width: math.max(
                  0.0,
                  tokens.textRail -
                      tokens.minTarget -
                      (onTitleTap == null ? 0 : tokens.space2),
                ),
              ),
            ] else
              // No exit: the title starts on the gutter, like the page's
              // first content row. A switcher's tappable pads its words by
              // space2, so its box starts that much earlier.
              SizedBox(
                width: onTitleTap == null
                    ? tokens.gutter
                    : tokens.gutter - tokens.space2,
              ),
            Expanded(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: tokens.minTarget),
                child: _KitTopBarTitle(
                  title: title,
                  subtitle: subtitle,
                  subtitleTail: subtitleTail,
                  subtitleTone: subtitleTone,
                  needsYou: needsYou,
                  onTitleTap: onTitleTap,
                  titleTapLabel: titleTapLabel,
                  brand: brand,
                  titleKey: titleKey,
                  scope: scope,
                  lineTop: titleLine == null ? null : titleTop,
                ),
              ),
            ),
            if (actions.isNotEmpty || menu.isNotEmpty || accessory != null) ...[
              SizedBox(width: tokens.space2),
              control(
                _KitTopBarActions(
                  actions: actions,
                  accessory: accessory,
                  accessoryShown: accessoryShown,
                  menu: menu,
                  window: window,
                  menuKey: menuKey,
                  menuLabel: menuLabel,
                  // The title keeps at least half the bar (KitTopBar.md).
                  reserved: constraints.maxWidth / 2,
                  maxWidth: constraints.maxWidth,
                ),
              ),
            ],
            if (resolved == KitTopBarExit.close) ...[
              SizedBox(width: tokens.space1),
              control(exitButton(AppIconography.close, l10n.kitTopBarClose)),
            ],
            SizedBox(width: _endRoom(context, tokens, resolved, window)),
          ],
        );
        return ConstrainedBox(
          constraints: BoxConstraints(minHeight: minHeight),
          child: Padding(
            padding: EdgeInsets.symmetric(vertical: tokens.space1),
            child: row,
          ),
        );
      },
    );
    if (toolbar) {
      // On PC the bar is the pane's toolbar: flat on the ground with a
      // hairline below. It paints no ground of its own: the page's live
      // background shows through it.
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [bar, const KitDivider()],
      );
    }
    // No band at rest: the bar sits on the page ground (KitScreen paints the
    // live background behind it), so a strong look runs up under the title.
    return bar;
  }

  /// The room after the bar's last button: enough for the ink of its glyph
  /// (the ⋯ or the close) to end on the gutter, the page's right rail,
  /// rather than 4 dp inside it (impeccable layout pass, 2026-10-28).
  /// Another last action keeps space1.
  double _endRoom(
    BuildContext context,
    KitTokens tokens,
    KitTopBarExit resolved,
    KitWindow window,
  ) {
    // The ⋯ ends the bar when there is a menu, a parked action, or more
    // ready actions than the window shows (_KitTopBarActions' rule).
    final ready = actions.where((a) => a.enabled && a.visible).length;
    final overflow =
        menu.isNotEmpty ||
        actions.any((a) => !a.enabled && a.visible) ||
        switch (window) {
          KitWindow.compact => ready > 1,
          KitWindow.medium => ready > 3,
          _ => false,
        };
    final last = resolved == KitTopBarExit.close
        ? AppIconography.close
        : overflow
        ? AppIconography.more
        : null;
    if (last == null) return tokens.space1;
    const glyph = AppIconography.actionSize;
    return KitOptical.endPad(
      context,
      last,
      glyph,
      tokens.gutter - (tokens.minTarget - glyph) / 2,
    );
  }
}

/// The title block: header semantics naming the route, the title (up to two
/// lines, never clamped below 2.0 text), the subtitle under it, and the
/// switcher's chevron and badge.
class _KitTopBarTitle extends StatelessWidget {
  const _KitTopBarTitle({
    required this.title,
    required this.subtitle,
    required this.subtitleTail,
    required this.subtitleTone,
    required this.needsYou,
    required this.onTitleTap,
    required this.titleTapLabel,
    required this.brand,
    required this.titleKey,
    required this.scope,
    this.lineTop,
  });

  /// Room above the title's first line, so its centre is the buttons'
  /// centre (KitTopBar.build). Null: the brand title, centred as a block.
  final double? lineTop;

  /// Where the title's first line looks centred, from the line's top: the
  /// middle of its letters (the font's ascent to descent), which sits a
  /// little below the middle of a line with extra leading.
  static double lineCentre(BuildContext context, String title) {
    final painter = TextPainter(
      text: TextSpan(
        text: title.isEmpty ? ' ' : title,
        style: KitText.styleOf(context, KitTextRole.headline, pageTitle: true),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      locale: Localizations.maybeLocaleOf(context),
      maxLines: 1,
      ellipsis: '\u2026',
    )..layout();
    final text = title.isEmpty ? ' ' : title;
    final first = text.characters.first.length;
    final boxes = painter.getBoxesForSelection(
      TextSelection(baseOffset: 0, extentOffset: first),
    );
    final centre = boxes.isEmpty
        ? painter.height / 2
        : (boxes.first.top + boxes.first.bottom) / 2;
    painter.dispose();
    return centre;
  }

  final String title;
  final String? subtitle;
  final String? subtitleTail;
  final AppStatusTone subtitleTone;
  final int needsYou;
  final VoidCallback? onTitleTap;
  final String? titleTapLabel;
  final bool brand;
  final Key? titleKey;
  final KitTopBarScope? scope;

  static KitTextTone _tone(AppStatusTone tone) => switch (tone) {
    AppStatusTone.neutral => KitTextTone.secondary,
    AppStatusTone.progress => KitTextTone.accent,
    AppStatusTone.ok => KitTextTone.success,
    AppStatusTone.attention => KitTextTone.attention,
    AppStatusTone.failure => KitTextTone.primary,
  };

  /// The title: one line on a phone, with the full words on a tap; two
  /// lines when the text is large or the bar is wide. A new title crosses
  /// over from the old one.
  Widget _titleText(BuildContext context) {
    final reduced = KitMotion.reduced(context);
    final phone = KitLayout.windowOf(context) == KitWindow.compact;
    final large = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    final oneLine = phone && !large;
    final text = AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.quick,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      layoutBuilder: (current, previous) => Stack(
        alignment: AlignmentDirectional.centerStart,
        children: [...previous, ?current],
      ),
      child: KitText(
        title,
        key: ValueKey(title),
        role: KitTextRole.headline,
        pageTitle: true,
        maxLines: oneLine ? 1 : 2,
        overflow: TextOverflow.ellipsis,
      ),
    );
    if (!oneLine || title.isEmpty || onTitleTap != null) return text;
    return Tooltip(
      message: title,
      triggerMode: TooltipTriggerMode.tap,
      excludeFromSemantics: true,
      child: text,
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final reduced = KitMotion.reduced(context);
    final subtitle = this.subtitle;
    final switcher = onTitleTap != null;
    final hasSubtitle =
        (subtitle != null && subtitle.isNotEmpty) || needsYou > 0;

    // The subtitle's state is part of the title's label; a needs-you count
    // reads in KitNeedsYou's words. On a switcher the badge adds the count.
    final needsYouWords = needsYou > 0 && !switcher
        ? l10n.kitNeedsYouSpan(needsYou).replaceFirst(RegExp(r'\s*·\s*$'), '')
        : null;
    final tail = subtitleTail;
    final label = [
      title,
      ?needsYouWords,
      if (subtitle != null && subtitle.isNotEmpty) subtitle,
      if (tail != null && tail.isNotEmpty) tail,
      if (switcher) titleTapLabel!,
    ].join(', ');

    final Widget titleText = brand
        ? Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // A small mark beside the name: the wordmark leads, quietly.
              const KitBrandMark(),
              SizedBox(width: tokens.space2),
              Flexible(
                child: KitText(
                  title,
                  role: KitTextRole.headline,
                  pageTitle: true,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          )
        : _titleText(context);

    final subtitleLine = hasSubtitle
        ? AnimatedSwitcher(
            duration: reduced ? Duration.zero : KitMotion.quick,
            switchInCurve: KitMotion.enter,
            switchOutCurve: KitMotion.exit,
            layoutBuilder: (current, previous) => Stack(
              alignment: AlignmentDirectional.centerStart,
              children: [...previous, ?current],
            ),
            child: _KitTopBarTailLine(
              key: ValueKey('${needsYou > 0 && !switcher}|$subtitle|$tail'),
              subtitle: subtitle ?? '',
              tail: tail,
              needsYou: switcher ? 0 : needsYou,
              style: KitText.styleOf(
                context,
                KitTextRole.secondary,
                tone: _tone(subtitleTone),
              ),
            ),
          )
        : null;

    final lineTop = this.lineTop;
    // The block starts at the top, its first line lowered onto the buttons'
    // centre; only the brand title is centred as a whole.
    final blockAlignment = lineTop == null
        ? AlignmentDirectional.centerStart
        : AlignmentDirectional.topStart;
    Widget block = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (lineTop != null && lineTop > 0) SizedBox(height: lineTop),
        if (switcher)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(child: titleText),
              SizedBox(width: tokens.space1),
              Icon(
                AppIconography.chevronDown,
                size: tokens.iconSize(context, tokens.smallIconSize),
                color: tokens.roles.text2,
              ),
            ],
          )
        else
          titleText,
        ?subtitleLine,
      ],
    );

    final scope = this.scope;
    if (!switcher) {
      final titleBlock = Semantics(
        key: titleKey,
        container: true,
        header: true,
        namesRoute: true,
        label: label,
        child: ExcludeSemantics(
          child: Align(alignment: blockAlignment, child: block),
        ),
      );
      if (scope == null) return titleBlock;
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // The title sits where the other pages' titles do: its line on
          // the buttons' centre.
          if (lineTop == null)
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space2),
              child: titleBlock,
            )
          else
            titleBlock,
          _KitTopBarScopeLine(scope: scope),
        ],
      );
    }
    // A switcher pads its words inside its own target; the lowered first
    // line already leaves the room above it.
    final switcherTop = lineTop == null ? tokens.space1 : 0.0;
    block = Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.space2,
        end: tokens.space2,
        top: switcherTop,
        bottom: tokens.space1,
      ),
      child: ExcludeSemantics(child: block),
    );
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: KitNeedsYou.badge(
        count: needsYou,
        child: Semantics(
          header: true,
          namesRoute: true,
          child: KitTappable(
            tappableKey: titleKey,
            onTap: onTitleTap,
            label: label,
            shape: KitShape.button,
            surface: KitSurfaceLevel.ground,
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.minTarget),
              // The title's line sits on the line of the back button and
              // the actions.
              child: Align(
                alignment: blockAlignment,
                widthFactor: 1,
                heightFactor: 1,
                child: block,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The actions and overflow at the end of the bar, laid out for [window]
/// (K2 §8.2 row KitTopBar).
class _KitTopBarActions extends StatelessWidget {
  const _KitTopBarActions({
    required this.actions,
    required this.menu,
    required this.window,
    required this.menuKey,
    required this.reserved,
    this.accessory,
    this.accessoryShown = true,
    this.menuLabel,
    this.maxWidth = 0,
  });

  final List<KitAction> actions;
  final Widget? accessory;
  final bool accessoryShown;
  final List<KitMenuItem> menu;
  final KitWindow window;
  final Key? menuKey;
  final String? menuLabel;

  /// The width the title keeps (expanded and up).
  final double reserved;
  final double maxWidth;

  static KitMenuItem _item(KitAction action) {
    final copyText = action.copyText;
    if (copyText != null) {
      return KitMenuItem.copy(
        label: action.label,
        text: copyText,
        icon: action.icon ?? AppIconography.copy,
        shortcut: action.shortcut,
        redact: action.redact,
      );
    }
    final onPressed = action.onPressed;
    return KitMenuItem(
      label: action.label,
      onSelected: onPressed ?? _noop,
      icon: action.icon,
      enabled: onPressed != null,
      disabledReason: action.disabledReason,
      shortcut: action.shortcut,
    );
  }

  static Widget _icon(KitAction action) {
    final copyText = action.copyText;
    if (copyText != null) {
      return KitIconButton.copy(
        key: action.key,
        text: copyText,
        tooltip: action.label,
        shortcut: action.shortcut,
        redact: action.redact,
      );
    }
    return KitIconButton(
      key: action.key,
      icon: action.icon!,
      tooltip: action.label,
      onPressed: action.onPressed,
      working: action.working,
      shortcut: action.shortcut,
    );
  }

  /// A labelled tertiary button's width, estimated from its words, icon and
  /// (on a fine pointer) shortcut hint.
  static double _labelledWidth(
    BuildContext context,
    KitAction action,
    bool fine,
  ) {
    final tokens = KitTokens.of(context);
    final text = [
      action.label,
      if (fine && action.shortcut != null) action.shortcut!,
    ].join('   ');
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: KitText.styleOf(context, KitTextRole.button),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final width =
        painter.width +
        tokens.iconSize(context, tokens.smallIconSize) +
        tokens.space2 * 4;
    painter.dispose();
    return width;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // A disabled action is not shown in the bar; it waits in the overflow
    // with its reason (STATE-8).
    final ready = [
      for (final action in actions)
        if (action.enabled && action.visible) action,
    ];
    final parked = [
      for (final action in actions)
        if (!action.enabled && action.visible) action,
    ];
    // An act that has nothing to do right now keeps its place in the row,
    // folded to nothing, so it can fade in and out without moving the rest.
    final resting = [
      for (final action in actions)
        if (!action.visible && action.enabled && action.key != null) action,
    ];

    var labelled = <KitAction>[];
    var icons = <KitAction>[];
    if (window.isWide) {
      final fine = KitLayout.finePointer(context);
      final overflowWidth = tokens.minTarget + tokens.space1;
      var budget = maxWidth - reserved - overflowWidth;
      for (final action in ready) {
        final width = _labelledWidth(context, action, fine) + tokens.space1;
        if (width > budget) break;
        budget -= width;
        labelled.add(action);
      }
      if (labelled.isEmpty && ready.isNotEmpty) {
        // 200 % text: labelled actions fall back to icons, then overflow.
        icons = ready.take(2).toList();
        labelled = [];
      }
    } else if (window == KitWindow.medium) {
      final allFit = menu.isEmpty && parked.isEmpty && ready.length <= 3;
      icons = ready.take(allFit ? 3 : 2).toList();
    } else {
      // Compact: the first action, then the overflow with the rest.
      icons = ready.take(1).toList();
    }
    final shown = {...labelled, ...icons};
    final overflow = [
      for (final action in ready)
        if (!shown.contains(action)) _item(action),
      for (final action in parked) _item(action),
      ...menu,
    ];

    // One small gap between neighbours, none after the last one shown. It
    // sits inside each part so a part that is hidden takes its gap with it.
    final Object? last = overflow.isNotEmpty
        ? null
        : accessory != null && accessoryShown
        ? #accessory
        : icons.isNotEmpty
        ? icons.last
        : labelled.isNotEmpty
        ? labelled.last
        : null;
    EdgeInsetsDirectional gap(Object part) => identical(part, last)
        ? EdgeInsetsDirectional.zero
        : EdgeInsetsDirectional.only(end: tokens.space1);
    final children = <Widget>[
      for (final action in labelled)
        Padding(
          padding: gap(action),
          child: KitButton.fromAction(action, role: KitButtonRole.tertiary),
        ),
      for (final action in actions)
        if (icons.contains(action) || resting.contains(action))
          KitPresence(
            key: ValueKey('kit-top-bar-presence:${action.label}'),
            show: action.visible,
            child: Padding(padding: gap(action), child: _icon(action)),
          ),
      if (accessory != null)
        KitPresence(
          key: const ValueKey('kit-top-bar-accessory'),
          show: accessoryShown,
          child: Padding(padding: gap(#accessory), child: accessory),
        ),
      if (overflow.isNotEmpty)
        Builder(
          builder: (anchor) => KitIconButton(
            key: menuKey,
            icon: AppIconography.more,
            tooltip: menuLabel ?? l10n.kitMore,
            onPressed: () => showKitMenu(
              anchor,
              items: overflow,
              semanticsLabel: menuLabel ?? l10n.kitTopBarMore,
            ),
          ),
        ),
    ];
    return Row(mainAxisSize: MainAxisSize.min, children: children);
  }
}

enum KitShellControlsLayout {
  /// One row: server pill at the start; search and, on a phone, the
  /// Settings gear at the end.
  bar,

  /// The PC sidebar header: server pill, then the project switcher, then a
  /// full-width search button that looks like a field ("Search").
  sidebar,
}

/// The shell's top controls (VL §6): the server pill, search and, on a phone
/// (no navigation bar, owner 2026-10-20), the Settings gear.
///
/// In the bar the pill is one line at the start ("name · state", owner
/// concepts 2026-10-22; two lines only when the word cannot share the line);
/// search and the gear are round icon buttons at the end.
///
/// States: connected, reconnecting (word + working mark), not answering,
/// needs-you badge. In the bar the status word is always visible (STATE-9).
/// In the sidebar the name is never cut and the status is a second line;
/// "Connected" is left to the green dot there, and stays in the label.
class KitShellControls extends StatelessWidget {
  const KitShellControls({
    super.key,
    required this.server,
    required this.serverStatus,
    this.serverTone = AppStatusTone.neutral,
    required this.onServer,
    this.needsYou = 0,
    this.project,
    this.onProject,
    this.onSearch,
    this.onSettings,
    this.showServer = true,
    this.layout = KitShellControlsLayout.bar,
    this.serverKey,
    this.projectKey,
    this.searchKey,
    this.settingsKey,
  });

  /// The server's display name.
  final String server;

  /// The status word: "Connected", "Reconnecting". Always in the label; on
  /// screen except for [AppStatusTone.ok] in the sidebar layout.
  final String serverStatus;
  final AppStatusTone serverTone;

  /// Opens the server switcher (a KitSheet).
  final VoidCallback onServer;

  /// Requests on other servers: [KitNeedsYou.badge] on the pill.
  final int needsYou;

  /// Sidebar layout only: the project switcher's title.
  final String? project;
  final VoidCallback? onProject;

  /// Null hides search.
  final VoidCallback? onSearch;

  /// Bar layout only: opens Settings. Null hides the gear (a window with a
  /// rail or sidebar names Settings there).
  final VoidCallback? onSettings;

  /// False leaves the server pill out of the bar layout, for a destination
  /// that has no current server (Settings, SC3). The sidebar keeps it.
  final bool showServer;
  final KitShellControlsLayout layout;
  final Key? serverKey;
  final Key? projectKey;
  final Key? searchKey;
  final Key? settingsKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final sidebar = layout == KitShellControlsLayout.sidebar;
    final radius = BorderRadius.circular(tokens.navRadius);
    final onSearch = this.onSearch;
    final onSettings = this.onSettings;
    final project = this.project;
    final onProject = this.onProject;

    final pillButton = KitTappable(
      tappableKey: serverKey,
      onTap: onServer,
      label: [
        KitBidi.auto(server),
        serverStatus,
        l10n.kitTopBarSwitchServer,
      ].join(', '),
      shape: KitShape.pill,
      surface: KitSurfaceLevel.surface2,
      child: ExcludeSemantics(
        // In the bar the pill's words stop growing past 1.5x (like a page
        // title), so at 2x on a narrow phone the name is read beside the
        // gear instead of being cut to "Lap…".
        child: KitText.capScale(
          max: sidebar ? double.infinity : 1.5,
          child: _KitServerPillContent(
            server: server,
            status: serverStatus,
            tone: serverTone,
            expand: sidebar,
          ),
        ),
      ),
    );
    final pill = KitNeedsYou.badge(
      count: needsYou,
      child: KitGlass(borderRadius: radius, child: pillButton),
    );

    if (!sidebar) {
      // The end: search, then the gear, each on a circle of its own the
      // pill's height, one gap apart (the owner's concepts, 2026-10-22).
      final tools = [
        if (onSearch != null)
          KitIconButton(
            key: searchKey,
            icon: AppIconography.search,
            tooltip: l10n.kitTopBarSearch,
            // Optically level with the gear beside it: the gear's teeth
            // read smaller than the lens at the same size.
            size: onSettings != null ? 22 : 24,
            round: true,
            onPressed: onSearch,
          ),
        if (onSettings != null)
          KitIconButton(
            key: settingsKey,
            icon: AppIconography.settingsGear,
            tooltip: l10n.librarySettingsTitle,
            size: 24,
            round: true,
            motion: KitIconMotion.turn,
            onPressed: onSettings,
          ),
      ];
      return Row(
        children: [
          if (showServer)
            Flexible(
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: pill,
              ),
            )
          else
            const Spacer(),
          if (tools.isNotEmpty) ...[
            SizedBox(width: tokens.space2),
            Row(
              mainAxisSize: MainAxisSize.min,
              spacing: tokens.space4,
              children: tools,
            ),
          ],
        ],
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: tokens.space2,
      children: [
        pill,
        if (project != null && onProject != null)
          KitTappable(
            tappableKey: projectKey,
            onTap: onProject,
            label: [
              KitBidi.auto(project),
              l10n.kitTopBarSwitchProject,
            ].join(', '),
            shape: KitShape.button,
            surface: KitSurfaceLevel.ground,
            child: ExcludeSemantics(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: tokens.minTarget),
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.space3,
                    vertical: tokens.space1,
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: KitText(
                          KitBidi.auto(project),
                          role: KitTextRole.headline,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      SizedBox(width: tokens.space1),
                      Icon(
                        AppIconography.chevronDown,
                        size: tokens.iconSize(context, tokens.smallIconSize),
                        color: tokens.roles.text2,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        if (onSearch != null)
          KitGlass(
            borderRadius: radius,
            child: KitTappable(
              tappableKey: searchKey,
              onTap: onSearch,
              label: l10n.kitTopBarSearch,
              shape: KitShape.pill,
              surface: KitSurfaceLevel.surface2,
              child: ExcludeSemantics(
                child: ConstrainedBox(
                  constraints: BoxConstraints(minHeight: tokens.minTarget),
                  child: Padding(
                    padding: EdgeInsetsDirectional.symmetric(
                      horizontal: tokens.space3,
                    ),
                    child: Row(
                      children: [
                        Icon(
                          AppIconography.search,
                          size: tokens.iconSize(context, tokens.smallIconSize),
                          color: tokens.roles.text2,
                        ),
                        SizedBox(width: tokens.space2),
                        Expanded(
                          child: KitText(
                            l10n.kitTopBarSearch,
                            // Semibold: at 13 px a lighter weight on the
                            // white pill reads under AA once sampled (G5).
                            role: KitTextRole.label,
                            tone: KitTextTone.secondary,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
