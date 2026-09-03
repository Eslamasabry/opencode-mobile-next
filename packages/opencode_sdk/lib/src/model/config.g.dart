// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Config _$ConfigFromJson(Map<String, dynamic> json) => $checkedCreate(
  'Config',
  json,
  ($checkedConvert) {
    final val = Config(
      dollarSchema: $checkedConvert(r'$schema', (v) => v as String?),
      shell: $checkedConvert('shell', (v) => v as String?),
      logLevel: $checkedConvert(
        'logLevel',
        (v) => $enumDecodeNullable(
          _$LogLevelEnumMap,
          v,
          unknownValue: LogLevel.unknownDefaultOpenApi,
        ),
      ),
      server: $checkedConvert(
        'server',
        (v) =>
            v == null ? null : ServerConfig.fromJson(v as Map<String, dynamic>),
      ),
      command: $checkedConvert(
        'command',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) => MapEntry(
            k,
            ConfigCommandValue.fromJson(e as Map<String, dynamic>),
          ),
        ),
      ),
      skills: $checkedConvert(
        'skills',
        (v) =>
            v == null ? null : ConfigSkills.fromJson(v as Map<String, dynamic>),
      ),
      references: $checkedConvert(
        'references',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) => MapEntry(k, OpencodeSdkRawUnion009.fromJson(e)),
        ),
      ),
      reference: $checkedConvert(
        'reference',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) => MapEntry(k, OpencodeSdkRawUnion010.fromJson(e)),
        ),
      ),
      watcher: $checkedConvert(
        'watcher',
        (v) => v == null
            ? null
            : ConfigWatcher.fromJson(v as Map<String, dynamic>),
      ),
      snapshot: $checkedConvert('snapshot', (v) => v as bool?),
      plugin: $checkedConvert(
        'plugin',
        (v) => (v as List<dynamic>?)
            ?.map(OpencodeSdkRawUnion011.fromJson)
            .toList(),
      ),
      share: $checkedConvert(
        'share',
        (v) => $enumDecodeNullable(
          _$ConfigShareEnumEnumMap,
          v,
          unknownValue: ConfigShareEnum.unknownDefaultOpenApi,
        ),
      ),
      autoshare: $checkedConvert('autoshare', (v) => v as bool?),
      autoupdate: $checkedConvert(
        'autoupdate',
        (v) => v == null ? null : OpencodeSdkRawUnion012.fromJson(v),
      ),
      disabledProviders: $checkedConvert(
        'disabled_providers',
        (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
      ),
      enabledProviders: $checkedConvert(
        'enabled_providers',
        (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
      ),
      model: $checkedConvert('model', (v) => v as String?),
      smallModel: $checkedConvert('small_model', (v) => v as String?),
      defaultAgent: $checkedConvert('default_agent', (v) => v as String?),
      subagentDepth: $checkedConvert(
        'subagent_depth',
        (v) => (v as num?)?.toInt(),
      ),
      username: $checkedConvert('username', (v) => v as String?),
      mode: $checkedConvert(
        'mode',
        (v) =>
            v == null ? null : ConfigMode.fromJson(v as Map<String, dynamic>),
      ),
      agent: $checkedConvert(
        'agent',
        (v) =>
            v == null ? null : ConfigAgent.fromJson(v as Map<String, dynamic>),
      ),
      provider: $checkedConvert(
        'provider',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) =>
              MapEntry(k, ProviderConfig.fromJson(e as Map<String, dynamic>)),
        ),
      ),
      mcp: $checkedConvert(
        'mcp',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) => MapEntry(k, OpencodeSdkRawUnion013.fromJson(e)),
        ),
      ),
      formatter: $checkedConvert(
        'formatter',
        (v) => v == null ? null : OpencodeSdkRawUnion014.fromJson(v),
      ),
      lsp: $checkedConvert(
        'lsp',
        (v) => v == null ? null : OpencodeSdkRawUnion015.fromJson(v),
      ),
      instructions: $checkedConvert(
        'instructions',
        (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
      ),
      layout: $checkedConvert(
        'layout',
        (v) => $enumDecodeNullable(
          _$LayoutConfigEnumMap,
          v,
          unknownValue: LayoutConfig.unknownDefaultOpenApi,
        ),
      ),
      permission: $checkedConvert(
        'permission',
        (v) => v == null ? null : PermissionConfig.fromJson(v),
      ),
      tools: $checkedConvert(
        'tools',
        (v) =>
            (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as bool)),
      ),
      attachment: $checkedConvert(
        'attachment',
        (v) => v == null
            ? null
            : AttachmentConfig.fromJson(v as Map<String, dynamic>),
      ),
      enterprise: $checkedConvert(
        'enterprise',
        (v) => v == null
            ? null
            : ConfigEnterprise.fromJson(v as Map<String, dynamic>),
      ),
      toolOutput: $checkedConvert(
        'tool_output',
        (v) => v == null
            ? null
            : ConfigToolOutput.fromJson(v as Map<String, dynamic>),
      ),
      compaction: $checkedConvert(
        'compaction',
        (v) => v == null
            ? null
            : ConfigCompaction.fromJson(v as Map<String, dynamic>),
      ),
      experimental: $checkedConvert(
        'experimental',
        (v) => v == null
            ? null
            : ConfigExperimental.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
  fieldKeyMap: const {
    'dollarSchema': r'$schema',
    'disabledProviders': 'disabled_providers',
    'enabledProviders': 'enabled_providers',
    'smallModel': 'small_model',
    'defaultAgent': 'default_agent',
    'subagentDepth': 'subagent_depth',
    'toolOutput': 'tool_output',
  },
);

