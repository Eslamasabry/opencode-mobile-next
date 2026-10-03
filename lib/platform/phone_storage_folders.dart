import 'dart:io';

import '../builtin/builtin_folders.dart';

/// Lists and makes folders in the phone's shared storage from this app's
/// own process (dart:io), which works once Android's "All files access" is
/// on. Used by the folder browser's "This phone's storage" place for hosts
/// that run on this phone (OpenCode inside the app, Termux).
class PhoneStorageFolders {
  PhoneStorageFolders({this.hintNames = 2, this.hintLimit = 40});

  /// The internal storage, where everything the browser shows starts.
  static const root = '/storage/emulated/0';

  /// Names shown in a folder's hint, and how many folders get a hint
  /// (each costs one small read).
  final int hintNames;
  final int hintLimit;

  /// `/sdcard/x` and the like read as the one spelling the app binds.
  static String? normalize(String path) {
    var value = path.trim().replaceAll(RegExp(r'/+$'), '');
    if (value == '/sdcard' || value.startsWith('/sdcard/')) {
      value = root + value.substring('/sdcard'.length);
    }
    if (value != root && !value.startsWith('$root/')) return null;
    if (value.split('/').contains('..')) return null;
    return value;
  }

  static String? parentOf(String path) {
    if (path == root) return null;
    final cut = path.lastIndexOf('/');
    return cut <= 0 ? null : path.substring(0, cut);
  }

  /// The folders directly inside [path], folders first by name (hidden ones
  /// included: the browser decides what to show). Each carries a hint of
  /// what is inside.
  Future<List<FolderEntry>> list(String path) async {
    final folder = normalize(path);
    if (folder == null) {
      throw const FolderListException(FolderListProblem.invalid);
    }
    final dir = Directory(folder);
    final folders = <Directory>[];
    try {
      if (!await dir.exists()) {
        throw const FolderListException(FolderListProblem.missing);
      }
      await for (final entity in dir.list(followLinks: false)) {
        if (entity is Directory) folders.add(entity);
      }
    } on FileSystemException catch (error) {
      throw FolderListException(_problem(error), error.message);
    }
    folders.sort(
      (a, b) =>
          _name(a.path).toLowerCase().compareTo(_name(b.path).toLowerCase()),
    );
    final entries = <FolderEntry>[];
    var hinted = 0;
    for (final directory in folders) {
      final name = _name(directory.path);
      if (name.startsWith('.') || hinted >= hintLimit) {
        entries.add(FolderEntry(name: name, path: directory.path));
        continue;
      }
      hinted++;
      entries.add(await _entry(directory, name));
    }
    return entries;
  }

  Future<FolderEntry> _entry(Directory directory, String name) async {
    final folders = <String>[];
    final files = <String>[];
    var git = false;
    try {
      await for (final entity in directory.list(followLinks: false)) {
        final child = _name(entity.path);
        if (child == '.git') git = true;
        if (child.startsWith('.')) continue;
        (entity is Directory ? folders : files).add(child);
        if (folders.length + files.length >= 200) break;
      }
    } on FileSystemException {
      // A folder that cannot be read still shows, without a hint.
    }
    folders.sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    files.sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    // Files that name a project come first: "package.json, src" says more
    // than the first two letters of the alphabet.
    const telling = {
      'package.json',
      'pubspec.yaml',
      'Cargo.toml',
      'pyproject.toml',
      'go.mod',
      'README.md',
    };
    final all = [
      ...files.where(telling.contains),
      ...folders,
      ...files.where((file) => !telling.contains(file)),
    ];
    return FolderEntry(
      name: name,
      path: directory.path,
      isGit: git,
      inside: all.take(hintNames).toList(),
      more: all.length > hintNames ? all.length - hintNames : 0,
    );
  }

  static String _name(String path) => path.substring(path.lastIndexOf('/') + 1);

  static FolderListProblem _problem(FileSystemException error) {
    final code = error.osError?.errorCode;
    if (code == 13 || code == 1) return FolderListProblem.denied;
    if (code == 2) return FolderListProblem.missing;
    return FolderListProblem.failed;
  }
}
