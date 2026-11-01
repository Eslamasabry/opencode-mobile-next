package io.github.eslamasabry.oc.feature.chatcomposer.presentation

/** What the composer screen can ask for (plan 03 §19.7 rule 3). Each action names its target: this conversation. */
interface ComposerActions {
    /** The field's committed words changed (the field owns text, selection and IME composition). */
    fun onTextChanged(text: String)

    fun onSend()

    /** Try again after a failed send. */
    fun onRetry()

    fun onStop()

    /** Arrow up/down at the text's edge; true when the key browsed history (the field must not also move). */
    fun onHistoryKey(older: Boolean, value: FieldValue, suggestionsOpen: Boolean): Boolean

    /** The field applied [FieldEdit] [id] to its state. */
    fun onEditApplied(id: Long)

    fun onModelChip()

    fun onApprovalChip()

    fun onMic()
}

/** Sheets and voice the composer opens; owned by the chat and the routes (plan 07 C13, plan 10). */
interface ComposerOpeners {
    fun openModelPicker()

    fun openApprovals()

    fun startVoice()
}
