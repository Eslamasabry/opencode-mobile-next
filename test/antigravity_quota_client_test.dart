import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/quota/antigravity_quota_client.dart';

void main() {
  group('AntigravityQuotaClient', () {
    final testNow = DateTime.utc(2026, 11, 7, 20, 0, 0);

    test(
      'parseCliJson parses both Gemini and Claude & GPT groups accurately',
      () {
        final cliData = {
          'command': {
            'name': 'usage',
            'data': {
              'groups': [
                {
                  'name': 'Gemini Models',
                  'description':
                      'Models within this group: Gemini Flash, Gemini Pro',
                  'buckets': [
                    {
                      'id': 'gemini-weekly',
                      'name': 'Weekly Limit Remaining',
                      'window': 'weekly',
                      'remaining_fraction': 0.6815,
                      'reset_time': '2026-11-14T07:25:34Z',
                    },
                    {
                      'id': 'gemini-5h',
                      'name': 'Five Hour Limit Remaining',
                      'window': '5h',
                      'remaining_fraction': 0.4579,
                      'reset_time': '2026-11-07T21:48:21Z',
                    },
                  ],
                },
                {
                  'name': 'Claude and GPT models',
                  'description':
                      'Models within this group: Claude Opus, Claude Sonnet, GPT-OSS',
                  'buckets': [
                    {
                      'id': '3p-weekly',
                      'name': 'Weekly Limit Remaining',
                      'window': 'weekly',
                      'remaining_fraction': 0.0,
                      'reset_time': '2026-11-11T10:05:57Z',
                    },
                    {
                      'id': '3p-5h',
                      'name': 'Five Hour Limit Remaining',
                      'window': '5h',
                      'disabled': true,
                      'remaining_fraction': 1.0,
                    },
                  ],
                },
              ],
            },
          },
        };

        final readings = AntigravityQuotaClient.parseCliJson(
          cliData,
          currentNow: testNow,
        );

        expect(readings.length, 2);

        // 1. Gemini Models
        final gemini = readings[0];
        expect(gemini.providerId, 'antigravity');
        expect(gemini.displayName, 'Antigravity (Gemini Models)');
        expect(gemini.windows.length, 2);
        expect(gemini.windows[0].windowId, '5h');
        expect(gemini.windows[0].remainingPercent, 46);
        expect(gemini.windows[0].durationSeconds, 18000);
        expect(
          gemini.windows[0].resetsAt,
          DateTime.utc(2026, 11, 7, 21, 48, 21),
        );
        expect(gemini.windows[1].windowId, 'weekly');
        expect(gemini.windows[1].remainingPercent, 68);
        expect(gemini.windows[1].durationSeconds, 604800);
        expect(
          gemini.windows[1].resetsAt,
          DateTime.utc(2026, 11, 14, 7, 25, 34),
        );

        // 2. Claude and GPT models
        final claude = readings[1];
        expect(claude.providerId, 'antigravity_claude');
        expect(claude.displayName, 'Antigravity (Claude and GPT models)');
        expect(claude.windows.length, 2);
        expect(claude.windows[0].windowId, '5h');
        // Disabled 5h bucket must report 0%, NOT 100%!
        expect(claude.windows[0].remainingPercent, 0);
        expect(claude.windows[1].windowId, 'weekly');
        expect(claude.windows[1].remainingPercent, 0);
        expect(
          claude.windows[1].resetsAt,
          DateTime.utc(2026, 11, 11, 10, 5, 57),
        );
      },
    );

    test(
      'fetchAllUsage runs the CLI inside built-in Linux and parses it',
      () async {
        final cliData = {
          'command': {
            'name': 'usage',
            'data': {
              'groups': [
                {
                  'name': 'Gemini Models',
                  'buckets': [
                    {
                      'id': 'gemini-5h',
                      'window': '5h',
                      'remaining_fraction': 0.45,
                      'reset_time': '2026-11-07T21:48:21Z',
                    },
                  ],
                },
              ],
            },
          },
        };

        final scripts = <String>[];
        final client = AntigravityQuotaClient(
          runner: (script, {timeout = const Duration(seconds: 20)}) async {
            scripts.add(script);
            return (exitCode: 0, output: 'agy 1.5.3\n${jsonEncode(cliData)}');
          },
          clock: () => testNow,
        );

        final readings = await client.fetchAllUsage(profileId: 'p1');
        expect(readings.length, 1);
        expect(readings[0].providerId, 'antigravity');
        expect(readings[0].windows[0].remainingPercent, 45);
        expect(
          scripts.single,
          contains('/home/oc/.oc-profiles/p1/.local/bin/agy'),
        );
        expect(scripts.single, contains('-p /usage --output-format json'));
        expect(client.cachedReadings, readings);
      },
    );

    test(
      'no CLI, not signed in or unreadable output reads as no readings',
      () async {
        for (final res in [
          (exitCode: 127, output: ''),
          (exitCode: 1, output: 'Please sign in'),
          (exitCode: 0, output: 'not json'),
        ]) {
          final client = AntigravityQuotaClient(
            runner: (script, {timeout = const Duration(seconds: 20)}) async =>
                res,
          );
          expect(await client.fetchAllUsage(), isEmpty);
        }
        expect(await AntigravityQuotaClient().fetchAllUsage(), isEmpty);
      },
    );

    test('a profile id that is not a plain id never reaches the script', () {
      final script = AntigravityQuotaClient.cliScript(
        profileId: r'x; rm -rf /',
      );
      expect(script, isNot(contains('rm -rf')));
      expect(script, contains('/home/oc/.local/bin/agy'));
    });

    test(
      'ACCOUNT_EMAIL line in CLI output populates accountRef on readings',
      () async {
        final cliData = {
          'command': {
            'name': 'usage',
            'data': {
              'groups': [
                {
                  'name': 'Gemini Models',
                  'buckets': [
                    {
                      'id': 'gemini-5h',
                      'window': '5h',
                      'remaining_fraction': 0.85,
                      'reset_time': '2026-11-07T21:48:21Z',
                    },
                  ],
                },
              ],
            },
          },
        };

        final client = AntigravityQuotaClient(
          runner: (script, {timeout = const Duration(seconds: 20)}) async => (
            exitCode: 0,
            output: 'ACCOUNT_EMAIL:altaf.alu@gmail.com\n${jsonEncode(cliData)}',
          ),
          clock: () => testNow,
        );

        final readings = await client.fetchAllUsage(profileId: 'p1');
        expect(readings.length, 1);
        expect(readings[0].providerId, 'antigravity');
        expect(readings[0].accountRef, 'altaf.alu@gmail.com');
      },
    );

    test('cliScript checks candidate token files for id_token_email', () {
      final script = AntigravityQuotaClient.cliScript(profileId: 'p1');
      expect(script, contains('antigravity-oauth-token'));
      expect(script, contains('id_token_email'));
      expect(script, contains('ACCOUNT_EMAIL'));
      expect(script, contains('export HOME='));
      // Never touches or leaks access/refresh tokens
      expect(script, isNot(contains('access_token')));
      expect(script, isNot(contains('refresh_token')));
    });

    test('readLocalAccount reads only email without credentials', () {
      final email = AntigravityQuotaClient.readLocalAccount();
      if (email != null) {
        expect(email, contains('@'));
        expect(email, isNot(contains('access_token')));
        expect(AntigravityQuotaClient.lastKnownAccountEmail, email);
      }
    });

    test('readLocalAccountAsync resolves email safely', () async {
      final email = await AntigravityQuotaClient.readLocalAccountAsync();
      if (email != null) {
        expect(email, contains('@'));
        expect(email, isNot(contains('access_token')));
      }
    });
  });
}
