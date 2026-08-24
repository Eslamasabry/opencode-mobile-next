//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union041_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion041AnyOf1 {
  /// Returns a new [OpencodeSdkRawUnion041AnyOf1] instance.
  OpencodeSdkRawUnion041AnyOf1({required this.success, required this.error});

  @JsonKey(
    name: r'success',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion041AnyOf1SuccessEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion041AnyOf1SuccessEnum success;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion041AnyOf1 &&
            runtimeType == other.runtimeType &&
            equals([success, error], [other.success, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([success, error]);

  factory OpencodeSdkRawUnion041AnyOf1.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion041AnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion041AnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion041AnyOf1SuccessEnum {
  @JsonValue('false')
  false_('false'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const OpencodeSdkRawUnion041AnyOf1SuccessEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
