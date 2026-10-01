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
