import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/product_failure.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/draft_attachment_storage.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxBridgeException;
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitAction, KitErrorKind, KitStateView;
import 'package:opencode_mobile/ui/widgets/product_states.dart';

void main() {
  group('productErrorText', () {
    test('passes a ProductException message through unchanged', () {
      expect(
        productErrorText(const ProductException('OpenCode is reconnecting.')),
        'OpenCode is reconnecting.',
      );
    });

    test('a 502 is said in words; the raw text is only the details', () {
      final error = ApiException(
        'List sessions failed (HTTP 502): upstream answered 502 while '
        'reading page 2',
        statusCode: 502,
      );
      expect(
        productErrorText(error),
        'The server had a problem (error 502). Try again in a moment.',
      );
      expect(productErrorText(error), isNot(contains('upstream')));
      expect(
        productErrorDetails(error),
        contains('upstream answered 502 while reading page 2'),
      );
    });

    test(
      'storage quota failures explain recovery without blaming the server',
      () {
        expect(
          productErrorText(
            const DraftAttachmentStorageException(
              DraftAttachmentStorageFailure.quota,
              'Browser storage is full. Remove unused attachments and try again.',
              cause: 'internal database details',
            ),
          ),
          'Browser storage is full. Remove unused attachments and try again.',
        );
      },
    );

    test('never passes an ApiException message through', () {
      String words(int? status, [String message = 'Load failed']) =>
          productErrorText(ApiException(message, statusCode: status));
      expect(words(401), startsWith("The server didn't accept the sign-in"));
      expect(words(403), startsWith("The server didn't accept the sign-in"));
      expect(
        words(404),
        "The server couldn't find it. It may have been "
        'moved or deleted.',
      );
      expect(words(409), startsWith('It changed on the server'));
      expect(words(429), startsWith('The server is busy'));
      expect(words(503), contains('(error 503)'));
      expect(words(418), startsWith("The server didn't accept the request"));
      expect(
        words(null, 'Load failed: timed out'),
        'The server took too long to answer. Try again.',
      );
      expect(
        words(null, 'Load failed: certificate not trusted'),
        startsWith("The server's security certificate isn't trusted"),
      );
      expect(
        words(null, 'Load failed: connection refused'),
        'OpenCode is unreachable. Try again.',
      );
      expect(
        words(null, 'Malformed V2 permission request envelope'),
        startsWith("The server's answer didn't make sense"),
      );
    });

    for (final status in [400, 422]) {
      for (final protocol in ['domain', 'v2']) {
        test('$protocol $status keeps server prose out of the body', () {
          final message = 'Invalid apiKey=auditSyntheticCredential123';
          final Object error = protocol == 'domain'
              ? ApiException(
                  'Send prompt failed (HTTP $status): $message',
                  statusCode: status,
                )
              : Api2RequestError(message, statusCode: status);
          expect(
            productErrorText(error),
            "The server didn't accept the request. Try again, or report the "
            'problem.',
          );
          final failure = ProductFailure.from(error);
          expect(failure.category, ProductFailureCategory.rejected);
          expect(failure.authoredMessage, isNull);
          final details = productErrorDetails(error);
          expect(details, contains('Invalid'));
          expect(details, isNot(contains('auditSyntheticCredential123')));
        });
      }
    }

    test('keeps the app\'s own staged-revert sentence', () {
      expect(
        productErrorText(
          ApiException(
            'Review the staged revert before sending this queued prompt.',
            statusCode: 409,
            errorTag: 'SessionRevertPending',
          ),
        ),
        'Review the staged revert before sending this queued prompt.',
      );
    });

    test('a staged-revert tag never permits arbitrary protocol prose', () {
      final error = ApiException(
        'Invalid apiKey=auditSyntheticCredential123',
        statusCode: 409,
        errorTag: 'SessionRevertPending',
      );
      expect(ProductFailure.from(error).authoredMessage, isNull);
      expect(
        productErrorText(error),
        'Review the staged revert before sending this queued prompt.',
      );
      expect(
        productErrorDetails(error),
        isNot(contains('auditSyntheticCredential123')),
      );
    });

    test('says OpenCode 2 failures in words too', () {
      expect(
        productErrorText(const Api2NetworkError('boom', timedOut: true)),
        'The server took too long to answer. Try again.',
      );
      expect(
        productErrorText(
          const Api2Unavailable('service_starting', statusCode: 503),
        ),
        contains('(error 503)'),
      );
    });

    test('a plain string is words; a raw failure string is classified', () {
      expect(
        productErrorText('This draft exceeds the attachment size limits.'),
        'This draft exceeds the attachment size limits.',
      );
      expect(
        productErrorText(
          'Check server health failed (HTTP 503): service unavailable',
        ),
        contains('(error 503)'),
      );
      expect(
        productErrorText(
          'Cannot reach https://host:4096: SocketException: Connection '
          'refused (OS Error: errno 111)',
        ),
        'OpenCode is unreachable. Try again.',
      );
      expect(
        productErrorText(
          'Could not save the active server profile: PlatformException(x)',
        ),
        "Something on this device didn't work. Try again.",
      );
    });

    test('says a StateError plainly, without blaming the network', () {
      expect(
        productErrorText(StateError('stream already closed')),
        'That didn\'t work. Details show what happened. Try again, or report the problem.',
      );
      expect(
        productErrorDetails(StateError('stream already closed')),
        contains('Bad state: stream already closed'),
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

    test('a timeout and a storage failure have their own words', () {
      expect(
        productErrorText(TimeoutException('slow')),
        'The server took too long to answer. Try again.',
      );
      expect(
        productErrorText(const FileSystemException('No space left', '/x')),
        "The app couldn't read or save a file on this device.",
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

    test('a PlatformException is a device failure; its native text is only '
        'the details', () {
      final error = PlatformException(
        code: 'Libsecret error',
        message: 'Failed to unlock the keyring',
      );
      expect(
        productErrorText(error),
        "Something on this device didn't work. Try again.",
      );
      expect(productErrorDetails(error), contains('Libsecret error'));
      expect(
        productErrorDetails(error),
        contains('Failed to unlock the keyring'),
      );
      expect(
        productErrorText(PlatformException(code: 'x', message: '  ')),
        isNot(contains('unreachable')),
      );
    });

    test('native and Termux output is said in words; app sentences stay', () {
      expect(
        productErrorText(
          const BuiltinLinuxException(
            'open failed: ENOENT (No such file or directory)',
            code: 'builtin_linux',
          ),
        ),
        "Something on this device didn't work. Try again.",
      );
      expect(
        productErrorText(
          const BuiltinLinuxException(
            'The built-in Linux runs on Android only.',
            code: 'unsupported_platform',
          ),
        ),
        'The built-in Linux runs on Android only.',
      );
      expect(
        productErrorText(
          const TermuxBridgeException(
            'bash: line 3: opencode: command not found',
            code: 'command_failed',
          ),
        ),
        startsWith("Termux didn't finish that."),
      );
      expect(
        productErrorText(
          const TermuxBridgeException(
            'Use a folder name without slashes.',
            code: 'invalid_folder_name',
          ),
        ),
        'Use a folder name without slashes.',
      );
    });

    test('details are redacted', () {
      final details = productErrorDetails(
        ApiException(
          'Load failed (HTTP 500): Authorization: Bearer '
          'abcdefghijklmnopqrstuvwxyz123456',
          statusCode: 500,
        ),
      );
      expect(details, isNot(contains('abcdefghijklmnopqrstuvwxyz123456')));
      expect(productErrorDetails(const ProductException('Words.')), isNull);
      expect(productErrorDetails('Plain words for people.'), isNull);
    });

    test('a transport failure offers the network fix', () {
      expect(
        productErrorKind(ApiException('Load failed: connection refused')),
        KitErrorKind.network,
      );
      expect(
        productErrorKind(ApiException('x', statusCode: 502)),
        KitErrorKind.other,
      );
    });

    test('says an unknown error plainly, without blaming the network', () {
      expect(
        productErrorText(Exception('boom')),
        'That didn\'t work. Details show what happened. Try again, or report the problem.',
      );
    });
  });

  group('the shared error parts show words, not the raw failure', () {
    Widget app(Widget child) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    );
    final bad = ApiException(
      'List sessions failed (HTTP 502): upstream answered 502 while reading '
      'page 2',
      statusCode: 502,
    );

    testWidgets('an error state: words, Try again, raw text only under '
        'Details', (tester) async {
      var retried = 0;
      await tester.pumpWidget(
        app(
          KitStateView.error(
            title: 'Could not load older conversations',
            body: productErrorText(bad),
            error: bad,
            details: productErrorDetails(bad),
            retry: KitAction(label: 'Try again', onPressed: () => retried++),
          ),
        ),
      );
      expect(find.text('Could not load older conversations'), findsOneWidget);
      expect(
        find.text(
          'The server had a problem (error 502). Try again in a moment.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('upstream answered'), findsNothing);
      expect(find.textContaining('ApiException'), findsNothing);
      await tester.tap(find.text('Try again'));
      expect(retried, 1);

      await tester.tap(find.byKey(const ValueKey('kit-state-details')));
      await tester.pumpAndSettle();
      expect(find.textContaining('upstream answered 502'), findsOneWidget);
    });

    testWidgets('a server refusal exposes only redacted Details', (
      tester,
    ) async {
      const error = Api2RequestError(
        'Invalid apiKey=auditSyntheticCredential123',
        statusCode: 400,
      );
      await tester.pumpWidget(
        app(
          KitStateView.error(
            title: 'Could not save',
            retry: KitAction(label: 'Try again', onPressed: () {}),
            body: productErrorText(error),
            error: error,
            details: productErrorDetails(error),
          ),
        ),
      );
      expect(find.textContaining('Invalid'), findsNothing);
      expect(find.textContaining('auditSyntheticCredential123'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-state-details')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Invalid'), findsOneWidget);
      expect(find.textContaining('auditSyntheticCredential123'), findsNothing);
    });

    testWidgets('showProductError: words in the body, the raw text folded', (
      tester,
    ) async {
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => showProductError(
                context,
                bad,
                title: 'Could not load older conversations',
              ),
              child: const Text('go'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('go'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'The server had a problem (error 502). Try again in a moment.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('upstream answered'), findsNothing);
      expect(find.text('Details'), findsOneWidget);
    });
  });
}
