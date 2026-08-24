//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'step_start_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class StepStartPart {
  /// Returns a new [StepStartPart] instance.
  StepStartPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    this.snapshot,
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
    unknownEnumValue: StepStartPartTypeEnum.unknownDefaultOpenApi,
  )
  final StepStartPartTypeEnum type;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is StepStartPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, snapshot],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.snapshot,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, messageID, type, snapshot]);

  factory StepStartPart.fromJson(Map<String, dynamic> json) =>
      _$StepStartPartFromJson(json);

  Map<String, dynamic> toJson() => _$StepStartPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum StepStartPartTypeEnum {
  @JsonValue(r'step-start')
  stepStart(r'step-start'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const StepStartPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
