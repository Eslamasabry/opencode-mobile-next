import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_report.dart';
import 'package:opencode_mobile/domain/crash_report.dart';
import 'package:opencode_mobile/domain/diagnostics_error.dart';

class _HostileError {
  @override
  String toString() => throw StateError('private-provider-key');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/share');
  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController store;
  late CrashReportBuilder builder;
  late List<String> shared;

  setUp(() {
    directory = Directory.systemTemp.createTempSync('crash-report-test-');
    diagnostics = AppDiagnosticsController();
    store = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    shared = [];
    builder = CrashReportBuilder(
      loadController: () async => store,
      shareText: (text) async {
        shared.add(text);
        return true;
      },
    );
  });

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
    store.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  void capture() {
    expect(store.setEnabled(true), isTrue);
    store.capture(
      StateError('private-provider-key https://private-host/secret'),
      StackTrace.fromString('/private/path conversation-secret'),
      'flutter',
    );
  }

  Future<CrashReportPreview> preview() async {
    final result = await builder.preview();
    expect(result.error, isNull);
    return result.preview!;
  }

  Map<String, String> savedFiles() => {
    for (final file in directory.listSync().whereType<File>())
      file.path: base64Encode(file.readAsBytesSync()),
  };

  test(
    'capture stays off and a broad diagnostics entry is not evidence',
    () async {
      diagnostics.record('private-provider-key', null, source: 'app');
      final result = await builder.preview();
      expect(result.error, DiagnosticsError.captureDisabled);
      expect(result.preview, isNull);
      expect(store.enabled, isFalse);
      expect(store.savedCount, 0);
      expect(shared, isEmpty);
      expect(directory.listSync(), isEmpty);
    },
  );

  test('enabled capture without a saved record returns noCapture', () async {
    store.setEnabled(true);
    diagnostics.record('private-provider-key', null, source: 'app');
    final result = await builder.preview();
    expect(result.error, DiagnosticsError.noCapture);
    expect(result.preview, isNull);
    expect(shared, isEmpty);
  });

  test(
    'preview contains validated evidence only and never writes or shares',
    () async {
      capture();
      store.capture(_HostileError(), StackTrace.current, 'platform');
      store.capture(StateError('private'), null, 'private-source');
      diagnostics.record(
        'unrelated-conversation-secret',
        StackTrace.fromString('unrelated-private-stack'),
        source: 'unrelated-private-profile',
      );
      final before = savedFiles();
      final report = await preview();
      expect(report.recordCount, 2);
      expect(report.text, contains('flutter | Invalid state'));
      expect(report.text, contains('platform | Application error'));
      expect(
        report.text,
        contains(store.snapshot.records.first.at.toIso8601String()),
      );
      for (final secret in [
        'private-provider-key',
        'private-host',
        '/private/path',
        'conversation-secret',
        'unrelated-private',
        directory.path,
      ]) {
        expect(report.text, isNot(contains(secret)));
      }
      expect(report.byteCount, utf8.encode(report.text).length);
      expect(savedFiles(), before);
      expect(shared, isEmpty);
      expect(store.enabled, isTrue);
    },
  );

  test('safe snapshot is immutable and remains a point-in-time record', () {
    capture();
    final snapshot = store.snapshot;
    expect(snapshot.available, isTrue);
    expect(snapshot.records.single.at.isUtc, isTrue);
    expect(() => snapshot.records.clear(), throwsUnsupportedError);
    store.capture(ArgumentError('private'), null, 'platform');
    expect(snapshot.records, hasLength(1));
    expect(store.snapshot.records, hasLength(2));
    expect(store.snapshot.revision, greaterThan(snapshot.revision));
    final epoch = store.snapshot.consentEpoch;
    store.setEnabled(true);
    expect(store.snapshot.consentEpoch, epoch);
    store.clear();
    expect(store.snapshot.consentEpoch, greaterThan(epoch));
  });

