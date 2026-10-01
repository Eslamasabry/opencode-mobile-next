import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Guards the public boundary of the repository itself: what a first-time
/// reader meets at the root, and what third-party material the tree carries.
void main() {
  _g25();

  test('the internal engineering log is out of the public tree', () {
    // The append-only working log carried machine paths, device names, and
    // release claims that were stale the week they were written. It is gone
    // from the tree entirely; git history keeps it for archaeology.
    for (final path in const ['HANDOFF.md', 'docs/internal/handoff.md']) {
      expect(
        File(path).existsSync(),
        isFalse,
        reason:
            '$path is internal scratch, not a document a public reader '
            'should be handed',
      );
    }
  });

  test('the root points contributors at CONTRIBUTING, not the log', () {
    final contributing = File('CONTRIBUTING.md');
    expect(contributing.existsSync(), isTrue);
    final text = contributing.readAsStringSync();
    // The facts a contributor cannot guess and cannot work without.
    expect(text, contains('3.47.2'));
    expect(text, contains('flutter analyze'));
    expect(text, contains('--concurrency=1'));
    expect(text, contains('flutter_secure_storage'));

    final readme = File('README.md').readAsStringSync();
    expect(readme, contains('CONTRIBUTING.md'));
    expect(
      readme,
      isNot(contains('handoff.md')),
      reason: 'the docs index still links the removed engineering log',
    );
    expect(
      readme,
      isNot(contains('[HANDOFF.md](HANDOFF.md)')),
      reason: 'the docs index still links the removed root log',
    );

    // The facts the log used to be the only home for now live where a
    // contributor will actually look for them.
    expect(text, contains('flutter_animate'));
    expect(text, contains('validateSigningRelease'));
  });

  test('third-party agent skill packs are not carried in the tree', () {
    final ignore = File('.gitignore').readAsStringSync();
    expect(
      ignore.split('\n').map((line) => line.trim()),
      contains('.claude/skills/'),
      reason:
          'skill packs are third-party prompt content with their own '
          'licensing; committing them makes this repo responsible for it',
    );

    // Their provenance stays recorded so they can be reinstalled and so a
    // future pack has a bar to clear.
    final provenance = File('docs/internal/developer-skills.md');
    expect(provenance.existsSync(), isTrue);
    final text = provenance.readAsStringSync();
    expect(text, contains('motion-design'));
    expect(text, contains('remotion-motion-graphics'));
    expect(text, contains('SPDX'));
  });

  test('every bundled third-party component has a license text', () {
    final notices = File('THIRD_PARTY_NOTICES.md').readAsStringSync();
    final referenced = RegExp(
      r'LICENSES/([A-Za-z0-9._-]+\.txt)',
    ).allMatches(notices).map((match) => match.group(1)!).toSet();
    expect(referenced, isNotEmpty);
    for (final name in referenced) {
      expect(
        File('LICENSES/$name').existsSync(),
        isTrue,
        reason: 'THIRD_PARTY_NOTICES.md references a missing license text',
      );
    }

    final present = Directory('LICENSES')
        .listSync()
        .whereType<File>()
        .map((file) => file.uri.pathSegments.last)
        .toSet();
    expect(
      present.difference(referenced),
      isEmpty,
      reason: 'a license text sits in LICENSES/ that no notice accounts for',
    );
  });

  test('a public repository carries its governance files', () {
    for (final path in const [
      'SECURITY.md',
      'SUPPORT.md',
      'CODE_OF_CONDUCT.md',
      'CONTRIBUTING.md',
      'LICENSE',
      '.github/CODEOWNERS',
      '.github/dependabot.yml',
      '.github/PULL_REQUEST_TEMPLATE.md',
      '.github/ISSUE_TEMPLATE/config.yml',
      '.github/ISSUE_TEMPLATE/bug_report.yml',
      '.github/ISSUE_TEMPLATE/desktop_bug.yml',
      '.github/ISSUE_TEMPLATE/feature_request.yml',
    ]) {
      expect(File(path).existsSync(), isTrue, reason: '$path is missing');
    }

    // The two facts that make this app's reporting rules different from a
    // typical app's, and the one supported-version rule.
    final security = File('SECURITY.md').readAsStringSync();
    expect(security, contains('security/advisories/new'));
    expect(security, contains('shell-capable'));
    expect(security, contains('current preview'));

    // Security reports must not be routed into public issues.
    final config = File('.github/ISSUE_TEMPLATE/config.yml').readAsStringSync();
    expect(config, contains('blank_issues_enabled: false'));
    expect(config, contains('security/advisories/new'));
  });

  test('the non-affiliation statement reaches every public entry point', () {
    // Upstream asks third-party projects using the OpenCode name to say so.
    // It is asserted in-app by release_blockers_test; these are the repository
    // surfaces a reader or reporter meets first.
    for (final path in const [
      'README.md',
      'SECURITY.md',
      'SUPPORT.md',
      '.github/ISSUE_TEMPLATE/bug_report.yml',
      '.github/ISSUE_TEMPLATE/feature_request.yml',
      'THIRD_PARTY_NOTICES.md',
    ]) {
      final text = File(
        path,
      ).readAsStringSync().replaceAll(RegExp(r'[>*\s]+'), ' ');
      expect(
        text,
        contains(
          'not built, maintained, endorsed by, or affiliated with the '
          'official OpenCode team',
        ),
        reason: '$path does not state the project is unaffiliated',
      );
    }
  });

  test('the notice inventory matches the resolved dependency versions', () {
    // The notices shipped `record` 6.2.1 for a build that carried 7.1.1, and
    // reproduced the 6.2.1 text with it. Attribution that names the wrong
    // version is worse than no table, so pin the two together.
    final notices = File('THIRD_PARTY_NOTICES.md').readAsStringSync();
    final documented = <String, String>{
      for (final row in RegExp(
        r'^\| `([a-z0-9_]+)` \| ([^|]+?) \|',
        multiLine: true,
      ).allMatches(notices))
        row.group(1)!: row.group(2)!.trim(),
    };
    expect(documented, isNotEmpty);

    final lock = File('pubspec.lock').readAsLinesSync();
    final resolved = <String, String>{};
    String? current;
    var hosted = false;
    for (final line in lock) {
      final entry = RegExp(r'^  ([a-z0-9_]+):$').firstMatch(line);
      if (entry != null) {
        current = entry.group(1);
        hosted = false;
        continue;
      }
      if (current == null) continue;
      if (line == '    source: hosted') hosted = true;
      final version = RegExp(r'^    version: "(.+)"$').firstMatch(line);
      if (version != null && hosted) resolved[current] = version.group(1)!;
    }
    expect(resolved.length, greaterThan(100));

    for (final entry in resolved.entries) {
      expect(
        documented[entry.key],
        entry.value,
        reason:
            'THIRD_PARTY_NOTICES.md is out of date for ${entry.key}: '
            'pubspec.lock resolves ${entry.value}, the notice says '
            '${documented[entry.key] ?? "nothing"}',
      );
    }
    expect(
      documented.keys.toSet().difference(resolved.keys.toSet()),
      isEmpty,
      reason:
          'the notice inventory lists a package that is no longer '
          'resolved in pubspec.lock',
    );
  });
}

