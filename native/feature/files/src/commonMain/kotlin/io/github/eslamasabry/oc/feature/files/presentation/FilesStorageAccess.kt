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
}
