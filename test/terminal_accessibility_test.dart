import 'dart:async';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

class _MemoryTerminalChannel implements TerminalChannel {
  final outputController = StreamController<String>();
  final writes = <String>[];
  int closeCalls = 0;

  @override
  Stream<String> get output => outputController.stream;

  @override
  void write(String value) => writes.add(value);

  @override
  Future<void> close() async {
    closeCalls++;
    if (!outputController.isClosed) await outputController.close();
  }
}

class _TerminalRepository implements ProductRepository {
  final channels = <_MemoryTerminalChannel>[];

  @override
  Future<TerminalChannel> connectTerminal(String id) async {
    final channel = _MemoryTerminalChannel();
    channels.add(channel);
    return channel;
  }

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _DelayedTerminalRepository implements ProductRepository {
  final requests = <Completer<TerminalChannel>>[];

  @override
  Future<TerminalChannel> connectTerminal(String id) {
    final request = Completer<TerminalChannel>();
    requests.add(request);
    return request.future;
  }

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _RepositoryRouter extends ChangeNotifier {
  ProductRepository? _repository;

  _RepositoryRouter(this._repository);

  ProductRepository? get repository => _repository;

  set repository(ProductRepository? value) {
    if (identical(value, _repository)) return;
    _repository = value;
    notifyListeners();
  }
}

const _process = TerminalProcess(
  id: 'pty-1',
  title: 'Shell',
  command: 'bash',
  arguments: [],
  directory: '/work',
  running: true,
  pid: 42,
);

Future<_TerminalRepository> _pumpTerminal(WidgetTester tester) async {
  final repository = _TerminalRepository();
  await tester.pumpWidget(
    MaterialApp(
      home: TerminalSurface(repository: repository, process: _process),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 200));
  return repository;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('accessible transcript strips terminal and transport controls', (
    tester,
  ) async {
    final repository = await _pumpTerminal(tester);
    await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
    await tester.pump();

    final channel = repository.channels.single.outputController;
    channel.add('\x1b]0;private');
    channel.add(' title\x07Hello \x1b[31mworld\x1b[0m\r');
    channel.add('\n');
    channel.add('\x00{"cursor":87}');
    channel.add('\x1bPprivate');
    channel.add(' transport\x1b\\Done\x01\n');
    channel.add('\x1b(');
    channel.add('BISO ');
    channel.add('\x1b)0text\n');
    channel.add('{"cursor":99}\n');
    await tester.pump();

    expect(
      find.text('Hello world\nDone\nISO text\n{"cursor":99}\n'),
      findsOneWidget,
    );
    expect(find.textContaining('private'), findsNothing);
    expect(find.textContaining('\x1b'), findsNothing);
    expect(find.textContaining('\x00'), findsNothing);
  });

  testWidgets('terminal key strip scrolls on phones with 48dp targets', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await _pumpTerminal(tester);

    final strip = tester.widget<ListView>(
      find.byKey(const Key('terminal-control-strip')),
    );
    expect(strip.scrollDirection, Axis.horizontal);

    final visibleKeys = find.descendant(
      of: find.byKey(const Key('terminal-control-strip')),
      matching: find.byType(OutlinedButton),
    );
    expect(visibleKeys, findsWidgets);
    for (final element in visibleKeys.evaluate()) {
      final size = tester.getSize(find.byWidget(element.widget));
      expect(size.width, greaterThanOrEqualTo(48));
      expect(size.height, greaterThanOrEqualTo(48));
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('closed terminal disables writes and announces why', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = await _pumpTerminal(tester);
    final firstChannel = repository.channels.single;

    expect(
      tester
          .widget<OutlinedButton>(find.widgetWithText(OutlinedButton, 'Ctrl-C'))
          .onPressed,
      isNotNull,
    );
    await firstChannel.outputController.close();
    await tester.pump();

    expect(find.text('Connection closed'), findsOneWidget);
    expect(
      find.bySemanticsLabel(RegExp('Terminal status: Connection closed')),
      findsOneWidget,
    );
    final controlNode = tester.getSemantics(
      find.bySemanticsLabel('Interrupt, Control C'),
    );
    expect(
      controlNode.getSemanticsData().flagsCollection.isEnabled,
      Tristate.isFalse,
    );
    expect(
      tester
          .widget<OutlinedButton>(find.widgetWithText(OutlinedButton, 'Ctrl-C'))
          .onPressed,
      isNull,
    );

    await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
    await tester.pump();
    final input = tester.widget<TextField>(
      find.byKey(const Key('terminal-accessible-input')),
    );
    expect(input.enabled, isFalse);
    expect(
      find.text('Input is unavailable while disconnected.'),
      findsOneWidget,
    );
    expect(find.byTooltip('Terminal input unavailable'), findsOneWidget);

    semantics.dispose();
  });

  testWidgets(
    'terminal lifecycle closes once and resumes with one replacement channel',
    (tester) async {
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      final repository = await _pumpTerminal(tester);
      expect(repository.channels, hasLength(1));

      for (final state in const [
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.detached,
      ]) {
        final channel = repository.channels.last;
        final channelCount = repository.channels.length;

        tester.binding.handleAppLifecycleStateChanged(state);
        await tester.pump();
        expect(channel.closeCalls, 1);
        expect(find.text('Paused'), findsOneWidget);
        expect(find.byType(TerminalSurface), findsOneWidget);
        expect(
          tester
              .widget<OutlinedButton>(
                find.widgetWithText(OutlinedButton, 'Ctrl-C'),
              )
              .onPressed,
          isNull,
        );

        tester.binding.handleAppLifecycleStateChanged(state);
        await tester.pump();
        expect(channel.closeCalls, 1);

        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
        expect(repository.channels, hasLength(channelCount + 1));
        expect(find.byType(TerminalSurface), findsOneWidget);

        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await tester.pump();
        expect(repository.channels, hasLength(channelCount + 1));
      }
    },
  );

  testWidgets('lifecycle invalidates a pending terminal connection', (
    tester,
  ) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    final repository = _DelayedTerminalRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: TerminalSurface(repository: repository, process: _process),
      ),
    );
    await tester.pump();
    expect(repository.requests, hasLength(1));

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(repository.requests, hasLength(2));

    final staleChannel = _MemoryTerminalChannel();
    repository.requests.first.complete(staleChannel);
    await tester.pump();
    expect(staleChannel.closeCalls, 1);

    final activeChannel = _MemoryTerminalChannel();
    repository.requests.last.complete(activeChannel);
    await tester.pump();
    expect(activeChannel.closeCalls, 0);
    expect(
      tester
          .widget<OutlinedButton>(find.widgetWithText(OutlinedButton, 'Ctrl-C'))
          .onPressed,
      isNotNull,
    );
    expect(find.byType(TerminalSurface), findsOneWidget);
  });