// ---------------------------------------------------------------------------
// G25 (docs/ux-system/revamp/STANDARDS.md §18): repository hygiene rules made
// mechanical. Rules: PROC-1, MOT-10, SEC-6, SEC-7, TEST-12, TEST-18.
//
// Absolute except TEST-12, which is a ratchet until the coordinator untracks
// the golden-failure artefacts committed before the gate existed (see
// docs/qa/gate-G25-2026-09-26/README.md). They are listed in
// test/repository_hygiene_baseline.json. The list may only shrink: a tracked
// `failures/` path not on it fails, a listed path that is no longer tracked
// fails (so it cannot be committed again later), and the list can never be
// longer than [_test12Ceiling].
//
// On CI (GITHUB_ACTIONS/CI set) the pinned-revision checks are skipped: the
// workflows install upstream Flutter because Shorebird's fork cannot be
// installed there. CI instead asserts it runs the one version every workflow
// pins. Provisional PROC-20 choice awaiting the coordinator; see the record.
// ---------------------------------------------------------------------------

/// The pinned Shorebird Flutter framework revision (AGENTS.md, PROC-1).
const _pinnedRevisionPrefix = '91f8bd75';

const _baselinePath = 'test/repository_hygiene_baseline.json';

/// The most TEST-12 baseline entries there may ever be: the 24 golden-failure
/// files tracked when the gate landed. Edits may only LOWER this number
/// (lower it whenever the baseline shrinks); raising it re-opens the ratchet.
const _test12Ceiling = 0;

/// True when the suite runs on a CI runner (GitHub Actions sets both).
final _onCi =
    Platform.environment['GITHUB_ACTIONS'] == 'true' ||
    Platform.environment['CI'] == 'true';

