//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/assistant_message_tokens.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'step_finish_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class StepFinishPart {
  /// Returns a new [StepFinishPart] instance.
  StepFinishPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.reason,

    this.snapshot,

    required this.cost,

    required this.tokens,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: StepFinishPartTypeEnum.unknownDefaultOpenApi,
  )
  final StepFinishPartTypeEnum type;

  @JsonKey(name: r'reason', required: true, includeIfNull: false)
  final String reason;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final num cost;

  @JsonKey(name: r'tokens', required: true, includeIfNull: false)
  final AssistantMessageTokens tokens;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is StepFinishPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, reason, snapshot, cost, tokens],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.reason,
                other.snapshot,
                other.cost,
                other.tokens,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        messageID,
        type,
        reason,
        snapshot,
        cost,
        tokens,
      ]);

  factory StepFinishPart.fromJson(Map<String, dynamic> json) =>
      _$StepFinishPartFromJson(json);

  Map<String, dynamic> toJson() => _$StepFinishPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum StepFinishPartTypeEnum {
  @JsonValue(r'step-finish')
  stepFinish(r'step-finish'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const StepFinishPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