  testWidgets(
    'terminal reconnects through the replacement repository after resume',
    (tester) async {
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      final retiredRepository = _TerminalRepository();
      final replacementRepository = _TerminalRepository();
      final router = _RepositoryRouter(retiredRepository);
      addTearDown(router.dispose);

      await tester.pumpWidget(
        MaterialApp(
          home: TerminalSurface(
            repository: retiredRepository,
            repositoryResolver: () => router.repository,
            repositoryChanges: router,
            process: _process,
          ),
        ),
      );
      await tester.pump();
      expect(retiredRepository.channels, hasLength(1));

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      router.repository = null;
      await tester.pump();

      // Exercise the conservative observer ordering too: the surface may see
      // resume before the app-wide controller has installed its new transport.
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      expect(find.text('Unavailable'), findsOneWidget);

      router.repository = replacementRepository;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(retiredRepository.channels, hasLength(1));
      expect(retiredRepository.channels.single.closeCalls, 1);
      expect(replacementRepository.channels, hasLength(1));
      expect(find.textContaining('Connected - PID 42'), findsOneWidget);
    },
  );

  testWidgets('terminal reconnects when a retained surface changes process', (
    tester,
  ) async {
    final repository = _TerminalRepository();
    final process = ValueNotifier<TerminalProcess>(_process);
    addTearDown(process.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: ValueListenableBuilder<TerminalProcess>(
          valueListenable: process,
          builder: (_, value, _) =>
              TerminalSurface(repository: repository, process: value),
        ),
      ),
    );
    await tester.pump();
    expect(repository.channels, hasLength(1));

    process.value = const TerminalProcess(
      id: 'pty-2',
      title: 'Second shell',
      command: 'bash',
      arguments: [],
      directory: '/work',
      running: true,
      pid: 84,
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(repository.channels, hasLength(2));
    expect(repository.channels.first.closeCalls, 1);
    expect(find.textContaining('Connected - PID 84'), findsOneWidget);
  });
}
