/// Linux › Services: what the person keeps running, one list by urgency.
/// Each row says its state in words and has one named chip: **Start** when
/// it is off or gave up, **Stop** while it is kept running (one state per
/// service: Start means "keep it running", also after a crash or a restart).
/// Add a service is the first row; a row opens the service's own page.
///
/// States: loading (skeleton, then "still waiting"), empty, list, retrying,
/// crashed, port busy, busy (starting, stopping), an act that failed, a list
/// that could not be read (words, Try again, Details) and Linux missing.
///
/// Kit parts only.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_services/linux_services.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'linux_manager_common.dart' show linuxRails;
import 'linux_service_add_page.dart';
import 'linux_service_page.dart';
import 'linux_services_common.dart';

/// Opens Linux › Services.
Future<void> openLinuxServices(BuildContext context) => pushKitPage<void>(
  context,
  (_) => const LinuxServicesPage(),
  settings: const RouteSettings(name: 'linux-services'),
);

class LinuxServicesPage extends ConsumerStatefulWidget {
  const LinuxServicesPage({super.key});

  @override
  ConsumerState<LinuxServicesPage> createState() => _LinuxServicesPageState();
}

class _LinuxServicesPageState extends ConsumerState<LinuxServicesPage> {
  late final LinuxServicesFeed _feed;

  @override
  void initState() {
    super.initState();
    _feed = LinuxServicesFeed(ref.read(linuxServicesProvider));
    unawaited(_feed.load());
  }

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  Future<void> _open(String name) async {
    await pushKitPage<void>(
      context,
      (_) => LinuxServicePage(name: name),
      settings: const RouteSettings(name: 'linux-service'),
    );
    // The page may have removed or changed the service.
    if (mounted) unawaited(_feed.load());
  }

  Future<void> _add() async {
    // The form hands back why a service it saved could not start.
    final problem = await pushKitPage<Object?>(
      context,
      (_) => const LinuxServiceAddPage(),
      settings: const RouteSettings(name: 'linux-service-add'),
    );
    if (!mounted) return;
    await _feed.load();
    if (problem != null) _feed.reportFailure(problem);
  }