  test('report remains within 20 records and its UTF-8 byte budget', () async {
    store.dispose();
    final now = DateTime.now().millisecondsSinceEpoch;
    File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    ).writeAsStringSync('${now - 10000}');
    store = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    for (var i = 0; i < 45; i++) {
      store.importAndroidAnr(now - 1000 + i);
    }
    final report = await preview();
    expect(report.recordCount, 20);
    expect(report.byteCount, utf8.encode(report.text).length);
    expect(report.byteCount, lessThanOrEqualTo(CrashReportBuilder.maxBytes));
    expect(
      report.recordCount,
      lessThanOrEqualTo(CrashReportBuilder.maxEntries),
    );
    expect(
      report.text.split('\n').where((line) => line.contains(' | anr | ')),
      hasLength(20),
    );
  });

  test(
    'explicit share opens with the exact preview and writes nothing',
    () async {
      capture();
      final report = await preview();
      final before = savedFiles();
      final result = await builder.share(report);
      expect(result.opened, isTrue);
      expect(result.error, isNull);
      expect(shared, [report.text]);
      expect(savedFiles(), before);
    },
  );

  test('new objects and another builder cannot authorize a preview', () async {
    capture();
    final report = await preview();
    for (final text in [report.text, 'private-forged-report']) {
      final foreign = CrashReportPreview(
        text: text,
        byteCount: report.byteCount,
        recordCount: report.recordCount,
      );
      final result = await builder.share(foreign);
      expect(result.error, DiagnosticsError.stalePreview);
      expect(result.opened, isFalse);
    }
    final other = CrashReportBuilder(
      loadController: () async => store,
      shareText: (text) async {
        shared.add(text);
        return true;
      },
    );
    expect((await other.share(report)).error, DiagnosticsError.stalePreview);
    expect(shared, isEmpty);
  });

  test('changed evidence requires another preview before sharing', () async {
    capture();
    final old = await preview();
    store.capture(ArgumentError('private'), null, 'platform');
    final stale = await builder.share(old);
    expect(stale.opened, isFalse);
    expect(stale.error, DiagnosticsError.stalePreview);
    expect(shared, isEmpty);
    final fresh = await preview();
    expect(fresh.recordCount, 2);
    expect((await builder.share(fresh)).opened, isTrue);
    expect(shared, [fresh.text]);
  });

  for (final change in ['disable', 'clear', 'disable then re-enable']) {
    test('$change invalidates an issued preview', () async {
      capture();
      final report = await preview();
      switch (change) {
        case 'disable':
          store.setEnabled(false);
        case 'clear':
          store.clear();
        default:
          store.setEnabled(false);
          store.setEnabled(true);
          store.capture(StateError('private'), null, 'flutter');
      }
      final result = await builder.share(report);
      expect(result.error, DiagnosticsError.stalePreview);
      expect(result.opened, isFalse);
      expect(shared, isEmpty);
    });
  }

  test(
    'controller replacement invalidates even identical restored records',
    () async {
      capture();
      final report = await preview();
      store.dispose();
      store = CrashDiagnosticsController.open(
        directory: directory,
        diagnostics: diagnostics,
      );
      expect(store.savedCount, report.recordCount);
      final result = await builder.share(report);
      expect(result.error, DiagnosticsError.stalePreview);
      expect(shared, isEmpty);
    },
  );

  test('storage failure is typed and makes old preview stale', () async {
    capture();
    final report = await preview();
    directory.deleteSync(recursive: true);
    store.capture(ArgumentError('private'), null, 'platform');
    expect(store.storageFailed, isTrue);
    expect((await builder.preview()).error, DiagnosticsError.storageFailed);
    expect((await builder.share(report)).error, DiagnosticsError.stalePreview);
    expect(shared, isEmpty);
  });

  test('capture cleared during the awaited loader is not shared', () async {
    capture();
    final pending = Completer<CrashDiagnosticsController?>();
    var waitForShare = false;
    final delayed = CrashReportBuilder(
      loadController: () => waitForShare ? pending.future : Future.value(store),
      shareText: (text) async {
        shared.add(text);
        return true;
      },
    );
    final report = (await delayed.preview()).preview!;
    waitForShare = true;
    final sharing = delayed.share(report);
    store.clear();
    pending.complete(store);
    expect((await sharing).error, DiagnosticsError.stalePreview);
    expect(shared, isEmpty);
  });

  test('unavailable loader exposes a fixed outcome and no report', () async {
    for (final load in <Future<CrashDiagnosticsController?> Function()>[
      () async => null,
      () async => throw StateError('private-provider-key /private/path'),
    ]) {
      final unavailable = CrashReportBuilder(loadController: load);
      final result = await unavailable.preview();
      expect(result.error, DiagnosticsError.unavailable);
      expect(result.preview, isNull);
    }
    capture();
    final report = await preview();
    store.dispose();
    // Replace for teardown while keeping the issued controller closed.
    store = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    expect((await builder.share(report)).error, DiagnosticsError.stalePreview);
  });

  test(
    'share rejection and private exceptions return shareUnavailable',
    () async {
      capture();
      for (final shareText in <Future<bool> Function(String)>[
        (_) async => false,
        (_) async => throw StateError('private-provider-key /private/path'),
      ]) {
        final rejected = CrashReportBuilder(
          loadController: () async => store,
          shareText: shareText,
        );
        final report = (await rejected.preview()).preview!;
        final result = await rejected.share(report);
        expect(result.opened, isFalse);
        expect(result.error, DiagnosticsError.shareUnavailable);
      }
    },
  );

  test('overlapping shares return busy without opening twice', () async {
    capture();
    final opened = Completer<bool>();
    final entered = Completer<void>();
    final delayed = CrashReportBuilder(
      loadController: () async => store,
      shareText: (text) {
        shared.add(text);
        entered.complete();
        return opened.future;
      },
    );
    final report = (await delayed.preview()).preview!;
    final first = delayed.share(report);
    await entered.future;
    expect((await delayed.share(report)).error, DiagnosticsError.busy);
    opened.complete(true);
    expect((await first).opened, isTrue);
    expect(shared, [report.text]);
  });

  test('production sharing uses oc/share with exact text only', () async {
    capture();
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return true;
        });
    final production = CrashReportBuilder(loadController: () async => store);
    final report = (await production.preview()).preview!;
    expect(calls, isEmpty);
    expect((await production.share(report)).opened, isTrue);
    expect(calls.single.method, 'shareText');
    expect(calls.single.arguments, {'text': report.text});
  });

  test(
    'production sharing is unavailable on an unsupported platform',
    () async {
      capture();
      debugDefaultTargetPlatformOverride = TargetPlatform.linux;
      final production = CrashReportBuilder(loadController: () async => store);
      final report = (await production.preview()).preview!;
      expect(production.shareSupported, isFalse);
      expect(
        (await production.share(report)).error,
        DiagnosticsError.shareUnavailable,
      );
    },
  );
}
