import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'glass/kit_glass.dart';
import 'kit_screen.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_chip.dart';
import 'kit_divider.dart';
import 'kit_icon.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_needs_you.dart';
import 'kit_status_mark.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

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
    required this.items,
    this.icon,
    this.chipKey,
    this.menuLabel,
  });

  /// The name shown on the chip; wrap a name the person or server chose
  /// with `KitBidi.auto`.
  final String label;

  /// What a screen reader says: "Project IPTV_King, opens a menu".
  final String semanticsLabel;

  /// The menu the chip opens; at least one entry.
  final List<KitMenuItem> items;
  final IconData? icon;
  final Key? chipKey;

  /// The menu's name for a screen reader.
  final String? menuLabel;
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
    this.subtitleTone = AppStatusTone.neutral,
    this.needsYou = 0,
    this.onTitleTap,
    this.titleTapLabel,
    this.actions = const [],
    this.menu = const [],
    this.brand = false,
    this.exit = KitTopBarExit.auto,
    this.onExit,
    this.titleKey,
    this.exitKey,
    this.menuKey,
    this.menuLabel,
    this.scope,
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
       subtitleTone = AppStatusTone.neutral,
       needsYou = 0,
       onTitleTap = null,
       titleTapLabel = null,
       brand = false,
       exit = KitTopBarExit.none,
       onExit = null,
       titleKey = null,
       exitKey = null,
       scope = null;

  /// Non-null only for [KitTopBar.shell].
  final KitShellControls? controls;

  /// The place, in the person's words (COPY-10).
  final String title;

  /// One line of state: "Working · 2 min", the server. Words from the one
  /// status source for the entity, never hand-built.
  final String? subtitle;
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
      return Padding(
        padding: EdgeInsetsDirectional.symmetric(
          horizontal: tokens.gutter,
          vertical: tokens.space1,
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

    final toolbar =
        window == KitWindow.large ||
        (window == KitWindow.expanded && KitLayout.finePointer(context));
    final bar = LayoutBuilder(
      builder: (context, constraints) {
        final row = Row(
          children: [
            if (resolved == KitTopBarExit.back) ...[
              exitButton(AppIconography.back, l10n.kitTopBarBack),
              SizedBox(width: tokens.space1),
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
              child: _KitTopBarTitle(
                title: title,
                subtitle: subtitle,
                subtitleTone: subtitleTone,
                needsYou: needsYou,
                onTitleTap: onTitleTap,
                titleTapLabel: titleTapLabel,
                brand: brand,
                titleKey: titleKey,
                scope: scope,
              ),
            ),
            if (actions.isNotEmpty || menu.isNotEmpty) ...[
              SizedBox(width: tokens.space2),
              _KitTopBarActions(
                actions: actions,
                menu: menu,
                window: window,
                menuKey: menuKey,
                menuLabel: menuLabel,
                // The title keeps at least half the bar (KitTopBar.md).
                reserved: constraints.maxWidth / 2,
                maxWidth: constraints.maxWidth,
              ),
            ],
            if (resolved == KitTopBarExit.close) ...[
              SizedBox(width: tokens.space1),
              exitButton(AppIconography.close, l10n.kitTopBarClose),
            ],
            SizedBox(width: tokens.space1),
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
      // hairline below. Glass stays on the floating navigation layer.
      return ColoredBox(
        color: tokens.roles.ground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [bar, const KitDivider()],
        ),
      );
    }
    return ColoredBox(color: tokens.roles.ground, child: bar);
  }
}

/// The title block: header semantics naming the route, the title (up to two
/// lines, never clamped below 2.0 text), the subtitle under it, and the
/// switcher's chevron and badge.
class _KitTopBarTitle extends StatelessWidget {
  const _KitTopBarTitle({
    required this.title,
    required this.subtitle,
    required this.subtitleTone,
    required this.needsYou,
    required this.onTitleTap,
    required this.titleTapLabel,
    required this.brand,
    required this.titleKey,
    required this.scope,
  });

  final String title;
  final String? subtitle;
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
    AppStatusTone.failure => KitTextTone.danger,
  };

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
    final label = [
      title,
      ?needsYouWords,
      if (subtitle != null && subtitle.isNotEmpty) subtitle,
      if (switcher) titleTapLabel!,
    ].join(', ');

    final Widget titleText = brand
        ? Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const KitBrandMark(size: KitBrandMarkSize.mark),
              SizedBox(width: tokens.space2),
              Flexible(
                child: KitText(
                  title,
                  role: KitTextRole.headline,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          )
        : KitText(
            title,
            role: KitTextRole.headline,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          );

    final subtitleLine = hasSubtitle
        ? AnimatedSwitcher(
            duration: reduced ? Duration.zero : KitMotion.quick,
            switchInCurve: KitMotion.enter,
            switchOutCurve: KitMotion.exit,
            layoutBuilder: (current, previous) => Stack(
              alignment: AlignmentDirectional.centerStart,
              children: [...previous, ?current],
            ),
            child: KitText.rich(
              key: ValueKey('${needsYou > 0 && !switcher}|$subtitle'),
              TextSpan(
                children: [
                  if (needsYou > 0 && !switcher)
                    KitNeedsYou.span(context, count: needsYou),
                  if (subtitle != null && subtitle.isNotEmpty)
                    TextSpan(
                      text: subtitle,
                      style: KitText.styleOf(
                        context,
                        KitTextRole.secondary,
                        tone: _tone(subtitleTone),
                      ),
                    ),
                ],
              ),
              role: KitTextRole.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          )
        : null;

    Widget block = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
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
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: block,
          ),
        ),
      );
      if (scope == null) return titleBlock;
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          titleBlock,
          Builder(
            builder: (chipContext) => Semantics(
              container: true,
              label: scope.semanticsLabel,
              button: true,
              excludeSemantics: true,
              onTap: () => unawaited(
                showKitMenu(
                  chipContext,
                  items: scope.items,
                  semanticsLabel: scope.menuLabel,
                ),
              ),
              child: KitChip.action(
                key: scope.chipKey,
                label: scope.label,
                icon: scope.icon,
                onPressed: () => unawaited(
                  showKitMenu(
                    chipContext,
                    items: scope.items,
                    semanticsLabel: scope.menuLabel,
                  ),
                ),
              ),
            ),
          ),
        ],
      );
    }
    block = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.space2,
        vertical: tokens.space1,
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
              child: block,
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
    this.menuLabel,
    this.maxWidth = 0,
  });

  final List<KitAction> actions;
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
        if (action.enabled) action,
    ];
    final parked = [
      for (final action in actions)
        if (!action.enabled) action,
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

    final children = <Widget>[
      for (final action in labelled)
        KitButton.fromAction(action, role: KitButtonRole.tertiary),
      for (final action in icons) _icon(action),
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
    return Row(
      mainAxisSize: MainAxisSize.min,
      spacing: tokens.space1,
      children: children,
    );
  }
}

