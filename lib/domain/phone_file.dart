import 'shared_storage_path.dart';

/// A file the agent named that lives on this phone's shared storage, and what
/// to do with it. The built-in Linux and Termux write into `/sdcard`, so a
/// path there is a plain Android file: Android opens it, not a preview that
/// reads it through the agent host (which refuses anything over 2 MB).
enum PhoneFileAction {
  /// Keep the in-app preview (text, code, pictures, PDF, unknown kinds).
  preview,

  /// Hand it to Android's app chooser ("Open with…").
  openWith,

  /// Hand it to Android's package installer. The system confirms; the app
  /// never installs silently.
  install,
}

/// Android's shared storage, as the agent writes it.
bool isPhoneSharedStoragePath(String path) => canonicalPhoneFile(path) != null;

/// The one spelling of a file this app may hand to Android: a file inside a
/// folder of the phone's primary shared storage (`/storage/emulated/N/...`,
/// also written `/sdcard/...`), with `.`/`..` resolved first so a path cannot
/// climb out of it. Null for everything else: the app's private storage
/// (the built-in Linux root with the server password file lives there),
/// another app's `Android/` folders, a whole volume, a bare name, an SD card.
///
/// Kotlin repeats this check on the real file system in `LocalFileGate`
/// (LocalFileGate.kt): keep both in step.
String? canonicalPhoneFile(String path) {
  final root = sharedProjectRoot(path);
  if (root == null) return null;
  final match = RegExp(r'^/storage/emulated/\d+/(.+)$').firstMatch(root);
  if (match == null) return null;
  final inside = match.group(1)!;
  if (inside == 'Android' || inside.startsWith('Android/')) return null;
  return root;
}

/// Kinds a preview cannot show; Android has an app for them.
const _binaryExtensions = {
  'zip',
  'tar',
  'gz',
  'tgz',
  'bz2',
  'xz',
  '7z',
  'rar',
  'iso',
  'img',
  'bin',
  'apks',
  'xapk',
  'aab',
  'jar',
  'so',
  'db',
  'sqlite',
  'mp3',
  'wav',
  'flac',
  'ogg',
  'm4a',
  'mp4',
  'mkv',
  'mov',
  'avi',
  'webm',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'odt',
  'ods',
  'epub',
};

/// What to do when the person taps [path]. A server that is not on this
/// phone has its own files: previews stay, whatever the path looks like.
PhoneFileAction phoneFileAction(
  String path, {
  required bool serverOnThisPhone,
}) {
  if (!serverOnThisPhone || !isPhoneSharedStoragePath(path)) {
    return PhoneFileAction.preview;
  }
  final name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
  final dot = name.lastIndexOf('.');
  final extension = dot < 0 ? '' : name.substring(dot + 1);
  if (extension == 'apk') return PhoneFileAction.install;
  if (_binaryExtensions.contains(extension)) return PhoneFileAction.openWith;
  return PhoneFileAction.preview;
}

/// The path a `file:` link names (`file:///sdcard/Download/a.apk`,
/// `file://localhost/…`), percent-decoded; null for any other link. The app
/// reads such a path itself (the phone's Android, or the server's files);
/// the link is never handed to a browser or `launchUrl`.
String? fileUriPath(String url) {
  final uri = Uri.tryParse(url.trim());
  if (uri == null || uri.scheme.toLowerCase() != 'file') return null;
  if (uri.host.isNotEmpty && uri.host.toLowerCase() != 'localhost') {
    return null;
  }
  final path = uri.path;
  if (!path.startsWith('/')) return null;
  try {
    return Uri.decodeComponent(path);
  } on FormatException {
    return null;
  }
}

/// Takes a tapped `file:` link for the app's own file opening. True when it
/// was one (so it never reaches the external-link gate); false for every
/// other link, which still goes through `openExternalLink`.
bool claimFileLink(String url, {required void Function(String path) open}) {
  final path = fileUriPath(url);
  if (path == null) return false;
  open(path);
  return true;
}
