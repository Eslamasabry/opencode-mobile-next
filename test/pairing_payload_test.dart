import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/pairing.dart';

/// A stand-in for the real serve password. Never the value of a live server:
/// a test fixture that is also a working credential is a credential in git.
/// It keeps the 43-character base64url shape `opencode serve` generates —
/// nothing here depends on the entropy — while reading as obviously fake, so
/// neither a human nor a secret scanner has to guess whether it was live.
const _password = 'fixture-not-a-live-serve-password-000000000';

/// The exact wire shape `opencode2 pair` encodes into its QR, confirmed both
/// by reading the CLI's own `cli.pair` handler and by decoding a live QR from
/// a running service. Key order and spelling are the server's, not ours.
String pairJson({
  List<String> urls = const ['http://127.0.0.1:49374'],
  String username = 'opencode',
  String password = _password,
}) =>
    '{"urls":${jsonEncode(urls)},"username":${jsonEncode(username)},'
    '"password":${jsonEncode(password)}}';

ServerProbe _probeReturning(Map<String, ServerProbeResult> byUrl) {
  return ({required String baseUrl, String? username, String? password}) async {
    final result = byUrl[baseUrl];
    if (result == null) {
      fail('The probe was called for an address it should never reach: '
          '$baseUrl');
    }
    return result;
  };
}

const _refused = ServerProbeResult.failure(
  'The connection was refused. Is opencode serve running on that host and '
  'port?',
  suggestsMissingServer: true,
);
const _timedOut = ServerProbeResult.failure(
  'The connection timed out. Check the address, and that the server is '
  'reachable from this phone.',
  suggestsMissingServer: true,
);
const _v2Ok = ServerProbeResult.success(
  '0.0.0-beta-18600',
  flavor: ServerFlavor.v2,
);
const _passwordRejected = ServerProbeResult.failure(
  'Password rejected. Copy the current "server password" line from the '
  'server output — it changes on every restart unless OPENCODE_PASSWORD is '
  'set.',
  flavor: ServerFlavor.v2,
  needsPassword: true,
);

