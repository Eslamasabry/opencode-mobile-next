/// Linux › Services › one service: its state in words, the acts that name
/// it (Open when it has a port, Start or Stop, Restart, Remove) and its live
/// log with search and copy. The log opens large when the service gave up.
///
/// Kit parts only.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_linux.dart' show BuiltinLinux;
import '../../../builtin/linux_services/linux_services.dart';
import '../../../domain/dev_preview.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../dev_preview_screen.dart' show openDevPreview;
import 'linux_manager_common.dart' show linuxRails;
import 'linux_services_common.dart';

class LinuxServicePage extends ConsumerStatefulWidget {
  const LinuxServicePage({super.key, required this.name, this.opener});

  /// The service's name (its id).
  final String name;

  /// Test seam for [openDevPreview].
  @visibleForTesting
  final Future<void> Function(DevPreviewLink link, String serverBaseUrl)?
  opener;

  @override
  ConsumerState<LinuxServicePage> createState() => _LinuxServicePageState();
}

class _LinuxServicePageState extends ConsumerState<LinuxServicePage> {
  late final LinuxServicesFeed _feed;
  final _lines = ValueNotifier<List<KitLogLine>>(const []);
  bool _seen = false;
  bool _closing = false;

  @override
  void initState() {
    super.initState();
    _feed = LinuxServicesFeed(ref.read(linuxServicesProvider));
    unawaited(_feed.load().then((_) => _readLog()));
  }

  @override
  void dispose() {
    _feed.dispose();
    _lines.dispose();
    super.dispose();
  }

  Future<void> _readLog() async {
    if (!mounted) return;
    final l10n = AppLocalizations.of(context);
    try {
      final tail = await _feed.api.logTail(widget.name);
      if (!mounted) return;
      _lines.value = [for (final l in tail) linuxServiceLogLine(l10n, l)];
    } catch (_) {
      // The panel keeps what it had; the state above still tells the story.
    }
  }

  Future<void> _open(LinuxServiceState s) {
    final link = DevPreviewLink(port: s.def.port!);
    final opener = widget.opener;
    if (opener != null) return opener(link, BuiltinLinux.serverUrl);
    return openDevPreview(context, link, serverBaseUrl: BuiltinLinux.serverUrl);
  }

  Future<void> _remove(LinuxServiceState s) async {
    if (!await confirmRemoveLinuxService(context, s)) return;
    final ok = await _feed.run(widget.name, LinuxServiceAct.remove);
    if (ok && mounted) _close();
  }