/// A reference to the banned package anywhere in a Dart file: plain,
/// multi-line and conditional (`if (dart.library.io) '…'`) directives alike.
/// Built from pieces so this file never matches itself.
final _animateUri = 'package:${'flutter'}_animate/';

final _failuresPath = RegExp(r'(^|/)failures/');

List<String> _trackedFiles() {
  final result = Process.runSync('git', const [
    'ls-files',
    '-z',
  ], stdoutEncoding: utf8);
  if (result.exitCode != 0) {
    fail('G25: `git ls-files` failed (${result.exitCode}): ${result.stderr}');
  }
  final files = (result.stdout as String)
      .split('\u0000')
      .where((path) => path.isNotEmpty)
      .toList();
  // An empty listing would make every absolute check pass vacuously.
  expect(files.length, greaterThan(100), reason: 'G25: git ls-files is empty');
  return files;
}

/// Kotlin/Groovy source with `/* … */` and `// …` comments removed, so a
/// commented-out setting neither satisfies nor trips a check.
String _withoutComments(String source) => source
    .replaceAll(RegExp(r'/\*.*?\*/', dotAll: true), '')
    .replaceAll(RegExp(r'//.*$', multiLine: true), '');

/// The root of the Flutter SDK running this test: FLUTTER_ROOT (exported by
/// the `flutter` launcher script), else the SDK that owns flutter_tester.
Directory _runningFlutterRoot() {
  final env = Platform.environment['FLUTTER_ROOT'];
  if (env != null && env.isNotEmpty) return Directory(env);
  var dir = File(Platform.resolvedExecutable).parent;
  while (dir.parent.path != dir.path) {
    if (File('${dir.path}/bin/cache/flutter.version.json').existsSync()) {
      return dir;
    }
    dir = dir.parent;
  }
  fail(
    'G25: cannot locate the running Flutter SDK (no FLUTTER_ROOT, and '
    '${Platform.resolvedExecutable} is not inside a Flutter checkout)',
  );
}

Map<String, dynamic> _runningFlutterVersion() {
  final root = _runningFlutterRoot();
  final versionFile = File('${root.path}/bin/cache/flutter.version.json');
  expect(
    versionFile.existsSync(),
    isTrue,
    reason: 'G25: ${versionFile.path} is missing',
  );
  return jsonDecode(versionFile.readAsStringSync()) as Map<String, dynamic>;
}

/// Every `flutter-version:` a tracked workflow installs, with
/// `${{ env.NAME }}` resolved from the same file, keyed by the value.
Map<String, List<String>> _workflowFlutterPins(List<String> tracked) {
  final pins = <String, List<String>>{};
  for (final path in tracked) {
    if (!RegExp(r'^\.github/workflows/[^/]+\.ya?ml$').hasMatch(path)) continue;
    final text = File(path).readAsStringSync();
    for (final match in RegExp(
      r'''^\s*flutter-version:\s*["']?(.+?)["']?\s*$''',
      multiLine: true,
    ).allMatches(text)) {
      var value = match.group(1)!;
      final env = RegExp(
        r'^\$\{\{\s*env\.([A-Za-z_]+)\s*\}\}$',
      ).firstMatch(value);
      if (env != null) {
        value =
            RegExp(
              '^\\s*${env.group(1)}:\\s*["\']?([^"\'\\s]+)["\']?\\s*\$',
              multiLine: true,
            ).firstMatch(text)?.group(1) ??
            'unresolved ${env.group(1)}';
      }
      (pins[value] ??= []).add(path);
    }
  }
  return pins;
}

