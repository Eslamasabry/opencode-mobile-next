// Coverage ratchets for what the app itself knows about each agent: the
// catalog entry (name, download, sign-in) and the certification matrix row.
// The agents list and the agent picker draw them; each field is checked
// against its ledger (see paseo_coverage_support.dart).
//
// The catalog is app code, not a wire answer, so its inventory is dumped from
// the live catalog (`OC_DUMP_CATALOG=1` rewrites
// test/fixtures/coverage/phone_agent_catalog_source.json) and a test fails
// when the dump and the live catalog differ.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart' show frames;

Map<String, Object?> _artifact(AgentArtifact a) => {
  'url': a.url.toString(),
  'sha256': a.sha256,
  'format': a.format.name,
  if (a.archiveMember != null) 'archiveMember': a.archiveMember,
  if (a.downloadBytes != null) 'downloadBytes': a.downloadBytes,
  if (a.installedBytes != null) 'installedBytes': a.installedBytes,
};

Map<String, Object?> _entry(AgentDescriptor d) => {
  'id': d.id,
  'name': d.name,
  'iconKey': d.iconKey,
  'route': d.route.name,
  'providerId': d.providerId,
  'signInMethod': d.signInMethod.name,
  'availability': d.availability.name,
  if (d.unavailableReason != null)
    'unavailableReason': d.unavailableReason!.name,
  if (d.limitation != null) 'limitation': d.limitation,
  if (d.resumeReason != null) 'resumeReason': d.resumeReason,
  'capabilities': {
    'resumeVerified': d.capabilities.resumeVerified,
    'modelList': d.capabilities.modelList,
    'permissions': d.capabilities.permissions,
    'images': d.capabilities.images,
    'cancel': d.capabilities.cancel,
  },
  if (d.recipe != null)
    'recipe': {
      'version': d.recipe!.version,
      'executable': d.recipe!.executable,
      'launchArgs': d.recipe!.launchArgs,
      'signInArgs': d.recipe!.signInArgs,
      'artifacts': {
        for (final e in d.recipe!.artifacts.entries)
          e.key.name: _artifact(e.value),
      },
    },
};

const _sourcePath = 'test/fixtures/coverage/phone_agent_catalog_source.json';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  final live = [for (final d in AgentCatalog.builtIn.agents) _entry(d)];
  if (Platform.environment['OC_DUMP_CATALOG'] == '1') {
    File(_sourcePath).writeAsStringSync(
      const JsonEncoder.withIndent('  ').convert({'agents': live}),
    );
  }
  test('the inventory file is the live catalog', () {
    final saved =
        (jsonDecode(File(_sourcePath).readAsStringSync()) as Map)['agents'];
    expect(jsonEncode(saved), jsonEncode(live));
  });

  final catalog = CoverageFamily('phone_agent_catalog', prefix: '');
  final cert = CoverageFamily('phone_agent_certification', prefix: '');
  group('ledger · catalog', () => registerLedgerTests(catalog));
  group('ledger · certification', () => registerLedgerTests(cert));

  Future<GlobalKey> pump(
    WidgetTester tester,
    FakePhoneAgentsSource agents,
    Widget child,
  ) async {
    tester.view.physicalSize = const Size(412, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final boundary = GlobalKey();
    final host = FakeChatsHost(
      FakeChatFeedSource(
        items: const [],
        projects: [project('alpha')],
        lastUsed: '/root/projects/alpha',
      ),
    )..phoneAgents = agents;
    await tester.pumpWidget(
      chatsApp(host, RepaintBoundary(key: boundary, child: child)),
    );
    await frames(tester, 14);
    return boundary;
  }

  for (final variant in catalog.cases) {
    final id = variant['id'] as String;
    testWidgets('agent catalog · $id', (tester) async {
      final agent = Map<String, dynamic>.from(
        (variant['payload'] as Map)['agent'] as Map,
      );
      final agentId = agent['id'] as String;
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor(agentId, FakeAgentStage.notInstalled)],
      );
      final boundary = await pump(
        tester,
        agents,
        ListView(children: const [AgentsSection()]),
      );
      final screen = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'phone_$id');
      final problems = checkCase(catalog, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }

  for (final variant in cert.cases) {
    final id = variant['id'] as String;
    testWidgets('agent certification · $id', (tester) async {
      final agentId = (variant['payload'] as Map)['id'] as String;
      final agents = FakeAccountAgentsSource(
        rows: [
          for (final rowId in {'claude', agentId})
            agentRowFor(rowId, FakeAgentStage.ready),
        ],
      );
      for (final row in agents.agentRows) {
        agents.check(
          row.id,
          const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn),
        );
      }
      final boundary = await pump(tester, agents, const AgentSheet());
      final screen = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'phone_$id');
      final problems = checkCase(cert, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
