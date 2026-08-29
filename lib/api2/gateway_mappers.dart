/// Pure mapping functions from OpenCode 2 wire types (`lib/api2/models.dart`)
/// onto the app's protocol-neutral domain value classes
/// (`lib/domain/server_gateway.dart` + the v1-shaped classes in
/// `lib/api/models.dart` that the UI renders).
///
/// Everything in here is side-effect free so it can be unit tested against
/// the captured fixtures in `test/fixtures/api2/`.
library;

import '../api/models.dart';
import '../domain/server_gateway.dart';
import 'models.dart';
import 'transport.dart';

// ---------------- Capabilities ----------------

/// The exact feature truth for an OpenCode 2 (beta-18600) server, per
/// `docs/opencode2-port-matrix.md` §2:
///
/// - `mcpConfigWrites` is true because `PUT /api/mcp/{server}` replaced the
///   v1 config-patch MCP add.
/// - `globalEventStream` is true because the single `/api/event` stream
///   carries every location (events are tagged with `location`), so a global
///   channel can be served by an unfiltered subscription.
/// - Everything else in the list has no v2 endpoint (workspace inventory /
///   warp / steal / console orgs, MCP OAuth, share/archive/todos/message
///   delete, symbols/text search, LSP/formatter status, tool inventory,
///   shell settings, remote upgrade, diagnostics send, git init, provider
///   runtime refresh, `/config/providers` fallback, destructive worktree
///   reset, legacy question requests — replaced by forms, wired in a later
///   slice).
const ServerCapabilities api2ServerCapabilities = ServerCapabilities(
  managedWorkspaces: false,
  workspaceWarp: false,
  sessionSteal: false,
  consoleOrganizations: false,
  mcpOAuth: false,
  mcpConfigWrites: true,
  sessionShare: false,
  sessionArchive: false,
  sessionTodos: false,
  messageDelete: false,
  workspaceSymbols: false,
  textSearch: false,
  languageServiceStatus: false,
  formatterStatus: false,
  toolInventory: false,
  experimentalCapabilities: false,
  shellSettings: false,
  remoteUpgrade: false,
  clientDiagnostics: false,
  gitInit: false,
  providerRuntimeRefresh: false,
  configuredProviderFallback: false,
  globalEventStream: true,
  worktreeReset: false,
  legacyQuestionRequests: false,
);

// ---------------- Health / sessions ----------------

Health mapApi2Health(Api2Health health) =>
    Health(healthy: health.healthy, version: health.version);

Session mapApi2Session(Api2Session session) => Session(
  id: session.id,
  title: session.title,
  projectID: session.projectID,
  workspaceID: session.location?.workspaceID,
  parentID: session.parentID,
  directory: session.location?.directory,
  path: session.subpath?.isNotEmpty == true ? session.subpath : null,
  reverted: session.reverted,
  shareUrl: null, // v2 has no session sharing.
  time: SessionTime(
    created: session.time.created,
    updated: session.time.updated,
    archived: (session.time.archived ?? 0) > 0 ? session.time.archived : null,
  ),
);

// ---------------- Messages ----------------

