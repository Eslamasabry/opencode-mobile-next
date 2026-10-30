import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/network.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/widgets/setup_progress_view.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

/// A setup job held still in one state: nothing native ever runs.
class _HeldSetup implements SetupEngine {
  _HeldSetup(SetupProgress progress) : _progress = ValueNotifier(progress);

  final ValueNotifier<SetupProgress> _progress;

  @override
  List<SetupComponent> get registry => [
    for (final component in setupComponents(
      lookupAppLocalizations(const Locale('en')),
    ))
      if (component.app == null) component,
  ];

  @override
  ValueListenable<SetupProgress> get progress => _progress;

  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async {}

  @override
  Future<void> cancel() async {}

  @override
  Future<void> restore({SetupReadScope? scope}) async {}

  @override
  Future<Set<String>> installedOptional() async => {};

  void dispose() => _progress.dispose();
}

class _Scene implements SceneResource {
  _Scene(this.app, this.engine);

  final FakeApp app;
  final _HeldSetup engine;

  @override
  void dispose() {
    engine.dispose();
    app.dispose();
  }
}

const _done = [
  ComponentProgress(
    id: SetupComponentIds.linux,
    state: ComponentState.done,
    version: '24.04.5',
  ),
  ComponentProgress(
    id: SetupComponentIds.essentials,
    state: ComponentState.done,
    version: '2.43.0',
  ),
  ComponentProgress(
    id: SetupComponentIds.python,
    state: ComponentState.done,
    version: '3.12.3',
  ),
  ComponentProgress(
    id: SetupComponentIds.node,
    state: ComponentState.done,
    version: '22.11.0',
  ),
];

SetupProgress _job(
  SetupState state, {
  ComponentProgress openCode = const ComponentProgress(
    id: SetupComponentIds.openCode,
    state: ComponentState.pending,
  ),
  ComponentProgress start = const ComponentProgress(
    id: SetupComponentIds.start,
    state: ComponentState.pending,
  ),
  int? eta,
  String? error,
}) => SetupProgress(
  jobId: 'catalog',
  state: state,
  current: SetupComponentIds.openCode,
  overall: .7,
  etaSeconds: eta,
  error: error,
  logTail: 'Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease',
  components: [..._done, openCode, start],
);

WidgetbookUseCase _useCase(String name, SetupProgress progress) =>
    WidgetbookUseCase(
      name: name,
      builder: (_) => FakeAppScene<_Scene>(
        platform: FakePlatform.android,
        create: () async =>
            _Scene(await FakeApp.create(), _HeldSetup(progress)),
        builder: (context, scene) => PhoneSetupProgressScreen(
          engine: scene.engine,
          askNotifications: () async => true,
          openReady: (_) async {},
          readNetwork: () async =>
              const NetworkReading(transport: NetworkTransport.wifi),
        ),
      ),
    );

CatalogComponent phoneSetupProgressScreen() => catalogComponent(
  area: 'Servers',
  screen: 'PhoneSetupProgressScreen',
  name: 'Phone setup progress',
  useCases: [
    _useCase(
      'Downloading',
      _job(
        SetupState.running,
        eta: 170,
        openCode: const ComponentProgress(
          id: SetupComponentIds.openCode,
          state: ComponentState.running,
          stage: 'Downloading OpenCode',
          bytesDone: 41200000,
          bytesTotal: 64000000,
        ),
      ),
    ),
    _useCase(
      'A step with no amount',
      _job(
        SetupState.running,
        eta: 20,
        openCode: const ComponentProgress(
          id: SetupComponentIds.openCode,
          state: ComponentState.done,
          version: '1.4.2',
        ),
        start: const ComponentProgress(
          id: SetupComponentIds.start,
          state: ComponentState.running,
          stage: 'Starting OpenCode',
        ),
      ),
    ),
    _useCase('Paused', _job(SetupState.cancelled)),
    _useCase(
      "Didn't finish",
      _job(
        SetupState.failed,
        openCode: const ComponentProgress(
          id: SetupComponentIds.openCode,
          state: ComponentState.failed,
          stage: 'Downloading OpenCode',
          error: 'curl: (6) Could not resolve host',
        ),
      ),
    ),
    _useCase(
      'Done',
      _job(
        SetupState.done,
        openCode: const ComponentProgress(
          id: SetupComponentIds.openCode,
          state: ComponentState.done,
          version: '1.4.2',
        ),
        start: const ComponentProgress(
          id: SetupComponentIds.start,
          state: ComponentState.done,
        ),
      ),
    ),
  ],
);

/// The Details sheet's body: every step of the job and its folded log.
CatalogComponent phoneSetupDetails() => catalogComponent(
  area: 'Servers',
  screen: 'SetupProgressView',
  name: 'Phone setup details',
  useCases: [
    WidgetbookUseCase(
      name: 'Every step while one downloads',
      builder: (_) => FakeAppScene<_Scene>(
        platform: FakePlatform.android,
        create: () async => _Scene(
          await FakeApp.create(),
          _HeldSetup(
            _job(
              SetupState.running,
              openCode: const ComponentProgress(
                id: SetupComponentIds.openCode,
                state: ComponentState.running,
                stage: 'Downloading OpenCode',
                bytesDone: 41200000,
                bytesTotal: 64000000,
              ),
            ),
          ),
        ),
        builder: (context, scene) => ListView(
          children: [
            SetupProgressView(
              progress: scene.engine.progress.value,
              components: scene.engine.registry,
              checklistOnly: true,
            ),
          ],
        ),
      ),
    ),
  ],
);
