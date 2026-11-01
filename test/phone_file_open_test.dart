import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/phone_file.dart';
import 'package:opencode_mobile/domain/product_failure.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/platform/local_file_open.dart';
import 'package:opencode_mobile/ui/widgets/phone_file_prompt.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('a Paseo file refusal is not "couldn\'t reach your computer"', () {
    PaseoFailure refused(String reason) =>
        PaseoFailure(PaseoFailureKind.unavailable, reason: reason);

    test('too large to display, in the daemon\'s own words', () {
      final failure = refused('File is too large to display');
      expect(
        ProductFailure.from(failure).category,
        ProductFailureCategory.fileTooBig,
      );
      final words = productErrorText(failure);
      expect(words, contains('too big to show here'));
      expect(words, isNot(contains('reach your computer')));
      // The daemon's sentence stays under Details.
      expect(productErrorDetails(failure), contains('too large to display'));
    });

    test('missing, denied and binary files each get their own words', () {
      final cases = {
        'ENOENT: no such file or directory, open x': (
          ProductFailureCategory.fileMissing,
          "isn't there",
        ),
        "EACCES: permission denied, open 'x'": (
          ProductFailureCategory.fileDenied,
          "won't let this app read",
        ),
        'Cannot display binary file': (
          ProductFailureCategory.fileUnreadable,
          "can't be shown here",
        ),
      };
      cases.forEach((reason, expected) {
        final failure = refused(reason);
        expect(
          ProductFailure.from(failure).category,
          expected.$1,
          reason: reason,
        );
        expect(productErrorText(failure), contains(expected.$2));
      });
    });

    test('a daemon that really did not answer still says so', () {
      expect(
        ProductFailure.from(
          PaseoFailure(PaseoFailureKind.unavailable),
        ).category,
        ProductFailureCategory.computer,
      );
      expect(
        ProductFailure.from(
          PaseoFailure(PaseoFailureKind.disconnected),
        ).category,
        ProductFailureCategory.computer,
      );
    });
  });

  group('local versus remote', () {
    const apk = '/sdcard/Download/weather-app-1.2.0.apk';

    test('an .apk on this phone goes to the installer', () {
      expect(
        phoneFileAction(apk, serverOnThisPhone: true),
        PhoneFileAction.install,
      );
      expect(
        phoneFileAction(
          '/storage/emulated/0/Download/weather-app-1.2.0.APK',
          serverOnThisPhone: true,
        ),
        PhoneFileAction.install,
      );
    });

    test('a big or binary file on this phone goes to Open with', () {
      expect(
        phoneFileAction('/sdcard/Movies/trip.mp4', serverOnThisPhone: true),
        PhoneFileAction.openWith,
      );
    });

    test(
      'text on this phone and anything on a remote server stay previews',
      () {
        expect(
          phoneFileAction('/sdcard/notes.md', serverOnThisPhone: true),
          PhoneFileAction.preview,
        );
        expect(
          phoneFileAction(apk, serverOnThisPhone: false),
          PhoneFileAction.preview,
        );
        expect(
          phoneFileAction(
            '/root/projects/weather-app-1.2.0.apk',
            serverOnThisPhone: true,
          ),
          PhoneFileAction.preview,
        );
        expect(
          isPhoneSharedStoragePath('/sdcardx/weather-app-1.2.0.apk'),
          isFalse,
        );
      },
    );
  });

  group('the hand-off to Android', () {
    const channel = MethodChannel('oc/share');
    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    setUp(LocalFileOpener.resetSupportCache);

    Widget app(Widget child) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    );

    Future<void> tapGo(WidgetTester tester, String path, bool install) async {
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => offerPhoneFile(
                context,
                path: path,
                install: install,
                opener: LocalFileOpener(channel: channel),
              ),
              child: const Text('go'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('go'));
      await tester.pumpAndSettle();
    }

    testWidgets('an .apk asks, then calls the installer on confirm', (
      tester,
    ) async {
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'openLocalFile') calls.add(call);
            return true;
          });
      await tapGo(tester, '/sdcard/Download/weather-app-1.2.0.apk', true);
      expect(find.text('Install weather-app-1.2.0.apk?'), findsOneWidget);
      expect(
        find.textContaining('confirm before anything is installed'),
        findsOneWidget,
      );
      // Nothing installs until the person says so.
      expect(calls, isEmpty);
      await tester.tap(find.byKey(const ValueKey('phone-file-confirm')));
      await tester.pumpAndSettle();
      expect(calls.single.method, 'openLocalFile');
      expect(calls.single.arguments, {
        'path': '/sdcard/Download/weather-app-1.2.0.apk',
        'install': true,
      });
    });

    testWidgets('Open with asks Android\'s chooser, not the installer', (
      tester,
    ) async {
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'openLocalFile') calls.add(call);
            return true;
          });
      await tapGo(tester, '/sdcard/Movies/trip.mp4', false);
      expect(find.text('Open with…'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('phone-file-confirm')));
      await tester.pumpAndSettle();
      expect((calls.single.arguments as Map)['install'], false);
    });

    testWidgets('a build without the native half shows no Install… and says '
        'where the file is', (tester) async {
      final calls = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call.method);
            throw MissingPluginException();
          });
      String? copied;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, (call) async {
            if (call.method == 'Clipboard.setData') {
              copied = (call.arguments as Map)['text'] as String?;
            }
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(SystemChannels.platform, null),
      );
      await tapGo(tester, '/sdcard/Download/weather-app-1.2.0.apk', true);
      expect(find.byKey(const ValueKey('phone-file-confirm')), findsNothing);
      expect(find.textContaining('Install…'), findsNothing);
      expect(
        find.text(
          'weather-app-1.2.0.apk is in Download. Open it from the Files app '
          'to install it.',
        ),
        findsOneWidget,
      );
      expect(find.text('Close'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('phone-file-copy-path')));
      await tester.pumpAndSettle();
      expect(copied, '/sdcard/Download/weather-app-1.2.0.apk');
      // Only the probe was asked; nothing was handed to Android.
      expect(calls, ['supportsOpenLocalFile']);
    });

    testWidgets('the answer is asked once per app run', (tester) async {
      var probes = 0;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'supportsOpenLocalFile') probes++;
            return true;
          });
      final opener = LocalFileOpener(channel: channel);
      expect(await opener.isSupported(), isTrue);
      expect(await opener.isSupported(), isTrue);
      expect(probes, 1);
    });

    testWidgets('supported but Android finds no app: says where it is', (
      tester,
    ) async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            channel,
            (call) async => call.method == 'supportsOpenLocalFile',
          );
      await tapGo(tester, '/sdcard/Download/weather-app-1.2.0.apk', true);
      await tester.tap(find.byKey(const ValueKey('phone-file-confirm')));
      await tester.pumpAndSettle();
      await tester.pumpAndSettle(const Duration(milliseconds: 100));
      expect(find.byKey(const ValueKey('phone-file-find')), findsOneWidget);
    });
  });

  group('a file: link is a file, not a web link', () {
    test('its path is read, decoded; other links are not files', () {
      expect(
        fileUriPath('file:///sdcard/Download/weather-app-1.2.0.apk'),
        '/sdcard/Download/weather-app-1.2.0.apk',
      );
      expect(
        fileUriPath('file:///sdcard/My%20Files/a.apk'),
        '/sdcard/My Files/a.apk',
      );
      expect(fileUriPath('file://localhost/root/a.txt'), '/root/a.txt');
      expect(fileUriPath('file://evil.example/etc/passwd'), isNull);
      expect(fileUriPath('https://example.com/a.apk'), isNull);
      expect(fileUriPath('http://example.com/a.apk'), isNull);
    });

    test('on this phone a shared-storage file: link gets the phone sheet', () {
      String? opened;
      final claimed = claimFileLink(
        'file:///sdcard/Download/weather-app-1.2.0.apk',
        open: (path) => opened = path,
      );
      expect(claimed, isTrue);
      // The path then takes the same decision as a plain path.
      expect(
        phoneFileAction(opened!, serverOnThisPhone: true),
        PhoneFileAction.install,
      );
    });

    test('on a remote server the same link takes the remote path', () {
      String? opened;
      expect(
        claimFileLink(
          'file:///sdcard/Download/weather-app-1.2.0.apk',
          open: (path) => opened = path,
        ),
        isTrue,
      );
      expect(
        phoneFileAction(opened!, serverOnThisPhone: false),
        PhoneFileAction.preview,
      );
      // An app-private path is read through the server too, never launched.
      expect(
        claimFileLink(
          'file:///data/user/0/app/files/x.log',
          open: (path) => opened = path,
        ),
        isTrue,
      );
      expect(opened, '/data/user/0/app/files/x.log');
    });

    test('https links are left for openExternalLink', () {
      var called = false;
      expect(
        claimFileLink('https://example.com/a', open: (_) => called = true),
        isFalse,
      );
      expect(called, isFalse);
    });
  });
}
