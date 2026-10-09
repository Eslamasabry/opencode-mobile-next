import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import '../../domain/genui/gen_ui_status.dart';

abstract interface class GenUiSearchPublisher {
  Future<bool> publish({
    required String profileId,
    required GenUiAgent agent,
    required Uri endpoint,
    required String bearer,
  });
}

/// Publishes in the existing app-private rootfs, without putting the bearer in
/// a shell command, process arguments, preferences, diagnostics or output.
/// BuiltinLinux owns filesDir/linux/ubuntu; Android path_provider returns that
/// same filesDir as its application-support directory. No new host path is made.
final class BuiltinGenUiSearchPublisher implements GenUiSearchPublisher {
  BuiltinGenUiSearchPublisher({Future<Directory> Function()? supportDirectory})
    : _supportDirectory = supportDirectory ?? getApplicationSupportDirectory;
  final Future<Directory> Function() _supportDirectory;

  @override
  Future<bool> publish({
    required String profileId,
    required GenUiAgent agent,
    required Uri endpoint,
    required String bearer,
  }) async {
    if (!RegExp(r'^[a-zA-Z0-9_-]{1,96}$').hasMatch(profileId) ||
        agent.config == null ||
        endpoint.scheme != 'http' ||
        endpoint.host != '127.0.0.1' ||
        !endpoint.hasPort ||
        endpoint.port < 1 ||
        endpoint.port > 65535 ||
        endpoint.path != '/find-connectors' ||
        endpoint.userInfo.isNotEmpty ||
        endpoint.hasQuery ||
        endpoint.hasFragment ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(bearer)) {
      return false;
    }
    Directory? temporary;
    try {
      final support = await _supportDirectory();
      final root = await support.resolveSymbolicLinks();
      final relative = agent.runsAs == AgentRunUser.agentUser
          ? ['home', 'oc', '.oc-profiles', profileId, '.oc-genui']
          : ['root', '.oc-genui', agent.id];
      var path = root;
      for (final part in ['linux', 'ubuntu', ...relative]) {
        path = '$path/$part';
        if (await FileSystemEntity.type(path, followLinks: false) !=
            FileSystemEntityType.directory) {
          return false;
        }
      }
      final parent = Directory(path);
      // The installed helper directory must be private. OS users cannot see
      // the descriptor outside the app sandbox; guest accounts share its UID.
      if ((await parent.stat()).mode & 0x3f != 0) return false;
      final target = '$path/enabled.search.json';
      final type = await FileSystemEntity.type(target, followLinks: false);
      if (type != FileSystemEntityType.notFound &&
          type != FileSystemEntityType.file) {
        return false;
      }
      temporary = await parent.createTemp('.search-');
      final file = File('${temporary.path}/descriptor');
      await file.writeAsBytes(const []);
      // Only a path and a fixed mode enter argv; never descriptor contents.
      final chmod = await Process.run(
        Platform.isAndroid ? '/system/bin/chmod' : 'chmod',
        ['600', file.path],
      );
      if (chmod.exitCode != 0) return false;
      await file.writeAsString(
        jsonEncode({'endpoint': endpoint.toString(), 'bearer': bearer}),
        flush: true,
      );
      await file.rename(target);
      return true;
    } catch (_) {
      return false;
    } finally {
      try {
        await temporary?.delete(recursive: true);
      } catch (_) {
        /* No raw errors. */
      }
    }
  }
}
