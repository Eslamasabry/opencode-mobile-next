part of 'kit_composer.dart';

/// A send that failed, for the composer's top row: what failed in plain
/// words, Retry, and Details when there is technical text to show.
@immutable
class KitComposerFailure {
  const KitComposerFailure({
    required this.words,
    required this.onRetry,
    this.onDetails,
    this.retryKey,
    this.actionLabel,
    this.onAction,
    this.actionKey,
  }) : assert(
         (actionLabel == null) == (onAction == null),
         'KitComposerFailure: an action needs both its label and its callback',
       );

  final String words;
  final VoidCallback onRetry;
  final VoidCallback? onDetails;
  final Key? retryKey;

  /// The one thing that fixes this failure ("Repair"), shown in Retry's
  /// place: retrying first would fail the same way.
  final String? actionLabel;
  final VoidCallback? onAction;
  final Key? actionKey;
}

/// One round button of the composer's tray (plus-menu concept C, owner
/// 20 Oct 2026): what "+" opens in place, a short row attached to the box
/// above the field ("Photos", "Camera", "File", "More").
@immutable
class KitComposerTrayAction {
  const KitComposerTrayAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.disabledReason,
    this.buttonKey,
  });

  final IconData icon;

  /// One short word under the button.
  final String label;

  /// Runs once the tray has closed.
  final VoidCallback onTap;

  /// Non-null: the button stays in its place, dimmed, and says why.
  final String? disabledReason;
  final Key? buttonKey;
}

/// What Send does while a reply is being written.
enum KitComposerDelivery {
  /// A server that cannot steer (OpenCode 1) runs the message when the reply
  /// finishes. Never called steering.
  afterThisReply,

  /// Steer: reaches the agent at its next step (OpenCode 2, Codex, and the
  /// Paseo agents that steer).
  addToThisTurn,

  /// The agent cannot take a message mid-reply: Send stops the reply and
  /// the agent starts again from the message (a Paseo agent that cannot
  /// steer). Never offered as a choice.
  stopAndSend,
}

/// Where voice mode stands (P10.3).
enum KitVoicePhase {
  /// Getting the microphone.
  starting,

  /// Recording; the level meter moves.
  listening,

  /// Turning speech into text.
  transcribing,

  /// A voice turn was sent; waiting for the reply.
  waitingReply,

  /// Reading the reply aloud.
  speakingReply,

  /// The reply could not be read automatically; "Read it aloud" is offered.
  replyReady,

  /// A request needs the person; listening waits.
  paused,

  /// The app may not use the microphone: explained in the mode, with a fix.
  micDenied,

  /// The engine failed: the reason, with a fix.
  failed,
}

/// A teaching tip on one of the composer's buttons ([KitComposer.stopTip],
/// [KitComposer.sendTip]): its one sentence, and the host's word that it
/// faded.
@immutable
class KitComposerTip {
  const KitComposerTip({required this.message, required this.onDismissed});

  final String message;
  final VoidCallback onDismissed;
}

/// Voice mode's state and actions. Non-null [KitComposer.voice] turns the
/// pill into voice mode.
@immutable
class KitComposerVoice {
  const KitComposerVoice({
    required this.phase,
    required this.onExit,
    this.conversation = false,
    this.level,
    this.listeningSince,
    this.onListen,
    this.onStopListening,
    this.onStopSpeaking,
    this.onReadReply,
    this.readRepliesAloud = false,
    this.onReadRepliesAloudChanged,
    this.reason,
    this.fix,
    this.voiceKey,
  });

  final KitVoicePhase phase;

  /// Leaves voice mode; the draft keeps what was said.
  final VoidCallback onExit;

  /// True: speech is sent and replies loop (voice conversation); false:
  /// dictation into the draft.
  final bool conversation;

  /// 0..1 while listening ([KitLevelMeter.listen]).
  final ValueListenable<double>? level;

  /// When this recording began: the elapsed time ("0:42"), no cap (P10.3).
  final DateTime? listeningSince;

  /// Starts listening (idle, replyReady, after a reply).
  final VoidCallback? onListen;

  /// Ends this recording: dictation fills the draft, conversation sends.
  final VoidCallback? onStopListening;

  /// speakingReply: stop reading aloud.
  final VoidCallback? onStopSpeaking;

  /// replyReady: read the reply by hand.
  final VoidCallback? onReadReply;

  final bool readRepliesAloud;

  /// Null: the "Read replies aloud" toggle is not shown.
  final ValueChanged<bool>? onReadRepliesAloudChanged;

  /// micDenied / failed: the words; replyReady: why the reply was not read
  /// aloud, when it was not.
  final String? reason;

  /// micDenied: "Allow microphone"; failed: "Try again".
  final KitAction? fix;

  final Key? voiceKey;
}
