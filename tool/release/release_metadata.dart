/// Reads the app's full release identity, including its Android build number.
String? releaseVersionFromPubspec(String pubspec) {
  final match = RegExp(
    r'^version:\s*([0-9]+\.[0-9]+\.[0-9]+\+[0-9]+)\s*(?:#.*)?$',
    multiLine: true,
  ).firstMatch(pubspec);
  return match?.group(1);
}

/// Returns actionable discrepancies between the three release metadata files.
///
/// The changelog's first level-two heading must be the current release. The
/// versioned release notes must start with the exact public release header.
List<String> validateReleaseMetadata({
  required String pubspec,
  required String changelog,
  required String? releaseNotes,
}) {
  final version = releaseVersionFromPubspec(pubspec);
  if (version == null) {
    return ['pubspec.yaml must contain version: X.Y.Z+BUILD.'];
  }

  final issues = <String>[];
  final topHeading = RegExp(
    r'^## (.+)$',
    multiLine: true,
  ).firstMatch(changelog)?.group(1)?.trim();
  final topVersion = topHeading == null
      ? null
      : RegExp(
          r'^([0-9]+\.[0-9]+\.[0-9]+\+[0-9]+)(?:\s|$)',
        ).firstMatch(topHeading)?.group(1);
  if (topVersion != version) {
    issues.add(
      'CHANGELOG.md first release heading must name $version '
      '(found ${topHeading ?? "no release heading"}).',
    );
  }

  final notesPath = 'docs/releases/v$version.md';
  if (releaseNotes == null) {
    issues.add('Add $notesPath with header # OpenCode Mobile $version.');
  } else {
    final firstLine = releaseNotes.split('\n').first.trim();
    if (firstLine != '# OpenCode Mobile $version') {
      issues.add('$notesPath must start with # OpenCode Mobile $version.');
    }
  }
  return issues;
}
