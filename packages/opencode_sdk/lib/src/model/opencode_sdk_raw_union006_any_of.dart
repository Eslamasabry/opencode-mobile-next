//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union006_any_of_field.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union006_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion006AnyOf {
  /// Returns a new [OpencodeSdkRawUnion006AnyOf] instance.
  OpencodeSdkRawUnion006AnyOf({required this.field});

  @JsonKey(name: r'field', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion006AnyOfField field;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion006AnyOf &&
            runtimeType == other.runtimeType &&
            equals([field], [other.field]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([field]);

  factory OpencodeSdkRawUnion006AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion006AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion006AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}