/// Maps the 10-variant v2 message union onto the v1-shaped
/// `MessageWithParts` bundles the chat UI renders.
///
/// Lossy mappings (documented here on purpose — every deviation from the v2
/// wire truth in one place):
///
/// - **Tool results**: v2 tool output is a `content` array of text/file
///   items. Text items are joined with newlines into the single v1 `output`
///   string; file items become v1 `attachments` entries (`{url, mime,
///   name}`). Per-item ordering between text and files is not preserved.
/// - **`synthetic` / `system` / `skill` messages** become user-role messages
///   with a single `synthetic` text part — hidden from the transcript (v1
///   rendered synthetic parts as invisible context), so their content is
///   only reachable through inspection surfaces.
/// - **`shell` messages** become an assistant-role message with one v1 tool
///   part named `shell` (input `{command}`, output = captured output,
///   metadata `{exit, truncated, shellID}`). `running` maps to a running
///   tool state; `timeout`/`killed`/nonzero exits map to the error state.
/// - **`agent-switched` / `model-switched` / `location-switched` markers**
///   become hidden synthetic parts labeled `[agent switched: …]` etc. — the
///   v1 model has no divider concept, so they do not render as timeline
///   markers.
/// - **`compaction`**: `completed` becomes an assistant message whose text
///   is the summary (like a v1 `/summarize` result); `failed` becomes an
///   assistant message with `errorText`; `running` is a hidden synthetic.
/// - **Reasoning time/state** and provider `state` blobs on text items are
///   dropped (v1 parts have no equivalent field).
/// - **Streaming tool input** (`status: streaming`) maps to the v1
///   `pending` state with the raw JSON text as the pending input preview.
/// - **User attachments** are rebuilt as `data:` URIs from the stored
///   base64 when the server gives no source URI.
/// - **Unknown message/content variants** become hidden synthetic parts
///   carrying the variant name, so new server builds degrade quietly.
MessageWithParts mapApi2Message(String sessionID, Api2Message message) {
  switch (message) {
    case Api2UserMessage():
      return MessageWithParts(
        info: _info(sessionID, message, role: 'user'),
        parts: [
          if (message.text.isNotEmpty)
            Part(
              id: 'text-0',
              type: 'text',
              text: message.text,
              messageID: message.id,
            ),
          for (final (index, file) in message.files.indexed)
            Part(
              id: 'file-$index',
              type: 'file',
              messageID: message.id,
              mime: file.mime,
              filename: file.name,
              url: _attachmentUrl(file),
            ),
        ],
      );
    case Api2AssistantMessage():
      return MessageWithParts(
        info: _info(
          sessionID,
          message,
          role: 'assistant',
          agent: message.agent,
          model: message.model,
          cost: message.cost,
          tokens: message.tokens,
          completed: message.time.completed,
          errorText: message.error?.message ?? message.error?.type,
        ),
        parts: partsFromAssistantContent(message.id, message.content),
      );
    case Api2ShellMessage():
      return MessageWithParts(
        info: _info(
          sessionID,
          message,
          role: 'assistant',
          completed: message.time.completed,
        ),
        parts: [
          Part(
            id: message.shellID ?? 'shell-${message.id}',
            callID: message.shellID ?? 'shell-${message.id}',
            type: 'tool',
            messageID: message.id,
            toolName: 'shell',
            toolState: ToolState.fromJson({
              'status': switch (message.status) {
                'running' => 'running',
                'exited' when (message.exit ?? 0) == 0 => 'completed',
                _ => 'error',
              },
              'input': {'command': message.command},
              if (message.status != 'running')
                (message.status == 'exited' && (message.exit ?? 0) == 0
                        ? 'output'
                        : 'error'):
                    message.output ??
                    (message.status == 'exited'
                        ? ''
                        : 'Shell command ${message.status}'),
              'metadata': {
                if (message.exit != null) 'exit': message.exit,
                'truncated': message.outputTruncated,
                if (message.shellID != null) 'shellID': message.shellID,
              },
            }, toolName: 'shell'),
          ),
        ],
      );
    case Api2CompactionMessage():
      if (message.status == 'completed') {
        return MessageWithParts(
          info: _info(
            sessionID,
            message,
            role: 'assistant',
            completed: message.time.completed ?? message.time.created,
          ),
          parts: [
            Part(
              id: 'text-0',
              type: 'text',
              text: message.summary?.isNotEmpty == true
                  ? message.summary!
                  : 'Conversation compacted.',
              messageID: message.id,
            ),
          ],
        );
      }
      if (message.status == 'failed') {
        return MessageWithParts(
          info: _info(
            sessionID,
            message,
            role: 'assistant',
            completed: message.time.completed ?? message.time.created,
            errorText:
                message.error?.message ?? 'Conversation compaction failed',
          ),
        );
      }
      return _hiddenMessage(sessionID, message, '[compaction running]');
    case Api2SyntheticMessage():
      return _hiddenMessage(sessionID, message, message.text);
    case Api2SystemMessage():
      return _hiddenMessage(sessionID, message, message.text);
    case Api2SkillMessage():
      return _hiddenMessage(
        sessionID,
        message,
        '[skill activated: ${message.name}]\n${message.text}',
      );
    case Api2AgentSwitchedMessage():
      return _hiddenMessage(
        sessionID,
        message,
        '[agent switched: ${message.previous ?? '?'} → ${message.agent}]',
      );
    case Api2ModelSwitchedMessage():
      return _hiddenMessage(
        sessionID,
        message,
        '[model switched: ${message.previous ?? '?'} → '
        '${message.model ?? '?'}]',
      );
    case Api2LocationSwitchedMessage():
      return _hiddenMessage(
        sessionID,
        message,
        '[location switched: ${message.location?.directory ?? '?'}]',
      );
    case Api2UnknownMessage():
      return _hiddenMessage(sessionID, message, '[${message.type}]');
  }
}

