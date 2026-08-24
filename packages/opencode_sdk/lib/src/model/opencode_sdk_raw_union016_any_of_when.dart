//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union016_any_of_when.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion016AnyOfWhen {
  /// Returns a new [OpencodeSdkRawUnion016AnyOfWhen] instance.
  OpencodeSdkRawUnion016AnyOfWhen({
    required this.key,

    required this.op,

    required this.value,
  });

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(
    name: r'op',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion016AnyOfWhenOpEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion016AnyOfWhenOpEnum op;

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion016AnyOfWhen &&
            runtimeType == other.runtimeType &&
            equals([key, op, value], [other.key, other.op, other.value]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([key, op, value]);

  factory OpencodeSdkRawUnion016AnyOfWhen.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion016AnyOfWhenFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion016AnyOfWhenToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion016AnyOfWhenOpEnum {
  @JsonValue(r'eq')
  eq(r'eq'),
  @JsonValue(r'neq')
  neq(r'neq'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion016AnyOfWhenOpEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
