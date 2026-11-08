// Prepares a checkout for the F-Droid build (docs/release/fdroid-2026-10-31.md).
//
//   dart tool/fdroid/prebuild.dart [--root <checkout>]
//
// F-Droid compiles from source with upstream Flutter and refuses Google's
// proprietary SDKs, so the packages that bring them are taken out of
// pubspec.yaml before `flutter pub get`:
//
//   shorebird_code_push  the code-push updater (F-Droid delivers updates)
//
// plus the `shorebird.yaml` asset. The Dart code never imports the package
// directly in the F-Droid build: a conditional import on `OC_FDROID` selects
// a stub file (lib/update/shorebird_updater_stub.dart). The QR scanner needs
// no switch: it is `camera` plus `zxing2` in every build. Build with
// `--dart-define=OC_FDROID=true --android-project-arg=ocFdroid=true`.
import 'dart:io';

/// Dependencies removed from the F-Droid build, with the reason.
const fdroidRemovedPackages = {'shorebird_code_push': 'code-push updater'};

/// Asset lines removed from the F-Droid build.
const fdroidRemovedAssets = ['shorebird.yaml'];

/// [pubspec] without the packages and assets above. A package's key line goes
/// together with the comment block directly above it. Throws if one is
/// missing, so a rename upstream cannot silently leave it in.
String stripPubspecForFdroid(String pubspec) {
  final lines = pubspec.split('\n');
  final keep = List<bool>.filled(lines.length, true);
  for (final name in fdroidRemovedPackages.keys) {
    final at = lines.indexWhere((l) => l.startsWith('  $name:'));
    if (at < 0) throw StateError('pubspec.yaml has no "$name:" dependency');
    keep[at] = false;
    // A one-line `name: version`; a nested block would continue indented.
    var after = at + 1;
    while (after < lines.length && lines[after].startsWith('    ')) {
      keep[after++] = false;
    }
    var above = at - 1;
    while (above >= 0 && lines[above].trimLeft().startsWith('#')) {
      keep[above--] = false;
    }
  }
  for (final asset in fdroidRemovedAssets) {
    final at = lines.indexWhere((l) => l.trim() == '- $asset');
    if (at < 0) throw StateError('pubspec.yaml has no "$asset" asset');
    keep[at] = false;
  }
  return [
    for (var i = 0; i < lines.length; i++)
      if (keep[i]) lines[i],
  ].join('\n');
}

void main(List<String> args) {
  var root = '.';
  final at = args.indexOf('--root');
  if (at >= 0 && at + 1 < args.length) root = args[at + 1];
  final file = File('$root/pubspec.yaml');
  file.writeAsStringSync(stripPubspecForFdroid(file.readAsStringSync()));
  final yaml = File('$root/shorebird.yaml');
  if (yaml.existsSync()) yaml.deleteSync();
  stdout.writeln(
    'F-Droid prebuild: removed ${fdroidRemovedPackages.keys.join(', ')} '
    'and ${fdroidRemovedAssets.join(', ')}.',
  );
}