List<MessageWithParts> mapApi2Messages(
  String sessionID,
  Iterable<Api2Message> messages,
) => [for (final message in messages) mapApi2Message(sessionID, message)];

MessageInfo _info(
  String sessionID,
  Api2Message message, {
  required String role,
  String? agent,
  Api2ModelRef? model,
  double? cost,
  Api2Tokens? tokens,
  int? completed,
  String? errorText,
}) => MessageInfo(
  id: message.id,
  sessionID: sessionID,
  role: role,
  agent: agent,
  providerID: model?.providerID,
  modelID: model?.id,
  cost: cost ?? 0,
  tokens: tokens == null ? null : mapApi2Tokens(tokens),
  time: MsgTime(created: message.time.created, completed: completed),
  errorText: errorText,
);

MessageWithParts _hiddenMessage(
  String sessionID,
  Api2Message message,
  String text,
) => MessageWithParts(
  info: _info(
    sessionID,
    message,
    role: 'user',
    completed: message.time.completed,
  ),
  parts: [
    Part(
      id: 'text-0',
      type: 'text',
      text: text,
      messageID: message.id,
      synthetic: true,
    ),
  ],
);

Tokens mapApi2Tokens(Api2Tokens tokens) => Tokens(
  input: tokens.input,
  output: tokens.output,
  reasoning: tokens.reasoning,
  cacheRead: tokens.cacheRead,
  cacheWrite: tokens.cacheWrite,
);

String? _attachmentUrl(Api2FileAttachment file) {
  if (file.sourceUri?.isNotEmpty == true) return file.sourceUri;
  if (file.data?.isNotEmpty == true) {
    return 'data:${file.mime ?? 'application/octet-stream'};base64,${file.data}';
  }
  return null;
}

/// Converts a v2 assistant `content` array into v1 parts.
///
/// Part IDs must be stable across refetches and match the IDs the live event
/// adapter uses while streaming: `text-<n>` / `reasoning-<n>` counted per
/// type in content order (mirroring the per-type `ordinal` on
/// `session.text.*` / `session.reasoning.*` events), and the tool call ID
/// for tool parts.
List<Part> partsFromAssistantContent(
  String messageID,
  List<Api2AssistantContent> content,
) {
  final parts = <Part>[];
  var textCount = 0;
  var reasoningCount = 0;
  for (final item in content) {
    switch (item) {
      case Api2TextContent():
        parts.add(
          Part(
            id: 'text-${textCount++}',
            type: 'text',
            text: item.text,
            messageID: messageID,
          ),
        );
      case Api2ReasoningContent():
        parts.add(
          Part(
            id: 'reasoning-${reasoningCount++}',
            type: 'reasoning',
            text: item.text,
            messageID: messageID,
          ),
        );
      case Api2ToolCallContent():
        parts.add(
          Part(
            id: item.id,
            callID: item.id,
            type: 'tool',
            messageID: messageID,
            toolName: item.name,
            toolState: mapApi2ToolState(item.state, toolName: item.name),
          ),
        );
      case Api2UnknownContent():
        parts.add(
          Part(
            id: 'unknown-${parts.length}',
            type: 'text',
            text: '[${item.raw['type'] ?? 'unknown content'}]',
            messageID: messageID,
            synthetic: true,
          ),
        );
    }
  }
  return parts;
}

/// Builds the v1 `state` JSON shape for a tool part from a v2 tool state,
/// then parses it through the v1 [ToolState] parser so output-file scanning
/// and pretty-printing behave exactly as they do for v1 payloads.
ToolState mapApi2ToolState(Api2ToolState state, {String? toolName}) =>
    ToolState.fromJson(v1ToolStateJson(state), toolName: toolName);

