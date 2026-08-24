//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/assistant_message_tokens.dart';
import 'package:opencode_sdk/src/model/assistant_message_path.dart';
import 'package:opencode_sdk/src/model/assistant_message_time.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union001.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'assistant_message.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AssistantMessage {
  /// Returns a new [AssistantMessage] instance.
  AssistantMessage({
    required this.id,

    required this.sessionID,

    required this.role,

    required this.time,

    this.error,

    required this.parentID,

    required this.modelID,

    required this.providerID,

    required this.mode,

    required this.agent,

    required this.path,

    this.summary,

    required this.cost,

    required this.tokens,

    this.structured,

    this.variant,

    this.finish,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(
    name: r'role',
    required: true,
    includeIfNull: false,
    unknownEnumValue: AssistantMessageRoleEnum.unknownDefaultOpenApi,
  )
  final AssistantMessageRoleEnum role;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final AssistantMessageTime time;

  @JsonKey(name: r'error', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion001? error;

  @JsonKey(name: r'parentID', required: true, includeIfNull: false)
  final String parentID;

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'mode', required: true, includeIfNull: false)
  final String mode;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final AssistantMessagePath path;

  @JsonKey(name: r'summary', required: false, includeIfNull: false)
  final bool? summary;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final num cost;

  @JsonKey(name: r'tokens', required: true, includeIfNull: false)
  final AssistantMessageTokens tokens;

  @JsonKey(name: r'structured', required: false, includeIfNull: false)
  final Object? structured;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'finish', required: false, includeIfNull: false)
  final String? finish;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AssistantMessage &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                sessionID,
                role,
                time,
                error,
                parentID,
                modelID,
                providerID,
                mode,
                agent,
                path,
                summary,
                cost,
                tokens,
                structured,
                variant,
                finish,
              ],
              [
                other.id,
                other.sessionID,
                other.role,
                other.time,
                other.error,
                other.parentID,
                other.modelID,
                other.providerID,
                other.mode,
                other.agent,
                other.path,
                other.summary,
                other.cost,
                other.tokens,
                other.structured,
                other.variant,
                other.finish,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        role,
        time,
        error,
        parentID,
        modelID,
        providerID,
        mode,
        agent,
        path,
        summary,
        cost,
        tokens,
        structured,
        variant,
        finish,
      ]);

  factory AssistantMessage.fromJson(Map<String, dynamic> json) =>
      _$AssistantMessageFromJson(json);

  Map<String, dynamic> toJson() => _$AssistantMessageToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum AssistantMessageRoleEnum {
  @JsonValue(r'assistant')
  assistant(r'assistant'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AssistantMessageRoleEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
