/// Which folders live in the phone's shared storage (the "internal storage"
/// and SD cards other apps and the Files app see), as opposed to the app's
/// own project space (`/root/projects`, Termux's home).
///
/// Android 11+ shows an app such folders but hides other apps' non-media
/// files unless the person allows "All files access" (see
/// `lib/platform/storage_access.dart`). Only a project here asks for it.
library;

final _sharedPatterns = <RegExp>[
  // The internal storage, by every name Android gives it.
  RegExp(r'^/sdcard(/|$)'),
  RegExp(r'^/mnt/sdcard(/|$)'),
  RegExp(r'^/mnt/user/\d+/(emulated/\d+|primary)(/|$)'),
  RegExp(r'^/storage/emulated/\d+(/|$)'),
  RegExp(r'^/storage/self/primary(/|$)'),
  // An SD card or USB drive: /storage/1A2B-3C4D.
  RegExp(r'^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/|$)'),
  // Termux's shortcut after termux-setup-storage (its `~/storage/shared`).
  RegExp(r'^/data/data/com\.termux/files/home/storage(/|$)'),
  RegExp(r'^~/storage(/|$)'),
];

/// Whether [path] is in shared storage. A path with `..` segments is judged
/// after they are resolved, so `/root/projects/../../sdcard` counts.
bool isSharedStoragePath(String? path) {
  if (path == null) return false;
  final value = _resolve(path.trim().replaceAll('\\', '/'));
  return _sharedPatterns.any((pattern) => pattern.hasMatch(value));
}

String _resolve(String path) {
  if (!path.contains('/.')) return path;
  final out = <String>[];
  for (final part in path.split('/')) {
    if (part.isEmpty || part == '.') continue;
    if (part == '..') {
      if (out.isNotEmpty) out.removeLast();
      continue;
    }
    out.add(part);
  }
  final joined = out.join('/');
  return path.startsWith('/') ? '/$joined' : joined;
}

final _primaryAliases = <RegExp>[
  RegExp(r'^/sdcard(?=/|$)'),
  RegExp(r'^/mnt/sdcard(?=/|$)'),
  RegExp(r'^/storage/self/primary(?=/|$)'),
  RegExp(r'^/mnt/user/\d+/(?:emulated/(\d+)|primary)(?=/|$)'),
];

/// The folder a shared-storage project is opened from, in the one spelling the
/// built-in Linux binds: `/storage/emulated/N/<folder>[/...]` or
/// `/storage/XXXX-XXXX/<folder>[/...]`. Null for anything else, for a path that
/// is a whole volume (never bound whole: only the folders a person opened),
/// and for Termux's own shortcut (Termux reads it, not proot).
///
/// Kotlin mirrors this in `SharedStorageBinds.canonicalRoot`
/// (BuiltinLinux.kt): keep both in step.
String? sharedProjectRoot(String? path) {
  if (path == null) return null;
  var value = _resolve(path.trim().replaceAll('\\', '/'));
  value = value.replaceAll(RegExp(r'/+$'), '');
  for (final alias in _primaryAliases) {
    final match = alias.firstMatch(value);
    if (match != null) {
      final user = match.groupCount >= 1 ? (match.group(1) ?? '0') : '0';
      value = '/storage/emulated/$user${value.substring(match.end)}';
      break;
    }
  }
  final volume = RegExp(
    r'^/storage/(?:emulated/\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/.+)$',
  ).firstMatch(value);
  if (volume == null) return null;
  return value;
}

/// proot arguments for the shared-storage projects [roots] (canonical, see
/// [sharedProjectRoot]). `confined` is true once AI Team is on: then only
/// each opened folder is bound, at its own path and (primary volume) its
/// `/sdcard` alias. Otherwise the whole `/storage` and `/sdcard` are visible,
/// as before AI Team. Mirrors `SharedStorageBinds.binds` in BuiltinLinux.kt.
List<String> sharedStorageProotBinds(
  Iterable<String> roots, {
  required bool confined,
}) {
  if (!confined) {
    return const ['--bind=/storage', '--bind=/storage/emulated/0:/sdcard'];
  }
  final seen = <String>{};
  final out = <String>[];
  for (final raw in roots) {
    final root = sharedProjectRoot(raw);
    if (root == null || !seen.add(root)) continue;
    out.add('--bind=$root');
    const primary = '/storage/emulated/0';
    if (root.startsWith('$primary/')) {
      out.add('--bind=$root:/sdcard${root.substring(primary.length)}');
    }
  }
  return out;
}