void _g25() {
  group('G25 repository hygiene', () {
    test('MOT-10: flutter_animate is never a dependency or an import', () {
      final tracked = _trackedFiles();
      final offenders = <String>[];
      for (final path in tracked) {
        final pubspec = RegExp(r'(^|/)pubspec\.(yaml|lock)$').hasMatch(path);
        if (!pubspec && !path.endsWith('.dart')) continue;
        final file = File(path);
        if (!file.existsSync()) continue;
        final text = file.readAsStringSync();
        if (pubspec
            ? text.contains('flutter_animate')
            : text.contains(_animateUri)) {
          offenders.add(path);
        }
      }
      // The two manifests that matter most must have been scanned.
      expect(
        tracked,
        containsAll(const ['pubspec.yaml', 'pubspec.lock']),
        reason: 'G25: pubspec.yaml/pubspec.lock are not tracked',
      );
      expect(
        offenders,
        isEmpty,
        reason:
            'MOT-10: flutter_animate is banned (pending timers fail widget '
            'tests); use the framework animation APIs. Named in: $offenders',
      );
    });

    test('SEC-6, SEC-7: no skill packs, keystores or signing secrets '
        'and no screen recordings in docs/qa are tracked', () {
      final banned = <String, RegExp>{
        'SEC-7 .claude/skills/ is never committed': RegExp(
          r'^\.claude/skills/',
        ),
        'SEC-6 no keystore (*.jks)': RegExp(r'\.jks$', caseSensitive: false),
        'SEC-6 no keystore (*.keystore)': RegExp(
          r'\.keystore$',
          caseSensitive: false,
        ),
        'SEC-6 no android/key.properties': RegExp(r'^android/key\.properties$'),
        'no video files under docs/qa/': RegExp(
          r'^docs/qa/.*\.(mp4|webm|mov)$',
          caseSensitive: false,
        ),
      };
      final files = _trackedFiles();
      final problems = <String>[
        for (final rule in banned.entries)
          for (final path in files)
            if (rule.value.hasMatch(path)) '${rule.key}: $path is tracked',
      ];
      expect(problems, isEmpty, reason: problems.join('\n'));

      // The ignore rules that keep a local signing setup out of `git add`.
      final androidIgnore = File(
        'android/.gitignore',
      ).readAsLinesSync().map((line) => line.trim()).toSet();
      for (final rule in const [
        'key.properties',
        '**/*.jks',
        '**/*.keystore',
      ]) {
        expect(
          androidIgnore,
          contains(rule),
          reason: 'android/.gitignore no longer ignores $rule (SEC-6)',
        );
      }
    });

    test('TEST-12: golden-failure artefacts are never committed (ratchet)', () {
      final baseline =
          ((jsonDecode(File(_baselinePath).readAsStringSync())
                      as Map<String, dynamic>)['TEST-12']
                  as List<dynamic>)
              .cast<String>();
      expect(
        baseline.length,
        lessThanOrEqualTo(_test12Ceiling),
        reason:
            'TEST-12: $_baselinePath lists ${baseline.length} paths but the '
            'ratchet ceiling is $_test12Ceiling. The baseline may only '
            'shrink; untrack the new failure files instead of listing them',
      );
      final allowed = baseline.toSet();
      expect(
        allowed.length,
        baseline.length,
        reason: 'TEST-12: $_baselinePath lists a path twice',
      );
      final tracked = _trackedFiles().where(_failuresPath.hasMatch).toSet();

      final added = tracked.difference(allowed).toList()..sort();
      expect(
        added,
        isEmpty,
        reason:
            'TEST-12: golden-failure artefacts are committed. Untrack them '
            '(they are test output, not evidence):\n${added.join('\n')}',
      );

      final gone = allowed.difference(tracked).toList()..sort();
      final smaller = tracked.toList()..sort();
      expect(
        gone,
        isEmpty,
        reason:
            'TEST-12: the baseline shrank by ${gone.length}; a stale entry '
            'would let that path be committed again. Commit this list as '
            '"TEST-12" in $_baselinePath and lower _test12Ceiling to '
            '${smaller.length}:\n'
            '${const JsonEncoder.withIndent('  ').convert(smaller)}',
      );
    });

    test('the pubspec version is name+build with a positive build number', () {
      final version = RegExp(
        r'^version:\s*(\S+)\s*$',
        multiLine: true,
      ).firstMatch(File('pubspec.yaml').readAsStringSync())?.group(1);
      expect(version, isNotNull, reason: 'pubspec.yaml has no version line');
      expect(
        RegExp(r'^\d+\.\d+\.\d+\+[1-9]\d*$').hasMatch(version!),
        isTrue,
        reason: 'pubspec version "$version" is not X.Y.Z+N with N ≥ 1',
      );
    });

    test('PROC-1: the Android build targets compileSdk 37 and Java 17', () {
      const app = 'android/app/build.gradle.kts';
      final gradle = _withoutComments(File(app).readAsStringSync());
      final compileSdk = RegExp(
        r'^\s*compileSdk\s*=\s*(\S+)\s*$',
        multiLine: true,
      ).allMatches(gradle).map((m) => m.group(1)).toList();
      expect(compileSdk, ['37'], reason: '$app must set compileSdk = 37 once');
      for (final setting in <String, RegExp>{
        'sourceCompatibility = JavaVersion.VERSION_17': RegExp(
          r'sourceCompatibility\s*=\s*JavaVersion\.VERSION_17\b',
        ),
        'targetCompatibility = JavaVersion.VERSION_17': RegExp(
          r'targetCompatibility\s*=\s*JavaVersion\.VERSION_17\b',
        ),
        'jvmTarget = JvmTarget.JVM_17': RegExp(
          r'jvmTarget\s*=\s*(org\.jetbrains\.kotlin\.gradle\.dsl\.)?'
          r'JvmTarget\.JVM_17\b',
        ),
      }.entries) {
        expect(
          setting.value.hasMatch(gradle),
          isTrue,
          reason: '$app lost "${setting.key}" (Java 17)',
        );
      }

      // No Android build file may select any other Java version.
      final javaSettings = <RegExp>[
        RegExp(r'JavaVersion\.VERSION_(\w+)'),
        RegExp(r'JvmTarget\.JVM_(\w+)'),
        RegExp(r'''jvmTarget\s*=\s*["']([^"']+)["']'''),
        RegExp(r'jvmToolchain\(\s*(\d+)\s*\)'),
        RegExp(r'JavaLanguageVersion\.of\(\s*(\d+)\s*\)'),
      ];
      final others = <String>[];
      for (final path in _trackedFiles()) {
        if (!path.startsWith('android/')) continue;
        final file = File(path);
        if (!file.existsSync()) continue;
        if (RegExp(r'\.(kts|gradle)$').hasMatch(path)) {
          final text = _withoutComments(file.readAsStringSync());
          for (final pattern in javaSettings) {
            for (final match in pattern.allMatches(text)) {
              if (match.group(1) != '17') others.add('$path: ${match[0]}');
            }
          }
        } else if (path.endsWith('.properties')) {
          for (final line in file.readAsLinesSync()) {
            final home = RegExp(
              r'^\s*org\.gradle\.java\.home\s*[=:]\s*(.*)$',
            ).firstMatch(line);
            if (home != null &&
                !RegExp(r'(^|\D)17(\D|$)').hasMatch(home.group(1)!)) {
              others.add('$path: ${line.trim()}');
            }
          }
        }
      }
      expect(
        others,
        isEmpty,
        reason:
            'PROC-1: an Android build file selects a Java other than 17:'
            '\n${others.join('\n')}',
      );
    });

    test(
      'PROC-1, TEST-18: the running Flutter is the pinned revision and '
      'the widget-name ratchet was generated from it',
      () {
        final running =
            _runningFlutterVersion()['frameworkRevision'] as String?;
        expect(
          running,
          startsWith(_pinnedRevisionPrefix),
          reason:
              'PROC-1: tests run on Flutter revision $running from '
              '${_runningFlutterRoot().path}; use the pinned Shorebird '
              'Flutter 3.47.1 ($_pinnedRevisionPrefix…)',
        );

        final widgets =
            (jsonDecode(
                      File(
                        'test/kit_ratchet_flutter_widgets.json',
                      ).readAsStringSync(),
                    )
                    as Map<String, dynamic>)['flutterRevision']
                as String?;
        expect(
          widgets,
          running,
          reason:
              'TEST-18: test/kit_ratchet_flutter_widgets.json was generated '
              'from Flutter $widgets but tests run on $running; regenerate '
              'it with `dart run tool/kit/flutter_widget_names.dart`',
        );
      },
      skip: _onCi
          ? 'PROC-1 revision pin is local-only: CI installs upstream Flutter '
                '(Shorebird fork not installable on runners); the CI pin '
                'test below checks the upstream version instead'
          : false,
    );

    test('PROC-1: every CI workflow pins the same Flutter version, and CI '
        'runs it', () {
      final pins = _workflowFlutterPins(_trackedFiles());
      expect(
        pins,
        isNotEmpty,
        reason: 'G25: no workflow installs Flutter with `flutter-version:`',
      );
      expect(
        pins.keys.toList(),
        hasLength(1),
        reason:
            'PROC-1: the workflows install different Flutter versions: '
            '${pins.map((v, files) => MapEntry(v, files.toSet().toList()))}',
      );
      if (!_onCi) return;
      final running = _runningFlutterVersion()['frameworkVersion'] as String?;
      expect(
        running,
        pins.keys.single,
        reason:
            'PROC-1: this CI run uses Flutter $running but the workflows '
            'pin ${pins.keys.single}',
      );
    });
  });
}
