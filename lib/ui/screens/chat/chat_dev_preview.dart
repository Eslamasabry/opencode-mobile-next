part of '../chat_screen.dart';

// Dev-server preview from the conversation: a tapped localhost link opens
// the in-app preview, and "Preview a running app" in the conversation menu
// lists the ports the conversation mentioned (message text and tool output).

extension _ChatDevPreview on _ChatScreenState {
  /// The local dev-server links this conversation mentioned, newest port
  /// first, one per port. Read from what is loaded: replies, the person's
  /// own words and tool output.
  List<DevPreviewLink> _devPreviewLinks() {
    if (_conn.isIsolated || _watching) return const [];
    final texts = <String>[];
    for (final message in _visibleHistory) {
      if (message.info.id.startsWith('local-')) continue;
      for (final part in message.parts) {
        if (part.type == 'text' && part.text.isNotEmpty) {
          texts.add(part.text);
        } else if (part.type == 'tool') {
          final output = part.toolState.output;
          if (output != null && output.isNotEmpty) texts.add(output);
        }
      }
    }
    return DevPreviewLink.recent(texts);
  }

  Future<void> _openDevPreviewLink(DevPreviewLink link) => openDevPreview(
    context,
    link,
    serverBaseUrl: _conn.profile?.baseUrl,
    leased: _conn.profile?.transientTransport ?? false,
  );

  /// A tapped link: a local dev-server address opens the preview; anything
  /// else is not claimed and goes through the external-link gate.
  bool _claimDevLink(BuildContext context, String url) {
    if (_conn.isIsolated || _watching) return false;
    // A file: link is a file, not a web page: the same opening as a plain
    // path (the phone's own Android files, or the server's file preview).
    if (_conn.capabilities.fileBrowsing &&
        claimFileLink(url, open: (path) => unawaited(_openPathLink(path)))) {
      return true;
    }
    final link = DevPreviewLink.parse(url);
    if (link == null) return false;
    unawaited(_openDevPreviewLink(link));
    return true;
  }

  /// The detector for this conversation's project, made on first use: the
  /// shells the server runs, and listening ports when the server is the
  /// phone. The Development services screen reads the same way.
  DetectedDevServers _devScan() => _devScans[this] ??= detectedDevServersFor(
    _conn,
    isCurrent: () => mounted && !_conn.isIsolated,
  );

  /// Whether "Preview a running app" is offered: the conversation mentioned
  /// a port, or the server is this phone, where running servers can be found
  /// by their ports.
  bool _devPreviewOffered() {
    if (_conn.isIsolated || _watching) return false;
    return _devPreviewLinks().isNotEmpty ||
        phoneScriptRunner(_conn.profile) != null;
  }

  /// "Preview a running app": the servers found running now, then the ports
  /// seen in this conversation. One choice, one tap to open it.
  Future<void> _chooseDevPreview() async {
    if (!mounted) return;
    final scan = _devScan();
    try {
      await scan.refresh().timeout(const Duration(seconds: 8));
    } catch (_) {
      // What was mentioned is still listed.
    }
    if (!mounted) return;
    final mentioned = _devPreviewLinks();
    final entries = <({DevPreviewLink link, DetectedServer? server})>[
      for (final server in scan.servers)
        for (final port in server.ports)
          (link: DevPreviewLink(port: port), server: server),
    ];
    final seen = {for (final e in entries) e.link.port};
    for (final link in mentioned) {
      if (seen.add(link.port)) entries.add((link: link, server: null));
    }
    final strings = _chatL10n(context);
    if (entries.isEmpty) {
      await showKitAlert(
        context,
        title: strings.devPreviewNoneFound,
        body: strings.devPreviewSheetSubtitle,
        icon: AppIconography.browser,
        alertKey: const ValueKey('dev-preview-none'),
      );
      return;
    }
    final target = DevPreviewTarget.resolve(
      entries.first.link,
      serverBaseUrl: _conn.profile?.baseUrl,
      leased: _conn.profile?.transientTransport ?? false,
    );
    final where = target.isRemote
        ? strings.devPreviewOnServer(target.host ?? '')
        : strings.devPreviewOnPhone;
    final picked = await showKitChoiceSheet<DevPreviewLink>(
      context,
      title: strings.devPreviewMenu,
      subtitle: strings.devPreviewSheetSubtitle,
      sheetKey: const ValueKey('dev-preview-sheet'),
      choices: [
        for (final entry in entries)
          KitChoice<DevPreviewLink>(
            key: ValueKey('dev-preview-port-${entry.link.port}'),
            value: entry.link,
            title: strings.devPreviewPortTitle('${entry.link.port}'),
            supporting: entry.server == null
                ? where
                : '$where · ${strings.devPreviewRunningNow(_foundTitle(entry.server!))}',
          ),
      ],
    );
    if (picked == null || !mounted) return;
    final server = scan.forPort(picked.port);
    await openDevPreview(
      context,
      picked,
      serverBaseUrl: _conn.profile?.baseUrl,
      leased: _conn.profile?.transientTransport ?? false,
      onLogs: server == null
          ? null
          : () => unawaited(
              showDevServerLogs(
                context,
                title: _foundTitle(server),
                read: () => scan.readLog(server),
                unavailable: server.hasLogs
                    ? null
                    : AppLocalizations.of(context).servicesLogsUnavailable,
              ),
            ),
    );
  }

  String _foundTitle(DetectedServer server) =>
      server.name.isNotEmpty ? server.name : server.command;
}

/// One detector per conversation screen; it holds no timer or resource.
final Expando<DetectedDevServers> _devScans = Expando<DetectedDevServers>(
  'chat dev scans',
);
