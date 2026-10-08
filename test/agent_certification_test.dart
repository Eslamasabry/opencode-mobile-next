import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_tools/agent_certification.dart';
import 'package:opencode_mobile/domain/agent_tools/agent_certification_snapshot.dart';

void main() {
  final claude = AgentCatalog.builtIn.byId('claude')!;

  Map<String, dynamic> record({String id = 'claude'}) => {
    'id': id,
    'agentVersion': AgentCatalog.builtIn
        .byId(id == 'opencode1' ? 'opencode' : id)
        ?.recipe
        ?.version,
    'helperVersion': '0.9.2',
    'architecture': 'arm64',
    'cells': {
      for (final cell in ['resume', 'models', 'permission', 'images', 'abort'])
        cell: {'state': 'pass', 'evidence': 'docs/qa/example/README.md#proof'},
    },
  };

  AgentCapabilities project(
    List<Object?> rows, {
    AgentDescriptor? descriptor,
    AgentArchitecture? architecture = AgentArchitecture.arm64,
    String? helperVersion = '0.9.2',
  }) => AgentCertificationMatrix.fromJson({'agents': rows}).capabilitiesFor(
    descriptor: descriptor ?? claude,
    architecture: architecture,
    helperVersion: helperVersion,
  );

  List<bool> flags(AgentCapabilities capabilities) => [
    capabilities.resumeVerified,
    capabilities.modelList,
    capabilities.permissions,
    capabilities.images,
    capabilities.cancel,
  ];

  void expectClosed(AgentCapabilities capabilities) =>
      expect(flags(capabilities), everyElement(isFalse));

  test('bundled snapshot preserves the reviewed FQ1 JSON verbatim', () {
    expect(
      agentCertificationBundledJson,
      File(
        'docs/verification/agent-certification-matrix.json',
      ).readAsStringSync(),
    );
    expect(jsonDecode(agentCertificationBundledJson), isA<Map>());
  });

  test('bundled Claude evidence qualifies arm64 and x64 with exact helper', () {
    expect(claude.recipe!.version, '2.1.283');
    for (final architecture in AgentArchitecture.values) {
      final proof = AgentCertificationMatrix.bundled.capabilitiesFor(
        descriptor: claude,
        architecture: architecture,
        helperVersion: '0.9.2',
      );
      expect(proof.resumeVerified, isTrue);
      expect(flags(proof), everyElement(isTrue));
    }
    for (final helperVersion in [null, '0.9.3']) {
      expectClosed(
        AgentCertificationMatrix.bundled.capabilitiesFor(
          descriptor: claude,
          architecture: AgentArchitecture.arm64,
          helperVersion: helperVersion,
        ),
      );
    }
  });

  test('bundled other agents remain unverified on arm64 and x64', () {
    for (final descriptor in AgentCatalog.builtIn.agents) {
      if (descriptor.id == 'claude') continue;
      for (final architecture in AgentArchitecture.values) {
        expectClosed(
          AgentCertificationMatrix.bundled.capabilitiesFor(
            descriptor: descriptor,
            architecture: architecture,
            helperVersion: '0.9.2',
          ),
        );
      }
    }
  });

  test('each exact pass cell grants only its corresponding capability', () {
    final names = ['resume', 'models', 'permission', 'images', 'abort'];
    for (var index = 0; index < names.length; index++) {
      final row = record();
      final cells = row['cells'] as Map;
      cells.removeWhere((key, _) => key != names[index]);
      expect(
        flags(project([row])),
        List.generate(names.length, (flag) => flag == index),
      );
    }
    expect(flags(project([record()])), everyElement(isTrue));
    final installed = record();
    installed['cells'] = {
      for (final cell in ['install', 'version', 'smoke', 'cards'])
        cell: {'state': 'pass', 'evidence': 'docs/qa/example/README.md'},
    };
    expectClosed(project([installed]));
  });

  test('omitted architecture qualifies protocol capabilities across CPUs', () {
    final row = record()..remove('architecture');
    for (final architecture in [...AgentArchitecture.values, null]) {
      expect(
        flags(project([row], architecture: architecture)),
        everyElement(isTrue),
      );
    }
    expectClosed(project([row], helperVersion: null));
    expectClosed(project([row], helperVersion: '0.9.3'));
    for (final field in ['agentVersion', 'helperVersion']) {
      expectClosed(project([Map.of(row)..remove(field)]));
      expectClosed(project([Map.of(row)..[field] = 'different-version']));
    }
  });

  test('present architecture scopes evidence to that CPU', () {
    for (final certified in AgentArchitecture.values) {
      final row = record()..['architecture'] = certified.name;
      for (final observed in [...AgentArchitecture.values, null]) {
        expect(
          flags(project([row], architecture: observed)),
          everyElement(observed == certified),
        );
      }
    }
  });

  test('photos require their own passing images cell', () {
    final row = record()..remove('architecture');
    row['cells'] = <String, dynamic>{
      'cards': {'state': 'pass', 'evidence': 'docs/qa/example/README.md'},
    };
    expectClosed(project([row]));
    final cells = row['cells'] as Map;
    for (final state in ['pass', 'partial', 'fail', 'off', 'untested']) {
      cells['images'] = {
        'state': state,
        'evidence': 'docs/qa/example/README.md#photos',
      };
      expect(flags(project([row], architecture: AgentArchitecture.arm64)), [
        false,
        false,
        false,
        state == 'pass',
        false,
      ]);
    }
    cells['images'] = {'state': 'pass', 'evidence': null};
    expectClosed(project([row]));
  });

  test(
    'missing or mismatched pins and unknown architecture invalidate proof',
    () {
      for (final field in ['agentVersion', 'helperVersion', 'architecture']) {
        for (final invalid in [null, '', 'unknown', 7, ' arm64']) {
          final row = record()..[field] = invalid;
          expectClosed(project([row]));
        }
      }
      expectClosed(project([record()], helperVersion: null));
      expectClosed(project([record()], helperVersion: '0.9.3'));
      expectClosed(project([record()], architecture: null));
      expectClosed(project([record()], architecture: AgentArchitecture.x64));
      final nextRecipe = AgentInstallRecipe(
        version: '${claude.recipe!.version}-next',
        executable: claude.recipe!.executable,
        artifacts: claude.recipe!.artifacts,
      );
      final upgraded = AgentDescriptor(
        id: claude.id,
        name: claude.name,
        iconKey: claude.iconKey,
        route: claude.route,
        providerId: claude.providerId,
        signInMethod: claude.signInMethod,
        recipe: nextRecipe,
        limitation: claude.limitation,
        resumeReason: claude.resumeReason,
      );
      expectClosed(project([record()], descriptor: upgraded));
      final noRecipe = AgentDescriptor(
        id: claude.id,
        name: claude.name,
        iconKey: claude.iconKey,
        route: claude.route,
        providerId: claude.providerId,
        signInMethod: claude.signInMethod,
        availability: AgentAvailability.hidden,
        unavailableReason: AgentUnavailableReason.recipeUnverified,
        resumeReason: claude.resumeReason,
      );
      expectClosed(project([record()], descriptor: noRecipe));
    },
  );

  test('only exact pass with safe repository evidence grants a capability', () {
    for (final evidence in [
      'docs/qa/example/README.md',
      'docs/qa/example/README.md#proof',
      'docs/qa/example/README.md #2',
    ]) {
      final row = record();
      row['cells'] = {
        'resume': {'state': 'pass', 'evidence': evidence},
      };
      expect(project([row]).resumeVerified, isTrue);
    }
    for (final state in [
      'fail',
      'untested',
      'partial',
      'off',
      'n/a',
      'blocked',
      'blocked:OW1',
      'PASS',
      ' pass',
      null,
      true,
    ]) {
      final row = record();
      row['cells'] = {
        'resume': {'state': state, 'evidence': 'docs/qa/example/README.md'},
      };
      expectClosed(project([row]));
    }
    for (final evidence in [
      null,
      '',
      ' ',
      '/docs/qa/README.md',
      '../docs/qa/README.md',
      'docs/../README.md',
      'docs//README.md',
      'docs/./README.md',
      'docs\\qa\\README.md',
      'https://example.test/README.md',
      'docs/qa/README.md?token=redacted',
      'docs/%2e%2e/README.md',
      'docs/qa/README.md#',
      'docs/qa/README.md#proof#other',
      'docs/qa/README.md (forms)',
      'docs/qa/README.md\u0000',
      '#95 run, APK 2187',
      true,
    ]) {
      final row = record();
      row['cells'] = {
        'resume': {'state': 'pass', 'evidence': evidence},
      };
      expectClosed(project([row]));
    }
  });

  test(
    'duplicate IDs invalidate all records including the OpenCode 1 alias',
    () {
      expectClosed(project([record(), record()]));
      expectClosed(
        project([
          record(),
          {'id': 'claude', 'cells': null},
        ]),
      );
      expectClosed(
        project([
          {'id': 'claude'},
          record(),
        ]),
      );
      final opencode = AgentCatalog.builtIn.byId('opencode')!;
      expect(
        project([record(id: 'opencode1')], descriptor: opencode).resumeVerified,
        isTrue,
      );
      expectClosed(
        project([
          record(id: 'opencode'),
          record(id: 'opencode1'),
        ], descriptor: opencode),
      );
    },
  );

  test('malformed and unrecognized records never grant capabilities', () {
    for (final invalid in [null, [], 3, 'records']) {
      expectClosed(
        AgentCertificationMatrix.fromJson({'agents': invalid}).capabilitiesFor(
          descriptor: claude,
          architecture: AgentArchitecture.arm64,
          helperVersion: '0.9.2',
        ),
      );
    }
    expectClosed(
      project([
        null,
        3,
        [],
        {'id': true},
        record(id: 'unknown'),
      ]),
    );
    for (final cells in [
      null,
      [],
      true,
      {'resume': true},
      {'resume': null},
      {
        'unexpected': {'state': 'pass', 'evidence': 'docs/qa/README.md'},
      },
    ]) {
      expectClosed(project([record()..['cells'] = cells]));
    }
    final unknown = AgentDescriptor(
      id: 'unknown',
      name: 'Unknown',
      iconKey: 'unknown',
      route: claude.route,
      providerId: 'unknown',
      signInMethod: claude.signInMethod,
      recipe: claude.recipe,
      capabilities: const AgentCapabilities(resumeVerified: true),
      limitation: 'Needs verification.',
    );
    expectClosed(project([record(id: 'unknown')], descriptor: unknown));
  });

  test('projection freezes input and retains unverified resume copy', () {
    final row = record();
    final matrix = AgentCertificationMatrix.fromJson({
      'agents': [row],
    });
    (row['cells'] as Map).clear();
    row['agentVersion'] = 'changed';
    expect(
      flags(
        matrix.capabilitiesFor(
          descriptor: claude,
          architecture: AgentArchitecture.arm64,
          helperVersion: '0.9.2',
        ),
      ),
      everyElement(isTrue),
    );
    expect(claude.resumeLabel, "Can't reopen old chats");
    expect(claude.resumeNote, 'Starts a new chat');
  });

  group('certified for chat (the agent picker\'s gate)', () {
    Map<String, dynamic> chatRow({
      String id = 'claude',
      String? agentVersion,
      Map<String, String> states = const {'install': 'pass', 'smoke': 'pass'},
    }) => {
      'id': id,
      'agentVersion':
          agentVersion ?? AgentCatalog.builtIn.byId(id)!.recipe!.version,
      'helperVersion': '0.9.2',
      'cells': {
        for (final MapEntry(:key, :value) in states.entries)
          key: {'state': value, 'evidence': 'docs/qa/example/README.md'},
      },
    };

    bool certified(Map<String, dynamic> row, [String id = 'claude']) =>
        AgentCertificationMatrix.fromJson({
          'agents': [row],
        }).certifiedForChat(id);

    test('the bundled matrix certifies Claude Code and nothing else yet', () {
      final bundled = AgentCertificationMatrix.bundled;
      expect(bundled.certifiedForChat('claude'), isTrue);
      for (final id in [
        'codex',
        'gemini',
        'qwen',
        'goose',
        'omp-acp',
        'fx',
        'unknown',
      ]) {
        expect(bundled.certifiedForChat(id), isFalse, reason: id);
      }
    });

    test('needs the pinned version with install and smoke passed', () {
      expect(certified(chatRow()), isTrue);
      expect(certified(chatRow(agentVersion: '2.1.282')), isFalse);
      expect(
        certified(chatRow(states: {'install': 'pass', 'smoke': 'untested'})),
        isFalse,
      );
      expect(
        certified(chatRow(states: {'install': 'pass', 'smoke': 'blocked:OW1'})),
        isFalse,
      );
      expect(certified(chatRow(states: {'smoke': 'pass'})), isFalse);
      // A row for another agent certifies nothing here.
      expect(certified(chatRow(id: 'fx'), 'claude'), isFalse);
    });
  });
}
