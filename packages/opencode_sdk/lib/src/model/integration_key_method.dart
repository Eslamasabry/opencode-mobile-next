//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_key_method.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationKeyMethod {
  /// Returns a new [IntegrationKeyMethod] instance.
  IntegrationKeyMethod({required this.type, this.label});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationKeyMethodTypeEnum.unknownDefaultOpenApi,
  )
  final IntegrationKeyMethodTypeEnum type;

  @JsonKey(name: r'label', required: false, includeIfNull: false)
  final String? label;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationKeyMethod &&
            runtimeType == other.runtimeType &&
            equals([type, label], [other.type, other.label]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, label]);

  factory IntegrationKeyMethod.fromJson(Map<String, dynamic> json) =>
      _$IntegrationKeyMethodFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationKeyMethodToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationKeyMethodTypeEnum {
  @JsonValue(r'key')
  key(r'key'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationKeyMethodTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
