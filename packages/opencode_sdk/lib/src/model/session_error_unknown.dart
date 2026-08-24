//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_error_unknown.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionErrorUnknown {
  /// Returns a new [SessionErrorUnknown] instance.
  SessionErrorUnknown({required this.type, required this.message});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionErrorUnknownTypeEnum.unknownDefaultOpenApi,
  )
  final SessionErrorUnknownTypeEnum type;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionErrorUnknown &&
            runtimeType == other.runtimeType &&
            equals([type, message], [other.type, other.message]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, message]);

  factory SessionErrorUnknown.fromJson(Map<String, dynamic> json) =>
      _$SessionErrorUnknownFromJson(json);

  Map<String, dynamic> toJson() => _$SessionErrorUnknownToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionErrorUnknownTypeEnum {
  @JsonValue(r'unknown')
  unknown(r'unknown'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionErrorUnknownTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