  void _close() {
    if (_closing) return;
    _closing = true;
    Navigator.of(context).maybePop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ListenableBuilder(
      listenable: _feed,
      builder: (context, _) {
        final s = _feed.byName(widget.name);
        if (s != null) _seen = true;
        // Removed from elsewhere (or just now): nothing left to show.
        if (s == null && _seen && !_feed.loading) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) _close();
          });
        }
        return KitScreen(
          topBar: KitTopBar(title: s?.def.title ?? widget.name),
          width: KitScreenWidth.reading,
          body: _body(context, l10n, s),
        );
      },
    );
  }

  Widget _body(
    BuildContext context,
    AppLocalizations l10n,
    LinuxServiceState? s,
  ) {
    if (s == null) {
      if (_feed.loading) {
        return ListView(
          key: const ValueKey('linux-service-loading'),
          padding: KitScreen.padding(context),
          children: const [KitSkeletonRows(count: 3)],
        );
      }
      if (_feed.failure != null) {
        return KitStateView.error(
          key: const ValueKey('linux-service-load-error'),
          title: l10n.linuxSvcCouldNotStart,
          body: linuxServiceLoadBody(l10n, _feed.failure),
          details: linuxServiceDetails(_feed.failure),
          retry: KitAction(
            label: l10n.commonRetry,
            onPressed: () => unawaited(_feed.load().then((_) => _readLog())),
          ),
        );
      }
      return const SizedBox.shrink();
    }
    return ListView(
      key: const ValueKey('linux-service-list'),
      padding: EdgeInsetsDirectional.only(
        top: KitTokens.of(context).space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: _children(context, l10n, s),
    );
  }

  List<Widget> _children(
    BuildContext context,
    AppLocalizations l10n,
    LinuxServiceState s,
  ) {
    final tokens = KitTokens.of(context);
    final rails = linuxRails(context);
    final title = s.def.title;
    final act = _feed.pending[widget.name];
    final busy = act != null;
    final running =
        s.phase == LinuxServicePhase.running ||
        s.phase == LinuxServicePhase.starting;
    final keepsRunning = busy
        ? act != LinuxServiceAct.stop && act != LinuxServiceAct.remove
        : linuxServiceShowsStop(s);
    final reason = busy ? linuxServiceStatus(context, l10n, s, act: act) : null;
    final needsYou = linuxServiceNeedsYou(s);
    final cameBack =
        !busy &&
        s.phase == LinuxServicePhase.running &&
        s.restarts > 0 &&
        s.lastExitAt != null;
    final canOpen =
        s.def.port != null && s.phase == LinuxServicePhase.running && !busy;

    return [
      if (_feed.actFailure != null)
        Padding(
          padding: rails,
          child: KitNotice.error(
            key: const ValueKey('linux-service-act-error'),
            message: linuxServiceProblemText(l10n, _feed.actFailure),
            details: linuxServiceDetails(_feed.actFailure),
          ),
        ),
      KitRowGroup(
        children: [
          KitRow(
            key: const ValueKey('linux-service-status'),
            leading: KitRow.icon(
              context,
              linuxServiceIcon(s),
              needsYou: needsYou,
            ),
            title: linuxServiceStatus(context, l10n, s, act: act),
            supporting: cameBack
                ? TextSpan(
                    text: l10n.linuxSvcCameBack(
                      s.restarts,
                      KitTime.clock(context, s.lastExitAt!),
                    ),
                  )
                : null,
            supportingMaxLines: 3,
            titleMaxLines: 3,
          ),
        ],
      ),
      SizedBox(height: tokens.sectionGap),
      KitRowGroup(
        children: [
          if (canOpen)
            KitRow(
              key: const ValueKey('linux-service-open'),
              leading: KitRow.icon(context, AppIconography.externalLink),
              title: l10n.linuxSvcOpenNamed(title),
              supporting: TextSpan(
                text: l10n.linuxSvcOpenDetail('${s.def.port}'),
              ),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: () => unawaited(_open(s)),
            ),
          if (keepsRunning)
            KitRow(
              key: const ValueKey('linux-service-stop'),
              leading: KitRow.icon(context, AppIconography.stopCircle),
              title: l10n.linuxSvcStopNamed(title),
              supporting: TextSpan(text: l10n.linuxSvcStopDetail),
              supportingMaxLines: 2,
              enabled: !busy,
              disabledReason: reason,
              onTap: () =>
                  unawaited(_feed.run(widget.name, LinuxServiceAct.stop)),
            )
          else
            KitRow(
              key: const ValueKey('linux-service-start'),
              leading: KitRow.icon(context, AppIconography.play),
              title: l10n.linuxSvcStartNamed(title),
              supporting: TextSpan(text: l10n.linuxSvcStartDetail),
              supportingMaxLines: 2,
              enabled: !busy,
              disabledReason: reason,
              onTap: () =>
                  unawaited(_feed.run(widget.name, LinuxServiceAct.start)),
            ),
          if (running || s.phase == LinuxServicePhase.backoff)
            KitRow(
              key: const ValueKey('linux-service-restart'),
              leading: KitRow.icon(context, AppIconography.restart),
              title: l10n.linuxSvcRestartNamed(title),
              enabled: !busy,
              disabledReason: reason,
              onTap: () =>
                  unawaited(_feed.run(widget.name, LinuxServiceAct.restart)),
            ),
          KitRow(
            key: const ValueKey('linux-service-remove'),
            leading: KitRow.icon(context, AppIconography.removeCircle),
            title: l10n.linuxSvcRemoveNamed(title),
            destructive: true,
            enabled: !busy,
            disabledReason: reason,
            onTap: () => unawaited(_remove(s)),
          ),
        ],
      ),
      SizedBox(height: tokens.sectionGap),
      Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
        child: _log(context, l10n, s),
      ),
    ];
  }

  Widget _log(
    BuildContext context,
    AppLocalizations l10n,
    LinuxServiceState s,
  ) {
    final live =
        s.phase == LinuxServicePhase.running ||
        s.phase == LinuxServicePhase.starting ||
        s.phase == LinuxServicePhase.backoff;
    final failed = s.phase == LinuxServicePhase.failed;
    final panel = KitLogPanel(
      panelKey: const ValueKey('linux-service-log'),
      lines: _lines,
      title: l10n.linuxSvcLogTitle,
      emptyText: l10n.linuxSvcLogEmpty,
      searchable: true,
      live: live,
      ended: live ? null : KitLogEnd(exitCode: s.lastExit, failed: failed),
      onRefresh: _readLog,
      size: failed ? KitLogSize.fill : KitLogSize.folded,
    );
    // A service that gave up opens its log large; the panel then fills a
    // height the page gives it.
    return failed
        ? SizedBox(height: MediaQuery.sizeOf(context).height / 2, child: panel)
        : panel;
  }
}