void main() {
  group('parsePairingPayload — the real payload', () {
    test('parses the exact shape opencode2 pair emits', () {
      final result = parsePairingPayload(pairJson());
      expect(result.ok, isTrue);
      expect(result.error, isNull);
      final payload = result.payload!;
      expect(payload.urls, ['http://127.0.0.1:49374']);
      expect(payload.username, 'opencode');
      expect(payload.password, _password);
    });

    test('keeps several addresses in the order the server reported them', () {
      final result = parsePairingPayload(
        pairJson(
          urls: ['http://127.0.0.1:4097', 'http://192.0.2.20:4097'],
        ),
      );
      expect(result.payload!.urls, [
        'http://127.0.0.1:4097',
        'http://192.0.2.20:4097',
      ]);
    });

    test('trims and de-duplicates addresses', () {
      final result = parsePairingPayload(
        pairJson(
          urls: [
            '  http://127.0.0.1:4097  ',
            'http://127.0.0.1:4097',
            '',
            'https://desk.example',
          ],
        ),
      );
      expect(result.payload!.urls, [
        'http://127.0.0.1:4097',
        'https://desk.example',
      ]);
    });

    test('surrounding whitespace from a clipboard round-trip is tolerated', () {
      final result = parsePairingPayload('\n  ${pairJson()}  \n\t');
      expect(result.ok, isTrue);
      expect(result.payload!.password, _password);
    });

    test('a payload without username still means opencode', () {
      final result = parsePairingPayload(
        '{"urls":["http://127.0.0.1:4097"],"password":"$_password"}',
      );
      expect(result.payload!.username, 'opencode');
    });

    test('a blank username falls back rather than authenticating as ""', () {
      final result = parsePairingPayload(pairJson(username: '   '));
      expect(result.payload!.username, 'opencode');
    });

    test('a non-default username is carried through, not assumed away', () {
      final result = parsePairingPayload(pairJson(username: 'someone-else'));
      expect(result.payload!.username, 'someone-else');
    });
  });

  group('parsePairingPayload — every failure is honest, none crash', () {
    void expectRejected(String raw, {String? because}) {
      final result = parsePairingPayload(raw);
      expect(result.ok, isFalse, reason: 'should have been rejected: $because');
      expect(result.payload, isNull);
      expect(result.error, isNotNull);
      expect(result.error, isNotEmpty);
    }

    test('empty and whitespace-only input', () {
      expectRejected('', because: 'empty');
      expectRejected('   \n\t ', because: 'whitespace');
    });

    test('malformed JSON', () {
      expectRejected('{"urls":[', because: 'truncated');
      expectRejected('{urls: [1,2,3]}', because: 'unquoted keys');
      expectRejected('not json at all', because: 'plain text');
      expectRejected('{"urls":["a"],}', because: 'trailing comma');
    });

    test('JSON that is not an object', () {
      expectRejected('["http://127.0.0.1:4097"]', because: 'array');
      expectRejected('"just a string"', because: 'string');
      expectRejected('42', because: 'number');
      expectRejected('null', because: 'null');
      expectRejected('true', because: 'bool');
    });

    test('missing or wrongly-typed urls', () {
      expectRejected(
        '{"username":"opencode","password":"$_password"}',
        because: 'no urls key',
      );
      expectRejected(
        '{"urls":"http://127.0.0.1:4097","password":"$_password"}',
        because: 'urls is a string',
      );
      expectRejected(
        '{"urls":{"0":"http://127.0.0.1:4097"},"password":"$_password"}',
        because: 'urls is an object',
      );
      expectRejected(
        '{"urls":[7],"password":"$_password"}',
        because: 'url entry is a number',
      );
      expectRejected(
        '{"urls":[null],"password":"$_password"}',
        because: 'url entry is null',
      );
    });

    test('an empty urls array — a shape the server really can emit', () {
      // The CLI prints `urls[0] ?? "(none)"`, so it knows this can happen.
      final result = parsePairingPayload(pairJson(urls: const []));
      expect(result.ok, isFalse);
      expect(result.error, contains('no server address'));
      // The message has to tell the user what to do about it, not just that
      // parsing failed.
      expect(result.error, contains('opencode2 pair'));
    });

    test('a urls array of nothing but blanks reads as empty', () {
      final result = parsePairingPayload(pairJson(urls: const ['', '   ']));
      expect(result.ok, isFalse);
      expect(result.error, contains('no server address'));
    });

    test('too many addresses', () {
      final many = List.generate(
        maxPairingUrls + 1,
        (i) => 'https://host$i.example',
      );
      expectRejected(pairJson(urls: many), because: 'over the url cap');
    });

    test('missing or wrongly-typed password', () {
      expectRejected(
        '{"urls":["http://127.0.0.1:4097"],"username":"opencode"}',
        because: 'no password key',
      );
      expectRejected(
        '{"urls":["http://127.0.0.1:4097"],"password":12345}',
        because: 'password is a number',
      );
      expectRejected(
        '{"urls":["http://127.0.0.1:4097"],"password":null}',
        because: 'password is null',
      );
      expectRejected(
        '{"urls":["http://127.0.0.1:4097"],"password":{"v":"x"}}',
        because: 'password is an object',
      );
    });

    test('wrongly-typed username', () {
      expectRejected(
        '{"urls":["http://127.0.0.1:4097"],"username":9,'
        '"password":"$_password"}',
        because: 'username is a number',
      );
    });

    test('enormous input is refused without being parsed', () {
      final huge = '{"urls":["${'a' * (maxPairingPayloadLength + 1)}"]}';
      final stopwatch = Stopwatch()..start();
      final result = parsePairingPayload(huge);
      stopwatch.stop();
      expect(result.ok, isFalse);
      expect(result.error, contains('too long'));
      // Rejected on length, so no megabyte ever reaches jsonDecode.
      expect(stopwatch.elapsedMilliseconds, lessThan(500));
    });

    test('a single enormous address inside an otherwise valid payload', () {
      final result = parsePairingPayload(
        '{"urls":["https://${'a' * 4000}.example"],"password":"$_password"}',
      );
      expect(result.ok, isFalse);
      expect(result.error, isNotNull);
    });

    test('deeply nested JSON does not blow the stack', () {
      final nested = '${'[' * 2000}${']' * 2000}';
      final result = parsePairingPayload(nested);
      expect(result.ok, isFalse);
    });

    test('no failure message ever echoes the input back', () {
      // A malformed payload may still contain a real password with one
      // character wrong. Quoting the input would put it on screen.
      const secret = 'sup3r-s3cret-value';
      final messages = <String?>[
        parsePairingPayload('{"urls":[],"password":"$secret"}').error,
        parsePairingPayload('{"password":"$secret"}').error,
        parsePairingPayload('{"urls":"x","password":"$secret"}').error,
        parsePairingPayload('{"urls":["a"],"password":$secret').error,
        parsePairingPayload(secret).error,
      ];
      for (final message in messages) {
        expect(message, isNotNull);
        expect(message, isNot(contains(secret)));
      }
    });
  });

  group('PairingPayload holds the credential carefully', () {
    test('toString redacts the password', () {
      final payload = parsePairingPayload(pairJson()).payload!;
      final rendered = payload.toString();
      expect(rendered, isNot(contains(_password)));
      expect(rendered, contains('<redacted>'));
      // The parts that are not secret stay visible — they are the useful
      // half of a diagnostic.
      expect(rendered, contains('127.0.0.1:49374'));
      expect(rendered, contains('opencode'));
    });

    test('consume drops the reference and toString says so', () {
      final payload = parsePairingPayload(pairJson()).payload!;
      expect(payload.isConsumed, isFalse);
      payload.consume();
      expect(payload.isConsumed, isTrue);
      expect(() => payload.password, throwsStateError);
      expect(payload.toString(), contains('<consumed>'));
      expect(payload.toString(), isNot(contains(_password)));
      // Consuming twice is not an error; the urls survive for display.
      payload.consume();
      expect(payload.urls, isNotEmpty);
    });

    test('urls cannot be mutated by a caller', () {
      final payload = parsePairingPayload(pairJson()).payload!;
      expect(() => payload.urls.add('https://evil.example'), throwsA(anything));
    });
  });

  group('looksLikePairingPayload', () {
    test('recognises a real payload', () {
      expect(looksLikePairingPayload(pairJson()), isTrue);
      expect(looksLikePairingPayload('  ${pairJson()}  '), isTrue);
    });

    test('recognises a malformed payload so we can explain it', () {
      // Better to claim it and reject it with a reason than to stay silent.
      expect(looksLikePairingPayload('{"urls":[],"password":"x"}'), isTrue);
    });

    test('ignores what people actually type into these fields', () {
      expect(looksLikePairingPayload(''), isFalse);
      expect(looksLikePairingPayload('http://127.0.0.1:4097'), isFalse);
      expect(looksLikePairingPayload('192.0.2.20:4096'), isFalse);
      expect(looksLikePairingPayload(_password), isFalse);
      expect(looksLikePairingPayload('server password $_password'), isFalse);
      expect(looksLikePairingPayload('{"foo":"bar"}'), isFalse);
    });

    test('ignores anything over the size cap', () {
      expect(
        looksLikePairingPayload('{"urls":${'x' * maxPairingPayloadLength}}'),
        isFalse,
      );
    });
  });

  group('selectPairingUrl — which address this device should dial', () {
    tearDown(() => debugPlatformCapabilities = null);

    test('a phone prefers a routable address over the server loopback', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['http://127.0.0.1:4097', 'https://desk.example:4097']),
      ).payload!;
      var order = <String>[];
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: ({required baseUrl, username, password}) async {
          order.add(baseUrl);
          return _v2Ok;
        },
      );
      // The server's own 127.0.0.1 is not the phone's; try the routable one
      // first, and stop as soon as it answers.
      expect(order, ['https://desk.example:4097']);
      expect(selection.chosenUrl, 'https://desk.example:4097');
      expect(selection.ok, isTrue);
      expect(selection.connected, isTrue);
    });

    test('a desktop prefers loopback — the server is this machine', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['https://desk.example:4097', 'http://127.0.0.1:4097']),
      ).payload!;
      var order = <String>[];
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: true,
        probe: ({required baseUrl, username, password}) async {
          order.add(baseUrl);
          return _v2Ok;
        },
      );
      expect(order, ['http://127.0.0.1:4097']);
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
    });

    test('the platform seam picks the default preference', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['http://127.0.0.1:4097', 'https://desk.example:4097']),
      ).payload!;

      debugPlatformCapabilities = const PlatformCapabilities.android();
      final onPhone = await selectPairingUrl(
        payload,
        probe: _probeReturning({'https://desk.example:4097': _v2Ok}),
      );
      expect(onPhone.chosenUrl, 'https://desk.example:4097');

      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final onDesktop = await selectPairingUrl(
        payload,
        probe: _probeReturning({'http://127.0.0.1:4097': _v2Ok}),
      );
      expect(onDesktop.chosenUrl, 'http://127.0.0.1:4097');
    });

    test('falls through to the next address when the first is refused',
        () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['https://desk.example:4097', 'http://127.0.0.1:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: _probeReturning({
          'https://desk.example:4097': _refused,
          'http://127.0.0.1:4097': _v2Ok,
        }),
      );
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
      expect(selection.connected, isTrue);
      // The failure of the first is still recorded, so the UI can say which
      // address it fell back from.
      final skipped = selection.outcomes.firstWhere(
        (o) => o.url == 'https://desk.example:4097',
      );
      expect(skipped.probed, isTrue);
      expect(skipped.reason, contains('refused'));
    });

    test('a cleartext LAN address is refused before any request is made',
        () async {
      // `opencode service set hostname 0.0.0.0` makes the server print one of
      // these. Dialing it would send HTTP Basic across the network in clear.
      final payload = parsePairingPayload(
        pairJson(
          urls: ['http://192.0.2.20:4097', 'http://127.0.0.1:4097'],
        ),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        // Only loopback may be probed; the fake fails the test otherwise.
        probe: _probeReturning({'http://127.0.0.1:4097': _v2Ok}),
      );
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
      final lan = selection.outcomes.firstWhere(
        (o) => o.url == 'http://192.0.2.20:4097',
      );
      expect(lan.probed, isFalse);
      expect(lan.reason, contains('HTTPS is required'));
      expect(lan.result, isNull);
    });

    test('a malformed address in the payload is reported, not dialed',
        () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['ftp://desk.example', 'http://127.0.0.1:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: _probeReturning({'http://127.0.0.1:4097': _v2Ok}),
      );
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
      final bad = selection.outcomes.firstWhere(
        (o) => o.url == 'ftp://desk.example',
      );
      expect(bad.probed, isFalse);
      expect(bad.reason, isNotNull);
    });

    test('when nothing answers, the reason is given per address', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['http://127.0.0.1:4097', 'https://desk.example:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: _probeReturning({
          'http://127.0.0.1:4097': _refused,
          'https://desk.example:4097': _timedOut,
        }),
      );
      expect(selection.ok, isFalse);
      expect(selection.chosenUrl, isNull);
      expect(selection.outcomes, hasLength(2));
      // The user has to be able to tell "run adb reverse" from "bind the
      // service to the network", and only the per-address verdicts say which.
      final detail = selection.failureDetail;
      expect(detail, contains('http://127.0.0.1:4097'));
      expect(detail, contains('refused'));
      expect(detail, contains('https://desk.example:4097'));
      expect(detail, contains('timed out'));
    });

    test('an address that answers 401 is still the right host', () async {
      // A stale pairing code against a restarted server: the host is correct
      // and "password rejected" is a far more useful verdict than "could not
      // connect to anything".
      final payload = parsePairingPayload(
        pairJson(urls: ['https://desk.example:4097', 'http://127.0.0.1:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: _probeReturning({
          'https://desk.example:4097': _passwordRejected,
          'http://127.0.0.1:4097': _refused,
        }),
      );
      expect(selection.ok, isTrue);
      expect(selection.chosenUrl, 'https://desk.example:4097');
      expect(selection.connected, isFalse);
      expect(selection.chosenResult!.needsPassword, isTrue);
    });

    test('a working address beats one that merely answers', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['https://desk.example:4097', 'http://127.0.0.1:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: _probeReturning({
          'https://desk.example:4097': _passwordRejected,
          'http://127.0.0.1:4097': _v2Ok,
        }),
      );
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
      expect(selection.connected, isTrue);
    });

    test('the payload credentials reach the probe unchanged', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['http://127.0.0.1:4097'], username: 'opencode'),
      ).payload!;
      String? sawUser;
      String? sawPassword;
      await selectPairingUrl(
        payload,
        preferLoopback: true,
        probe: ({required baseUrl, username, password}) async {
          sawUser = username;
          sawPassword = password;
          return _v2Ok;
        },
      );
      expect(sawUser, 'opencode');
      expect(sawPassword, _password);
    });

    test('a bare host from the payload is normalized before use', () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['127.0.0.1:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: true,
        probe: _probeReturning({'http://127.0.0.1:4097': _v2Ok}),
      );
      expect(selection.chosenUrl, 'http://127.0.0.1:4097');
    });

    test('every address rejected up front still yields per-address reasons',
        () async {
      final payload = parsePairingPayload(
        pairJson(urls: ['http://192.0.2.20:4097', 'http://198.51.100.4:4097']),
      ).payload!;
      final selection = await selectPairingUrl(
        payload,
        preferLoopback: false,
        probe: ({required baseUrl, username, password}) async {
          fail('No address here is safe to dial: $baseUrl');
        },
      );
      expect(selection.ok, isFalse);
      expect(selection.outcomes, hasLength(2));
      expect(selection.outcomes.every((o) => !o.probed), isTrue);
      expect(selection.failureDetail, contains('192.0.2.20'));
      expect(selection.failureDetail, contains('198.51.100.4'));
    });
  });

  group('orderPairingCandidates', () {
    test('splits loopback from routable and honours the preference', () {
      const urls = [
        'https://desk.example',
        'http://127.0.0.1:4097',
        'http://localhost:4097',
        'https://other.example',
      ];
      expect(orderPairingCandidates(urls, preferLoopback: true), [
        'http://127.0.0.1:4097',
        'http://localhost:4097',
        'https://desk.example',
        'https://other.example',
      ]);
      expect(orderPairingCandidates(urls, preferLoopback: false), [
        'https://desk.example',
        'https://other.example',
        'http://127.0.0.1:4097',
        'http://localhost:4097',
      ]);
    });

    test('is stable within each group', () {
      const urls = ['https://b.example', 'https://a.example'];
      expect(
        orderPairingCandidates(urls, preferLoopback: false),
        urls,
      );
    });

    test('an unparseable address is treated as routable, not dropped', () {
      final ordered = orderPairingCandidates(
        const ['::::', 'http://127.0.0.1:4097'],
        preferLoopback: true,
      );
      expect(ordered, hasLength(2));
      expect(ordered.first, 'http://127.0.0.1:4097');
    });
  });
}
