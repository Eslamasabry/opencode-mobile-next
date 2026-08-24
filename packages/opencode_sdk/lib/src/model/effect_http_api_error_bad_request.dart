//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'effect_http_api_error_bad_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EffectHttpApiErrorBadRequest {
  /// Returns a new [EffectHttpApiErrorBadRequest] instance.
  EffectHttpApiErrorBadRequest({required this.tag});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EffectHttpApiErrorBadRequestTagEnum.unknownDefaultOpenApi,
  )
  final EffectHttpApiErrorBadRequestTagEnum tag;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EffectHttpApiErrorBadRequest &&
            runtimeType == other.runtimeType &&
            equals([tag], [other.tag]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag]);

  factory EffectHttpApiErrorBadRequest.fromJson(Map<String, dynamic> json) =>
      _$EffectHttpApiErrorBadRequestFromJson(json);

  Map<String, dynamic> toJson() => _$EffectHttpApiErrorBadRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EffectHttpApiErrorBadRequestTagEnum {
  @JsonValue(r'BadRequest')
  badRequest(r'BadRequest'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EffectHttpApiErrorBadRequestTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
