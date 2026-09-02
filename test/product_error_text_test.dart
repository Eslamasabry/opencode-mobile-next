import 'dart:io';

import 'package:flutter/foundation.dart' show TargetPlatform;
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';

void main() {
  group('productErrorText', () {
    test('passes a ProductException message through unchanged', () {
      expect(
        productErrorText(const ProductException('OpenCode is reconnecting.')),
        'OpenCode is reconnecting.',
      );
    });

    test('passes an ApiException message through unchanged', () {
      expect(
        productErrorText(
          ApiException('Send prompt failed (HTTP 400): Bad model'),
        ),
        'Send prompt failed (HTTP 400): Bad model',
      );
    });

    test('treats a plain string as already-composed product copy', () {
      expect(
        productErrorText('This draft exceeds the attachment size limits.'),
        'This draft exceeds the attachment size limits.',
      );
    });

    test('collapses a StateError to the generic connectivity line', () {
      expect(
        productErrorText(StateError('stream already closed')),
        'OpenCode is unreachable. Try again.',
      );
    });

    test('collapses a SocketException to the generic connectivity line', () {
      expect(
        productErrorText(
          const SocketException('Connection refused (OS Error: errno 111)'),
        ),
        'OpenCode is unreachable. Try again.',
      );
    });

    test('names the missing keyring instead of blaming the server', () {
      expect(
        productErrorText(
          SecureStorageUnavailable.forPlatform(
            TargetPlatform.linux,
            cause: PlatformException(code: 'Libsecret error'),
          ),
        ),
        SecureStorageUnavailable.linuxMessage,
      );
      expect(
        productErrorText(
          SecureStorageUnavailable.forPlatform(TargetPlatform.android),
        ),
        'Could not store the password securely on this device.',
      );
      expect(
        SecureStorageUnavailable.linuxMessage,
        'Could not store the password: no keyring is available. Install '
        'GNOME Keyring or KWallet, or run the app inside a desktop session, '
        'then try again.',
      );
    });

    test('shows a PlatformException message rather than "unreachable"', () {
      expect(
        productErrorText(
          PlatformException(
            code: 'Libsecret error',
            message: 'Failed to unlock the keyring',
          ),
        ),
        'Failed to unlock the keyring',
      );
      expect(
        productErrorText(PlatformException(code: 'Libsecret error')),
        'This device reported an error (Libsecret error).',
      );
      expect(
        productErrorText(PlatformException(code: 'x', message: '  ')),
        isNot(contains('unreachable')),
      );
    });

    test('collapses unknown objects to the generic connectivity line', () {
      expect(
        productErrorText(Exception('boom')),
        'OpenCode is unreachable. Try again.',
      );
    });
  });

  group('OpenCodeApi.errorBodyDetail', () {
    test('extracts the message field from a JSON error body string', () {
      expect(
        OpenCodeApi.errorBodyDetail(
          '{"_tag":"InvalidRequestError","message":"Model not found"}',
        ),
        'Model not found',
      );
    });

    test('extracts the nested data.message field from a decoded map', () {
      expect(
        OpenCodeApi.errorBodyDetail({
          '_tag': 'ProviderAuthError',
          'data': {'message': 'API key is invalid'},
        }),
        'API key is invalid',
      );
    });

    test('truncates a non-JSON (HTML) body to about 120 characters', () {
      final html =
          '<html><head><title>502 Bad Gateway</title></head>'
          '<body><h1>502 Bad Gateway</h1>'
          '<p>${'x' * 300}</p></body></html>';
      final detail = OpenCodeApi.errorBodyDetail(html)!;
      expect(detail.length, lessThanOrEqualTo(120));
      expect(detail, startsWith('<html><head><title>502 Bad Gateway'));
      expect(detail, endsWith('…'));
    });

    test('collapses whitespace runs in the extracted detail', () {
      expect(
        OpenCodeApi.errorBodyDetail('an\n  indented\n  plain body'),
        'an indented plain body',
      );
    });

    test('returns null for missing or empty bodies', () {
      expect(OpenCodeApi.errorBodyDetail(null), isNull);
      expect(OpenCodeApi.errorBodyDetail(''), isNull);
      expect(OpenCodeApi.errorBodyDetail('   '), isNull);
    });
  });
}
