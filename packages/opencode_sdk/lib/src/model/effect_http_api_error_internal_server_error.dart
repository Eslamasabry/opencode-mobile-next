//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'effect_http_api_error_internal_server_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EffectHttpApiErrorInternalServerError {
  /// Returns a new [EffectHttpApiErrorInternalServerError] instance.
  EffectHttpApiErrorInternalServerError({required this.tag});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        EffectHttpApiErrorInternalServerErrorTagEnum.unknownDefaultOpenApi,
  )
  final EffectHttpApiErrorInternalServerErrorTagEnum tag;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EffectHttpApiErrorInternalServerError &&
            runtimeType == other.runtimeType &&
            equals([tag], [other.tag]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag]);

  factory EffectHttpApiErrorInternalServerError.fromJson(
    Map<String, dynamic> json,
  ) => _$EffectHttpApiErrorInternalServerErrorFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EffectHttpApiErrorInternalServerErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EffectHttpApiErrorInternalServerErrorTagEnum {
  @JsonValue(r'InternalServerError')
  internalServerError(r'InternalServerError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EffectHttpApiErrorInternalServerErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
