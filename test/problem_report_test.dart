// Report a problem (slice-P8.2): what a report carries and how it reaches
// GitHub. The redaction cases use fake keys only; none is a real credential.
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/feedback/problem_report.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/widgets/external_link.dart';
import 'package:package_info_plus/package_info_plus.dart';

// Fake credentials, split so no scanner mistakes this file for a leak.
const _fakeAnthropic =
    'sk-ant-'
    'api03-FAKEFAKEFAKEFAKEFAKE1234567890';
const _fakeOpenAi =
    'sk-proj-'
    'FAKEfakeFAKEfake0987654321zzzz';
const _fakeBearer =
    'Bearer '
    'eyFAKEtokenFAKEtokenFAKEtoken12345';
const _fakePassword = 'hunter2-fake-server-pass';
const _fakeRegistered = 'loaded-provider-secret-FAKE-42';

/// What `/config/providers` returns, key and all.
const _providersJson =
    '{"providers":[{"id":"anthropic","options":{"apiKey":"$_fakeAnthropic"}},'
    '{"id":"openai","key":"$_fakeOpenAi"}]}';

ProblemReportEvent _event(
  String message, {
  ProblemEventKind kind = ProblemEventKind.error,
  String stack = '',
  String source = 'flutter',
  int minute = 0,
}) => ProblemReportEvent(
  kind: kind,
  time: DateTime.utc(2026, 9, 27, 10, minute),
  source: source,
  message: message,
  stack: stack,
);

