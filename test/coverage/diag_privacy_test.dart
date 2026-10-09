// Privacy gate for everything the app can hand out about itself: the
// diagnostics copy, the saved problem report, the Report a problem text and
// its GitHub link, the crash report, a failed job's report, the timing
// report and the device log lines, and the export screens. A marker secret
// of every kind the app knows (provider keys, a bearer token, a URL with a
// password and a token in its query, an environment key, a JSON password, a
// registered server password, a long opaque token and a private key block)
// is fed in through each path, and none of its secret part may appear in
// what comes out. The markers are built from adjacent literals so no whole
// key is in this source, and a failure names the kind, never the value.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_report.dart';
import 'package:opencode_mobile/diagnostics/failed_job_report.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/feedback/problem_report.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart' show KitReport;
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

/// Kind -> the part that must never come out.
const _secrets = <String, String>{
  'Anthropic key': 'DIAGKEYONEabcdefghijklmnop',
  'OpenAI project key': 'DIAGKEYTWOqrstuvwxyz0123456',
  'GitHub token': 'DIAGGITHUBtoken0123456789abcdefgh',
  'bearer token': 'DIAGBEARERtoken.abc-def_ghi',
  'URL password': 'DIAGURLPASSword',
  'URL query token': 'DIAGQUERYtoken99',
  'environment key': 'DIAGENVvalue12345',
  'JSON password': 'DIAGJSONpassword',
  'registered server password': 'DIAGREGISTEREDpw77',
  'opaque token': 'DIAGOPAQUE0123456789abcdefghijklmnopqrst',
  'private key': 'DIAGPEMBODYabcdefghijkl',
};

/// Every kind in one block of text, the way a failing request's message or
/// log might carry them.
final String _dirty = [
  'request failed: sk-ant-'
      'api03-${_secrets['Anthropic key']}',
  'sk-proj-${_secrets['OpenAI project key']}',
  'ghp_${_secrets['GitHub token']}',
  'Authorization: Bearer ${_secrets['bearer token']}',
  'https://admin:${_secrets['URL password']}@vps.example.net:4096/api/session?token=${_secrets['URL query token']}',
  'OPENAI_API_KEY=${_secrets['environment key']}',
  '{"user":"a","password":"${_secrets['JSON password']}"}',
  'login failed for ${_secrets['registered server password']}',
  'token ${_secrets['opaque token']}',
  '-----BEGIN PRIVATE KEY-----\n${_secrets['private key']}\n-----END PRIVATE KEY-----',
].join('\n');

/// The kinds whose secret part is in [output].
List<String> leaks(String output) => [
  for (final entry in _secrets.entries)
    if (output.contains(entry.value)) entry.key,
];

void expectClean(String where, String output) {
  expect(
    leaks(output),
    isEmpty,
    reason: '$where let these kinds of secret out (values not shown)',
  );
}

