//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_cost_experimental_over200_k.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCostExperimentalOver200K {
  /// Returns a new [ModelCostExperimentalOver200K] instance.
  ModelCostExperimentalOver200K({
    required this.input,

    required this.output,

    required this.cache,
  });

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'cache', required: true, includeIfNull: false)
  final SessionTokensCache cache;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCostExperimentalOver200K &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, cache],
              [other.input, other.output, other.cache],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([input, output, cache]);

  factory ModelCostExperimentalOver200K.fromJson(Map<String, dynamic> json) =>
      _$ModelCostExperimentalOver200KFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCostExperimentalOver200KToJson(this);

  String toString() {
    return toJson().toString();
  }
}