Map<String, dynamic> _$ConfigToJson(Config instance) => <String, dynamic>{
  r'$schema': ?instance.dollarSchema,
  'shell': ?instance.shell,
  'logLevel': ?_$LogLevelEnumMap[instance.logLevel],
  'server': ?instance.server?.toJson(),
  'command': ?instance.command?.map((k, e) => MapEntry(k, e.toJson())),
  'skills': ?instance.skills?.toJson(),
  'references': ?instance.references?.map((k, e) => MapEntry(k, e.toJson())),
  'reference': ?instance.reference?.map((k, e) => MapEntry(k, e.toJson())),
  'watcher': ?instance.watcher?.toJson(),
  'snapshot': ?instance.snapshot,
  'plugin': ?instance.plugin?.map((e) => e.toJson()).toList(),
  'share': ?_$ConfigShareEnumEnumMap[instance.share],
  'autoshare': ?instance.autoshare,
  'autoupdate': ?instance.autoupdate?.toJson(),
  'disabled_providers': ?instance.disabledProviders,
  'enabled_providers': ?instance.enabledProviders,
  'model': ?instance.model,
  'small_model': ?instance.smallModel,
  'default_agent': ?instance.defaultAgent,
  'subagent_depth': ?instance.subagentDepth,
  'username': ?instance.username,
  'mode': ?instance.mode?.toJson(),
  'agent': ?instance.agent?.toJson(),
  'provider': ?instance.provider?.map((k, e) => MapEntry(k, e.toJson())),
  'mcp': ?instance.mcp?.map((k, e) => MapEntry(k, e.toJson())),
  'formatter': ?instance.formatter?.toJson(),
  'lsp': ?instance.lsp?.toJson(),
  'instructions': ?instance.instructions,
  'layout': ?_$LayoutConfigEnumMap[instance.layout],
  'permission': ?instance.permission?.toJson(),
  'tools': ?instance.tools,
  'attachment': ?instance.attachment?.toJson(),
  'enterprise': ?instance.enterprise?.toJson(),
  'tool_output': ?instance.toolOutput?.toJson(),
  'compaction': ?instance.compaction?.toJson(),
  'experimental': ?instance.experimental?.toJson(),
};

const _$LogLevelEnumMap = {
  LogLevel.DEBUG: 'DEBUG',
  LogLevel.INFO: 'INFO',
  LogLevel.WARN: 'WARN',
  LogLevel.ERROR: 'ERROR',
  LogLevel.unknownDefaultOpenApi: 'unknown_default_open_api',
};

const _$ConfigShareEnumEnumMap = {
  ConfigShareEnum.manual: 'manual',
  ConfigShareEnum.auto: 'auto',
  ConfigShareEnum.disabled: 'disabled',
  ConfigShareEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

const _$LayoutConfigEnumMap = {
  LayoutConfig.auto: 'auto',
  LayoutConfig.stretch: 'stretch',
  LayoutConfig.unknownDefaultOpenApi: 'unknown_default_open_api',
};
