import 'package:flutter/material.dart';

import '../../kit/kit.dart';
import 'agents_text.dart';

/// A failed agent step: the plain words with the way forward, and, when
/// there is technical text, a Details fold under it (the one place raw text
/// may show, redacted by the kit) so it can be read, copied and reported.
class AgentErrorNotice extends StatelessWidget {
  const AgentErrorNotice({super.key, required this.failure});

  final AgentFailure failure;

  @override
  Widget build(BuildContext context) {
    final technical = failure.technical;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitNotice.error(
          key: const ValueKey('agents-error'),
          message: failure.words,
        ),
        if (technical != null && technical.trim().isNotEmpty)
          KitDetailsFold(
            foldKey: const ValueKey('agents-error-details'),
            textKey: const ValueKey('agents-error-text'),
            text: technical,
          ),
      ],
    );
  }
}