enum KitShellControlsLayout {
  /// One row: server pill at the start, search button at the end.
  bar,

  /// The PC sidebar header: server pill, then the project switcher, then a
  /// full-width search button that looks like a field ("Search").
  sidebar,
}

/// The glass top controls (VL §6): the server pill and search, dim glass
/// (they hold words) sharing one BackdropGroup provided by the shell. Each
/// gives under a finger (fluid glass). In the bar, pill and search are one
/// [KitGlass.pair]: while the page is scrolled ([KitGlass.scrolledOf]) search
/// slides up to the pill, one small gap from it; back at the top they pull
/// apart.
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
    this.layout = KitShellControlsLayout.bar,
    this.serverKey,
    this.projectKey,
    this.searchKey,
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
  final KitShellControlsLayout layout;
  final Key? serverKey;
  final Key? projectKey;
  final Key? searchKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final sidebar = layout == KitShellControlsLayout.sidebar;
    final radius = BorderRadius.circular(tokens.navRadius);
    final onSearch = this.onSearch;
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
        child: _KitServerPillContent(
          server: server,
          status: serverStatus,
          tone: serverTone,
          expand: sidebar,
        ),
      ),
    );
    final pill = KitNeedsYou.badge(
      count: needsYou,
      child: KitGlass(borderRadius: radius, child: pillButton),
    );

    if (!sidebar) {
      if (onSearch == null) {
        return Align(alignment: AlignmentDirectional.centerStart, child: pill);
      }
      return KitGlass.pair(
        joined: KitGlass.scrolledOf(context),
        borderRadius: radius,
        leading: KitNeedsYou.badge(count: needsYou, child: pillButton),
        trailing: KitIconButton(
          key: searchKey,
          icon: AppIconography.search,
          tooltip: l10n.kitTopBarSearch,
          onPressed: onSearch,
        ),
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
                            role: KitTextRole.secondary,
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

/// The pill's look: the state mark, the server's name, its status word and
/// the switcher chevron (which never mirrors: it points down).
class _KitServerPillContent extends StatelessWidget {
  const _KitServerPillContent({
    required this.server,
    required this.status,
    required this.tone,
    required this.expand,
  });

  final String server;
  final String status;
  final AppStatusTone tone;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final dotColor = switch (tone) {
      AppStatusTone.neutral => roles.text3,
      AppStatusTone.progress => roles.accent,
      AppStatusTone.ok => roles.success,
      AppStatusTone.attention => roles.attention,
      AppStatusTone.failure => roles.danger,
    };
    final Widget mark = tone == AppStatusTone.progress
        ? KitStatusMark(state: KitMarkState.working, label: status)
        : SizedBox.square(
            dimension: tokens.iconSize(context, tokens.smallIconSize),
            child: Center(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: dotColor,
                  shape: BoxShape.circle,
                ),
                child: SizedBox.square(dimension: tokens.space2),
              ),
            ),
          );
    // The PC sidebar (296 dp) never cuts the name: it wraps, and the status
    // takes a second line. "Connected" is left to the green dot there (the
    // pill's label still says it); Offline and Reconnecting keep the word.
    if (expand) {
      final word = tone == AppStatusTone.ok ? null : status;
      return ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.minTarget),
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            end: tokens.space2,
            top: tokens.space1,
            bottom: tokens.space1,
          ),
          child: Row(
            children: [
              mark,
              SizedBox(width: tokens.space2),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    KitText(KitBidi.auto(server), role: KitTextRole.rowTitle),
                    if (word != null)
                      KitText(
                        word,
                        role: KitTextRole.caption,
                        tone: KitTextTone.secondary,
                      ),
                  ],
                ),
              ),
              SizedBox(width: tokens.space1),
              Icon(
                AppIconography.chevronDown,
                size: tokens.iconSize(context, tokens.smallIconSize),
                color: roles.text2,
              ),
            ],
          ),
        ),
      );
    }
    // Keep the status in full (STATE-9). At large text it may be wider
    // than the whole pill, so let it wrap below the name instead; and at
    // large text the name never gives way to the status word ("127.…" at
    // 2.0, emulator QA F8): when the two do not fit on one line the status
    // goes under the name, which may take two lines.
    final label = LayoutBuilder(
      builder: (context, constraints) {
        final statusText = ' · $status';
        final scaler = MediaQuery.textScalerOf(context);
        final direction = Directionality.of(context);
        double widthOf(String text, KitTextRole role) {
          final painter = TextPainter(
            text: TextSpan(text: text, style: KitText.styleOf(context, role)),
            textDirection: direction,
            textScaler: scaler,
            maxLines: 1,
          )..layout();
          final width = painter.width;
          painter.dispose();
          return width;
        }

        final statusWidth = widthOf(statusText, KitTextRole.caption);
        final largeText = scaler.scale(1) >= 1.3;
        final stack =
            statusWidth + tokens.minTarget > constraints.maxWidth ||
            (largeText &&
                statusWidth +
                        widthOf(KitBidi.auto(server), KitTextRole.rowTitle) >
                    constraints.maxWidth);
        final name = KitText(
          KitBidi.auto(server),
          role: KitTextRole.rowTitle,
          maxLines: stack ? 2 : 1,
          overflow: TextOverflow.ellipsis,
        );
        if (stack) {
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              name,
              KitText(
                status,
                role: KitTextRole.caption,
                tone: KitTextTone.secondary,
              ),
            ],
          );
        }
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(child: name),
            KitText(
              statusText,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
              maxLines: 1,
            ),
          ],
        );
      },
    );
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.space3,
          end: tokens.space2,
          top: tokens.space1,
          bottom: tokens.space1,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            mark,
            SizedBox(width: tokens.space2),
            Flexible(child: label),
            SizedBox(width: tokens.space1),
            Icon(
              AppIconography.chevronDown,
              size: tokens.iconSize(context, tokens.smallIconSize),
              color: roles.text2,
            ),
          ],
        ),
      ),
    );
  }
}