void main() {
  setUp(KitRedact.clearKnownSecrets);
  tearDown(() {
    KitRedact.clearKnownSecrets();
    debugPlatformCapabilities = null;
  });

  group('what a report carries', () {
    test('title, description, the attached error, environment and '
        'diagnostics, newest first', () {
      final report = ProblemReport.build(
        description: 'Tapped Send\nNothing happened',
        version: '1.0.44+50',
        platform: 'Android',
        error: const KitReport(
          title: "Couldn't load files",
          details: 'FormatException: bad listing',
          source: 'files-viewer',
          errorType: 'FormatException',
        ),
        events: [
          _event('newest', minute: 5, stack: '#0 main (lib/a.dart:1)'),
          _event(
            'OCTRACE chat.open 120ms',
            kind: ProblemEventKind.timing,
            source: 'OCTRACE',
            minute: 4,
          ),
          _event('oldest', minute: 1),
        ],
      );

      expect(report.title, "Couldn't load files");
      expect(report.version, '1.0.44+50');
      expect(report.whatHappened, startsWith('Tapped Send\nNothing happened'));
      // The error's title is the report's title, not repeated below.
      expect(report.whatHappened, contains('Error\nType: FormatException'));
      expect(report.whatHappened, isNot(contains("Couldn't load files")));
      expect(report.whatHappened, contains('Where: files-viewer'));
      expect(report.whatHappened, contains('App: 1.0.44+50'));
      expect(report.whatHappened, contains('Platform: Android'));
      expect(
        report.diagnostics.indexOf('newest'),
        lessThan(report.diagnostics.indexOf('oldest')),
      );
      expect(report.diagnostics, contains('[2026-09-27 10:05:00 UTC] error'));
      expect(report.diagnostics, contains('    #0 main (lib/a.dart:1)'));
      // The previewed text is exactly the parts, in order.
      expect(
        report.text,
        '${report.title}\n\n${report.whatHappened}\n\n'
        'Diagnostics\n${report.diagnostics}',
      );
    });

    test('the connection goes in the environment, scrubbed; with nothing '
        'attached or said, the newest kept error titles it', () {
      final report = ProblemReport.build(
        description: '',
        version: '1.4.0+54',
        platform: 'Android',
        connection: 'OpenCode 2.0.28 · on this phone · connected',
        fallbackTitle: 'Lost the live connection to the server',
      );
      expect(report.title, 'Lost the live connection to the server');
      expect(
        report.whatHappened,
        endsWith(
          'Platform: Android\n'
          'Connection: OpenCode 2.0.28 · on this phone · connected',
        ),
      );

      // The person's own words still win over the kept error, and a
      // connection line never carries an address.
      final said = ProblemReport.build(
        description: 'Send did nothing',
        version: '1.4.0+54',
        platform: 'Android',
        connection: 'http://100.70.12.4:4096 · connected',
        fallbackTitle: 'Lost the live connection to the server',
      );
      expect(said.title, 'Send did nothing');
      expect(said.whatHappened, isNot(contains('100.70.12.4')));

      final none = ProblemReport.build(
        description: '',
        version: '1.4.0+54',
        platform: 'Android',
      );
      expect(none.title, 'Problem report');
      expect(none.whatHappened, isNot(contains('Connection:')));
    });

    test('a failed job report carries no OCTRACE timings: its log ends '
        'with the real error, not status polls', () {
      final report = ProblemReport.build(
        description: '',
        version: '1.0.44+62',
        platform: 'Android',
        error: const KitReport(
          title: 'OpenCode was installed, but it did not start.',
          source: 'failed job · setup · setup-1',
          log:
              '==> Getting the model list\n'
              '[oc] OpenCode was installed but did not start',
        ),
        events: [
          for (var i = 0; i < 12; i++)
            _event(
              'OCTRACE 2.9ms linux.setupStatus',
              kind: ProblemEventKind.timing,
              source: 'OCTRACE',
              minute: i,
            ),
          _event('StateError: kept', minute: 20),
        ],
      );
      expect(report.diagnostics, isNot(contains('OCTRACE')));
      expect(report.diagnostics, isNot(contains('timing ·')));
      expect(
        report.diagnostics,
        contains('[oc] OpenCode was installed but did not start'),
      );
      // Errors around the failure still come along.
      expect(report.diagnostics, contains('StateError: kept'));

      // Without a failed job, the newest timings still help.
      final plain = ProblemReport.build(
        description: 'slow',
        version: '1.0.44+62',
        platform: 'Android',
        events: [
          _event(
            'OCTRACE chat.open 120ms',
            kind: ProblemEventKind.timing,
            source: 'OCTRACE',
          ),
        ],
      );
      expect(plain.diagnostics, contains('OCTRACE chat.open'));
    });

    test('without an error, the first line of the description is the title; '
        'without diagnostics there is no Diagnostics part', () {
      final report = ProblemReport.build(
        description: '  The terminal froze after rotating\nmore words ',
        version: '1.0.44+50',
        platform: 'Android',
      );
      expect(report.title, 'The terminal froze after rotating');
      expect(report.diagnostics, isEmpty);
      expect(report.text, isNot(contains('Diagnostics')));
      // Nothing written twice: the first line already is the title.
      expect(report.text, startsWith('The terminal froze after rotating\n'));
      expect(
        'The terminal froze after rotating'.allMatches(report.text),
        hasLength(1),
      );
    });

    test('keeps a bounded number of errors, timings and stack lines', () {
      final report = ProblemReport.build(
        description: 'x',
        version: '1',
        platform: 'Android',
        maxErrors: 2,
        maxTimings: 1,
        maxStackLines: 2,
        events: [
          _event('e1', stack: 'f1\nf2\nf3\nf4'),
          _event('t1', kind: ProblemEventKind.timing),
          _event('t2', kind: ProblemEventKind.timing),
          _event('e2'),
          _event('e3'),
        ],
      );
      expect(report.diagnostics, contains('e1'));
      expect(report.diagnostics, contains('e2'));
      expect(report.diagnostics, isNot(contains('e3')));
      expect(report.diagnostics, contains('t1'));
      expect(report.diagnostics, isNot(contains('t2')));
      expect(report.diagnostics, contains('    f2'));
      expect(report.diagnostics, isNot(contains('    f3')));
      expect(report.diagnostics, contains('… 2 more lines'));
    });

    test('the platform label and the version fallback', () async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.windows,
      );
      expect(
        bugReportPlatformLabel(),
        'Windows desktop (experimental — contributor-tested)',
      );
      expect(
        await problemReportAppVersion(
          info: PackageInfo(
            appName: 'opencode_mobile',
            packageName: 'io.github.eslamasabry.opencode_mobile',
            version: '1.0.31',
            buildNumber: '32',
          ),
        ),
        '1.0.31+32',
      );
    });
  });

  group('never a credential or a server address', () {
    test('fake provider keys, a bearer token, a password, a registered '
        'secret and server addresses are absent from every part and the '
        'link', () {
      KitRedact.registerKnownSecret(_fakeRegistered);
      final dirty =
          'GET https://phone.tail1234.ts.net:4096/config/providers '
          'failed at http://192.168.1.20:4096/session?x=1\n'
          'then tried 10.0.0.7:4096 directly\n'
          'Authorization: $_fakeBearer\n'
          'password=$_fakePassword\n'
          'loaded $_fakeRegistered\n'
          '$_providersJson';
      final report = ProblemReport.build(
        description: 'Setup broke. $dirty',
        version: '1.0.44+50',
        platform: 'Android',
        error: KitReport(title: 'Sync failed', details: dirty),
        events: [_event(dirty, stack: dirty, source: 'sse $_fakeOpenAi')],
      );
      final link = report.link(maxLength: 100000);
      for (final part in [
        report.text,
        link.uri.toString(),
        Uri.decodeFull(link.uri.toString()),
        ...link.uri.queryParameters.values,
      ]) {
        for (final secret in [
          _fakeAnthropic,
          _fakeOpenAi,
          'eyFAKEtokenFAKEtokenFAKEtoken12345',
          _fakePassword,
          _fakeRegistered,
          'phone.tail1234.ts.net',
          '192.168.1.20',
          '10.0.0.7',
        ]) {
          expect(part, isNot(contains(secret)), reason: secret);
        }
      }
      // The path says which call failed; the host is gone.
      expect(report.text, contains('https://[server]/config/providers'));
      expect(report.text, contains('[address]'));
    });

    test('the persisted store and the in-memory list feed the same events, '
        'newest first', () async {
      final diagnostics = AppDiagnosticsController()
        ..record(StateError('first'), null, source: 'flutter')
        ..record(StateError('second'), null, source: 'sse');
      final memory = problemReportEvents(diagnostics: diagnostics);
      expect(memory.map((e) => e.message), [
        'Bad state: second',
        'Bad state: first',
      ]);
      expect(memory.every((e) => e.isError), isTrue);
    });
  });

  group('the GitHub link', () {
    ProblemReport big({int events = 40, String description = 'It broke'}) =>
        ProblemReport.build(
          description: description,
          version: '1.0.44+50',
          platform: 'Android',
          events: [
            for (var i = 0; i < events; i++)
              _event('error number $i with some words', stack: 'frame $i'),
          ],
          maxErrors: events,
        );

    test('a short report is prefilled whole and passes openExternalLink\'s '
        'policy', () {
      final report = ProblemReport.build(
        description: 'Short',
        version: '1.0.44+50',
        platform: 'Android',
        events: [_event('one error')],
      );
      final link = report.link();
      expect(link.clipboard, ProblemReportClipboard.none);
      expect(link.clipboardText, isNull);
      final uri = link.uri;
      expect(safeExternalLinkUri(uri.toString()), isNotNull);
      expect(uri.host, 'github.com');
      expect(uri.path, '/Eslamasabry/opencode-mobile-next/issues/new');
      expect(uri.queryParameters['template'], 'bug_report.yml');
      expect(uri.queryParameters['title'], report.title);
      expect(uri.queryParameters['app-version'], '1.0.44+50');
      expect(uri.queryParameters['what-happened'], report.whatHappened);
      expect(uri.queryParameters['logs'], report.diagnostics);
    });

    test('long diagnostics go to the clipboard and the form says to paste '
        'them; the link still fits', () {
      final report = big();
      final link = report.link();
      expect(link.clipboard, ProblemReportClipboard.diagnostics);
      expect(link.clipboardText, report.diagnostics);
      expect(link.uri.toString().length, lessThanOrEqualTo(2048));
      expect(safeExternalLinkUri(link.uri.toString()), isNotNull);
      expect(link.uri.queryParameters['what-happened'], report.whatHappened);
      expect(link.uri.queryParameters['logs'], contains('paste them here'));
    });

    test('a very long description copies the whole report', () {
      final report = big(description: 'word ' * 800);
      final link = report.link();
      expect(link.clipboard, ProblemReportClipboard.whole);
      expect(link.clipboardText, report.text);
      expect(link.uri.toString().length, lessThanOrEqualTo(2048));
      expect(
        link.uri.queryParameters['what-happened'],
        contains('paste it here'),
      );
    });
  });
}