Map<String, dynamic> v1ToolStateJson(Api2ToolState state) => switch (state) {
  Api2ToolStreaming() => {'status': 'pending', 'raw': state.rawInput},
  Api2ToolRunning() => {
    'status': 'running',
    'input': state.input,
    if (state.metadata != null) 'metadata': state.metadata,
  },
  Api2ToolCompleted() => {
    'status': 'completed',
    'input': state.input,
    'output': _joinedToolText(state.content),
    'attachments': _toolAttachments(state.content),
    if (state.metadata != null) 'metadata': state.metadata,
  },
  Api2ToolError() => {
    'status': 'error',
    'input': state.input,
    'error':
        state.error?.message ??
        (_joinedToolText(state.content).isNotEmpty
            ? _joinedToolText(state.content)
            : 'Tool failed'),
    'attachments': _toolAttachments(state.content),
    if (state.metadata != null) 'metadata': state.metadata,
  },
  Api2ToolStateUnknown() => {'status': 'pending'},
};

String _joinedToolText(List<Api2ToolResultItem> content) => content
    .whereType<Api2ToolResultText>()
    .map((item) => item.text)
    .where((text) => text.isNotEmpty)
    .join('\n');

List<Map<String, dynamic>> _toolAttachments(
  List<Api2ToolResultItem> content,
) => [
  for (final item in content.whereType<Api2ToolResultFile>())
    {
      'url': item.uri,
      if (item.mime != null) 'mime': item.mime,
      if (item.name != null) 'name': item.name,
    },
];

// ---------------- Permissions ----------------

/// v2 `Permission.Request` → the v1-shaped [PermissionRequest] the app's
/// permission sheets render. `source.id` doubles as the v1 `callID`.
PermissionRequest mapApi2PermissionRequest(Api2PermissionRequest request) =>
    PermissionRequest(
      id: request.id,
      sessionID: request.sessionID ?? '',
      permission: request.action,
      patterns: request.resources,
      metadata: request.metadata ?? const {},
      always: request.save,
      tool:
          request.source?.messageID?.isNotEmpty == true &&
              request.source?.id?.isNotEmpty == true
          ? PermissionTool(
              messageID: request.source!.messageID!,
              callID: request.source!.id!,
            )
          : null,
    );

/// Maps the reply strings the app already sends ("once"/"always"/"reject",
/// plus the "allow"/"deny" synonyms used by notification actions) onto the
/// v2 wire enum. Unknown values are a programming error surfaced as a typed
/// [ProductException].
Api2PermissionReply mapPermissionReply(String reply) =>
    switch (reply.trim().toLowerCase()) {
      'once' || 'allow' || 'yes' => Api2PermissionReply.once,
      'always' => Api2PermissionReply.always,
      'reject' || 'deny' || 'never' || 'no' => Api2PermissionReply.reject,
      _ => throw ProductException('Unknown permission reply "$reply"'),
    };

// ---------------- Providers / agents ----------------

/// Builds the v1 [ProvidersResponse] the model picker consumes from the v2
/// provider + model catalogs. The raw v2 `Model.Info` JSON is preserved as
/// `modelData` (with `variants` reshaped from a list to the v1 keyed map)
/// so the existing catalog derivation keeps working.
ProvidersResponse mapApi2Providers({
  required List<Api2ProviderInfo> providers,
  required List<Api2ModelInfo> models,
  Api2ModelInfo? defaultModel,
}) {
  final modelsByProvider = <String, List<Api2ModelInfo>>{};
  for (final model in models) {
    final providerID = model.providerID ?? '';
    if (providerID.isEmpty) continue;
    modelsByProvider.putIfAbsent(providerID, () => []).add(model);
  }
  final infos = <ProviderInfo>[];
  final seen = <String>{};
  for (final provider in providers) {
    if (!seen.add(provider.id)) continue;
    final providerModels = modelsByProvider[provider.id] ?? const [];
    infos.add(
      ProviderInfo(
        id: provider.id,
        name: provider.name ?? provider.id,
        modelIDs: [
          for (final model in providerModels)
            if (model.enabled) model.modelID ?? model.id,
        ],
        modelData: {
          for (final model in providerModels)
            model.modelID ?? model.id: _modelData(model),
        },
      ),
    );
  }
  // Providers that only appear on models (defensive against catalog skew).
  for (final entry in modelsByProvider.entries) {
    if (!seen.add(entry.key)) continue;
    infos.add(
      ProviderInfo(
        id: entry.key,
        name: entry.key,
        modelIDs: [
          for (final model in entry.value)
            if (model.enabled) model.modelID ?? model.id,
        ],
        modelData: {
          for (final model in entry.value)
            model.modelID ?? model.id: _modelData(model),
        },
      ),
    );
  }
  return ProvidersResponse(
    providers: infos,
    defaultProviderID: defaultModel?.providerID,
    defaultModelID: defaultModel?.modelID ?? defaultModel?.id,
  );
}

