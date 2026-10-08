import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';

class _UnsafeError {
  @override
  String toString() => throw StateError('Never serialize arbitrary errors');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController capture;

  File file(String name) => File('${directory.path}/$name');
  String savedText() => directory
      .listSync()
      .whereType<File>()
      .map((f) => f.readAsStringSync())
      .join('\n');

  setUp(() {
    directory = Directory.systemTemp.createTempSync('crash-diagnostics-test-');
    diagnostics = AppDiagnosticsController();
    capture = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
  });

  tearDown(() {
    capture.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  test('capture defaults off and discards a legacy native record', () {
    file('native-last-crash.properties').writeAsStringSync('legacy');
    capture.dispose();
    capture = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    capture.capture(StateError('private'), StackTrace.current, 'flutter');
    expect(capture.enabled, isFalse);
    expect(directory.listSync(), isEmpty);
    expect(diagnostics.isEmpty, isTrue);
  });

  test(
    'provider keys and arbitrary exception and stack values never reach disk',
    () {
      // Deliberately unregistered short key: regex redaction cannot identify it.
      const providerKey = 'oak-cloud';
      const patternedKey = 'sk-proj-fakeOnly01234567890123456789';
      expect(capture.setEnabled(true), isTrue);
      capture.capture(
        StateError('$providerKey $patternedKey'),
        StackTrace.fromString(providerKey),
        'flutter',
      );
      expect(savedText(), isNot(contains(providerKey)));
      expect(savedText(), isNot(contains(patternedKey)));
      expect(savedText(), contains('Invalid state'));
      expect(diagnostics.reportText(), isNot(contains(providerKey)));
      expect(diagnostics.entries.single.stack, isEmpty);
    },
  );

  test('capture does not invoke hostile error.toString', () {
    capture.setEnabled(true);
    expect(
      () => capture.capture(_UnsafeError(), StackTrace.current, 'platform'),
      returnsNormally,
    );
    expect(savedText(), contains('Application error'));
  });

  test('saved error survives reopen and imports into App diagnostics', () {
    capture.setEnabled(true);
    capture.capture(StateError('private'), StackTrace.current, 'flutter');
    capture.dispose();
    diagnostics.dispose();
    diagnostics = AppDiagnosticsController();
    capture = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    expect(capture.enabled, isTrue);
    expect(capture.savedCount, 1);
    expect(diagnostics.entries.single.message, 'Invalid state');
    expect(diagnostics.entries.single.source, 'crash.flutter');
  });

  test('records lists saved categories newest first without values', () {
    capture.setEnabled(true);
    final anr = DateTime.now();
    capture.importAndroidAnr(anr.millisecondsSinceEpoch);
    capture.capture(ArgumentError('private-value'), null, 'widget');
    final records = capture.records;
    expect(records, hasLength(2));
    expect(records.first.source, 'widget');
    expect(records.first.category, 'Invalid argument');
    expect(records.last.source, 'anr');
    expect(
      records.last.time.millisecondsSinceEpoch,
      anr.millisecondsSinceEpoch,
    );
    expect(records.toString(), isNot(contains('private-value')));
    // A read-only copy: changing it does not touch the saved records.
    records.clear();
    expect(capture.savedCount, 2);
    capture.clear();
    expect(capture.records, isEmpty);
  });

  test('reapplying enabled consent preserves saved evidence', () {
    capture.setEnabled(true);
    capture.capture(StateError('private'), null, 'flutter');
    final before = savedText();
    expect(capture.setEnabled(true), isTrue);
    expect(capture.savedCount, 1);
    expect(savedText(), before);
  });

  test(
    'disable erases Flutter and native evidence and consent across reopen',
    () {
      capture.setEnabled(true);
      capture.capture(StateError('private'), null, 'flutter');
      file(
        'native-last-crash.properties',
      ).writeAsStringSync('synthetic native');
      expect(capture.setEnabled(false), isTrue);
      expect(directory.listSync(), isEmpty);
      expect(diagnostics.isEmpty, isTrue);
      capture.dispose();
      capture = CrashDiagnosticsController.open(
        directory: directory,
        diagnostics: diagnostics,
      );
      expect(capture.enabled, isFalse);
      expect(capture.savedCount, 0);
    },
  );

  test('App diagnostics clear removes native and saved crash evidence', () {
    capture.setEnabled(true);
    capture.capture(StateError('private'), null, 'flutter');
    file('native-last-crash.properties').writeAsStringSync('synthetic native');
    diagnostics.clear();
    expect(capture.enabled, isTrue);
    expect(capture.savedCount, 0);
    expect(file('crash-diagnostics.json').existsSync(), isFalse);
    expect(file('native-last-crash.properties').existsSync(), isFalse);
    expect(
      file(CrashDiagnosticsController.consentFileName).existsSync(),
      isTrue,
    );
  });

  test(
    'ANR and native import accept only timestamps since consent and deduplicate',
    () {
      capture.dispose();
      final now = DateTime.now().millisecondsSinceEpoch;
      file(
        CrashDiagnosticsController.consentFileName,
      ).writeAsStringSync('${now - 1000}');
      capture = CrashDiagnosticsController.open(
        directory: directory,
        diagnostics: diagnostics,
      );
      capture.importAndroidAnr(now - 2000);
      capture.importAndroidAnr(now + 100000);
      capture.importAndroidAnr('private');
      expect(capture.savedCount, 0);
      capture.importAndroidAnr(now - 10);
      capture.importAndroidAnr(now - 10);
      capture.importNativeCrash({
        'timestamp': now - 5,
        'message': 'oak-cloud',
        'frames': ['oak-cloud'],
      });
      expect(capture.savedCount, 2);
      expect(savedText(), isNot(contains('oak-cloud')));
      expect(diagnostics.entries.first.message, contains('stopped responding'));
      capture.clear();
      capture.importAndroidAnr(now - 10);
      expect(capture.savedCount, 0);
    },
  );

  test('ring and snapshot byte budget remain bounded', () {
    capture.dispose();
    final now = DateTime.now().millisecondsSinceEpoch;
    file(
      CrashDiagnosticsController.consentFileName,
    ).writeAsStringSync('${now - 1000}');
    capture = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    for (var i = 0; i < 45; i++) {
      capture.importAndroidAnr(now - 500 + i);
    }
    expect(capture.savedCount, CrashDiagnosticsController.maxEntries);
    expect(
      file('crash-diagnostics.json').lengthSync(),
      lessThanOrEqualTo(CrashDiagnosticsController.maxBytes),
    );
    expect(file('crash-diagnostics.pending').existsSync(), isFalse);
  });

  test(
    'corrupt restored categories fail closed without entering diagnostics',
    () {
      capture.setEnabled(true);
      capture.dispose();
      file('crash-diagnostics.json').writeAsStringSync(
        '[{"source":"flutter","category":"oak-cloud","time":${DateTime.now().millisecondsSinceEpoch}}]',
      );
      capture = CrashDiagnosticsController.open(
        directory: directory,
        diagnostics: diagnostics,
      );
      expect(capture.savedCount, 0);
      expect(file('crash-diagnostics.json').existsSync(), isFalse);
      expect(diagnostics.isEmpty, isTrue);
    },
  );

  test('storage failure never throws into error handling', () {
    capture.setEnabled(true);
    directory.deleteSync(recursive: true);
    expect(
      () => capture.capture(StateError('private'), null, 'flutter'),
      returnsNormally,
    );
    expect(capture.storageFailed, isTrue);
    expect(capture.setEnabled(true), isFalse);
    expect(capture.enabled, isFalse);
  });

  test('pending snapshot symlink cannot redirect private evidence', () {
    capture.setEnabled(true);
    File('${directory.path}/outside').writeAsStringSync('unchanged');
    // The target is an unrelated file in the fixture, representing another store.
    Link(
      '${directory.path}/crash-diagnostics.pending',
    ).createSync('${directory.path}/outside');
    expect(
      () => capture.capture(StateError('private'), null, 'flutter'),
      returnsNormally,
    );
    expect(capture.storageFailed, isTrue);
    expect(file('outside').readAsStringSync(), 'unchanged');
  });

  test(
    'global Flutter and platform hooks delegate only categories, never keys',
    () {
      const key = 'oak-cloud';
      FlutterErrorDetails? delegated;
      Object? platformError;
      final previousFlutter = FlutterError.onError;
      final previousPlatform = PlatformDispatcher.instance.onError;
      FlutterError.onError = (details) => delegated = details;
      PlatformDispatcher.instance.onError = (error, stack) {
        platformError = error;
        expect(stack.toString(), isEmpty);
        return true;
      };
      capture.setEnabled(true);
      final handle = installAppErrorCapture(
        diagnostics,
        crashCapture: capture.capture,
      );
      try {
        FlutterError.onError!(
          FlutterErrorDetails(
            exception: StateError(key),
            stack: StackTrace.fromString(key),
            library: key,
          ),
        );
        expect(delegated!.exceptionAsString(), 'Invalid state');
        expect(delegated!.stack, isNull);
        expect(delegated!.library, 'Flutter framework');
        PlatformDispatcher.instance.onError!(
          StateError(key),
          StackTrace.fromString(key),
        );
        expect(platformError, 'Invalid state');
        expect(savedText(), isNot(contains(key)));
      } finally {
        handle.restore();
        FlutterError.onError = previousFlutter;
        PlatformDispatcher.instance.onError = previousPlatform;
      }
    },
  );
}
