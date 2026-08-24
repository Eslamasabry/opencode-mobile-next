//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'forbidden_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ForbiddenError {
  /// Returns a new [ForbiddenError] instance.
  ForbiddenError({required this.tag, required this.message});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ForbiddenErrorTagEnum.unknownDefaultOpenApi,
  )
  final ForbiddenErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ForbiddenError &&
            runtimeType == other.runtimeType &&
            equals([tag, message], [other.tag, other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag, message]);

  factory ForbiddenError.fromJson(Map<String, dynamic> json) =>
      _$ForbiddenErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ForbiddenErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ForbiddenErrorTagEnum {
  @JsonValue(r'ForbiddenError')
  forbiddenError(r'ForbiddenError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ForbiddenErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
