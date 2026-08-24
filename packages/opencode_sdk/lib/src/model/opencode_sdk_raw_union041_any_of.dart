//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union041_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion041AnyOf {
  /// Returns a new [OpencodeSdkRawUnion041AnyOf] instance.
  OpencodeSdkRawUnion041AnyOf({required this.success, required this.version});

  @JsonKey(
    name: r'success',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion041AnyOfSuccessEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion041AnyOfSuccessEnum success;

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final String version;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion041AnyOf &&
            runtimeType == other.runtimeType &&
            equals([success, version], [other.success, other.version]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([success, version]);

  factory OpencodeSdkRawUnion041AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion041AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion041AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion041AnyOfSuccessEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const OpencodeSdkRawUnion041AnyOfSuccessEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
