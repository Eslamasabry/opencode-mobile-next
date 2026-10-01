import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/shared_storage_path.dart';

/// The shared-storage folders a person opened as projects on this phone's
/// in-app server, per profile (`oc.sharedProjects.<profileId>`, swept with
/// the profile). Once AI Team is on, the built-in Linux sees shared storage
/// only through these folders, so they are also handed to the native side,
/// which keeps its own copy to rebuild the binds after a restart.
class SharedProjectRoots {
  SharedProjectRoots._();

  static const prefix = 'oc.sharedProjects.';
  static const _channel = MethodChannel(
    'io.github.eslamasabry.opencode_mobile/builtin_linux',
  );

  @visibleForTesting
  static Future<void> Function(List<String> roots)? pushOverride;

  static String keyFor(String profileId) => '$prefix$profileId';

  /// Remembers [path] for [profileId] and refreshes the native copy.
  static Future<void> remember(String profileId, String path) async {
    final root = sharedProjectRoot(path);
    if (root == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final key = keyFor(profileId);
      final list = {...?prefs.getStringList(key), root}.toList()..sort();
      await prefs.setStringList(key, list);
      await push(prefs);
    } catch (_) {
      // Remembering is best effort: the folder still opens.
    }
  }

  /// Sends the union over all profiles. Call after a profile's keys are
  /// swept so a deleted server leaves nothing bound.
  static Future<void> push(SharedPreferences prefs) async {
    final roots = <String>{
      for (final key in prefs.getKeys())
        if (key.startsWith(prefix)) ...?prefs.getStringList(key),
    }.toList()..sort();
    final override = pushOverride;
    if (override != null) return override(roots);
    try {
      await _channel.invokeMethod<void>('setSharedProjects', {'roots': roots});
    } on PlatformException {
      // Not Android or not ready: nothing to refresh.
    } on MissingPluginException {
      // Same.
    }
  }
}
