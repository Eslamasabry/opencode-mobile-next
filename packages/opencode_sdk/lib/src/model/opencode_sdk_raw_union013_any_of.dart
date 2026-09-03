//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union013_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion013AnyOf {
  /// Returns a new [OpencodeSdkRawUnion013AnyOf] instance.
  OpencodeSdkRawUnion013AnyOf({required this.enabled});

  @JsonKey(name: r'enabled', required: true, includeIfNull: false)
  final bool enabled;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion013AnyOf &&
            runtimeType == other.runtimeType &&
            equals([enabled], [other.enabled]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([enabled]);

  factory OpencodeSdkRawUnion013AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion013AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion013AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}
