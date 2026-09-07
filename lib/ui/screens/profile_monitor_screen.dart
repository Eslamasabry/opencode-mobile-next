import 'package:flutter/material.dart';
import '../../domain/profile_monitor.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../../state/profile_monitor.dart' show ProfileMonitor;
import '../app_theme.dart';
import 'activity_screen.dart' show showQuestionSheet;
import 'chat/form_flow.dart';
import 'chat/permission_sheet.dart';

/// Shared explicit route: revalidates profile, location and exact request before
/// displaying the existing resolver. It never answers from monitor metadata.
Future<void> openMonitoredRequest(
  BuildContext context,
  ConnectionController controller,
  MonitoredRoute route,
) async {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  if (controller.profile?.id != route.profileID &&
      controller.busySessions.isNotEmpty) {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.monitorSwitchTitle),
        content: Text(l10n.monitorSwitchDetail),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.monitorSwitch),
          ),
        ],
      ),
    );
    if (accepted != true || !context.mounted) return;
  }
  try {
    if (!await controller.prepareMonitoredRequest(route) || !context.mounted) {
      throw StateError('Changed');
    }
    switch (route.kind) {
      case MonitoredRequestKind.permission:
        final request = controller.permissions[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        await showPermissionSheet(
          context,
          permission: request,
          controller: controller,
        );
      case MonitoredRequestKind.question:
        final request = controller.questions[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        await showQuestionSheet(context, controller, request);
      case MonitoredRequestKind.form:
        final request = controller.forms[route.requestID];
        if (request == null || request.sessionID != route.sessionID) {
          throw StateError('Changed');
        }
        await presentConnectionForm(context, controller, request);
    }
    await controller.profileMonitor.refresh();
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.monitorOpenFailed)));
    }
  }
}

class ProfileMonitorScreen extends StatelessWidget {
  const ProfileMonitorScreen({super.key, required this.controller});
  final ConnectionController controller;
  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.monitorTitle),
        actions: [
          IconButton(
            tooltip: l10n.monitorRefresh,
            onPressed: controller.profileMonitor.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: controller,
          builder: (context, _) => ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(l10n.monitorScope),
              const SizedBox(height: 12),
              Text(l10n.monitorDisclosure),
              const SizedBox(height: 16),
              Text(l10n.monitorNoNotifications),
              if (controller.store.profiles.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(l10n.monitorNoServers),
                ),
              for (final profile in controller.store.profiles)
                if (controller.isProfileReadable(profile.id))
                  _MonitorProfile(controller: controller, profile: profile),
            ],
          ),
        ),
      ),
    );
  }
}

class ProfileMonitorInbox extends StatelessWidget {
  const ProfileMonitorInbox({super.key, required this.controller});
  final ConnectionController controller;
  @override
  Widget build(BuildContext context) {
    if (controller.isIsolated) return const SizedBox.shrink();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final monitor = controller.profileMonitor;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ListTile(
          leading: const Icon(Icons.dns_outlined),
          title: Text(l10n.monitorTitle),
          subtitle: Text(
            l10n.monitorPendingSummary(
              controller.unifiedAttentionCount,
              controller.unknownAttentionProfileCount,
            ),
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => ProfileMonitorScreen(controller: controller),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            l10n.monitorScope,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
        for (final profile in controller.store.profiles)
          if (profile.id != controller.profile?.id &&
              controller.isProfileReadable(profile.id))
            for (final request in monitor.snapshotFor(profile.id).requests)
              if (monitor.snapshotFor(profile.id).isCurrent)
                _MonitorRequestRow(
                  controller: controller,
                  profile: profile,
                  request: request,
                ),
      ],
    );
  }
}