Map<String, dynamic> _modelData(Api2ModelInfo model) {
  final data = Map<String, dynamic>.from(model.raw);
  final variants = data['variants'];
  if (variants is List) {
    data['variants'] = {
      for (final variant in variants)
        if (variant is Map && variant['id'] is String)
          variant['id'] as String: {
            'body': variant['settings'] is Map
                ? Map<String, dynamic>.from(variant['settings'] as Map)
                : const <String, dynamic>{},
          },
    };
  }
  return data;
}

AgentInfo mapApi2Agent(Api2AgentInfo agent) =>
    AgentInfo(name: agent.id, mode: agent.mode);

// ---------------- Catalog ----------------

CatalogModel mapApi2CatalogModel(Api2ModelInfo model) {
  final rawVariants = model.raw['variants'];
  return CatalogModel(
    id: model.modelID ?? model.id,
    providerID: model.providerID ?? '',
    name: model.name ?? model.modelID ?? model.id,
    family: model.family,
    enabled: model.enabled,
    status: model.status ?? 'unknown',
    contextLimit: model.limit.context ?? 0,
    outputLimit: model.limit.output ?? 0,
    reasoning: false,
    attachments: model.capabilities.input.any((input) => input != 'text'),
    tools: model.capabilities.tools,
    variants: [
      if (rawVariants is List)
        for (final variant in rawVariants)
          if (variant is Map && variant['id'] is String)
            CatalogVariant(
              id: variant['id'] as String,
              options: variant['settings'] is Map
                  ? Map<String, dynamic>.from(variant['settings'] as Map)
                  : const {},
            ),
    ],
  );
}

CatalogProvider mapApi2CatalogProvider(Api2ProviderInfo provider) =>
    CatalogProvider(
      id: provider.id,
      name: provider.name ?? provider.id,
      enabled: provider.activation != 'disabled',
      integrationID: provider.integrationID,
    );

CatalogAgent mapApi2CatalogAgent(Api2AgentInfo agent) => CatalogAgent(
  id: agent.id,
  mode: agent.mode ?? 'primary',
  description: agent.description,
  hidden: agent.hidden,
  maxSteps: null,
);

// ---------------- Files / VCS ----------------

FileNode mapApi2FsEntry(Api2FsEntry entry) {
  final normalized = entry.path.endsWith('/')
      ? entry.path.substring(0, entry.path.length - 1)
      : entry.path;
  final segments = normalized.split('/');
  return FileNode(
    name: segments.isEmpty ? normalized : segments.last,
    path: normalized,
    isDir: entry.isDirectory,
  );
}

FileDiff mapVcsDiffJson(Map<String, dynamic> json) => FileDiff(
  file: (json['file'] ?? '').toString(),
  patch: json['patch']?.toString(),
  additions: (json['additions'] as num?)?.toInt(),
  deletions: (json['deletions'] as num?)?.toInt(),
  status: json['status']?.toString(),
);

VersionControlFile mapVcsStatusJson(Map<String, dynamic> json) =>
    VersionControlFile(
      path: (json['file'] ?? json['path'] ?? '').toString(),
      status: (json['status'] ?? '').toString(),
      additions: (json['additions'] as num?)?.toInt() ?? 0,
      deletions: (json['deletions'] as num?)?.toInt() ?? 0,
    );

// ---------------- Errors ----------------

/// Maps a v2 transport failure to the v1-shaped [ApiException] the live
/// screens already understand.
ApiException mapApi2Error(Api2Error error) => ApiException(
  error.message,
  statusCode: error.statusCode,
  errorTag: error.tag,
  requestID:
      error.detail('requestID') ??
      error.detail('sessionID') ??
      error.detail('id'),
);
