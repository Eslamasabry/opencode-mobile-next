package io.github.eslamasabry.oc.feature.files.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * Why a local shared-storage listing is hidden. Remote and app-private projects never request phone
 * access.
 */
public enum class FilesStorageBlock {
    None,
    AppPermission,
    SelectedFolder,
}

public enum class FilesStorageResult {
    Granted,
    RestartRequired,
    Cancelled,
    Failed,
}

/**
 * The target is checked again after Android Settings returns; no permission grant restarts a
 * server.
 */
public interface FilesStorageAccess {
    public suspend fun block(ref: ConversationRef, directory: String?): FilesStorageBlock

    public suspend fun request(
        ref: ConversationRef,
        directory: String?,
        block: FilesStorageBlock,
    ): FilesStorageResult

    /**
     * This phone's own storage as the Files page's Phone storage row sees it: [FilesStorageBlock.None] when All files
     * access is on, another value when it is off; null when this connection is not this phone's (its own OpenCode or
     * the phone agents' helper), where no such row is offered.
     */
    public suspend fun phoneStorage(ref: ConversationRef): FilesStorageBlock? = null

    /** Opens Android's page for All files access; a request to the person, never a grant. */
    public suspend fun requestPhoneStorage(ref: ConversationRef): FilesStorageResult = FilesStorageResult.Failed
}
