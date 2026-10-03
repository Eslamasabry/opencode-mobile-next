/// App-authored installation metadata, separate from runtime qualification.
/// A pinned download never proves sign-in, resume, or phone compatibility.
library;

enum AgentRoute { opencode1, opencode2, paseoNative, acpPaseo }

enum AgentArchitecture { arm64, x64 }

enum AgentArtifactFormat { executable, tarGz, tarBz2, zip, npmTarGz }

enum AgentSignInMethod { browserOAuthHost, apiKeyHost, none }

enum AgentAvailability { available, limited, hidden }

enum AgentUnavailableReason {
  recipeUnverified,
  dependencyClosureUnverified,
  unsupportedArchitecture,
}

/// Proof obtained for the selected runtime, never inferred from agent identity.
final class AgentCapabilities {
  const AgentCapabilities({
    this.resumeVerified = false,
    this.modelList = false,
    this.permissions = false,
    this.images = false,
    this.cancel = false,
  });

  final bool resumeVerified;
  final bool modelList;
  final bool permissions;
  final bool images;
  final bool cancel;
}

final class AgentArtifact {
  AgentArtifact({
    required this.url,
    required this.sha256,
    required this.format,
    this.archiveMember,
    this.downloadBytes,
    this.installedBytes,
  }) {
    if (url.scheme != 'https' ||
        url.host.isEmpty ||
        url.userInfo.isNotEmpty ||
        url.hasFragment) {
      throw ArgumentError('Artifact URL must be HTTPS without credentials.');
    }
    if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(sha256)) {
      throw ArgumentError('Artifact needs an exact lowercase SHA-256 pin.');
    }
    if (format == AgentArtifactFormat.executable) {
      if (archiveMember != null) {
        throw ArgumentError('Executable artifacts have no archive member.');
      }
    } else if (archiveMember == null || !_safeMember(archiveMember!)) {
      throw ArgumentError('Archive artifacts need a safe relative member.');
    }
    if ((downloadBytes != null && downloadBytes! <= 0) ||
        (installedBytes != null && installedBytes! <= 0)) {
      throw ArgumentError('Known artifact sizes must be positive.');
    }
  }

  final Uri url;
  final String sha256;
  final AgentArtifactFormat format;

  /// Entrypoint to copy for simple archives. For npmTarGz preserve the ENTIRE
  /// package tree and invoke this member with the separately pinned host Node.
  final String? archiveMember;
  final int? downloadBytes;

  /// Payload size, not a promise about total disk use or peak install space.
  /// Null means unknown; never substitute a made-up estimate.
  final int? installedBytes;

  static bool _safeMember(String value) {
    final normalized = value.startsWith('./') ? value.substring(2) : value;
    return normalized.isNotEmpty &&
        !normalized.startsWith('/') &&
        !normalized.contains('\\') &&
        !normalized.contains('\u0000') &&
        normalized
            .split('/')
            .every((part) => part.isNotEmpty && part != '.' && part != '..');
  }
}

final class AgentInstallRecipe {
  AgentInstallRecipe({
    required this.version,
    required this.executable,
    required Map<AgentArchitecture, AgentArtifact> artifacts,
    List<String> launchArgs = const [],
    List<String> signInArgs = const [],
  }) : artifacts = Map.unmodifiable(artifacts),
       launchArgs = List.unmodifiable(launchArgs),
       signInArgs = List.unmodifiable(signInArgs) {
    if (version.trim().isEmpty ||
        !RegExp(r'^[a-z][a-z0-9-]*$').hasMatch(executable) ||
        artifacts.isEmpty) {
      throw ArgumentError('Recipe needs a version, executable, and artifact.');
    }
    if ([...launchArgs, ...signInArgs].any((arg) => arg.contains('\u0000'))) {
      throw ArgumentError('Arguments cannot contain NUL.');
    }
  }

  final String version;
  final String executable;
  final Map<AgentArchitecture, AgentArtifact> artifacts;

