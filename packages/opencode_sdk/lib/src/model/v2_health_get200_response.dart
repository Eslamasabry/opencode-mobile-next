//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_health_get200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2HealthGet200Response {
  /// Returns a new [V2HealthGet200Response] instance.
  V2HealthGet200Response({required this.healthy});

  @JsonKey(
    name: r'healthy',
    required: true,
    includeIfNull: false,
    unknownEnumValue: V2HealthGet200ResponseHealthyEnum.unknownDefaultOpenApi,
  )
  final V2HealthGet200ResponseHealthyEnum healthy;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2HealthGet200Response &&
            runtimeType == other.runtimeType &&
            equals([healthy], [other.healthy]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([healthy]);

  factory V2HealthGet200Response.fromJson(Map<String, dynamic> json) =>
      _$V2HealthGet200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2HealthGet200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum V2HealthGet200ResponseHealthyEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const V2HealthGet200ResponseHealthyEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
