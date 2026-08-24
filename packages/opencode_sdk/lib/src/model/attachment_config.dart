//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/image_attachment_config.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'attachment_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AttachmentConfig {
  /// Returns a new [AttachmentConfig] instance.
  AttachmentConfig({this.image});

  @JsonKey(name: r'image', required: false, includeIfNull: false)
  final ImageAttachmentConfig? image;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AttachmentConfig &&
            runtimeType == other.runtimeType &&
            equals([image], [other.image]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([image]);

  factory AttachmentConfig.fromJson(Map<String, dynamic> json) =>
      _$AttachmentConfigFromJson(json);

  Map<String, dynamic> toJson() => _$AttachmentConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}