class _MonitorRequestRow extends StatelessWidget {
  const _MonitorRequestRow({
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
    return Material(
      color: Theme.of(context).colorScheme.surfaceContainerLow,
      child: ListTile(
        leading: const ServerAttentionDot(current: true),
        title: Text(
          request.title?.trim().isNotEmpty == true
              ? request.title!
              : l10n.monitorSession,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Text(
          l10n.monitorRequestSummary(
            profile.name,
            switch (request.kind) {
              MonitoredRequestKind.permission => l10n.monitorPermission,
              MonitoredRequestKind.question => l10n.monitorQuestion,
              MonitoredRequestKind.form => l10n.monitorForm,
            },
            l10n.monitorLastChecked,
            _time(context, checked),
          ),
        ),
        onTap: () => openMonitoredRequest(
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
        ),
      ),
    );
  }
}

/// A server-status dot always paired with textual status by its parent row.
class ServerAttentionDot extends StatelessWidget {
  const ServerAttentionDot({super.key, required this.current});
  final bool current;
  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: Icon(
      Icons.circle,
      size: 12,
      color: AppTheme.statusColor(
        Theme.of(context),
        current ? AppStatusTone.ok : AppStatusTone.neutral,
      ),
    ),
  );
}

String _time(BuildContext context, DateTime? value) => value == null
    ? lookupAppLocalizations(Localizations.localeOf(context)).monitorUnknown
    : '${MaterialLocalizations.of(context).formatShortDate(value.toLocal())} ${MaterialLocalizations.of(context).formatTimeOfDay(TimeOfDay.fromDateTime(value.toLocal()))}';
String monitorStatusText(AppLocalizations l10n, ProfileMonitorStatus status) =>
    switch (status) {
      ProfileMonitorStatus.disabled => l10n.monitorDisabled,
      ProfileMonitorStatus.waiting => l10n.monitorWaiting,
      ProfileMonitorStatus.checking => l10n.monitorChecking,
      ProfileMonitorStatus.current => l10n.monitorCurrent,
      ProfileMonitorStatus.unavailable => l10n.monitorUnavailable,
      ProfileMonitorStatus.wifiRequired => l10n.monitorWifiRequired,
      ProfileMonitorStatus.paused => l10n.monitorPaused,
    };

class _MonitorProfile extends StatefulWidget {
  const _MonitorProfile({required this.controller, required this.profile});
  final ConnectionController controller;
  final ServerProfile profile;
  @override
  State<_MonitorProfile> createState() => _MonitorProfileState();
}

class _MonitorProfileState extends State<_MonitorProfile> {
  bool _saving = false;
  Future<void> _save(ProfileNotifyRules rules) async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await widget.controller.profileMonitor.setRules(widget.profile.id, rules);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              lookupAppLocalizations(
                Localizations.localeOf(context),
              ).monitorSaveFailed,
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _quietTime(bool start, ProfileNotifyRules rules) async {
    final selected = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(
        hour: (start ? rules.quietStart : rules.quietEnd)! ~/ 60,
        minute: (start ? rules.quietStart : rules.quietEnd)! % 60,
      ),
    );
    if (selected != null && mounted) {
      await _save(
        start
            ? rules.copyWith(quietStart: selected.hour * 60 + selected.minute)
            : rules.copyWith(quietEnd: selected.hour * 60 + selected.minute),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final monitor = widget.controller.profileMonitor, id = widget.profile.id;
    final rules = monitor.rulesFor(id), snapshot = monitor.snapshotFor(id);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 24),
        const Divider(),
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: ServerAttentionDot(current: snapshot.isCurrent),
          title: Text(
            widget.profile.name,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          subtitle: Text(monitorStatusText(l10n, snapshot.status)),
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.monitorOptIn),
          subtitle: Text(l10n.monitorOptInDetail),
          value: rules.enabled,
          onChanged: _saving
              ? null
              : (value) => _save(rules.copyWith(enabled: value)),
        ),
        if (rules.enabled) ...[
          Text(
            l10n.monitorLabeledTime(
              l10n.monitorLastChecked,
              _time(context, snapshot.checkedAt),
            ),
          ),
          Text(
            l10n.monitorLabeledTime(
              l10n.monitorNextCheck,
              _time(context, snapshot.nextCheckAt),
            ),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.monitorNotifications),
            value: rules.notifications,
            onChanged: _saving
                ? null
                : (value) => _save(rules.copyWith(notifications: value)),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.monitorWifi),
            subtitle: Text(
              platformCapabilities.supportsBackgroundService
                  ? l10n.monitorWifiDetail
                  : l10n.monitorWifiUnsupported,
            ),
            value: rules.wifiOnly,
            onChanged:
                _saving || !platformCapabilities.supportsBackgroundService
                ? null
                : (value) => _save(rules.copyWith(wifiOnly: value)),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.monitorQuiet),
            subtitle: Text(l10n.monitorQuietDetail),
            value: rules.quietStart != null && rules.quietEnd != null,
            onChanged: _saving
                ? null
                : (value) => _save(
                    value
                        ? rules.copyWith(quietStart: 22 * 60, quietEnd: 8 * 60)
                        : rules.copyWith(clearQuiet: true),
                  ),
          ),
          if (rules.quietStart != null && rules.quietEnd != null) ...[
            ListTile(
              title: Text(l10n.monitorQuietStart),
              trailing: Text(
                TimeOfDay(
                  hour: rules.quietStart! ~/ 60,
                  minute: rules.quietStart! % 60,
                ).format(context),
              ),
              onTap: _saving ? null : () => _quietTime(true, rules),
            ),
            ListTile(
              title: Text(l10n.monitorQuietEnd),
              trailing: Text(
                TimeOfDay(
                  hour: rules.quietEnd! ~/ 60,
                  minute: rules.quietEnd! % 60,
                ).format(context),
              ),
              onTap: _saving ? null : () => _quietTime(false, rules),
            ),
          ],
          if (snapshot.isCurrent && snapshot.requests.isEmpty)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Text(l10n.monitorAllClear),
            ),
          if (snapshot.isCurrent)
            for (final request in snapshot.requests)
              _MonitorRequestRow(
                controller: widget.controller,
                profile: widget.profile,
                request: request,
              ),
        ],
      ],
    );
  }
}
