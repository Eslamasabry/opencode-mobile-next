import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/profile_monitor.dart';
import '../../domain/session_title_text.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/profile_monitor.dart' show ProfileMonitor;
import '../../state/profiles.dart';
import '../app_theme.dart';
import '../navigation/attention_landing.dart' show chatLandingRoute;
import '../kit/kit_buttons.dart' show KitAction, KitActionBlock;
import '../kit/kit_dialog.dart';
import '../kit/kit_icon.dart';
import '../kit/kit_needs_you.dart';
import '../kit/kit_row.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../widgets/phone_server_card.dart' show serverDisplayName;
import 'chat/form_flow.dart';

/// Shared explicit route: revalidates profile, location and exact request before
/// displaying the existing resolver. It never answers from monitor metadata.
///
/// P4.2a: a permission, question or form lands on its card in its
/// conversation (never a sheet over a list); a check-in opens its
/// conversation, on its newest failed turn with [landOnFailure] (an Inbox
/// row for a failed run on another server).
Future<void> openMonitoredRequest(
  BuildContext context,
  ConnectionController controller,
  MonitoredRoute route, {
  bool landOnFailure = false,
}) async {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  if (controller.profile?.id != route.profileID &&
      controller.busySessions.isNotEmpty) {
    final profiles = controller.store.profiles;
    String nameOf(String? id) => serverDisplayName(
      profiles.where((p) => p.id == id).firstOrNull,
      l10n,
      among: profiles,
    );
    // Names both servers and what keeps running (map: switch dialog "fix").
    final target = nameOf(route.profileID);
    final accepted = await showKitConfirm(
      context,
      title: l10n.monitorSwitchToTitle(target),
      body: l10n.profileMonitorSwitchBody(
        nameOf(controller.profile?.id),
        target,
      ),
      confirmLabel: l10n.monitorSwitchTo(target),
      icon: AppIconography.swap,
    );
    if (!accepted || !context.mounted) return;
  }
  // Switching servers rebuilds the list this was opened from, and the row
  // that was tapped goes with it. The navigator stays: what follows the
  // switch is shown through it.
  final navigator = Navigator.of(context);
  // Below the navigator (so sheets and routes find it), unlike its own.
  BuildContext here() => context.mounted
      ? context
      : navigator.overlay?.context ?? navigator.context;
  try {
    if (!await controller.prepareMonitoredRequest(route) ||
        !navigator.mounted) {
      throw StateError('Changed');
    }
    // P4.2a: a request lands on its card in its conversation, never on a
    // sheet over a list. A server-wide form has no conversation: its own
    // flow opens.
    Future<void> land() => navigator.push(
      chatLandingRoute(
        sessionID: route.sessionID,
        landOnRequestID: route.requestID,
      ),
    );
    switch (route.kind) {
      case MonitoredRequestKind.permission:
        final request = controller.permissions[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        await land();
      case MonitoredRequestKind.question:
        final request = controller.questions[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        await land();
      case MonitoredRequestKind.form:
        final request = controller.forms[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        if (route.sessionID == 'global') {
          await presentConnectionForm(here(), controller, request);
        } else {
          await land();
        }
      case MonitoredRequestKind.checkIn:
        // The reminder's answer is the conversation itself, on the existing
        // chat route; nothing is sent or resolved on the user's behalf.
        await navigator.push(
          chatLandingRoute(
            sessionID: route.sessionID,
            landOnFailure: landOnFailure,
          ),
        );
    }
    await controller.profileMonitor.refresh();
  } catch (_) {
    // Already on that server, but its request is not loaded here yet (an
    // agent daemon lists its waiting requests a moment after connecting):
    // the conversation shows the same request, so that is where to go.
    if (navigator.mounted &&
        controller.profile?.id == route.profileID &&
        route.sessionID != 'global') {
      // It lands on the card once the conversation lists the request.
      await navigator.push(
        chatLandingRoute(
          sessionID: route.sessionID,
          landOnRequestID: route.kind == MonitoredRequestKind.checkIn
              ? null
              : route.requestID,
          landOnFailure: landOnFailure,
        ),
      );
      return;
    }
    if (navigator.mounted) {
      // The row it came from may be gone: the answer is a blocking alert,
      // never a snackbar (K2 §4.8).
      await showKitAlert(
        here(),
        title: l10n.profileMonitorOpenFailedTitle,
        body: l10n.monitorOpenFailed,
        icon: AppIconography.info,
      );
    }
  }
}

/// What other saved servers wait on, as the Inbox's rows: only the rows,
/// no summary row (owner rule R4: the counts sit on the server switcher and
/// the server rows, not as a settings row among requests). The Background
/// checks page it once came with is gone (slice-close-misc): whether a
/// server is checked is set in Notifications.
class ProfileMonitorInbox extends StatelessWidget {
  const ProfileMonitorInbox({super.key, required this.controller});
  final ConnectionController controller;

  /// What other saved servers wait on, as rows for one list (owner rule
  /// R1): [requests] each lead with the needs-you mark, the server that
  /// checked most recently first; [checkIns] are reminders about long
  /// runs, not requests. The connected server's own requests are the
  /// Inbox's; they are never listed twice.
  static ({List<Widget> requests, List<Widget> checkIns}) rowsFor(
    ConnectionController controller,
  ) {
    if (controller.isIsolated) return (requests: const [], checkIns: const []);
    final monitor = controller.profileMonitor;
    final current = [
      for (final profile in controller.store.profiles)
        if (controller.isProfileReadable(profile.id))
          if (monitor.snapshotFor(profile.id) case final snapshot
              when snapshot.isCurrent)
            (profile: profile, snapshot: snapshot),
    ];
    final byTime = [...current]
      ..sort((a, b) {
        final at = a.snapshot.checkedAt, bt = b.snapshot.checkedAt;
        if (at == null || bt == null) return 0;
        return bt.compareTo(at);
      });
    return (
      requests: [
        for (final (:profile, :snapshot) in byTime)
          if (profile.id != controller.profile?.id)
            for (final request in snapshot.requests)
              _MonitorRequestRow(
                key: ValueKey(('monitor-row', request.identity)),
                controller: controller,
                profile: profile,
                request: request,
              ),
      ],
      checkIns: [
        for (final (:profile, :snapshot) in current)
          for (final interval in snapshot.dueCheckIns(
            monitor.rulesFor(profile.id),
          ))
            _MonitorRequestRow(
              key: ValueKey(('monitor-row', interval.toRequest().identity)),
              controller: controller,
              profile: profile,
              request: interval.toRequest(),
            ),
      ],
    );
  }

  /// Servers the monitor is set to watch that did not answer its last
  /// check: said in words, with a way to check again, so the list is never
  /// blank when the only news is that a server cannot be reached.
  static List<ServerProfile> unreachableOf(ConnectionController controller) {
    if (controller.isIsolated) return const [];
    final monitor = controller.profileMonitor;
    return [
      for (final profile in controller.store.profiles)
        if (profile.id != controller.profile?.id &&
            controller.isProfileReadable(profile.id) &&
            monitor.rulesFor(profile.id).enabled &&
            monitor.snapshotFor(profile.id).status ==
                ProfileMonitorStatus.unavailable)
          profile,
    ];
  }

  @override
  Widget build(BuildContext context) {
    if (controller.isIsolated) return const SizedBox.shrink();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final rows = rowsFor(controller);
    final down = unreachableOf(controller);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ...rows.requests,
        ...rows.checkIns,
        for (final profile in down)
          Builder(
            builder: (context) {
              final server = serverDisplayName(
                profile,
                l10n,
                among: controller.store.profiles,
              );
              return KitRow(
                key: ValueKey('monitor-unreachable-${profile.id}'),
                leading: KitRow.icon(context, AppIconography.cloudOff),
                title: l10n.monitorUnreachableTitle(server),
                titleMaxLines: 2,
                supporting: TextSpan(text: l10n.monitorUnreachableDetail),
                supportingMaxLines: 2,
                below: KitActionBlock(
                  tertiary: [
                    KitAction(
                      key: ValueKey('monitor-recheck-${profile.id}'),
                      label: l10n.monitorUnreachableCheck(server),
                      onPressed: () =>
                          unawaited(controller.profileMonitor.refresh()),
                    ),
                  ],
                ),
              );
            },
          ),
      ],
    );
  }
}

/// One request another saved server is waiting on: the Inbox's own
/// "needs you" row, naming the server (map: embedded inbox "fix"). A
/// check-in reminder is not a request, so it stays a plain row.
class _MonitorRequestRow extends StatelessWidget {
  const _MonitorRequestRow({
    super.key,
    required this.controller,
    required this.profile,
    required this.request,
  });
  final ConnectionController controller;
  final ServerProfile profile;
  final MonitoredRequest request;
  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final checked = controller.profileMonitor.snapshotFor(profile.id).checkedAt;
    final shown = displaySessionTitleText(request.title);
    final title = shown.isNotEmpty ? shown : l10n.monitorSession;
    final server = serverDisplayName(
      profile,
      l10n,
      among: controller.store.profiles,
    );
    final key = ValueKey('monitor-row-${request.identity}');
    void open() => openMonitoredRequest(
      context,
      controller,
      MonitoredRoute(
        profileID: profile.id,
        requestID: request.id,
        sessionID: request.sessionID,
        kind: request.kind,
        createdAt: checked ?? DateTime.now(),
        serverUrl: profile.baseUrl,
        sourceIdentity: ProfileMonitor.routeSourceIdentity(profile),
        directory: request.directory,
        workspace: request.workspace,
      ),
    );
    final reason = switch (request.kind) {
      MonitoredRequestKind.permission => KitNeedsYouReason.consent,
      MonitoredRequestKind.question => KitNeedsYouReason.decision,
      MonitoredRequestKind.form => KitNeedsYouReason.decision,
      MonitoredRequestKind.checkIn => null,
    };
    if (reason != null) {
      return KitNeedsYou.row(
        key: key,
        title: title,
        reason: reason,
        server: server,
        ifIgnored: l10n.profileMonitorIfIgnored,
        onOpen: open,
      );
    }
    return KitRow(
      key: key,
      leading: KitRow.icon(context, AppIconography.waitingStart),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(
        text: l10n.monitorRequestSummary(
          server,
          l10n.monitorCheckInDue,
          l10n.monitorLastChecked,
          _time(context, checked),
        ),
      ),
      supportingMaxLines: 2,
      trailing: const _Chevron(),
      onTap: open,
    );
  }
}

String _time(BuildContext context, DateTime? value) => value == null
    ? lookupAppLocalizations(Localizations.localeOf(context)).monitorUnknown
    : '${MaterialLocalizations.of(context).formatShortDate(value.toLocal())} ${MaterialLocalizations.of(context).formatTimeOfDay(TimeOfDay.fromDateTime(value.toLocal()))}';

/// A row's trailing "opens" mark.
class _Chevron extends StatelessWidget {
  const _Chevron();

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsetsDirectional.only(end: KitTokens.of(context).space3),
    child: const KitIcon(
      AppIconography.chevronRight,
      size: KitIconSize.small,
      tone: KitTextTone.tertiary,
    ),
  );
}
