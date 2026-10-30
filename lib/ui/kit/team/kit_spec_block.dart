import 'package:flutter/material.dart';
import '../kit_field.dart';
import '../kit_max_width.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';

/// A spec section whose draft lifecycle belongs to the caller.
/// States: empty, disabled.
class KitSpecBlock extends StatelessWidget {
  const KitSpecBlock({
    super.key,
    required this.label,
    required this.controller,
    this.helper,
    this.onChanged,
    this.readOnly = false,
  });
  final String label;
  final TextEditingController controller;
  final String? helper;
  final ValueChanged<String>? onChanged;
  final bool readOnly;
  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (!readOnly) {
      return KitField(
        label: label,
        controller: controller,
        helper: helper,
        kind: KitFieldKind.multiline,
        onChanged: onChanged,
      );
    }
    final t = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitText(label, role: KitTextRole.label),
        SizedBox(height: t.space2),
        KitText(controller.text, role: KitTextRole.body),
        if (helper != null)
          KitText(
            helper!,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
      ],
    );
  }
}
