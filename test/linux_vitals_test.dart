// The vitals on top of Linux on this phone (S2a): what is read from Android
// and how (lib/builtin/linux_vitals.dart), the one sentence under them, the
// polling rule (only while the page is open and on top, at most every 5 s,
// one read at a time), and the strip on the Linux page.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services.dart';
import 'package:opencode_mobile/builtin/linux_vitals.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_manager_common.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_manager_screen.dart';

import 'support/fake_linux_services.dart';

PhoneProcess _p(
  int pid,
  String name,
  double cpu,
  int rssMb, {
  PhoneProcessOwner owner = PhoneProcessOwner.opencode,
}) => PhoneProcess(
  pid: pid,
  ppid: 1,
  name: name,
  command: name,
  cpuPercent: cpu,
  rssBytes: rssMb * 1024 * 1024,
  elapsed: const Duration(minutes: 3),
  owner: owner,
);

class _Thermal extends ThermalBridge {
  _Thermal(this.status);
  ThermalStatus status;
  @override
  Future<ThermalReading> current() async => ThermalReading(status: status);
}

class _Processes extends PhoneProcesses {
  _Processes(this.snapshot);
  PhoneProcessSnapshot snapshot;
  Object? error;
  int calls = 0;
  @override
  Future<PhoneProcessSnapshot> list() async {
    calls++;
    if (error case final e?) throw e;
    return snapshot;
  }
}

class _CountingSource extends LinuxVitalsSource {
  _CountingSource(this.answer);
  LinuxVitals answer;
  int reads = 0;
  Completer<void>? hold;
  @override
  Future<LinuxVitals> read() async {
    reads++;
    await hold?.future;
    return answer;
  }
}

const _meminfo =
    'MemTotal:        7864320 kB\n'
    'MemFree:          512000 kB\n'
    'MemAvailable:    2202010 kB\n'
    'Buffers:           10000 kB\n';

final _snapshot = PhoneProcessSnapshot(
  processes: [
    _p(10, 'oc_mobile', 40, 300, owner: PhoneProcessOwner.app),
    _p(20, 'node', 180, 900),
    _p(21, 'python3', 20, 200, owner: PhoneProcessOwner.helper),
    _p(22, 'bash', 0, 100, owner: PhoneProcessOwner.terminal),
  ],
);

Future<AppLocalizations> _en() =>
    AppLocalizations.delegate.load(const Locale('en'));

