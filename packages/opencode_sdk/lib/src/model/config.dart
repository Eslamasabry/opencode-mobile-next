//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_config.dart';
import 'package:opencode_sdk/src/model/provider_config.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union011.dart';
import 'package:opencode_sdk/src/model/layout_config.dart';
import 'package:opencode_sdk/src/model/config_watcher.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union008.dart';
import 'package:opencode_sdk/src/model/config_tool_output.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union010.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union013.dart';
import 'package:opencode_sdk/src/model/attachment_config.dart';
import 'package:opencode_sdk/src/model/server_config.dart';
import 'package:opencode_sdk/src/model/config_agent.dart';
import 'package:opencode_sdk/src/model/config_skills.dart';
import 'package:opencode_sdk/src/model/log_level.dart';
import 'package:opencode_sdk/src/model/config_experimental.dart';
import 'package:opencode_sdk/src/model/config_command_value.dart';
import 'package:opencode_sdk/src/model/config_compaction.dart';
import 'package:opencode_sdk/src/model/config_enterprise.dart';
import 'package:opencode_sdk/src/model/config_mode.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union012.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union009.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Config {
  /// Returns a new [Config] instance.
  Config({
    this.dollarSchema,

    this.shell,

    this.logLevel,

    this.server,

    this.command,

    this.skills,

    this.references,

    this.reference,

    this.watcher,

    this.snapshot,

    this.plugin,

    this.share,

    this.autoshare,

    this.autoupdate,

    this.disabledProviders,

    this.enabledProviders,

    this.model,

    this.smallModel,

    this.defaultAgent,

    this.subagentDepth,

    this.username,

    this.mode,

    this.agent,

    this.provider,

    this.mcp,

    this.formatter,

    this.lsp,

    this.instructions,

    this.layout,

    this.permission,

    this.tools,

    this.attachment,

    this.enterprise,

    this.toolOutput,

    this.compaction,

    this.experimental,
  });

  @JsonKey(name: r'$schema', required: false, includeIfNull: false)
  final String? dollarSchema;

  @JsonKey(name: r'shell', required: false, includeIfNull: false)
  final String? shell;

  @JsonKey(
    name: r'logLevel',
    required: false,
    includeIfNull: false,
    unknownEnumValue: LogLevel.unknownDefaultOpenApi,
  )
  final LogLevel? logLevel;

  @JsonKey(name: r'server', required: false, includeIfNull: false)
  final ServerConfig? server;

  @JsonKey(name: r'command', required: false, includeIfNull: false)
  final Map<String, ConfigCommandValue>? command;

  @JsonKey(name: r'skills', required: false, includeIfNull: false)
  final ConfigSkills? skills;

  @JsonKey(name: r'references', required: false, includeIfNull: false)
  final Map<String, OpencodeSdkRawUnion008>? references;

  @JsonKey(name: r'reference', required: false, includeIfNull: false)
  final Map<String, OpencodeSdkRawUnion009>? reference;

  @JsonKey(name: r'watcher', required: false, includeIfNull: false)
  final ConfigWatcher? watcher;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final bool? snapshot;

  @JsonKey(name: r'plugin', required: false, includeIfNull: false)
  final List<OpencodeSdkRawUnion010>? plugin;

  @JsonKey(
    name: r'share',
    required: false,
    includeIfNull: false,
    unknownEnumValue: ConfigShareEnum.unknownDefaultOpenApi,
  )
  final ConfigShareEnum? share;

  @JsonKey(name: r'autoshare', required: false, includeIfNull: false)
  final bool? autoshare;

  @JsonKey(name: r'autoupdate', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion011? autoupdate;

  @JsonKey(name: r'disabled_providers', required: false, includeIfNull: false)
  final List<String>? disabledProviders;

  @JsonKey(name: r'enabled_providers', required: false, includeIfNull: false)
  final List<String>? enabledProviders;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final String? model;

  @JsonKey(name: r'small_model', required: false, includeIfNull: false)
  final String? smallModel;

  @JsonKey(name: r'default_agent', required: false, includeIfNull: false)
  final String? defaultAgent;

  // minimum: 0
  @JsonKey(name: r'subagent_depth', required: false, includeIfNull: false)
  final int? subagentDepth;

  @JsonKey(name: r'username', required: false, includeIfNull: false)
  final String? username;

  @JsonKey(name: r'mode', required: false, includeIfNull: false)
  final ConfigMode? mode;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final ConfigAgent? agent;

  @JsonKey(name: r'provider', required: false, includeIfNull: false)
  final Map<String, ProviderConfig>? provider;

  @JsonKey(name: r'mcp', required: false, includeIfNull: false)
  final Map<String, OpencodeSdkRawUnion012>? mcp;

  @JsonKey(name: r'formatter', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion013? formatter;

  @JsonKey(name: r'lsp', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion014? lsp;

  @JsonKey(name: r'instructions', required: false, includeIfNull: false)
  final List<String>? instructions;

  @JsonKey(
    name: r'layout',
    required: false,
    includeIfNull: false,
    unknownEnumValue: LayoutConfig.unknownDefaultOpenApi,
  )
  final LayoutConfig? layout;

  @JsonKey(name: r'permission', required: false, includeIfNull: false)
  final PermissionConfig? permission;

  @JsonKey(name: r'tools', required: false, includeIfNull: false)
  final Map<String, bool>? tools;

  @JsonKey(name: r'attachment', required: false, includeIfNull: false)
  final AttachmentConfig? attachment;

  @JsonKey(name: r'enterprise', required: false, includeIfNull: false)
  final ConfigEnterprise? enterprise;

  @JsonKey(name: r'tool_output', required: false, includeIfNull: false)
  final ConfigToolOutput? toolOutput;

  @JsonKey(name: r'compaction', required: false, includeIfNull: false)
  final ConfigCompaction? compaction;

  @JsonKey(name: r'experimental', required: false, includeIfNull: false)
  final ConfigExperimental? experimental;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Config &&
            runtimeType == other.runtimeType &&
            equals(
              [
                dollarSchema,
                shell,
                logLevel,
                server,
                command,
                skills,
                references,
                reference,
                watcher,
                snapshot,
                plugin,
                share,
                autoshare,
                autoupdate,
                disabledProviders,
                enabledProviders,
                model,
                smallModel,
                defaultAgent,
                subagentDepth,
                username,
                mode,
                agent,
                provider,
                mcp,
                formatter,
                lsp,
                instructions,
                layout,
                permission,
                tools,
                attachment,
                enterprise,
                toolOutput,
                compaction,
                experimental,
              ],
              [
                other.dollarSchema,
                other.shell,
                other.logLevel,
                other.server,
                other.command,
                other.skills,
                other.references,
                other.reference,
                other.watcher,
                other.snapshot,
                other.plugin,
                other.share,
                other.autoshare,
                other.autoupdate,
                other.disabledProviders,
                other.enabledProviders,
                other.model,
                other.smallModel,
                other.defaultAgent,
                other.subagentDepth,
                other.username,
                other.mode,
                other.agent,
                other.provider,
                other.mcp,
                other.formatter,
                other.lsp,
                other.instructions,
                other.layout,
                other.permission,
                other.tools,
                other.attachment,
                other.enterprise,
                other.toolOutput,
                other.compaction,
                other.experimental,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        dollarSchema,
        shell,
        logLevel,
        server,
        command,
        skills,
        references,
        reference,
        watcher,
        snapshot,
        plugin,
        share,
        autoshare,
        autoupdate,
        disabledProviders,
        enabledProviders,
        model,
        smallModel,
        defaultAgent,
        subagentDepth,
        username,
        mode,
        agent,
        provider,
        mcp,
        formatter,
        lsp,
        instructions,
        layout,
        permission,
        tools,
        attachment,
        enterprise,
        toolOutput,
        compaction,
        experimental,
      ]);

  factory Config.fromJson(Map<String, dynamic> json) => _$ConfigFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ConfigShareEnum {
  @JsonValue(r'manual')
  manual(r'manual'),
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'disabled')
  disabled(r'disabled'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ConfigShareEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
