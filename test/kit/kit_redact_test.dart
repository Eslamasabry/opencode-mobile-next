// KitRedact: secrets are masked for display and copy; technical values that
// merely look long (paths, SHAs, UUIDs, words) pass through untouched.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

const m = KitRedact.mask;

void main() {
  setUp(KitRedact.clearKnownSecrets);
  tearDown(KitRedact.clearKnownSecrets);

  group('known secrets', () {
    test(
      'masks exact occurrences anywhere, including inside ordinary words',
      () {
        KitRedact.registerKnownSecret('fixture-value');
        expect(
          KitRedact.text('fixture-value /prefixfixture-valuesuffix'),
          '$m /prefix${m}suffix',
        );
        expect(KitRedact.containsSecret('fixture-value'), isTrue);
        expect(KitRedact.text('FIXTURE-VALUE'), 'FIXTURE-VALUE');
      },
    );

    test('ignores values shorter than six characters', () {
      for (final value in ['', 'a', 'abcde']) {
        KitRedact.registerKnownSecret(value);
      }
      KitRedact.registerKnownSecret('abcdef');
      expect(KitRedact.text('a abcde abcdef'), 'a abcde $m');
    });

    test('matches metacharacters literally', () {
      KitRedact.registerKnownSecret(r'a.*[b]$');
      expect(KitRedact.text(r'a.*[b]$ axxxb'), '$m axxxb');
    });

    test(
      'masks longest overlapping values first regardless of registration order',
      () {
        for (final values in [
          ['fixture', 'fixture-long-secret'],
          ['fixture-long-secret', 'fixture'],
        ]) {
          KitRedact.clearKnownSecrets();
          for (final value in values) {
            KitRedact.registerKnownSecret(value);
          }
          expect(KitRedact.text('fixture-long-secret fixture'), '$m $m');
        }
      },
    );

    test('runs before provider patterns and still applies other patterns', () {
      KitRedact.registerKnownSecret('sk-ant-fixture-secret');
      expect(
        KitRedact.text('sk-ant-fixture-secret password=other-value'),
        '$m password=$m',
      );
    });

    test('clearing removes all registered values', () {
      KitRedact.registerKnownSecret('fixture-value');
      KitRedact.registerKnownSecret('second-value');
      KitRedact.clearKnownSecrets();
      expect(
        KitRedact.text('fixture-value second-value'),
        'fixture-value second-value',
      );
      expect(KitRedact.containsSecret('fixture-value'), isFalse);
    });

    test('duplicate registration and repeated redaction are idempotent', () {
      KitRedact.registerKnownSecret('fixture-value');
      KitRedact.registerKnownSecret('fixture-value');
      final once = KitRedact.text('fixture-value fixture-value');
      expect(once, '$m $m');
      expect(KitRedact.text(once), once);
    });
  });

  group('masks', () {
    final cases = <String, String>{
      'key sk-ant-api03-AbCdEfGhIjKlMnOpQrSt done': 'key sk-ant-$m done',
      'sk-proj-AbCdEfGhIjKlMnOpQrStUv': 'sk-proj-$m',
      'OPENAI=sk-AbCdEfGhIjKlMnOpQrSt12': 'OPENAI=sk-$m',
      'AIzaSyA1234567890abcdefghijklmnopqrstu': 'AIza$m',
      'ghp_0123456789abcdefghijABCDEFGHIJ': 'ghp_$m',
      'gho_0123456789abcdefghijABCDEFGHIJ': 'gho_$m',
      'ghs_0123456789abcdefghijABCDEFGHIJ': 'ghs_$m',
      'github_pat_11ABCDEFG0123456789_abcdefghijklmnop': 'github_pat_$m',
      'slack xoxb-1234567890-abcdefghij': 'slack xoxb-$m',
      'xoxp-1234567890-abcdefghij': 'xoxp-$m',
      'curl -H Bearer abc.def-ghi_123': 'curl -H Bearer $m',
      'Authorization: Basic b3BlbmNvZGU6cGFzcw==': 'Authorization: $m',
      'authorization=Token xyz123': 'authorization=$m',
      'Proxy-Authorization: Basic Zm9vOmJhcg==': 'Proxy-Authorization: $m',
      '{"Authorization": "Bearer abc"}': '{"Authorization": "$m"}',
      'api_key=abc123': 'api_key=$m',
      'apikey: abc123': 'apikey: $m',
      'API-KEY=abc123': 'API-KEY=$m',
      'token=abc123&x=1': 'token=$m&x=1',
      'client_secret: s3cr3t': 'client_secret: $m',
      'password=hunter2 user=me': 'password=$m user=me',
      'passwd: hunter2': 'passwd: $m',
      '{"password":"hunter2"}': '{"password":"$m"}',
      "secret = 'abc'": "secret = '$m'",
      'https://user:pass@example.com/x': 'https://$m@example.com/x',
      'git clone https://ghtoken@github.com/o/r':
          'git clone https://$m@github.com/o/r',
      'jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig-nat_ure end': 'jwt $m end',
      // Quoted values are masked through their closing quote.
      'password=";hunter2"': 'password="$m"',
      'password="correct horse battery staple"': 'password="$m"',
      "password='correct horse battery staple' user=me":
          "password='$m' user=me",
      r'password="a\"b; c" next': 'password="$m" next',
      '{"password":"a,b;c","user":"me"}': '{"password":"$m","user":"me"}',
      // The whole Authorization value, not just its first word.
      'Authorization: Digest username="alice", nonce="fake-nonce", '
              'response="fake-response"':
          'Authorization: $m',
      'Authorization: AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20260926/'
              'us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-date, '
              'Signature=fakesignature0123':
          'Authorization: $m',
      'authorization: Digest username="alice", response="fake-response"':
          'authorization: $m',
      'proxy-authorization: Digest username="bob", response="fake"':
          'proxy-authorization: $m',
      '{"Authorization":"Bearer x","id":1}': '{"Authorization":"$m","id":1}',
      r'{"log":"Authorization: Digest username=\"alice\", response=\"f\"","n":1}':
          '{"log":"Authorization: $m","n":1}',
      "curl -H 'Authorization: AWS4-HMAC-SHA256 Credential=x, Signature=f' "
              'https://h':
          "curl -H 'Authorization: $m' https://h",
      'Authorization: Digest response="x"\nHost: example.com':
          'Authorization: $m\nHost: example.com',
      // JWTs recognised by decoding, whatever the header's spelling.
      'jwt eyAiYWxnIjogIkhTMjU2IiB9.eyJzdWIiOiIxIn0.ZmFrZS1zaWduYXR1cmU end':
          'jwt $m end',
      'eyJhbGciOiJIUzI1NiJ9.e30.ZmFrZS1zaWduYXR1cmU': m,
      '//example.com/callback?token=fixture-secret':
          '//example.com/callback?token=$m',
      'Location:https://example.com/callback?token=fixture-secret':
          'Location:https://example.com/callback?token=$m',
      '?x-api-key=fixture-secret&next=ok': '?x-api-key=$m&next=ok',
      '?x-api-key=fixture-secret#next=ok': '?x-api-key=$m#next=ok',
      'Host: example.com\n  x-api-key: fixture-secret extra\nAccept: */*':
          'Host: example.com\n  x-api-key: $m\nAccept: */*',
      'prefix x-api-key=fixture-secret&next=ok': 'prefix x-api-key=$m&next=ok',
      'https://example.com/cb?token=fixture-secret#next=ok':
          'https://example.com/cb?token=$m#next=ok',
      r'{"message":"password=\"fixture-secret\""}':
          '{"message":"password=\\"$m\\""}',
      r'{"message":"password=\"fixture; secret\" next=ok"}':
          '{"message":"password=\\"$m\\" next=ok"}',
      'access_key=fixture-secret': 'access_key=$m',
      'Secret_Key=fixture-secret': 'Secret_Key=$m',
      'private_key=fixture-secret': 'private_key=$m',
      // URL query and fragment credentials.
      'https://example.com/cb?token=abc&password=hunter2':
          'https://example.com/cb?token=$m&password=$m',
      'https://example.com/cb#access_token=abc123&x=1':
          'https://example.com/cb#access_token=$m&x=1',
      // Adversarial copy/diagnostic regressions (all credentials are fixtures).
      '{"password":\n"hunter2"}': '{"password":\n"$m"}',
      '{"Authorization":\n"Basic dXNlcjpwYXNz"}': '{"Authorization":\n"$m"}',
      '//password=hunter2': '//password=$m',
      '/*password=hunter2*/': '/*password=$m*/',
      'prefix.eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.ZmFrZS1zaWduYXR1cmU':
          'prefix.$m',
      'AWS_SECRET_ACCESS_KEY=fixture-access-secret': 'AWS_SECRET_ACCESS_KEY=$m',
      'OPENAI_API_KEY=sk-fixture-only': 'OPENAI_API_KEY=$m',
      'GITHUB_TOKEN=fixture-token': 'GITHUB_TOKEN=$m',
      'SERVICE_KEY=fixture-key': 'SERVICE_KEY=$m',
      'SERVICE_SECRET=fixture-secret': 'SERVICE_SECRET=$m',
      'SERVICE_PASSWORD=fixture-password': 'SERVICE_PASSWORD=$m',
      'Set-Cookie: session=abc123; HttpOnly': 'Set-Cookie: $m',
      'Cookie: session=abc123': 'Cookie: $m',
      'x-api-key: fixture-key': 'x-api-key: $m',
      'postgres://user:pass@host': 'postgres://$m@host',
      'url=https://example.com/cb?token=abc&password=hunter2':
          'url=https://example.com/cb?token=$m&password=$m',
      'password=abc(def)': 'password=$m',
      for (final label in [
        'PRIVATE KEY',
        'RSA PRIVATE KEY',
        'EC PRIVATE KEY',
        'ENCRYPTED PRIVATE KEY',
        'OPENSSH PRIVATE KEY',
      ])
        'before\n-----BEGIN $label-----\nfixture-private-key\n'
                'second-line\n-----END $label-----\nafter':
            'before\n$m\nafter',
    };
    for (final e in cases.entries) {
      test(e.key, () {
        expect(KitRedact.text(e.key), e.value);
        expect(KitRedact.containsSecret(e.key), isTrue);
      });
    }

    test('serialized password retains valid JSON', () {
      final result = KitRedact.text(
        jsonEncode({'message': 'password="fixture-secret"'}),
      );
      expect(jsonDecode(result), {'message': 'password="$m"'});
    });

    test('serialized password with an embedded escaped quote stays valid', () {
      final result = KitRedact.text(
        jsonEncode({'message': r'password="fixture\"secret" next=ok'}),
      );
      expect(jsonDecode(result), {'message': 'password="$m" next=ok'});
    });

    test('is idempotent', () {
      for (final input in cases.keys) {
        final once = KitRedact.text(input);
        expect(KitRedact.text(once), once);
      }
    });
  });

  group('leaves alone', () {
    const untouched = [
      '',
      '/home/eslam/Storage/Code/oc_app/lib/ui/kit/kit_secret_field.dart',
      r'C:\Users\me\token\notes.txt',
      'b72cf95f3a1e9c0d4b5a6f7e8d9c0b1a2f3e4d5c',
      'b72cf95f',
      '123e4567-e89b-12d3-a456-426614174000',
      'Pneumonoultramicroscopicsilicovolcanoconiosis',
      'the task-abcdefghijklmnopqrstu finished',
      'risk-assessment-document-version-two',
      'Tokens: 1,234 input, 56 output',
      'max_tokens=4096',
      '{"sort_key":"created_at","partition_key":"user_id"}',
      'const cache_key = "users:123";',
      'const cache_Key = "users:123";',
      'cache_api_key = "users:123"',
      'cache_apikey = "users:123"',
      'prefix Cookie: ordinary text',
      'Enter your password below',
      'https://example.com/path?q=1',
      'git@github.com:owner/repo.git',
      'The Authorization header is missing',
      'Bearer',
      'sk-short',
      'ghp_short',
      'eyJhbGciOi only one segment',
      '/tmp/token=cache/output.log',
      '/tmp/password:notes.txt',
      '/tmp/cache/my_secret=old.txt',
      r'C:\temp\token=cache\out.log',
      'open lib.ui.kit and pubspec.yaml.lock',
      'password=""',
      '/tmp/Authorization=cache/output.log',
      '/tmp/cache&token=output.log',
      'const token = await getToken();',
      'const token = getToken();',
      '/tmp/cache?token=output.log',
      '/tmp/cache#token=output.log',
      '-----BEGIN PUBLIC KEY-----\nfixture-public-key\n-----END PUBLIC KEY-----',
    ];
    for (final s in untouched) {
      test(s.isEmpty ? '(empty)' : s, () {
        expect(KitRedact.text(s), s);
        expect(KitRedact.containsSecret(s), isFalse);
      });
    }
  });
}
