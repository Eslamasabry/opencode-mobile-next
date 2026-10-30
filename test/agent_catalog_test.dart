import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

void main() {
  AgentArtifact artifact({
    String? member,
    AgentArtifactFormat format = AgentArtifactFormat.executable,
  }) => AgentArtifact(
    url: Uri.parse('https://example.test/agent-1.0.0'),
    sha256: 'a' * 64,
    format: format,
    archiveMember: member,
  );
  AgentInstallRecipe recipe({
    Map<AgentArchitecture, AgentArtifact>? artifacts,
    List<String>? args,
  }) => AgentInstallRecipe(
    version: '1.0.0',
    executable: 'agent',
    artifacts: artifacts ?? {AgentArchitecture.arm64: artifact()},
    launchArgs: args ?? [],
  );
  AgentDescriptor agent({
    AgentInstallRecipe? installRecipe,
    AgentUnavailableReason? reason,
    AgentAvailability availability = AgentAvailability.limited,
  }) => AgentDescriptor(
    id: 'new-agent',
    name: 'New Agent',
    iconKey: 'new-agent',
    route: AgentRoute.acpPaseo,
    providerId: 'new-agent',
    signInMethod: AgentSignInMethod.apiKeyHost,
    recipe: installRecipe,
    unavailableReason: reason,
    availability: availability,
    limitation: 'Needs host setup.',
    resumeReason: 'Saved conversations need a check.',
  );

  test('runtime capabilities fail closed even when installation is pinned', () {
    final entry = agent(installRecipe: recipe());
    expect(entry.installableOn(AgentArchitecture.arm64), isTrue);
    expect(entry.installableOn(AgentArchitecture.x64), isFalse);
    expect(entry.capabilities.resumeVerified, isFalse);
    expect(entry.resumeLabel, "Can't reopen old chats");
    expect(entry.resumeNote, 'Starts a new chat');
    expect(entry.capabilities.modelList, isFalse);
    expect(entry.capabilities.permissions, isFalse);
    expect(entry.capabilities.images, isFalse);
    expect(entry.capabilities.cancel, isFalse);
  });

  test(
    'unknown recipe stays hidden with a reason and unknown sizes stay null',
    () {
      final entry = agent(
        reason: AgentUnavailableReason.recipeUnverified,
        availability: AgentAvailability.hidden,
      );
      expect(entry.installableOn(AgentArchitecture.arm64), isFalse);
      expect(entry.artifactFor(AgentArchitecture.arm64), isNull);
      expect(artifact().downloadBytes, isNull);
      expect(artifact().installedBytes, isNull);
      expect(() => agent(), throwsArgumentError);
      expect(
        () => agent(
          installRecipe: recipe(),
          reason: AgentUnavailableReason.recipeUnverified,
        ),
        throwsArgumentError,
      );
    },
  );

  test('artifact rejects unpinned, credential-bearing, or escaping inputs', () {
    expect(
      () => AgentArtifact(
        url: Uri.parse('http://example.test/agent'),
        sha256: 'a' * 64,
        format: AgentArtifactFormat.executable,
      ),
      throwsArgumentError,
    );
    expect(
      () => AgentArtifact(
        url: Uri.parse('https://user:secret@example.test/agent'),
        sha256: 'a' * 64,
        format: AgentArtifactFormat.executable,
      ),
      throwsArgumentError,
    );
    expect(
      () => AgentArtifact(
        url: Uri.parse('https://example.test/agent'),
        sha256: 'unknown',
        format: AgentArtifactFormat.executable,
      ),
      throwsArgumentError,
    );
    for (final member in [
      '../agent',
      '/agent',
      'a/../agent',
      'a\\agent',
      'a//agent',
    ]) {
      expect(
        () => artifact(member: member, format: AgentArtifactFormat.tarGz),
        throwsArgumentError,
      );
    }
    expect(
      () => artifact(format: AgentArtifactFormat.tarGz),
      throwsArgumentError,
    );
    expect(() => artifact(member: 'agent'), throwsArgumentError);
    expect(
      artifact(
        member: './agent',
        format: AgentArtifactFormat.tarGz,
      ).archiveMember,
      './agent',
    );
  });

  test('recipes and catalogs defend against input mutation', () {
    final artifacts = {AgentArchitecture.arm64: artifact()};
    final args = ['--acp'];
    final installRecipe = recipe(artifacts: artifacts, args: args);
    final entries = [agent(installRecipe: installRecipe)];
    final catalog = AgentCatalog(agents: entries);
    artifacts.clear();
    args.clear();
    entries.clear();
    expect(catalog.agents, hasLength(1));
    expect(installRecipe.artifacts, hasLength(1));
    expect(installRecipe.launchArgs, ['--acp']);
    expect(() => catalog.agents.clear(), throwsUnsupportedError);
    expect(() => installRecipe.artifacts.clear(), throwsUnsupportedError);
    expect(() => installRecipe.launchArgs.clear(), throwsUnsupportedError);
    expect(() => installRecipe.signInArgs.add('login'), throwsUnsupportedError);
  });

  test(
    'a future agent is one data entry with no provider dispatch changes',
    () {
      final descriptor = agent(installRecipe: recipe());
      final catalog = AgentCatalog(agents: [descriptor]);
      expect(catalog.byId('new-agent'), same(descriptor));
      expect(catalog.byId('missing'), isNull);
      expect(
        () => AgentCatalog(agents: [descriptor, descriptor]),
        throwsArgumentError,
      );
    },
  );

  test(
    'seed recipes are pinned for both architectures without runtime claims',
    () {
      final catalog = AgentCatalog.builtIn;
      expect(catalog.agents.map((entry) => entry.id).toSet(), {
        'opencode2',
        'claude',
        'codex',
        'gemini',
        'qwen',
        'kilo',
        'cline',
        'goose',
        'omp-acp',
        'fx',
      });
      for (final entry in catalog.agents) {
        for (final architecture in AgentArchitecture.values) {
          expect(entry.installableOn(architecture), isTrue, reason: entry.id);
          expect(
            entry.artifactFor(architecture)!.sha256,
            matches(RegExp(r'^[a-f0-9]{64}$')),
          );
        }
        expect(entry.capabilities.resumeVerified, isFalse);
        expect(entry.capabilities.permissions, isFalse);
        expect(entry.availability, AgentAvailability.limited);
      }
      expect(catalog.byId('qwen')!.signInMethod, AgentSignInMethod.apiKeyHost);
      expect(catalog.byId('gemini')!.recipe!.launchArgs, ['--acp']);
      expect(
        catalog.byId('gemini')!.artifactFor(AgentArchitecture.arm64)!.format,
        AgentArtifactFormat.npmTarGz,
      );
      expect(catalog.byId('claude')!.recipe!.version, '2.1.294');
    },
  );

  test('Kilo and Cline are pinned ACP agents with exact launch args', () {
    final catalog = AgentCatalog.builtIn;
    const pins = {
      'kilo': (
        version: '7.8.3',
        member: 'package/bin/kilo',
        args: ['acp'],
        signIn: ['auth', 'login'],
        arm64:
            'bd61f03eb14053fdcaf5245eb1740455b0447fe9d6d48b4adaeea6bb4ae53a90',
        x64: '03b963726d8002ea6bee21d8db6121201e6ed8230a0e1fce8857f4c8d091333c',
        path: '@kilocode/cli-linux-',
      ),
      'cline': (
        version: '3.0.68',
        member: 'package/bin/cline',
        args: ['--acp'],
        signIn: ['auth'],
        arm64:
            '5e0ad56ba4c494c437f6dcdc2557c165478e05e1800e1fe6f7d476e4fd4c84f2',
        x64: 'a979929090eb9a934b3907e2cf519368964d6a35e96079d6db4fc89caad98ab1',
        path: '@cline/cli-linux-',
      ),
    };
    for (final MapEntry(key: id, value: pin) in pins.entries) {
      final entry = catalog.byId(id)!;
      expect(entry.route, AgentRoute.acpPaseo, reason: id);
      expect(entry.providerId, id);
      expect(entry.recipe!.executable, id);
      expect(entry.recipe!.version, pin.version);
      expect(entry.recipe!.launchArgs, pin.args);
      expect(entry.recipe!.signInArgs, pin.signIn);
      final arm = entry.artifactFor(AgentArchitecture.arm64)!;
      final x64 = entry.artifactFor(AgentArchitecture.x64)!;
      expect(arm.sha256, pin.arm64);
      expect(x64.sha256, pin.x64);
      for (final a in [arm, x64]) {
        expect(a.format, AgentArtifactFormat.tarGz);
        expect(a.archiveMember, pin.member);
        expect(a.url.host, 'registry.npmjs.org');
        expect(a.url.path, contains(pin.path));
        expect(a.url.path, endsWith('-${pin.version}.tgz'));
        expect(a.downloadBytes, isPositive);
        expect(a.installedBytes, greaterThan(a.downloadBytes!));
      }
      expect(arm.url.path, contains('linux-arm64'));
      expect(x64.url.path, contains('linux-x64'));
    }
  });

  test('catalog installs only supported OpenCode 2', () {
    final catalog = AgentCatalog.builtIn;
    final preferred = catalog.agents.first;
    expect(preferred.id, 'opencode2');
    expect(preferred.route, AgentRoute.opencode2);
    expect(preferred.recipe!.version, '2.1.1');
    for (final architecture in AgentArchitecture.values) {
      expect(
        preferred.artifactFor(architecture)!.url.path,
        endsWith('-2.1.1.tgz'),
      );
    }
    expect(catalog.byId('opencode'), isNull);
  });

  test('unproven resume and visible limits require plain explanations', () {
    expect(
      () => AgentDescriptor(
        id: 'agent',
        name: 'Agent',
        iconKey: 'agent',
        route: AgentRoute.acpPaseo,
        providerId: 'agent',
        signInMethod: AgentSignInMethod.none,
        recipe: recipe(),
        availability: AgentAvailability.available,
      ),
      throwsArgumentError,
    );
    expect(
      () => AgentDescriptor(
        id: 'agent',
        name: 'Agent',
        iconKey: 'agent',
        route: AgentRoute.acpPaseo,
        providerId: 'agent',
        signInMethod: AgentSignInMethod.none,
        recipe: recipe(),
        resumeReason: 'Needs a check.',
      ),
      throwsArgumentError,
    );
  });
}
