//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_part.dart';
import 'package:opencode_sdk/src/model/message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_messages200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessages200ResponseInner {
  /// Returns a new [SessionMessages200ResponseInner] instance.
  SessionMessages200ResponseInner({required this.info, required this.parts});

  @JsonKey(name: r'info', required: true, includeIfNull: false)
  final Message info;

  @JsonKey(name: r'parts', required: true, includeIfNull: false)
  final List<ModelPart> parts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessages200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals([info, parts], [other.info, other.parts]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([info, parts]);

  factory SessionMessages200ResponseInner.fromJson(Map<String, dynamic> json) =>
      _$SessionMessages200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessages200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
