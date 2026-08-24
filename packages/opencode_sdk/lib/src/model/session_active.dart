//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_active.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionActive {
  /// Returns a new [SessionActive] instance.
  SessionActive({required this.type});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionActiveTypeEnum.unknownDefaultOpenApi,
  )
  final SessionActiveTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionActive &&
            runtimeType == other.runtimeType &&
            equals([type], [other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type]);

  factory SessionActive.fromJson(Map<String, dynamic> json) =>
      _$SessionActiveFromJson(json);

  Map<String, dynamic> toJson() => _$SessionActiveToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionActiveTypeEnum {
  @JsonValue(r'running')
  running(r'running'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionActiveTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
