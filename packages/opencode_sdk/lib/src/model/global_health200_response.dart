//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'global_health200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class GlobalHealth200Response {
  /// Returns a new [GlobalHealth200Response] instance.
  GlobalHealth200Response({required this.healthy, required this.version});

  @JsonKey(
    name: r'healthy',
    required: true,
    includeIfNull: false,
    unknownEnumValue: GlobalHealth200ResponseHealthyEnum.unknownDefaultOpenApi,
  )
  final GlobalHealth200ResponseHealthyEnum healthy;

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final String version;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is GlobalHealth200Response &&
            runtimeType == other.runtimeType &&
            equals([healthy, version], [other.healthy, other.version]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([healthy, version]);

  factory GlobalHealth200Response.fromJson(Map<String, dynamic> json) =>
      _$GlobalHealth200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$GlobalHealth200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum GlobalHealth200ResponseHealthyEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const GlobalHealth200ResponseHealthyEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