  /// Argument arrays are data, never shell fragments. ACP provider config uses
  /// executable + launchArgs; Paseo native providers own their launch protocol.
  final List<String> launchArgs;

  /// Empty means interactive host setup; it does not mean no authentication.
  final List<String> signInArgs;
}

final class AgentDescriptor {
  AgentDescriptor({
    required this.id,
    required this.name,
    required this.iconKey,
    required this.route,
    required this.providerId,
    required this.signInMethod,
    this.recipe,
    this.capabilities = const AgentCapabilities(),
    this.availability = AgentAvailability.limited,
    this.unavailableReason,
    this.limitation,
    this.resumeReason,
  }) {
    if (!RegExp(r'^[a-z][a-z0-9-]*$').hasMatch(id) ||
        providerId.trim().isEmpty ||
        name.trim().isEmpty ||
        iconKey.trim().isEmpty) {
      throw ArgumentError('Agent identity must be nonempty and stable.');
    }
    if ((recipe == null || availability == AgentAvailability.hidden) &&
        unavailableReason == null) {
      throw ArgumentError('An unavailable agent needs an explicit reason.');
    }
    if (availability != AgentAvailability.hidden && unavailableReason != null) {
      throw ArgumentError('Unavailable agents must be hidden.');
    }
    if (!capabilities.resumeVerified &&
        (resumeReason?.trim().isEmpty ?? true)) {
      throw ArgumentError('Unverified resume needs a plain explanation.');
    }
    if (availability == AgentAvailability.limited &&
        (limitation?.trim().isEmpty ?? true)) {
      throw ArgumentError('Limited agents need a plain explanation.');
    }
  }

  final String id;
  final String name;
  final String iconKey;
  final AgentRoute route;
  final String providerId;
  final AgentSignInMethod signInMethod;
  final AgentInstallRecipe? recipe;
  final AgentCapabilities capabilities;
  final AgentAvailability availability;
  final AgentUnavailableReason? unavailableReason;
  final String? limitation;
  final String? resumeReason;

  AgentArtifact? artifactFor(AgentArchitecture architecture) =>
      recipe?.artifacts[architecture];

  /// Installation availability does not make this agent selectable for chat.
  bool installableOn(AgentArchitecture architecture) =>
      unavailableReason == null && artifactFor(architecture) != null;
}

final class AgentCatalog {
  AgentCatalog({required List<AgentDescriptor> agents})
    : agents = List.unmodifiable(agents) {
    if (agents.map((agent) => agent.id).toSet().length != agents.length) {
      throw ArgumentError('Agent IDs must be unique.');
    }
  }

  final List<AgentDescriptor> agents;

  AgentDescriptor? byId(String id) {
    for (final agent in agents) {
      if (agent.id == id) return agent;
    }
    return null;
  }

  static final AgentCatalog builtIn = _builtInCatalog();
}