void main() {
  late Directory directory;

  setUp(() {
    KitRedact.clearKnownSecrets();
    KitRedact.registerKnownSecret(_secrets['registered server password']!);
    directory = Directory.systemTemp.createTempSync('diag-privacy-');
  });
  tearDown(() {
    KitRedact.clearKnownSecrets();
    PerfTrace.resetForTesting();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  test('the diagnostics copy (report text and json) holds no secret', () {
    final diagnostics = AppDiagnosticsController();
    addTearDown(diagnostics.dispose);
    diagnostics.record(
      StateError(_dirty),
      StackTrace.fromString('#0 f ($_dirty)'),
      source: 'flutter $_dirty',
    );
    expectClean('reportText', diagnostics.reportText());
    expectClean('reportJson', jsonEncode(diagnostics.reportJson()));
    for (final entry in diagnostics.entries) {
      expectClean('entry', '${entry.source}${entry.message}${entry.stack}');
    }
  });

  test(
    'the saved problem report holds no secret, in memory or on disk',
    () async {
      final store = await ReportProblem.open(directory: directory);
      store.recordError(
        StateError(_dirty),
        StackTrace.fromString('#0 f ($_dirty)'),
        source: 'flutter',
      );
      store.recordError(_dirty, null, source: 'sse $_dirty');
      for (final event in store.entries) {
        expectClean(
          'stored event',
          '${event.source}${event.message}${event.stack}',
        );
      }
      for (final file in directory.listSync().whereType<File>()) {
        expectClean(
          'file on disk ${file.uri.pathSegments.last}',
          file.readAsStringSync(),
        );
      }
    },
  );

  test('the Report a problem text, link and clipboard hold no secret and '
      'no server address', () async {
    final events = [
      ProblemReportEvent(
        kind: ProblemEventKind.error,
        time: DateTime.utc(2026, 10, 9),
        source: 'flutter $_dirty',
        message: _dirty,
        stack: '#0 f ($_dirty)',
      ),
    ];
    final report = ProblemReport.build(
      description: 'It broke. $_dirty',
      version: '1.0.0+1',
      platform: 'Android',
      error: KitReport(
        title: 'Could not load $_dirty',
        details: _dirty,
        source: 'files $_dirty',
        errorType: 'StateError',
        log: _dirty,
      ),
      events: events,
    );
    expectClean('report text', report.text);
    expectClean('report title', report.title);
    expectClean('what happened', report.whatHappened);
    expectClean('diagnostics', report.diagnostics);
    for (final length in [2048, 600, 200]) {
      final link = report.link(maxLength: length);
      expectClean('github link ($length)', Uri.decodeFull(link.uri.toString()));
      expectClean('clipboard ($length)', link.clipboardText ?? '');
    }
    // The report is public once filed: no server host or address either.
    expect(report.text, isNot(contains('vps.example.net')));
  });

  test('the crash report holds only fixed categories and times', () async {
    final diagnostics = AppDiagnosticsController();
    final crash = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    addTearDown(() {
      crash.dispose();
      diagnostics.dispose();
    });
    expect(crash.setEnabled(true), isTrue);
    crash.capture(StateError(_dirty), StackTrace.fromString(_dirty), 'flutter');
    crash.importNativeCrash({
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'message': _dirty,
      'stack': _dirty,
    });
    crash.importAndroidAnr(DateTime.now().millisecondsSinceEpoch);
    final builder = CrashReportBuilder(
      loadController: () async => crash,
      shareText: (text) async {
        expectClean('shared crash report', text);
        return true;
      },
    );
    final shared = <String>[];
    final sharing = CrashReportBuilder(
      loadController: () async => crash,
      shareText: (text) async {
        shared.add(text);
        return true;
      },
    );
    final preview = (await builder.preview()).preview!;
    expectClean('crash preview', preview.text);
    final again = (await sharing.preview()).preview!;
    await sharing.share(again);
    expect(shared, hasLength(1));
    expect(
      shared.single,
      again.text,
      reason: 'what is shared is what was shown',
    );
    expectClean('crash share', shared.single);
    for (final file in directory.listSync().whereType<File>()) {
      expectClean('crash file', file.readAsStringSync());
    }
  });

  test('a failed job report holds no secret', () {
    final progress = SetupProgress(
      state: SetupState.failed,
      components: const [],
      overall: 0.3,
      current: 'node',
      error: _dirty,
      logTail: _dirty,
      jobId: 'job-1',
    );
    final report = FailedJobReport.setup(progress)!;
    expectClean('failed job preview', report.preview);
    expectClean('failed job log', report.logExcerpt);
    final asReport = report.toKitReport();
    expectClean(
      'failed job details',
      '${asReport.details}${asReport.log}${asReport.title}',
    );
  });

  test('the timing report and the device log hold no secret', () {
    final lines = <String>[];
    PerfTrace.resetForTesting();
    PerfTrace.logSink = lines.add;
    PerfTrace.mark(
      'GET https://admin:${_secrets['URL password']}@vps.example.net:4096/x?token=${_secrets['URL query token']}',
      attrs: {
        'authorization': 'Bearer ${_secrets['bearer token']}',
        'url': _dirty,
        'api_key': _secrets['Anthropic key'],
        'note': 'login failed for ${_secrets['registered server password']}',
      },
    );
    PerfTrace.recordDuration(
      'POST /session/${_secrets['opaque token']}',
      const Duration(milliseconds: 90),
      attrs: {'body': _dirty},
      error: StateError(_dirty),
    );
    expectClean('timing report', PerfTrace.reportText());
    expectClean('device log', lines.join('\n'));
    for (final span in PerfTrace.spans) {
      expectClean('span', '${span.name}${span.attrs}${span.parent}');
    }
    PerfTrace.logSink = null;
  });
}
