//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'conflict_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConflictError {
  /// Returns a new [ConflictError] instance.
  ConflictError({required this.tag, required this.message, this.resource});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ConflictErrorTagEnum.unknownDefaultOpenApi,
  )
  final ConflictErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'resource', required: false, includeIfNull: false)
  final String? resource;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConflictError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, message, resource],
              [other.tag, other.message, other.resource],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, message, resource]);

  factory ConflictError.fromJson(Map<String, dynamic> json) =>
      _$ConflictErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ConflictErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ConflictErrorTagEnum {
  @JsonValue(r'ConflictError')
  conflictError(r'ConflictError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ConflictErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