  Future<void> _remove(LinuxServiceState s) async {
    if (!await confirmRemoveLinuxService(context, s)) return;
    await _feed.run(s.def.name, LinuxServiceAct.remove);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return KitScreen(
      topBar: KitTopBar(title: l10n.linuxSvcTitle),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: _feed,
        builder: (context, _) => _body(context, l10n),
      ),
    );
  }

  Widget _body(BuildContext context, AppLocalizations l10n) {
    final states = _feed.states;
    if (states == null && _feed.loading) {
      return ListView(
        key: const ValueKey('linux-services-loading'),
        padding: KitScreen.padding(context),
        children: [
          const KitSkeletonRows(),
          KitSince(
            since: _feed.loadingSince,
            builder: (context, status) => status.isSlow
                ? Padding(
                    padding: linuxRails(context),
                    child: KitText(
                      l10n.kitSinceStillWaiting(status.elapsed.inSeconds),
                      role: KitTextRole.caption,
                    ),
                  )
                : const SizedBox.shrink(),
          ),
        ],
      );
    }
    if (states == null) return _loadFailed(l10n);
    return KitRefresh(
      onRefresh: _feed.load,
      child: ListView(
        key: const ValueKey('linux-services-list'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.only(
          top: KitTokens.of(context).space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: _list(context, l10n, states),
      ),
    );
  }

  Widget _loadFailed(AppLocalizations l10n) {
    final failure = _feed.failure;
    final missing =
        failure is LinuxServiceException &&
        failure.problem == LinuxServiceProblem.linuxMissing;
    if (missing) {
      return KitStateView(
        key: const ValueKey('linux-services-missing'),
        icon: AppIconography.terminal,
        title: l10n.localTerminalNotSetUpTitle,
        body: l10n.linuxSvcProblemMissing,
      );
    }
    return KitStateView.error(
      key: const ValueKey('linux-services-load-error'),
      title: l10n.linuxSvcCouldNotStart,
      body: linuxServiceLoadBody(l10n, failure),
      details: linuxServiceDetails(failure),
      retry: KitAction(label: l10n.commonRetry, onPressed: _feed.load),
    );
  }

  List<Widget> _list(
    BuildContext context,
    AppLocalizations l10n,
    List<LinuxServiceState> states,
  ) {
    final tokens = KitTokens.of(context);
    final rails = linuxRails(context);
    final sorted = linuxServicesByUrgency(states);
    return [
      if (_feed.failure != null)
        Padding(
          padding: rails,
          child: KitNotice.error(
            key: const ValueKey('linux-services-stale'),
            message: linuxServiceProblemText(l10n, _feed.failure),
            details: linuxServiceDetails(_feed.failure),
            retry: KitAction(label: l10n.commonRetry, onPressed: _feed.load),
          ),
        ),
      if (_feed.actFailure != null)
        Padding(
          padding: rails,
          child: KitNotice.error(
            key: const ValueKey('linux-services-act-error'),
            message: linuxServiceProblemText(l10n, _feed.actFailure),
            details: linuxServiceDetails(_feed.actFailure),
          ),
        ),
      KitRowGroup(
        children: [
          KitRow(
            key: const ValueKey('linux-services-add'),
            leading: KitRow.icon(context, AppIconography.addCircle),
            title: l10n.linuxSvcAdd,
            supporting: TextSpan(text: l10n.linuxSvcAddDetail),
            supportingMaxLines: 2,
            trailing: const KitChevron(),
            onTap: () => unawaited(_add()),
          ),
        ],
      ),
      SizedBox(height: tokens.sectionGap),
      if (sorted.isEmpty)
        Padding(
          padding: rails,
          child: KitText(
            l10n.linuxSvcEmpty,
            key: const ValueKey('linux-services-empty'),
            role: KitTextRole.secondary,
          ),
        )
      else ...[
        KitRowGroup(
          key: const ValueKey('linux-services-group'),
          children: [for (final s in sorted) _row(context, l10n, s)],
        ),
        SizedBox(height: tokens.space3),
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitText(l10n.linuxSvcKeepsRunning, role: KitTextRole.caption),
        ),
        SizedBox(height: tokens.space2),
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitText(
            l10n.linuxSvcScreenOff,
            key: const ValueKey('linux-services-screen-off'),
            role: KitTextRole.caption,
          ),
        ),
      ],
    ];
  }

  Widget _row(
    BuildContext context,
    AppLocalizations l10n,
    LinuxServiceState s,
  ) {
    final name = s.def.name;
    final title = s.def.title;
    final act = _feed.pending[name];
    final stop = act == null
        ? linuxServiceShowsStop(s)
        : act == LinuxServiceAct.stop || act == LinuxServiceAct.remove
        ? false
        : true;
    final line = linuxServiceRowLine(context, l10n, s, act: act);
    final run = stop ? LinuxServiceAct.stop : LinuxServiceAct.start;
    void press() {
      // One act at a time per service: a tap while it works is ignored.
      if (act == null) unawaited(_feed.run(name, run));
    }

    final running =
        s.phase == LinuxServicePhase.running ||
        s.phase == LinuxServicePhase.backoff;
    return KitRow(
      key: ValueKey('linux-service-$name'),
      leading: KitRow.icon(
        context,
        linuxServiceIcon(s),
        needsYou: linuxServiceNeedsYou(s),
      ),
      title: title,
      supporting: TextSpan(text: line),
      supportingKey: ValueKey('linux-service-line-$name'),
      supportingMaxLines: 2,
      chip: KitRowChip(
        key: ValueKey('linux-service-chip-$name'),
        label: stop ? l10n.linuxSvcStop : l10n.linuxSvcStart,
        semanticsLabel: stop
            ? l10n.linuxSvcStopNamed(title)
            : l10n.linuxSvcStartNamed(title),
        onPressed: press,
      ),
      onTap: () => unawaited(_open(name)),
      menu: [
        if (running)
          KitMenuItem(
            key: ValueKey('linux-service-restart-$name'),
            label: l10n.linuxSvcRestartNamed(title),
            icon: AppIconography.restart,
            enabled: act == null,
            disabledReason: act == null ? null : line,
            onSelected: () =>
                unawaited(_feed.run(name, LinuxServiceAct.restart)),
          ),
        KitMenuItem(
          key: ValueKey('linux-service-toggle-$name'),
          label: stop
              ? l10n.linuxSvcStopNamed(title)
              : l10n.linuxSvcStartNamed(title),
          icon: stop ? AppIconography.stopCircle : AppIconography.play,
          enabled: act == null,
          disabledReason: act == null ? null : line,
          onSelected: press,
        ),
        KitMenuItem(
          key: ValueKey('linux-service-remove-$name'),
          label: l10n.linuxSvcRemoveNamed(title),
          icon: AppIconography.removeCircle,
          destructive: true,
          enabled: act == null,
          disabledReason: act == null ? null : line,
          onSelected: () => unawaited(_remove(s)),
        ),
      ],
    );
  }
}
