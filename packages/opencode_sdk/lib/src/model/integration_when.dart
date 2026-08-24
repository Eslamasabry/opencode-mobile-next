//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_when.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationWhen {
  /// Returns a new [IntegrationWhen] instance.
  IntegrationWhen({required this.key, required this.op, required this.value});

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(
    name: r'op',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationWhenOpEnum.unknownDefaultOpenApi,
  )
  final IntegrationWhenOpEnum op;

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationWhen &&
            runtimeType == other.runtimeType &&
            equals([key, op, value], [other.key, other.op, other.value]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([key, op, value]);

  factory IntegrationWhen.fromJson(Map<String, dynamic> json) =>
      _$IntegrationWhenFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationWhenToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationWhenOpEnum {
  @JsonValue(r'eq')
  eq(r'eq'),
  @JsonValue(r'neq')
  neq(r'neq'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationWhenOpEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
