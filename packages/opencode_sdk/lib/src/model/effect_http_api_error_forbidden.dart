//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'effect_http_api_error_forbidden.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EffectHttpApiErrorForbidden {
  /// Returns a new [EffectHttpApiErrorForbidden] instance.
  EffectHttpApiErrorForbidden({required this.tag});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EffectHttpApiErrorForbiddenTagEnum.unknownDefaultOpenApi,
  )
  final EffectHttpApiErrorForbiddenTagEnum tag;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EffectHttpApiErrorForbidden &&
            runtimeType == other.runtimeType &&
            equals([tag], [other.tag]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag]);

  factory EffectHttpApiErrorForbidden.fromJson(Map<String, dynamic> json) =>
      _$EffectHttpApiErrorForbiddenFromJson(json);

  Map<String, dynamic> toJson() => _$EffectHttpApiErrorForbiddenToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EffectHttpApiErrorForbiddenTagEnum {
  @JsonValue(r'Forbidden')
  forbidden(r'Forbidden'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EffectHttpApiErrorForbiddenTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