void main() {
  group('reading from Android', () {
    test('MemAvailable from /proc/meminfo, in bytes', () {
      expect(parseMemAvailable(_meminfo), 2202010 * 1024);
      expect(parseMemAvailable('MemTotal: 1 kB\n'), isNull);
      expect(parseMemAvailable(''), isNull);
    });

    test('Linux is every process but the app; per-core CPU becomes the '
        "phone's share", () {
      final share = linuxShareOf(_snapshot, cores: 8);
      // node 180 + python 20 + bash 0, over 8 cores; the app's 40 is not Linux.
      expect(share.cpu, 25);
      expect(share.memory, (900 + 200 + 100) * 1024 * 1024);
      expect(share.heaviest, 'node');
      expect(share.heaviestCpu, 180);
      expect(
        linuxShareOf(
          PhoneProcessSnapshot(processes: [_p(1, 'x', 900, 1)]),
          cores: 2,
        ).cpu,
        100,
        reason: 'never above the whole phone',
      );
      final none = linuxShareOf(const PhoneProcessSnapshot(), cores: 8);
      expect(none.cpu, 0);
      expect(none.heaviest, isNull);
    });

    test('heat words follow Android\'s thermal status', () {
      expect(linuxHeatOf(ThermalStatus.unknown), isNull);
      expect(linuxHeatOf(ThermalStatus.none), LinuxHeat.cool);
      expect(linuxHeatOf(ThermalStatus.light), LinuxHeat.cool);
      expect(linuxHeatOf(ThermalStatus.moderate), LinuxHeat.warm);
      expect(linuxHeatOf(ThermalStatus.severe), LinuxHeat.hot);
      expect(linuxHeatOf(ThermalStatus.critical), LinuxHeat.tooHot);
      expect(linuxHeatOf(ThermalStatus.shutdown), LinuxHeat.tooHot);
    });

    test('one source failing leaves only its readings out', () async {
      final processes = _Processes(_snapshot);
      final source = LinuxVitalsSource(
        thermal: _Thermal(ThermalStatus.light),
        processes: processes,
        meminfo: () async => _meminfo,
        cores: 8,
      );
      final all = await source.read();
      expect(all.heat, LinuxHeat.cool);
      expect(all.phoneMemoryFreeBytes, 2202010 * 1024);
      expect(all.linuxCpuPercent, 25);
      processes.error = PlatformException(code: 'x');
      final noList = await LinuxVitalsSource(
        thermal: _Thermal(ThermalStatus.unknown),
        processes: processes,
        meminfo: () async => throw const FileSystemExceptionStandIn(),
        cores: 8,
      ).read();
      expect(noList.heat, isNull);
      expect(noList.phoneMemoryFreeBytes, isNull);
      expect(noList.linuxCpuPercent, isNull);
      expect(noList.linuxMemoryBytes, isNull);
    });
  });

  group('the sentence', () {
    test('cool: memory, and nothing heats the phone', () async {
      final l10n = await _en();
      expect(
        linuxVitalsSentence(
          l10n,
          const LinuxVitals(heat: LinuxHeat.cool, linuxMemoryBytes: 1503238553),
        ),
        'Linux uses 1.4 GB of memory. Nothing is heating the phone.',
      );
    });

    test('no heat reading: no claim about heat', () async {
      final l10n = await _en();
      expect(
        linuxVitalsSentence(
          l10n,
          const LinuxVitals(linuxMemoryBytes: 1503238553),
        ),
        'Linux uses 1.4 GB of memory.',
      );
      expect(linuxVitalsSentence(l10n, const LinuxVitals()), isNull);
    });

    test('warm with a busy Linux program: it is named', () async {
      final l10n = await _en();
      expect(
        linuxVitalsSentence(
          l10n,
          const LinuxVitals(
            heat: LinuxHeat.hot,
            linuxCpuPercent: 25,
            linuxMemoryBytes: 1,
            heaviestProgram: 'node',
            heaviestCpuPercent: 180,
          ),
        ),
        'Hot. node is using 180% CPU.',
      );
    });

    test('warm with a quiet Linux: not Linux', () async {
      final l10n = await _en();
      expect(
        linuxVitalsSentence(
          l10n,
          const LinuxVitals(
            heat: LinuxHeat.warm,
            linuxCpuPercent: 3,
            linuxMemoryBytes: 1,
            heaviestProgram: 'bash',
            heaviestCpuPercent: 4,
          ),
        ),
        'Warm, but not because of Linux: it uses 3% CPU.',
      );
    });
  });

  group('polling', () {
    // Widget tests run on a fake clock: pump moves time.
    testWidgets('every 5 s while started, never after stop', (tester) async {
      final source = _CountingSource(const LinuxVitals());
      final watch = LinuxVitalsWatch(source)..start();
      await tester.pump();
      expect(source.reads, 1, reason: 'one read at once');
      await tester.pump(const Duration(seconds: 4));
      expect(source.reads, 1);
      await tester.pump(const Duration(seconds: 1));
      expect(source.reads, 2);
      await tester.pump(const Duration(seconds: 5));
      await tester.pump(const Duration(seconds: 5));
      expect(source.reads, 4);
      watch.stop();
      await tester.pump(const Duration(minutes: 5));
      expect(source.reads, 4);
      watch.dispose();
    });

    testWidgets('a covered page skips its ticks', (tester) async {
      var visible = true;
      final source = _CountingSource(const LinuxVitals());
      final watch = LinuxVitalsWatch(source, visible: () => visible)..start();
      await tester.pump();
      visible = false;
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(seconds: 5));
      }
      expect(source.reads, 1);
      visible = true;
      await tester.pump(const Duration(seconds: 5));
      expect(source.reads, 2);
      watch.dispose();
    });

    testWidgets('a slow read is never doubled', (tester) async {
      final source = _CountingSource(const LinuxVitals())
        ..hold = Completer<void>();
      final watch = LinuxVitalsWatch(source)..start();
      for (var i = 0; i < 4; i++) {
        await tester.pump(const Duration(seconds: 5));
      }
      expect(source.reads, 1);
      source.hold!.complete();
      source.hold = null;
      await tester.pump();
      await tester.pump(const Duration(seconds: 5));
      expect(source.reads, 2);
      watch.dispose();
    });
  });

  group('the Linux page', () {
    Future<_CountingSource> mount(WidgetTester tester, LinuxVitals v) async {
      final source = _CountingSource(v);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            linuxVitalsSourceProvider.overrideWithValue(source),
            linuxServicesProvider.overrideWithValue(FakeLinuxServices()),
            builtinLinuxProvider.overrideWithValue(_Linux()),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: LinuxManagerScreen(onRepair: () async {}),
          ),
        ),
      );
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      return source;
    }

    testWidgets('shows what was read, and only that', (tester) async {
      await mount(
        tester,
        const LinuxVitals(
          heat: LinuxHeat.cool,
          phoneMemoryFreeBytes: 2254857830,
          linuxCpuPercent: 12.4,
          linuxMemoryBytes: 1503238553,
        ),
      );
      expect(find.byKey(const ValueKey('linux-vitals')), findsOneWidget);
      expect(find.text('Cool'), findsOneWidget);
      expect(find.text('Phone heat'), findsOneWidget);
      expect(find.text('2.1 GB'), findsOneWidget);
      expect(find.text('12%'), findsOneWidget);
      expect(
        find.text('Linux uses 1.4 GB of memory. Nothing is heating the phone.'),
        findsOneWidget,
      );
      // Battery is S2b: no cell, never a made-up value.
      expect(find.textContaining('Battery'), findsNothing);
      expect(find.textContaining('°C'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('an unread value is left out, not shown as 0', (tester) async {
      await mount(
        tester,
        const LinuxVitals(linuxCpuPercent: 3, linuxMemoryBytes: 1048576),
      );
      expect(find.text('Phone heat'), findsNothing);
      expect(find.text('Memory free'), findsNothing);
      expect(find.text('3%'), findsOneWidget);
      expect(find.text('Linux uses 1.0 MB of memory.'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('reads every 5 s while open, never once it is gone', (
      tester,
    ) async {
      final source = await mount(tester, const LinuxVitals());
      final first = source.reads;
      expect(first, 1);
      await tester.pump(const Duration(seconds: 5));
      expect(source.reads, 2);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 30));
      expect(source.reads, 2);
    });

    testWidgets('a hot phone names the busy program; the line opens Running', (
      tester,
    ) async {
      await mount(
        tester,
        const LinuxVitals(
          heat: LinuxHeat.hot,
          linuxCpuPercent: 25,
          linuxMemoryBytes: 1,
          heaviestProgram: 'node',
          heaviestCpuPercent: 180,
        ),
      );
      final semantics = tester.ensureSemantics();
      await tester.pump();
      expect(find.text('Hot'), findsOneWidget);
      expect(find.text('Hot. node is using 180% CPU.'), findsOneWidget);
      expect(find.bySemanticsLabel('Open Running now'), findsOneWidget);
      semantics.dispose();
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });
}

/// A read error of the kind `File.readAsString` throws.
class FileSystemExceptionStandIn implements Exception {
  const FileSystemExceptionStandIn();
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    services: [],
  );

  @override
  Future<String?> setupStatus() async => null;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');
}
