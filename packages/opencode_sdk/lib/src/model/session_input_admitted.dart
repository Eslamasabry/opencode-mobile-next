//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_input_admitted.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionInputAdmitted {
  /// Returns a new [SessionInputAdmitted] instance.
  SessionInputAdmitted({
    required this.admittedSeq,

    required this.id,

    required this.sessionID,

    required this.prompt,

    required this.delivery,

    required this.timeCreated,

    this.promotedSeq,
  });

  // minimum: 0
  @JsonKey(name: r'admittedSeq', required: true, includeIfNull: false)
  final int admittedSeq;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'prompt', required: true, includeIfNull: false)
  final Prompt prompt;

  @JsonKey(
    name: r'delivery',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionInputAdmittedDeliveryEnum.unknownDefaultOpenApi,
  )
  final SessionInputAdmittedDeliveryEnum delivery;

  @JsonKey(name: r'timeCreated', required: true, includeIfNull: false)
  final num timeCreated;

  // minimum: 0
  @JsonKey(name: r'promotedSeq', required: false, includeIfNull: false)
  final int? promotedSeq;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionInputAdmitted &&
            runtimeType == other.runtimeType &&
            equals(
              [
                admittedSeq,
                id,
                sessionID,
                prompt,
                delivery,
                timeCreated,
                promotedSeq,
              ],
              [
                other.admittedSeq,
                other.id,
                other.sessionID,
                other.prompt,
                other.delivery,
                other.timeCreated,
                other.promotedSeq,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        admittedSeq,
        id,
        sessionID,
        prompt,
        delivery,
        timeCreated,
        promotedSeq,
      ]);

  factory SessionInputAdmitted.fromJson(Map<String, dynamic> json) =>
      _$SessionInputAdmittedFromJson(json);

  Map<String, dynamic> toJson() => _$SessionInputAdmittedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionInputAdmittedDeliveryEnum {
  @JsonValue(r'steer')
  steer(r'steer'),
  @JsonValue(r'queue')
  queue(r'queue'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionInputAdmittedDeliveryEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
