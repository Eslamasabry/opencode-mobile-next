import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/dev_preview.dart';
import '../domain/development_service.dart';
import 'connection.dart';
import 'detected_dev_servers.dart';
import 'dev_server_scan.dart';
import 'development_service_store.dart';
import 'development_services.dart';

/// Whether the app really knows a dev server is running for the connected
/// project, and where to preview it: the Terminal's header line
/// ("Dev server running · Open preview").
///
/// Two signals count, and only these:
/// - a saved development service whose own shell is running
///   ([DevelopmentServiceStatus.running], only where the server has
///   `developmentServices`), at the port of its saved address;
/// - a program in the built-in Linux, started inside this project, that holds
///   a socket and answered on loopback ([ListeningPorts]; only when the
///   connected server is this phone, where ports can be read).
///
/// Ports a shell printed are never used: text in output is a guess.
class ProjectDevServer extends ChangeNotifier {
  ProjectDevServer({
    required Future<DevPreviewLink?> Function() look,
    this.every = const Duration(seconds: 15),
  }) : _look = look;

  /// The real signals for [connection]'s project.
  factory ProjectDevServer.of(ConnectionController connection) {
    final directory = connection.directory ?? '';
    final profileID = connection.profile?.id ?? '';
    final scope = (profileID, connection.locationRevision, directory);
    bool current() =>
        (
          connection.profile?.id ?? '',
          connection.locationRevision,
          connection.directory ?? '',
        ) ==
        scope;
    DevelopmentServices? saved;
    if (profileID.isNotEmpty &&
        directory.isNotEmpty &&
        connection.capabilities.developmentServices) {
      saved = DevelopmentServices(
        store: DevelopmentServiceStore(
          preferences: connection.store.prefs,
          profileID: profileID,
          canWrite: () => connection.isProfileReadable(profileID),
        ),
        directory: directory,
        workspace: connection.workspace,
        isCurrent: current,
        supported: () => connection.capabilities.developmentServices,
        resolveGateway: () async {
          try {
            return await connection.prepareActionRepository();
          } catch (_) {
            return null;
          }
        },
      );
    }
    final runOnPhone = phoneScriptRunner(connection.profile);
    final listening = runOnPhone == null || directory.isEmpty
        ? null
        : DetectedDevServers(
            directory: directory,
            isCurrent: current,
            runOnPhone: runOnPhone,
            // Shells are not read: their ports come from printed text.
            resolveGateway: () async => null,
          );
    final watch = ProjectDevServer(
      look: () =>
          lookFor(directory: directory, saved: saved, listening: listening),
    );
    watch._disposers.addAll([?saved?.dispose, ?listening?.dispose]);
    return watch;
  }

  /// One look at both signals; the first saved service that runs wins, then
  /// the first program listening in the project.
  @visibleForTesting
  static Future<DevPreviewLink?> lookFor({
    required String directory,
    DevelopmentServices? saved,
    DetectedDevServers? listening,
  }) async {
    if (saved != null && saved.services.isNotEmpty) {
      await saved.refresh();
      for (final service in saved.services) {
        if (saved.status(service) != DevelopmentServiceStatus.running) {
          continue;
        }
        final uri = Uri.tryParse(service.url);
        if (uri != null && uri.hasPort && uri.port > 0 && uri.port < 65536) {
          return DevPreviewLink(port: uri.port);
        }
      }
    }
    if (listening != null) {
      await listening.refresh();
      for (final server in listening.servers) {
        if (server.source != DetectedSource.process) continue;
        final cwd = server.cwd;
        if (cwd == null) continue;
        final inProject = cwd == directory || cwd.startsWith('$directory/');
        if (!inProject) continue;
        final port = server.firstPort;
        if (port != null) return DevPreviewLink(port: port);
      }
    }
    return null;
  }

  final Future<DevPreviewLink?> Function() _look;
  final Duration every;
  final _disposers = <VoidCallback>[];
  Timer? _timer;
  bool _looking = false;
  bool _disposed = false;

  /// The dev server to preview, or null when none is known to run.
  DevPreviewLink? get running => _running;
  DevPreviewLink? _running;

  /// Looks now and then every [every] until [pause].
  void resume() {
    if (_disposed || _timer != null) return;
    unawaited(refresh());
    _timer = Timer.periodic(every, (_) => unawaited(refresh()));
  }

  void pause() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> refresh() async {
    if (_looking || _disposed) return;
    _looking = true;
    DevPreviewLink? found;
    try {
      found = await _look();
    } catch (_) {
      // A look that fails says nothing new: keep what was known.
      found = _running;
    } finally {
      _looking = false;
    }
    if (_disposed) return;
    if (found?.key != _running?.key || found?.port != _running?.port) {
      _running = found;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    pause();
    for (final dispose in _disposers) {
      dispose();
    }
    super.dispose();
  }
}
