import 'package:flutter/widgets.dart';

import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_menu.dart';

/// What the conversation menu can do (slice-P10.2). The chat's title menu
/// and a Work row's menu offer the same entries; a row hands the ones that
/// need the open conversation to the chat ([ChatRouteArguments.menuAction]).
enum SessionMenuAction {
  changes,
  timeline,
  find,
  subagents,
  details,
  share,
  unshare,
  compact,
  fork,
  rename,
  continueOnComputer,
  continueOnPhone,
  archive,
  delete;

  /// The entry's key, `session-menu-<value>`, and the stable name tests and
  /// the route use.
  String get value => switch (this) {
    SessionMenuAction.continueOnComputer => 'continue-computer',
    SessionMenuAction.continueOnPhone => 'continue-phone',
    _ => name,
  };

  /// Whether the act needs the open conversation (its transcript, find
  /// bar or composer): a Work row opens the chat and hands it over.
  bool get needsConversation => switch (this) {
    SessionMenuAction.details ||
    SessionMenuAction.share ||
    SessionMenuAction.unshare ||
    SessionMenuAction.rename => false,
    _ => true,
  };
}

/// Which entries this conversation offers here. Built from the server's
/// [ServerCapabilities] (never the backend kind) plus what only the open
/// conversation knows.
@immutable
class SessionMenuOffer {
  const SessionMenuOffer({
    required this.changes,
    required this.timeline,
    required this.subagents,
    required this.sharing,
    required this.shared,
    required this.compact,
    required this.fork,
    required this.continueOnComputer,
    required this.continueOnPhone,
    this.archive = false,
    this.delete = false,
    this.hasPrompt = true,
  });

  /// The server's part; [hasPrompt] and [timeline] default to what a Work
  /// row can know (the conversation checks again when it opens).
  factory SessionMenuOffer.of(
    ServerCapabilities capabilities, {
    required bool shared,
    required bool savedServer,
    bool compact = true,
    bool timeline = true,
    bool hasPrompt = true,
  }) => SessionMenuOffer(
    changes: capabilities.sessionDiff,
    timeline: timeline,
    subagents: capabilities.projectManagement,
    sharing: capabilities.sessionShare,
    shared: shared,
    compact: compact && capabilities.sessionCompact,
    fork: capabilities.sessionFork,
    continueOnComputer: capabilities.cliSessionResume,
    continueOnPhone: savedServer,
    archive: capabilities.sessionArchive,
    delete: capabilities.sessionDelete,
    hasPrompt: hasPrompt,
  );

  final bool changes;
  final bool timeline;
  final bool subagents;
  final bool sharing;
  final bool shared;
  final bool compact;
  final bool fork;
  final bool continueOnComputer;
  final bool continueOnPhone;
  final bool archive;
  final bool delete;

  /// Compact and Fork act on prompts: before the first one they wait,
  /// dimmed, with the reason.
  final bool hasPrompt;
}

/// The conversation menu: "Go to" (Changes, Timeline, Find, Subagents,
/// Details) then "Do" (Share, Compact, Fork, Rename, Continue on computer,
/// Open on another phone), as [KitMenuItem]s under two [KitMenuGroup]
/// headings. The chat's title bar and a Work row's menu both use it, so the
/// two can never disagree (P10.2 "the same menu from the Work row").
///
/// [shortcuts] shows the PC accelerators the open conversation answers
/// (Find is Ctrl+F there); a Work row passes false. [explain] adds the one
/// line that says what a "Do" act does; a Work row, whose menu also holds
/// the row's own acts, leaves it to the labels so the menu fits a phone.
List<KitMenuItem> sessionMenuItems(
  AppLocalizations l10n,
  SessionMenuOffer offer, {
  required ValueChanged<SessionMenuAction> onSelected,
  bool shortcuts = false,
  bool explain = true,
}) {
  final goTo = KitMenuGroup(l10n.sessionMenuGoTo);
  final act = KitMenuGroup(l10n.sessionMenuDo);
  KitMenuItem item(
    SessionMenuAction action,
    String label,
    IconData icon,
    KitMenuGroup? group, {
    String? supporting,
    String? shortcut,
    bool enabled = true,
    bool destructive = false,
  }) => KitMenuItem(
    key: ValueKey('session-menu-${action.value}'),
    label: label,
    icon: icon,
    group: group,
    supporting: explain ? supporting : null,
    shortcut: shortcut,
    enabled: enabled,
    destructive: destructive,
    disabledReason: enabled ? null : l10n.sessionMenuNeedsPrompt,
    onSelected: () => onSelected(action),
  );
  return [
    if (offer.changes)
      item(
        SessionMenuAction.changes,
        l10n.chatUiChanges,
        AppIconography.review,
        goTo,
      ),
    if (offer.timeline)
      item(
        SessionMenuAction.timeline,
        l10n.chatUiTimeline,
        AppIconography.timeline,
        goTo,
      ),
    item(
      SessionMenuAction.find,
      l10n.sessionMenuFind,
      AppIconography.search,
      goTo,
      shortcut: shortcuts ? 'Ctrl+F' : null,
    ),
    if (offer.subagents)
      item(
        SessionMenuAction.subagents,
        l10n.sessionMenuSubagents,
        AppIconography.branch,
        goTo,
      ),
    item(
      SessionMenuAction.details,
      l10n.sessionMenuDetails,
      AppIconography.info,
      goTo,
    ),
    if (offer.sharing)
      offer.shared
          ? item(
              SessionMenuAction.unshare,
              l10n.chatUiStopSharing,
              AppIconography.networkOff,
              act,
              supporting: l10n.sessionMenuStopSharingHint,
            )
          : item(
              SessionMenuAction.share,
              l10n.chatUiShareSession,
              AppIconography.globe,
              act,
              supporting: l10n.sessionMenuShareHint,
            ),
    if (offer.compact)
      item(
        SessionMenuAction.compact,
        l10n.chatUiCompactContext,
        AppIconography.collapse,
        act,
        supporting: l10n.sessionMenuCompactHint,
        enabled: offer.hasPrompt,
      ),
    if (offer.fork)
      item(
        SessionMenuAction.fork,
        l10n.chatUiForkSession,
        AppIconography.fork,
        act,
        supporting: l10n.sessionMenuForkHint,
        enabled: offer.hasPrompt,
      ),
    item(
      SessionMenuAction.rename,
      l10n.chatUiRenameSession,
      AppIconography.edit,
      act,
    ),
    if (offer.continueOnComputer)
      item(
        SessionMenuAction.continueOnComputer,
        l10n.handoffUiComputerTitle,
        AppIconography.computer,
        act,
        supporting: l10n.sessionMenuContinueComputerHint,
      ),
    if (offer.continueOnPhone)
      item(
        SessionMenuAction.continueOnPhone,
        l10n.handoffUiPhoneTitle,
        AppIconography.qrCode,
        act,
        supporting: l10n.sessionMenuContinuePhoneHint,
      ),
    if (offer.archive)
      item(
        SessionMenuAction.archive,
        l10n.sessionMenuArchive,
        AppIconography.archive,
        act,
        supporting: l10n.sessionMenuArchiveHint,
      ),
    if (offer.delete)
      item(
        SessionMenuAction.delete,
        l10n.sessionMenuDelete,
        AppIconography.delete,
        // The kit sets destructive acts apart under a divider of their own.
        null,
        supporting: l10n.sessionMenuDeleteHint,
        destructive: true,
      ),
  ];
}
