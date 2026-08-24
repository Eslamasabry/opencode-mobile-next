//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_info_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2InfoTime {
  /// Returns a new [ModelV2InfoTime] instance.
  ModelV2InfoTime({required this.released});

  @JsonKey(name: r'released', required: true, includeIfNull: false)
  final num released;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2InfoTime &&
            runtimeType == other.runtimeType &&
            equals([released], [other.released]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([released]);

  factory ModelV2InfoTime.fromJson(Map<String, dynamic> json) =>
      _$ModelV2InfoTimeFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2InfoTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
