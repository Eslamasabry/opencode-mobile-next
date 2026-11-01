/// App-authored installation metadata, separate from runtime qualification.
/// A pinned download never proves sign-in, resume, or phone compatibility.
library;

enum AgentRoute { opencode2, paseoNative, acpPaseo }

enum AgentArchitecture { arm64, x64 }

enum AgentArtifactFormat { executable, tarGz, tarBz2, zip, npmTarGz }

enum AgentSignInMethod { browserOAuthHost, apiKeyHost, none }

enum AgentAvailability { available, limited, hidden }

const agentResumeUnverifiedLabel = "Can't reopen old chats";
const agentStartsNewChatNote = 'Starts a new chat';

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

  String? get resumeLabel =>
      capabilities.resumeVerified ? null : agentResumeUnverifiedLabel;
  String? get resumeNote =>
      capabilities.resumeVerified ? null : agentStartsNewChatNote;

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
    resumeReason: agentResumeUnverifiedLabel,
  );

  const github = 'https://github.com';
  final gemini = archive(
    'https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-0.66.0.tgz',
    '269734d35287fbd8ed2982534258ecf4a95f184b6e06e6732e0d6850c28701d9',
    'package/bundle/gemini.js',
    bytes: 20889095,
    installedBytes: 98815455,
    format: AgentArtifactFormat.npmTarGz,
  );
  final qwen = archive(
    'https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.26.4.tgz',
    'a7640f1c0656b7e54ea5995335ed6cc18ee219b8b4200fe8a6141cff148aaa89',
    'package/cli-entry.js',
    bytes: 42796897,
    installedBytes: 154807157,
    format: AgentArtifactFormat.npmTarGz,
  );
  return AgentCatalog(
    agents: [
      entry(
        'opencode2',
        'OpenCode 2',
        AgentRoute.opencode2,
        'opencode',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '2.2.5',
          executable: 'opencode',
          artifacts: {
            AgentArchitecture.arm64: archive(
              'https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.5.tgz',
              'f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053',
              'package/bin/opencode',
            ),
            AgentArchitecture.x64: archive(
              'https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.5.tgz',
              '873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08',
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
          version: '2.1.316',
          executable: 'claude',
          artifacts: {
            AgentArchitecture.arm64: binary(
              'https://downloads.claude.ai/claude-code-releases/2.1.316/linux-arm64/claude',
              '55cdb8e162d190432696ea54010e0fca2e18d06fa7a7127fd563b9ffd4f22624',
              328459544,
            ),
            AgentArchitecture.x64: binary(
              'https://downloads.claude.ai/claude-code-releases/2.1.316/linux-x64/claude',
              'fd13c70000fec3f2116dfbcbb6121210b4a29328bf0e46210a59dd594a67d824',
              328262000,
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
          version: '0.166.0',
          executable: 'codex',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/openai/codex/releases/download/rust-v0.166.0/codex-aarch64-unknown-linux-musl.tar.gz',
              '94eb707605ec2646b2e05b7edf7c4e7b3ccf02b16237c34ae9beb8cfb7f810a9',
              'codex-aarch64-unknown-linux-musl',
              bytes: 113382496,
              installedBytes: 277549176,
            ),
            AgentArchitecture.x64: archive(
              '$github/openai/codex/releases/download/rust-v0.166.0/codex-x86_64-unknown-linux-musl.tar.gz',
              'a0bf572d2f93f056c6712a6da348e96078dc84f3885fd7be67807ef389cb3978',
              'codex-x86_64-unknown-linux-musl',
              bytes: 121764138,
              installedBytes: 319346888,
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
          version: '0.66.0',
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
          version: '0.26.4',
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
        'kilo',
        'Kilo',
        AgentRoute.acpPaseo,
        'kilo',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '7.8.18',
          executable: 'kilo',
          artifacts: {
            AgentArchitecture.arm64: archive(
              'https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/cli-linux-arm64-7.8.18.tgz',
              '0f1bd0fb983f67ec1edf53c185e9521268f8f133af2a4cec2f1967e5d60e45ef',
              'package/bin/kilo',
              bytes: 83529597,
              installedBytes: 258264056,
            ),
            AgentArchitecture.x64: archive(
              'https://registry.npmjs.org/@kilocode/cli-linux-x64/-/cli-linux-x64-7.8.18.tgz',
              'bde638d0a493155088e4454fbe4a9697571443665e2a22d5a78cad645b96b554',
              'package/bin/kilo',
              bytes: 84132773,
              installedBytes: 259213488,
            ),
          },
          launchArgs: ['acp'],
          signInArgs: ['auth', 'login'],
        ),
        limitation:
            'Sign in or add a provider key, then check it on your phone. Large download.',
      ),
      entry(
        'cline',
        'Cline',
        AgentRoute.acpPaseo,
        'cline',
        AgentSignInMethod.browserOAuthHost,
        AgentInstallRecipe(
          version: '3.0.74',
          executable: 'cline',
          artifacts: {
            AgentArchitecture.arm64: archive(
              'https://registry.npmjs.org/@cline/cli-linux-arm64/-/cli-linux-arm64-3.0.74.tgz',
              '10c1f63c143b6f7eba3320543df2f6081232d79fb1830c293cf006115dce9049',
              'package/bin/cline',
              bytes: 54812128,
              installedBytes: 162253096,
            ),
            AgentArchitecture.x64: archive(
              'https://registry.npmjs.org/@cline/cli-linux-x64/-/cli-linux-x64-3.0.74.tgz',
              '52502438a8d78a451156c38df2eb31116d658c94d1d5461b4e29761d06211bfc',
              'package/bin/cline',
              bytes: 55037574,
              installedBytes: 161793504,
            ),
          },
          launchArgs: ['--acp'],
          signInArgs: ['auth'],
        ),
        limitation: 'Sign in on this phone, then check it. Large download.',
      ),
      entry(
        'goose',
        'Goose',
        AgentRoute.acpPaseo,
        'goose',
        AgentSignInMethod.apiKeyHost,
        AgentInstallRecipe(
          version: '1.54.0',
          executable: 'goose',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/aaif-goose/goose/releases/download/v1.54.0/goose-aarch64-unknown-linux-gnu.tar.gz',
              '0f174b5118a53888cd4e65b7ca9a4b72dd0b887e89142c8ce41e2409205e40d5',
              './goose',
              bytes: 93850226,
              installedBytes: 294298272,
            ),
            AgentArchitecture.x64: archive(
              '$github/aaif-goose/goose/releases/download/v1.54.0/goose-x86_64-unknown-linux-gnu.tar.gz',
              'ab396c95aebbd28b4d38c175234be8d4a3ea0c96e7ccb24c307e4618c9be57e1',
              './goose',
              bytes: 90912367,
              installedBytes: 307756800,
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
          version: '0.0.13',
          executable: 'fx',
          artifacts: {
            AgentArchitecture.arm64: archive(
              '$github/vercel-labs/fx/releases/download/v0.0.13/fx-linux-aarch64.tar.gz',
              '78f6a8171193a1d2033f93d1b5a2940ea4a62147daf60a37ae670a9bf85f12e9',
              'fx',
              bytes: 5692736,
              installedBytes: 11167446,
            ),
            AgentArchitecture.x64: archive(
              '$github/vercel-labs/fx/releases/download/v0.0.13/fx-linux-x86_64.tar.gz',
              '53a3b30ce1541048f8fa4b42b7bc68161466bb2e3708db104449a58af3807430',
              'fx',
              bytes: 5787883,
              installedBytes: 13194918,
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
