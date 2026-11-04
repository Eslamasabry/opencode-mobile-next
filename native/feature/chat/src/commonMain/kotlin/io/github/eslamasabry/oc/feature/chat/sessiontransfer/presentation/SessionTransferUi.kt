package io.github.eslamasabry.oc.feature.chat.sessiontransfer.presentation

import io.github.eslamasabry.oc.core.common.Failure

public enum class TransferNotice {
    Saved,
    Imported,
    NoteSaved,
    NoteRemoved,
    InvalidFile,
    TooLarge,
    Changed,
    ReferenceAdded,
}

public data class SessionTransferUi(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val transferAvailable: Boolean = false,
    val notesAvailable: Boolean = false,
    val referencesAvailable: Boolean = false,
    val sanitize: Boolean = true,
    val reviewTitle: String? = null,
    val reviewCount: Int? = null,
    val redacted: Boolean = false,
    val destination: String = "",
    val imported: Boolean = false,
    val referencePath: String = "",
    val note: String = "",
    val noteLoaded: Boolean = false,
    val noteRevision: Int = 0,
    val noteLimit: Int? = null,
    val notice: TransferNotice? = null,
    val failure: Failure? = null,
)

public interface SessionTransferActions {
    public fun open()

    public fun close()

    public fun sanitize(value: Boolean)

    public fun export()

    public fun choose()

    public fun destination(value: String)

    public fun importReviewed()

    public fun note(value: String)

    public fun loadNote()

    public fun saveNote()

    public fun referencePath(value: String)

    public fun addReference()

    public fun removeNote()
}
