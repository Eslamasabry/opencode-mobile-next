//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union017_any_of_when.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion017AnyOfWhen {
  /// Returns a new [OpencodeSdkRawUnion017AnyOfWhen] instance.
  OpencodeSdkRawUnion017AnyOfWhen({
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
        OpencodeSdkRawUnion017AnyOfWhenOpEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion017AnyOfWhenOpEnum op;

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion017AnyOfWhen &&
            runtimeType == other.runtimeType &&
            equals([key, op, value], [other.key, other.op, other.value]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([key, op, value]);

  factory OpencodeSdkRawUnion017AnyOfWhen.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion017AnyOfWhenFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion017AnyOfWhenToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion017AnyOfWhenOpEnum {
  @JsonValue(r'eq')
  eq(r'eq'),
  @JsonValue(r'neq')
  neq(r'neq'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion017AnyOfWhenOpEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
