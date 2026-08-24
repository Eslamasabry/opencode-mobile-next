//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_rule_config.dart';
import 'package:opencode_sdk/src/model/permission_action_config.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_config_any_of.g.dart';

// ignore_for_file: unused_import

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionConfigAnyOf {
  /// Returns a new [PermissionConfigAnyOf] instance.
  PermissionConfigAnyOf({
    this.read,

    this.edit,

    this.glob,

    this.grep,

    this.list,

    this.bash,

    this.task,

    this.externalDirectory,

    this.todowrite,

    this.question,

    this.webfetch,

    this.websearch,

    this.lsp,

    this.doomLoop,

    this.skill,
  });

  @JsonKey(name: r'read', required: false, includeIfNull: false)
  final PermissionRuleConfig? read;

  @JsonKey(name: r'edit', required: false, includeIfNull: false)
  final PermissionRuleConfig? edit;

  @JsonKey(name: r'glob', required: false, includeIfNull: false)
  final PermissionRuleConfig? glob;

  @JsonKey(name: r'grep', required: false, includeIfNull: false)
  final PermissionRuleConfig? grep;

  @JsonKey(name: r'list', required: false, includeIfNull: false)
  final PermissionRuleConfig? list;

  @JsonKey(name: r'bash', required: false, includeIfNull: false)
  final PermissionRuleConfig? bash;

  @JsonKey(name: r'task', required: false, includeIfNull: false)
  final PermissionRuleConfig? task;

  @JsonKey(name: r'external_directory', required: false, includeIfNull: false)
  final PermissionRuleConfig? externalDirectory;

  @JsonKey(
    name: r'todowrite',
    required: false,
    includeIfNull: false,
    unknownEnumValue: PermissionActionConfig.unknownDefaultOpenApi,
  )
  final PermissionActionConfig? todowrite;

  @JsonKey(
    name: r'question',
    required: false,
    includeIfNull: false,
    unknownEnumValue: PermissionActionConfig.unknownDefaultOpenApi,
  )
  final PermissionActionConfig? question;

  @JsonKey(
    name: r'webfetch',
    required: false,
    includeIfNull: false,
    unknownEnumValue: PermissionActionConfig.unknownDefaultOpenApi,
  )
  final PermissionActionConfig? webfetch;

  @JsonKey(
    name: r'websearch',
    required: false,
    includeIfNull: false,
    unknownEnumValue: PermissionActionConfig.unknownDefaultOpenApi,
  )
  final PermissionActionConfig? websearch;

  @JsonKey(name: r'lsp', required: false, includeIfNull: false)
  final PermissionRuleConfig? lsp;

  @JsonKey(
    name: r'doom_loop',
    required: false,
    includeIfNull: false,
    unknownEnumValue: PermissionActionConfig.unknownDefaultOpenApi,
  )
  final PermissionActionConfig? doomLoop;

  @JsonKey(name: r'skill', required: false, includeIfNull: false)
  final PermissionRuleConfig? skill;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionConfigAnyOf &&
            runtimeType == other.runtimeType &&
            equals(
              [
                read,
                edit,
                glob,
                grep,
                list,
                bash,
                task,
                externalDirectory,
                todowrite,
                question,
                webfetch,
                websearch,
                lsp,
                doomLoop,
                skill,
              ],
              [
                other.read,
                other.edit,
                other.glob,
                other.grep,
                other.list,
                other.bash,
                other.task,
                other.externalDirectory,
                other.todowrite,
                other.question,
                other.webfetch,
                other.websearch,
                other.lsp,
                other.doomLoop,
                other.skill,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        read,
        edit,
        glob,
        grep,
        list,
        bash,
        task,
        externalDirectory,
        todowrite,
        question,
        webfetch,
        websearch,
        lsp,
        doomLoop,
        skill,
      ]);

  factory PermissionConfigAnyOf.fromJson(Map<String, dynamic> json) =>
      _$PermissionConfigAnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionConfigAnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}
