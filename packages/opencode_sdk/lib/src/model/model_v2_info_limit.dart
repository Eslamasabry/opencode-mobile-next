//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_info_limit.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2InfoLimit {
  /// Returns a new [ModelV2InfoLimit] instance.
  ModelV2InfoLimit({required this.context, this.input, required this.output});

  @JsonKey(name: r'context', required: true, includeIfNull: false)
  final int context;

  @JsonKey(name: r'input', required: false, includeIfNull: false)
  final int? input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final int output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2InfoLimit &&
            runtimeType == other.runtimeType &&
            equals(
              [context, input, output],
              [other.context, other.input, other.output],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([context, input, output]);

  factory ModelV2InfoLimit.fromJson(Map<String, dynamic> json) =>
      _$ModelV2InfoLimitFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2InfoLimitToJson(this);

  String toString() {
    return toJson().toString();
  }
}
