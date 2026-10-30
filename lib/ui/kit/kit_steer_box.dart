import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import 'kit_buttons.dart';
import 'kit_field.dart';
import 'kit_icon_button.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_notice.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

/// What the box is doing right now.
enum KitSteerBoxPhase {
  /// Waiting for a message; Send is on once there is text.
  ready,

  /// The phone's speech window is open.
  dictating,

  /// The message is on its way; the field and Send hold still.
  sending,

  /// The message was taken. The box shows a check, then the host closes it.
  sent,
}

/// The small composer a home-screen widget opens: a title that says what
/// sending will do ("Add to this turn", "Send after this reply"), one field,
/// a microphone and Send, and one line for how the last try went.
///
/// A draft is kept in [controller] and stays through a failure. [blockedReason]
/// turns the field and Send off and says why in words (a connection that
/// cannot take it yet); [message] is a neutral line under the field, never
/// the server's own text. Send is also off while there is nothing to send.
///
/// Blocked turns the box to its disabled look with the reason; dictating and
/// sending are its working look; sent is its answered look.
///
/// States: disabled, working, answered.
class KitSteerBox extends StatelessWidget {
  const KitSteerBox({
    super.key,
    required this.title,
    required this.controller,
    required this.hint,
    required this.sendLabel,
    required this.phase,
    required this.onSend,
    required this.onDictate,
    required this.onClose,
    this.caption,
    this.message,
    this.blockedReason,
    this.fieldKey,
    this.micKey,
  });

  /// What sending does, in the person's words.
  final String title;

  /// A line under the title when the mode needs one (a warning).
  final String? caption;

  final TextEditingController controller;
  final String hint;
  final String sendLabel;
  final KitSteerBoxPhase phase;

  /// A neutral line about the last try ("Your message is still here.").
  final String? message;

  /// Why nothing can be sent now; null when sending is possible.
  final String? blockedReason;

  /// Sends the trimmed text. Not called while blocked or empty.
  final ValueChanged<String> onSend;

  /// Opens the phone's speech window.
  final VoidCallback onDictate;

  /// Closes the box.
  final VoidCallback onClose;

  final Key? fieldKey;
  final Key? micKey;

  static const int maxLength = 8000;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final blocked = blockedReason != null;
    final idle = phase == KitSteerBoxPhase.ready && !blocked;
    final reduced = KitMotion.reduced(context);
    final duration = reduced ? Duration.zero : KitMotion.quick;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: KitText(
                title,
                role: KitTextRole.headline,
                tone: KitTextTone.primary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            KitIconButton(
              icon: AppIconography.close,
              tooltip: l10n.widgetV2Cancel,
              onPressed: phase == KitSteerBoxPhase.sending ? null : onClose,
              disabledReason: phase == KitSteerBoxPhase.sending
                  ? l10n.widgetV2Sending
                  : null,
            ),
          ],
        ),
        if (caption != null) ...[
          SizedBox(height: tokens.space1),
          KitText(
            caption!,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ],
        SizedBox(height: tokens.space3),
        KitField(
          label: hint,
          hint: hint,
          controller: controller,
          kind: KitFieldKind.multiline,
          minLines: 2,
          maxLines: 5,
          enabled: idle || phase == KitSteerBoxPhase.dictating,
          disabledReason:
              blockedReason ??
              (phase == KitSteerBoxPhase.sent
                  ? l10n.widgetV2Accepted
                  : l10n.widgetV2Sending),
          inputFormatters: [LengthLimitingTextInputFormatter(maxLength)],
          autofocus: true,
          fieldKey: fieldKey,
        ),
        KitReveal(
          child: message == null
              ? null
              : Padding(
                  padding: EdgeInsets.only(top: tokens.space3),
                  child: KitNotice(
                    message: message!,
                    icon: AppIconography.info,
                    tone: AppStatusTone.neutral,
                  ),
                ),
        ),
        SizedBox(height: tokens.space3),
        Row(
          children: [
            KitIconButton(
              key: micKey,
              icon: AppIconography.mic,
              tooltip: l10n.widgetV2Dictate,
              working: phase == KitSteerBoxPhase.dictating,
              onPressed: idle ? onDictate : null,
              disabledReason: idle
                  ? null
                  : (blockedReason ?? l10n.widgetV2Busy),
            ),
            SizedBox(width: tokens.space3),
            Expanded(
              child: ListenableBuilder(
                listenable: controller,
                builder: (context, _) {
                  final text = controller.text.trim();
                  final sent = phase == KitSteerBoxPhase.sent;
                  return AnimatedSwitcher(
                    duration: duration,
                    switchInCurve: KitMotion.enter,
                    switchOutCurve: KitMotion.exit,
                    child: KitButton.primary(
                      key: ValueKey(sent),
                      label: sent
                          ? l10n.widgetV2Accepted
                          : phase == KitSteerBoxPhase.sending
                          ? l10n.widgetV2Sending
                          : sendLabel,
                      icon: sent ? AppIconography.check : AppIconography.send,
                      working: phase == KitSteerBoxPhase.sending,
                      onPressed: idle && text.isNotEmpty
                          ? () => onSend(text)
                          : null,
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ],
    );
  }
}