AgentCatalog _builtInCatalog() {
  AgentArtifact binary(String url, String hash, int bytes) => AgentArtifact(
    url: Uri.parse(url),
    sha256: hash,
    format: AgentArtifactFormat.executable,
    downloadBytes: bytes,
    installedBytes: bytes,
  );
  AgentArtifact archive(
    String url,
    String hash,
    String member, {
    int? bytes,
    int? installedBytes,
    AgentArtifactFormat format = AgentArtifactFormat.tarGz,
  }) => AgentArtifact(
    url: Uri.parse(url),
    sha256: hash,
    format: format,
    archiveMember: member,
    downloadBytes: bytes,
    installedBytes: installedBytes,
  );
  AgentDescriptor entry(
    String id,
    String name,
    AgentRoute route,
    String providerId,
    AgentSignInMethod signIn,
    AgentInstallRecipe recipe, {
    String? limitation,
  }) => AgentDescriptor(
    id: id,
    name: name,
    iconKey: id,
    route: route,
    providerId: providerId,
    signInMethod: signIn,
    recipe: recipe,
    limitation: limitation ?? 'Needs a check on your phone before use.',
    resumeReason: 'Continuing saved conversations still needs a host check.',
  );

  const github = 'https://github.com';
  final gemini = archive(
    'https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-0.62.0.tgz',
    '2276032b1c33d2b828b1cf197e52f48e74b0a395326763ff01a80d97d0fbc0c3',
    'package/bundle/gemini.js',
    bytes: 20787241,
    installedBytes: 98359283,
    format: AgentArtifactFormat.npmTarGz,
  );
  final qwen = archive(
    'https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.24.7.tgz',
    '64430248ab6e996fc0c6b9a0789361f868c3b97f83d16210e0424e51dffc5fa9',
    'package/cli-entry.js',
    bytes: 31240308,
    installedBytes: 109140236,
    format: AgentArtifactFormat.npmTarGz,
  );
  return AgentCatalog(
    agents: [
      entry(
        'opencode',
        'OpenCode',
        AgentRoute.opencode1,
        'opencode',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '1.18.32',
          executable: 'opencode',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/anomalyco/opencode/releases/download/v1.18.32/opencode-linux-arm64.tar.gz',
              '568461b7d4d8c19865c97e9a1102e613049c6039d01fe772154de873c1865840',
              'opencode',
            ),
            AgentArchitecture.x64: archive(
              '$github/anomalyco/opencode/releases/download/v1.18.32/opencode-linux-x64-baseline.tar.gz',
              '763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce',
              'opencode',
            ),
          },
          launchArgs: ['serve', '--hostname', '127.0.0.1'],
          signInArgs: ['auth', 'login'],
        ),
      ),
      entry(
        'opencode2',
        'OpenCode 2',
        AgentRoute.opencode2,
        'opencode',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '2.0.10',
          executable: 'opencode',
          artifacts: {
            AgentArchitecture.arm64: archive(
              'https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.0.10.tgz',
              'cf5416676240455dc5a98500237af296cb1a00efbd2cca302ad22c92ea080ebc',
              'package/bin/opencode',
            ),
            AgentArchitecture.x64: archive(
              'https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.0.10.tgz',
              '700c4d0fcc209e42f61c10f9773331d1d1f7eff35670971ee316b38641a1a76c',
              'package/bin/opencode',
            ),
          },
          launchArgs: ['serve', '--hostname', '127.0.0.1'],
        ),
      ),
      entry(
        'claude',
        'Claude Code',
        AgentRoute.paseoNative,
        'claude',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '2.1.283',
          executable: 'claude',
          artifacts: {
            AgentArchitecture.arm64: binary(
              'https://downloads.claude.ai/claude-code-releases/2.1.283/linux-arm64/claude',
              '346d294f0103d6fc0de11ac953579b5c62dfa90698a4cfc486b6f927c615e697',
              240902136,
            ),
            AgentArchitecture.x64: binary(
              'https://downloads.claude.ai/claude-code-releases/2.1.283/linux-x64/claude',
              '1859583ce32920595c61ef868bee52e1b1594f7486db209935e01f1e5e804ae2',
              241556664,
            ),
          },
          signInArgs: ['auth', 'login', '--claudeai'],
        ),
      ),
      entry(
        'codex',
        'Codex',
        AgentRoute.paseoNative,
        'codex',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '0.160.0',
          executable: 'codex',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/openai/codex/releases/download/rust-v0.160.0/codex-aarch64-unknown-linux-musl.tar.gz',
              '883620139925f677e5a12c95ba87a1d7f421388ebb797b1c10aba42a04ede9d7',
              'codex-aarch64-unknown-linux-musl',
              bytes: 101627917,
              installedBytes: 248966648,
            ),
            AgentArchitecture.x64: archive(
              '$github/openai/codex/releases/download/rust-v0.160.0/codex-x86_64-unknown-linux-musl.tar.gz',
              '306865417d4ee7a927785852910a527f41e1e159add390ac5ae3accb67d44a13',
              'codex-x86_64-unknown-linux-musl',
              bytes: 109304578,
              installedBytes: 289101384,
            ),
          },
          launchArgs: ['app-server'],
          signInArgs: ['login', '--device-auth'],
        ),
      ),
      entry(
        'gemini',
        'Gemini CLI',
        AgentRoute.acpPaseo,
        'gemini',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '0.62.0',
          executable: 'gemini',
          artifacts: {
            AgentArchitecture.arm64: gemini,
            AgentArchitecture.x64: gemini,
          },
          launchArgs: ['--acp'],
        ),
        limitation:
            'Needs host sign-in and a phone check. Optional terminal extensions are not installed.',
      ),
      entry(
        'qwen',
        'Qwen Code',
        AgentRoute.acpPaseo,
        'qwen',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '0.24.7',
          executable: 'qwen',
          artifacts: {
            AgentArchitecture.arm64: qwen,
            AgentArchitecture.x64: qwen,
          },
          launchArgs: ['--acp'],
        ),
        limitation:
            'Set up a provider key on the host. The old Qwen sign-in option is discontinued.',
      ),
      entry(
        'goose',
        'Goose',
        AgentRoute.acpPaseo,
        'goose',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '1.53.0',
          executable: 'goose',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/aaif-goose/goose/releases/download/v1.53.0/goose-aarch64-unknown-linux-gnu.tar.gz',
              '01a0ea109e1984e7a511ff3f99d1a29d32e6f90f0852b5a3b416499c8eaae567',
              './goose',
              bytes: 94681336,
              installedBytes: 285182872,
            ),
            AgentArchitecture.x64: archive(
              '$github/aaif-goose/goose/releases/download/v1.53.0/goose-x86_64-unknown-linux-gnu.tar.gz',
              'deb2191a6b75acc0a20232fc5c52655ea2f9cc8fa2f5dffc8622e8d378a915dc',
              './goose',
              bytes: 94629008,
              installedBytes: 298665904,
            ),
          },
          launchArgs: ['acp'],
          signInArgs: ['configure'],
        ),
        limitation:
            'Configure a provider on the host. Credential storage needs a phone check.',
      ),
      entry(
        'omp-acp',
        'Oh My Pi',
        AgentRoute.acpPaseo,
        'omp-acp',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '18.5.1',
          executable: 'omp',
          artifacts: {
            AgentArchitecture.arm64: binary(
              '$github/can1357/oh-my-pi/releases/download/v18.5.1/omp-linux-arm64',
              'e7b81b96b3f9f391ec8afc7ee64c052ce1783290f88bbb59ba36d15b3b4e927c',
              230476072,
            ),
            AgentArchitecture.x64: binary(
              '$github/can1357/oh-my-pi/releases/download/v18.5.1/omp-linux-x64',
              'fb7638e82f0c9d087e96e15d3eb83a662d92374cd907187d6d4d2605e39f1e3a',
              280536544,
            ),
          },
          launchArgs: ['acp'],
        ),
        limitation:
            'Run /login on the host and choose a provider. Needs a phone check.',
      ),
      entry(
        'fx',
        'fx',
        AgentRoute.acpPaseo,
        'fx',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '0.0.12',
          executable: 'fx',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/vercel-labs/fx/releases/download/v0.0.12/fx-linux-aarch64.tar.gz',
              '7265ecebf881ec4050d24fa4fac660ed86dfd11395fcde47b491dc084be1e61e',
              'fx',
              bytes: 5406247,
              installedBytes: 10652080,
            ),
            AgentArchitecture.x64: archive(
              '$github/vercel-labs/fx/releases/download/v0.0.12/fx-linux-x86_64.tar.gz',
              'c510956b92404a00f3054b4be0d378188f9f5217497555048de353b8f0e52d80',
              'fx',
              bytes: 5482652,
              installedBytes: 12520232,
            ),
          },
          launchArgs: ['acp'],
          signInArgs: ['login'],
        ),
        limitation:
            'Choose and sign in to a provider on the host. Needs a phone check.',
      ),
    ],
  );
}
